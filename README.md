# Europe PMC Institution Funding Portfolio

A small Spring Boot REST API. Given a university name, it finds funding awards in the Europe PMC
Grants API (Grist) and adds the publications linked to each award, found through the Europe PMC
Articles API. The caller never needs a grant ID or Grist query syntax.

Java 21 · Spring Boot 3.5 · Maven · no database · no extra runtime dependencies beyond Spring Web.

## Build, run, test

```bash
# Docker
docker build -t institution-portfolio .
docker run --rm -p 8080:8080 institution-portfolio

# Without Docker (JDK 21 + Maven)
mvn test                      # 36 tests, no network needed
mvn spring-boot:run
```

## Example requests

```bash
curl 'http://localhost:8080/institution-portfolio?institution=University%20of%20Cambridge&limit=5&publicationsPerGrant=3'
curl 'http://localhost:8080/institution-portfolio?institution=University%20of%20Manchester'
curl 'http://localhost:8080/institution-portfolio?institution=Imperial%20College%20London&limit=10&publicationsPerGrant=5'

# Errors
curl -i 'http://localhost:8080/institution-portfolio?institution=Cambridge&limit=50'   # 400
curl -i 'http://localhost:8080/institution-portfolio'                                  # 400
```

## API

`GET /institution-portfolio`

| Parameter              | Required | Default | Range  | Notes                                    |
|------------------------|----------|---------|--------|------------------------------------------|
| `institution`          | yes      | –       | 2–200 chars | Trimmed; inner whitespace collapsed; control characters rejected |
| `limit`                | no       | 5       | 1–10   | Maximum grants returned                  |
| `publicationsPerGrant` | no       | 3       | 1–5    | Maximum publications per grant           |

The defaults keep a call to about 6 upstream requests and a small payload. Callers can still go up
to the limits in the brief.

**HTTP status codes**

| Status | When |
|--------|------|
| 200 | Grant search succeeded. This includes zero grants, and grants whose publication lookups failed. |
| 400 | Invalid or missing parameters (`application/problem+json`, with an `errors` list). |
| 502 | The Grants API failed, returned an HTTP error or returned unreadable JSON. |
| 504 | The Grants API timed out. |

A failed publication lookup does **not** fail the request. It is reported on that grant (see below),
because the other grants' results are still valid.

### Response shape (abridged, real data)

```json
{
  "request":     { "institution": "University of Cambridge", "limit": 5, "publicationsPerGrant": 3,
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

**Publication lookup status, per grant**

| Status | Meaning |
|--------|---------|
| `FOUND` | At least one returned publication lists this grant ID **under the same funder**. |
| `UNCERTAIN` | Publications matched the grant ID, but none confirm the funder. |
| `NONE_FOUND` | The Articles API answered and returned no publications. This is not proof that none exist. |
| `LOOKUP_FAILED` | The Articles API call failed or timed out. `error` gives the reason. Nothing is known about publications. |
| `NOT_ATTEMPTED` | The grant record had no ID. |

**`fundingMatch`, per publication:** `FUNDER_CONFIRMED`, `FUNDER_NOT_REPORTED`,
`FUNDER_DIFFERS` or `GRANT_NOT_LISTED`. See "Grant linking" below.

## Design

```
PortfolioController  ── validates input, normalises whitespace
  └─ PortfolioService ── 1 Grist call, then N Articles calls on a bounded pool, collected in order
       ├─ GristGrantsClient / EuropePmcArticlesClient ── build URIs, parse JSON tolerantly
       │    └─ EuropePmcHttp ── single place that maps every HTTP failure to UpstreamException
       └─ GrantLinker ── pure functions: grant/publication matching, status, identity
```

- **Clients sit behind small interfaces** (`GrantsClient`, `ArticlesClient`), so the service can
  be tested with in-memory fakes.
- **Upstream JSON is read as a `JsonNode` tree, not mapped to fixed classes.** Grist JSON is
  converted from XML: a single result comes back as an object rather than an array, and repeated
  elements become arrays. The `Json` helper handles these shapes, so a missing field becomes
  `null` instead of failing the request.
- **Publication identity** is `key = "<source>:<id>"` (for example `MED:34113717`,
  `PPR:PPR220327`). Europe PMC itself identifies records this way. PMID, PMCID and DOI are
  returned alongside it. If `source` or `id` is missing, the key falls back to `DOI:<doi>`.

### Institution matching

- The name is placed in a quoted phrase: `aff:"<name>"`. Inside a phrase only `"` and `\` are
  special, so those two are escaped. Input such as `Cambridge" OR aff:"Oxford` therefore cannot
  change the query.
- Grist takes its parameters in the URL path (`/get/query=…&format=json`), not in a query
  string. The whole query is percent-encoded, including `&`, `/` and `=`, so it can't leak into
  another parameter.
- **Limitations.** This is a text search on the award holder's affiliation as recorded by the
  funder:
  - "University of Cambridge" can also match "Cambridge University Hospitals NHS Trust" or
    department-level names.
  - "Cambridge University" or an abbreviation may find a different set of grants.
  - Only participating funders are indexed.
  - Only the first results page is used.

  Each grant carries `institution.exactNameMatch` (a case- and punctuation-insensitive
  comparison), so a client can tell exact matches from variants. Nothing is filtered out.

### Grant linking

The service searches the Articles API with `GRANT_ID:"<id>"` and `resultType=core`. The core view
includes each article's own `grantsList`. Then it checks how the article's funding metadata relates
to the grant:

1. Find entries in the article's `grantsList` with the same grant ID. IDs are compared ignoring
   case and punctuation. A `/`-suffix variant also counts: the real data cites Wellcome award
   `088130` both as `088130` and as `088130/Z/09/Z`. The base ID must be at least 5 characters, so
   `MR` cannot match `MR/K006584/1`.
2. If one of those entries has the same funder (loose name comparison: "Wellcome" matches
   "Wellcome Trust"), the publication is `FUNDER_CONFIRMED`.
3. Otherwise it is `FUNDER_NOT_REPORTED`, `FUNDER_DIFFERS` or `GRANT_NOT_LISTED`.

Weaker matches are **returned, but labelled**. Nothing ambiguous is dropped or shown as verified.
A grant is `FOUND` only if at least one publication is `FUNDER_CONFIRMED`; otherwise it is
`UNCERTAIN`.

**De-duplication.** Within one grant, duplicates are removed by `key`. Across grants they are
**kept**: one paper can acknowledge several awards, and each association is a real fact about that
grant. The summary reports both `publicationAssociations` and `uniquePublications`, so the overlap
is visible rather than hidden.

### Concurrency and failure handling

- **Bounded and shared.** Publication lookups run on one fixed thread pool
  (`portfolio.max-concurrent-lookups`, default **4**). The pool is a singleton, so the limit applies
  to the whole service, not to each request: two simultaneous portfolio requests still make at most
  4 parallel Articles API calls between them. I chose 4 because it overlaps enough to bring 10
  lookups down from about 10 round trips to about 3, and stays polite to a public API.
- **Overlapping.** All lookups start before the first one is awaited. Results are collected by
  position into a list, so each result lands under the right grant and Grist order is kept,
  whatever order the calls finish in.
- **Timeouts.** Each call has a 3 s connect timeout and a 10 s read timeout. Each request also has
  an overall publication-lookup deadline (default **25 s**). A lookup still queued or running at the
  deadline is cancelled and reported as `LOOKUP_FAILED`. A cancelled task that hasn't started is
  never run, so a request that has already given up doesn't keep calling Europe PMC.
- **Isolation.** Each lookup catches its own failures and turns them into a `LOOKUP_FAILED` entry,
  so one bad grant cannot fail the batch. Only a Grist failure fails the request (502/504), because
  without grants there's nothing to return.
- **Trade-offs.**
  - A platform-thread pool is simple and easy to reason about. With Java 21, virtual threads plus a
    `Semaphore` would also work and would scale better if the limit grew.
  - The pool's queue is unbounded. That's fine at 10 tasks per request, but under heavy load it
    should be bounded and reject work with 503.
  - There are no retries. A retry with backoff on 5xx/timeouts would help, but it would multiply
    load on Europe PMC, so I left it out.

All settings are in `application.yml` and can be overridden with environment variables, for example
`PORTFOLIO_MAX_CONCURRENT_LOOKUPS=2`.

## Tests

`mvn test`: 36 tests, none of which call live Europe PMC.

| Test class | What it covers |
|------------|----------------|
| `QuerySyntaxTest` | Phrase quoting, quote/backslash escaping, whitespace normalisation, an injection attempt |
| `GristGrantsClientTest` | Real HTTP against a local stub server: full path encoding, parsing in order, missing fields, a single record returned as an object, empty results, HTTP 503, non-JSON body, read timeout mapped to `timeout=true` |
| `EuropePmcArticlesClientTest` | Strict encoding of `+`, `&` and `=` in grant IDs; parsing a trimmed **real** Articles API response |
| `GrantLinkerTest` | Confirmed, suffix variant, different funder, no agency, ID not listed, short-prefix guard, status rules, order and cap, dedupe within a grant, DOI fallback key |
| `PortfolioServiceTest` | Failure isolation (`LOOKUP_FAILED` vs `NONE_FOUND`), order kept when completion order is reversed, **maximum in-flight equals the pool size** (and lookups overlap), deadline, grant with no ID, empty search, truncation and sample-only summary, shared publication counted once as unique |
| `PortfolioControllerTest` | Defaults, whitespace normalisation, 400s, 502 vs 504 |

## Limitations

- Only the first Grist results page is fetched. `limit` takes the first N records from it.
- `FUNDER_CONFIRMED` means Europe PMC's metadata agree. It doesn't mean the link is correct (see
  the review below).
- Only the first `publicationsPerGrant` hits are classified. A grant marked `UNCERTAIN` could have
  confirmed publications further down the list.
- Funder names are compared with a heuristic. A curated funder-alias table, or FundRef/ROR IDs
  where available, would be more reliable.
- There's no caching, rate limiting, auth or metrics. Those were out of scope for the time box.

## Review of my solution

**Which design decision worked particularly well?**
Moving the matching rules into `GrantLinker` as pure functions, separate from I/O and concurrency.
Each part can be tested on its own: linking rules with plain records, concurrency with fakes that
count in-flight calls, and HTTP with a local stub. The four-way per-publication `fundingMatch`
also made the brief's rule ("do not silently treat an ambiguous match as verified") easy to apply
and easy for a client to see.

**What would you change or improve with more time?**
- Follow Grist pagination, so "first N" can mean first N matching an exact institution name.
- Narrow the Articles query with the funder (`GRANT_AGENCY`) to cut cross-funder noise at source.
- Add a short-lived cache of grant-ID lookups, since the same awards recur across institutions.
- Add retry with jittered backoff for idempotent 5xx/timeout failures.
- Add Micrometer metrics for upstream latency and error rate.
- Generate an OpenAPI description.

**Which assumption about Europe PMC data would you validate before production?**
That an article's `grantsList` (grant ID plus agency) is accurate enough to count as "confirmed".
In live testing, MRC grant `MR/R006504/1` (a neuroscience award) returned, as `FUNDER_CONFIRMED`,
a paper on the efficiency of the Indian judiciary. The metadata agree but the link looks wrong. I'd
measure precision on a labelled sample per funder. I'd also check how consistently Grist funder
names line up with Articles `agency` values, and how affiliation names vary for large
universities.

**Which piece of technical debt did you deliberately accept?**
- Funder matching uses a normalised-substring heuristic instead of a curated funder-ID mapping.
  It's good enough to separate the common cases, and it's isolated in one method
  (`GrantLinker.sameFunder`), so it can be replaced later.
- I stayed on Spring Boot 3.5 rather than 4.x, to avoid the Jackson 3 migration within the time
  box.
- The Grist response format was coded from the documented structure and is covered by fixtures.
  Those fixtures should be refreshed from a captured live response.
