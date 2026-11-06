package com.example.portfolio.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.portfolio.europepmc.ArticleRecord;
import com.example.portfolio.europepmc.ArticleRecord.FundingReference;
import com.example.portfolio.europepmc.ArticleSearchResult;
import com.example.portfolio.europepmc.GrantRecord;
import com.example.portfolio.model.FundingMatch;
import com.example.portfolio.model.LookupStatus;
import com.example.portfolio.model.PortfolioResponse.Publication;

// Linking rules, no I/O. A publication is "confirmed" only if its own grantsList has the same
// grant ID under the same funder; weaker matches are kept but labelled, never shown as verified.
public final class GrantLinker {

    private GrantLinker() {
    }

    public static List<Publication> toPublications(GrantRecord grant, ArticleSearchResult result, int max) {
        List<Publication> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ArticleRecord article : result.articles()) {
            if (out.size() >= max) {
                break;
            }
            String key = publicationKey(article);
            // Within one grant the same article should not appear twice; across grants it may,
            // legitimately, so de-duplication is only done here, per grant.
            if (key != null && !seen.add(key)) {
                continue;
            }
            out.add(new Publication(
                    key,
                    article.source(),
                    article.id(),
                    article.pmid(),
                    article.pmcid(),
                    article.doi(),
                    article.title(),
                    article.journal(),
                    article.pubYear(),
                    classify(grant, article)));
        }
        return out;
    }

    public static LookupStatus statusFor(List<Publication> publications) {
        if (publications.isEmpty()) {
            return LookupStatus.NONE_FOUND;
        }
        boolean anyConfirmed = publications.stream()
                .anyMatch(p -> p.fundingMatch() == FundingMatch.FUNDER_CONFIRMED);
        return anyConfirmed ? LookupStatus.FOUND : LookupStatus.UNCERTAIN;
    }

    static FundingMatch classify(GrantRecord grant, ArticleRecord article) {
        List<FundingReference> sameId = article.fundingReferences().stream()
                .filter(ref -> sameGrantId(grant.id(), ref.grantId()))
                .toList();
        if (sameId.isEmpty()) {
            return FundingMatch.GRANT_NOT_LISTED;
        }
        if (sameId.stream().anyMatch(ref -> sameFunder(grant.funder(), ref.agency()))) {
            return FundingMatch.FUNDER_CONFIRMED;
        }
        if (sameId.stream().anyMatch(ref -> ref.agency() == null) || grant.funder() == null) {
            return FundingMatch.FUNDER_NOT_REPORTED;
        }
        return FundingMatch.FUNDER_DIFFERS;
    }

    // SOURCE:ID, else DOI:<doi>, else null
    static String publicationKey(ArticleRecord article) {
        if (article.source() != null && article.id() != null) {
            return article.source() + ":" + article.id();
        }
        if (article.doi() != null) {
            return "DOI:" + article.doi().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    // Equal after normalising, or one is the other + "/suffix" (articles cite Wellcome 088130 as
    // 088130/Z/09/Z). Min 5 chars so a prefix like "MR" can't match "MR/K006584/1".
    static boolean sameGrantId(String a, String b) {
        String na = normaliseId(a);
        String nb = normaliseId(b);
        if (na == null || nb == null) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        return suffixVariant(a.strip(), b.strip()) || suffixVariant(b.strip(), a.strip());
    }

    private static boolean suffixVariant(String shortId, String longId) {
        return shortId.length() >= 5 && !shortId.contains("/")
                && longId.toUpperCase(Locale.ROOT).startsWith(shortId.toUpperCase(Locale.ROOT) + "/");
    }

    static String normaliseId(String id) {
        if (id == null) {
            return null;
        }
        String n = id.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return n.isEmpty() ? null : n;
    }

    // Loose: equal, or one contains the other ("Wellcome" vs "Wellcome Trust"). Heuristic -
    // a funder ID mapping would be better.
    static boolean sameFunder(String a, String b) {
        String na = normaliseName(a);
        String nb = normaliseName(b);
        if (na == null || nb == null) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        String shorter = na.length() <= nb.length() ? na : nb;
        String longer = shorter == na ? nb : na;
        return shorter.length() >= 4 && (" " + longer + " ").contains(" " + shorter + " ");
    }

    public static String normaliseName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip()
                .replaceFirst("^the ", "");
        return n.isEmpty() ? null : n;
    }
}
