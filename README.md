# Europe PMC – institution funding portfolio

Small Spring Boot service: give it a university name, it finds awards in the Europe PMC Grants API
(Grist) and attaches publications linked to each award from the Articles API. The caller doesn't
need grant IDs or Grist query syntax.

Java 21, Spring Boot 3.5, Maven. No DB, no extra libraries beyond Spring Web + validation.

## Run it

```bash
docker build -t institution-portfolio .
docker run --rm -p 8080:8080 institution-portfolio
```

or, with JDK 21 + Maven:

```bash
mvn test              # no network needed
mvn spring-boot:run
```

## Try it

```bash
curl 'localhost:8080/institution-portfolio?institution=University%20of%20Cambridge&limit=5&publicationsPerGrant=3'
curl 'localhost:8080/institution-portfolio?institution=University%20of%20Manchester'
curl 'localhost:8080/institution-portfolio?institution=Imperial%20College%20London&limit=10&publicationsPerGrant=5'

curl -i 'localhost:8080/institution-portfolio?institution=Cambridge&limit=50'   # 400
```

## Endpoint

`GET /institution-portfolio`

| param | default | allowed |
|---|---|---|
| `institution` | required | 2–200 chars, trimmed, no control chars |
| `limit` | 5 | 1–10 grants |
| `publicationsPerGrant` | 3 | 1–5 |

Defaults are on the small side (one request ≈ 6 upstream calls); the brief's maximums still work.

Status codes: **200** when the grant search worked (even with 0 grants or some failed publication
lookups), **400** bad params, **502** Grist error / unreadable response, **504** Grist timeout.
Errors are `application/problem+json`.

A failed publication lookup doesn't fail the request – it's reported on that grant, because the
other grants are still useful.

### Response (trimmed, real Articles data)

```json
{
  "request": { "institution": "University of Cambridge", "limit": 5, "publicationsPerGrant": 3,
               "grantsQuery": "aff:\"University of Cambridge\"" },
  "grantSearch": { "totalHitsReported": 3127, "returned": 5, "truncated": true },
  "grants": [{
    "id": "088130", "title": "...", "funder": "Wellcome Trust",
    "institution": { "name": "University of Cambridge", "department": null, "exactNameMatch": true },
    "publicationLookup": { "status": "FOUND", "query": "GRANT_ID:\"088130\"",
                           "totalHitsReported": 337, "returned": 3, "error": null },
    "publications": [{
      "key": "MED:34113717", "source": "MED", "id": "34113717", "pmid": "34113717",
      "pmcid": "PMC7610949", "doi": "10.5334/cpsy.70", "title": "...",
      "journal": "Computational psychiatry (Cambridge, Mass.)", "pubYear": "2021",
      "fundingMatch": "FUNDER_CONFIRMED" }]
  }],
  "summary": {
    "scope": "Counts cover only the grants and publications in this response, not the institution's full portfolio.",
    "grantsReturned": 5,
    "grantsByFunder": { "Wellcome Trust": 3, "Medical Research Council": 1, "(funder not reported)": 1 },
    "grantsByLookupStatus": { "FOUND": 3, "NONE_FOUND": 1, "NOT_ATTEMPTED": 1 },
    "publicationAssociations": 9,
    "uniquePublications": 9
  },
  "caveats": ["The Grants API reported 3127 matching records; only the first 5 are included.", "..."]
}
```

Per-grant `publicationLookup.status`:

- `FOUND` – at least one publication lists this grant ID under the same funder
- `UNCERTAIN` – hits for the ID, but none confirm the funder
- `NONE_FOUND` – the API answered with nothing (not proof there are no papers)
- `LOOKUP_FAILED` – call failed or timed out, see `error`
- `NOT_ATTEMPTED` – grant had no ID

Per-publication `fundingMatch`: `FUNDER_CONFIRMED`, `FUNDER_NOT_REPORTED`, `FUNDER_DIFFERS`,
`GRANT_NOT_LISTED`.

## How it works

```
PortfolioController   validation
 └ PortfolioService   1 Grist call, then N article lookups on a bounded pool
    ├ GristGrantsClient / EuropePmcArticlesClient   URL building + tolerant parsing
    │   └ EuropePmcHttp   every HTTP failure -> UpstreamException
    └ GrantLinker         matching rules (pure functions)
```

**Publication identity** is `source:id` (e.g. `MED:34113717`, `PPR:PPR220327`) – that's how
Europe PMC identifies records itself. PMID/PMCID/DOI are returned too; if source/id are missing the
key falls back to `DOI:<doi>`.

**Parsing.** Grist's JSON is converted from XML, so a single result comes back as an object rather
than an array, and text with attributes looks like `{"$": "..."}`. I read both APIs as `JsonNode`
trees with a few helpers instead of fixed DTOs, so a missing field is just `null`.

### Institution matching

- Query is `aff:"<name>"`. Inside a quoted phrase only `"` and `\` matter, so I escape those –
  `Cambridge" OR aff:"Oxford` stays a literal phrase.
- Grist takes parameters in the path (`/get/query=…&format=json`), so the whole query is
  percent-encoded, including `&`, `/` and `=`.
- It's a text match on the award holder's affiliation as the funder recorded it. "University of
  Cambridge" also pulls in things like hospital trusts or department-level names; "Cambridge
  University" may give a different set. I don't filter, but each grant has
  `institution.exactNameMatch` so the client can tell exact matches from variants.

### Linking grants to publications

Search Articles with `GRANT_ID:"<id>"` (`resultType=core`, which includes each article's
`grantsList`), then check the article's own funding metadata:

1. Look for the same grant ID in its grantsList – ignoring case/punctuation, and allowing a
   `/suffix` variant. Real data cites Wellcome `088130` as both `088130` and `088130/Z/09/Z`.
   (Minimum 5 chars, so `MR` can't match `MR/K006584/1`.)
2. Same ID + same funder → `FUNDER_CONFIRMED`. Otherwise one of the weaker labels.

I keep the weaker matches but label them rather than dropping them or presenting them as verified.
A grant is only `FOUND` if something is confirmed.

**Duplicates:** removed within a grant, kept across grants – one paper can genuinely acknowledge
several awards. The summary shows both `publicationAssociations` and `uniquePublications`.

### Concurrency and failures

- Article lookups run on one shared fixed pool of **4** threads (`portfolio.max-concurrent-lookups`).
  It's a singleton, so the cap is for the whole service – two simultaneous requests still make at
  most 4 parallel calls to Europe PMC. 4 gets 10 lookups down to ~3 round trips without hammering
  a public API.
- All lookups are started before waiting on any; results are read back by index, so each lands
  under its own grant in Grist order regardless of completion order.
- Timeouts: 3 s connect, 10 s read per call, plus a 25 s deadline for all of a request's lookups.
  Anything unfinished at the deadline is cancelled and marked `LOOKUP_FAILED`. A cancelled task that
  hasn't started never runs, so we don't keep calling Europe PMC after we've responded.
- Each lookup catches its own errors. Only a Grist failure fails the whole request.

Trade-offs: plain platform-thread pool because it's easy to reason about (virtual threads + a
`Semaphore` would be the Java 21 alternative). The pool queue is unbounded – fine for ≤10 tasks per
request, but under real load I'd bound it and return 503. No retries, since they multiply load on
the upstream.

All of these are in `application.yml` and overridable via env vars
(e.g. `PORTFOLIO_MAX_CONCURRENT_LOOKUPS=2`).

## Tests

`mvn test` – nothing hits live Europe PMC. Client tests run against a small local HTTP stub, so
encoding and timeouts are real.

- `QuerySyntaxTest` – quoting/escaping, injection attempt
- `GristGrantsClientTest` – path encoding, parsing order, missing fields, single-record-as-object,
  empty results, 503, non-JSON body, read timeout → 504 path
- `EuropePmcArticlesClientTest` – `+ & =` encoding in grant IDs; parsing a trimmed **real** response
- `GrantLinkerTest` – each match type, suffix variant, short-prefix guard, status rules, ordering, dedupe
- `PortfolioServiceTest` – failure isolation, order kept with reversed completion, max in-flight ==
  pool size, deadline, no-ID grant, empty search, sample-only summary
- `PortfolioControllerTest` – defaults, 400s, 502 vs 504

## Limitations

- First Grist page only; `limit` takes the first N.
- Only the first `publicationsPerGrant` hits are classified, so a grant marked `UNCERTAIN` could
  have confirmed papers further down.
- Funder matching is a name heuristic.
- `FUNDER_CONFIRMED` means Europe PMC's metadata agree – not that the link is right (see below).
- No caching, rate limiting, auth or metrics.

## Review of my solution

**What worked well?**
Keeping the matching rules (`GrantLinker`) as pure functions, away from HTTP and threading. It made
each concern testable on its own – rules with plain records, concurrency with fakes that count
in-flight calls, HTTP with a local stub. The per-publication `fundingMatch` label also made "don't
treat an ambiguous match as verified" easy to apply and visible to the client.

**What would I improve with more time?**
Page through Grist so "first N" can mean first N with an exact institution match; add
`GRANT_AGENCY` to the article query to cut cross-funder noise at the source; a short cache for
grant-ID lookups; retry with backoff on 5xx/timeouts; Micrometer metrics on upstream latency/errors;
an OpenAPI spec.

**Which assumption about the data would I validate first?**
That an article's grantsList is accurate enough to call a link "confirmed". While testing, MRC
grant `MR/R006504/1` (neuroscience) returned a paper on the efficiency of the Indian judiciary as
`FUNDER_CONFIRMED` – metadata agree, link looks wrong. I'd measure precision on a labelled sample
per funder, and check how consistently Grist funder names match Articles `agency` values.

**What technical debt did I accept?**
The funder-name heuristic instead of a proper funder-ID mapping – it's in one method
(`GrantLinker.sameFunder`) so it's easy to swap. I also stayed on Boot 3.5 rather than 4.x to avoid
the Jackson 3 migration in the time box.
