package com.example.portfolio.europepmc;

/**
 * Builds Europe PMC (Lucene/Solr-style) query strings from untrusted user input.
 *
 * <p>The user's text is always placed inside a double-quoted phrase. Inside a quoted phrase the
 * only characters with special meaning are the double quote and the backslash, so escaping those
 * two is enough to stop input like {@code Cambridge" OR aff:"Oxford} from changing the query
 * structure. Operators such as AND/OR, wildcards and brackets are harmless inside the phrase.
 */
public final class QuerySyntax {

    private QuerySyntax() {
    }

    /** {@code aff:"<name>"} for the Grants (Grist) API. */
    public static String affiliationQuery(String institution) {
        return "aff:" + quotedPhrase(institution);
    }

    /** {@code GRANT_ID:"<id>"} for the Articles API. */
    public static String grantIdQuery(String grantId) {
        return "GRANT_ID:" + quotedPhrase(grantId);
    }

    static String quotedPhrase(String raw) {
        String collapsed = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        StringBuilder sb = new StringBuilder(collapsed.length() + 2).append('"');
        for (char c : collapsed.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('"').toString();
    }
}
