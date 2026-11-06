package com.example.portfolio.europepmc;

// User input always goes inside a quoted phrase. Inside a phrase only " and \ are special,
// so escaping those two stops input like  Cambridge" OR aff:"Oxford  changing the query.
public final class QuerySyntax {

    private QuerySyntax() {
    }

    public static String affiliationQuery(String institution) {
        return "aff:" + quotedPhrase(institution);
    }

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
