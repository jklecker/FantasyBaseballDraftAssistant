package com.example.fantasybaseball.util;

/**
 * Fuzzy string matcher for player name search.
 * Supports substring, word-prefix, character-subsequence, and edit-distance matching.
 *
 * <p>Returns a score in [0.0, 1.0]:
 * <ul>
 *   <li>1.0  – exact substring match ("trout" in "mike trout")</li>
 *   <li>0.9  – all space-separated query tokens match ("mik tro" → "mike trout")</li>
 *   <li>0.85 – any word in text starts with query ("tro" → "trout")</li>
 *   <li>0.6  – query characters appear in order as a subsequence</li>
 *   <li>0.5  – edit distance ≤ 1 against a word prefix</li>
 *   <li>0.4  – edit distance ≤ 2 against a full word (longer queries only)</li>
 *   <li>0.0  – no reasonable match</li>
 * </ul>
 */
public class FuzzyMatcher {

    private FuzzyMatcher() {}

    /**
     * Compute a fuzzy match score between {@code text} and {@code query}.
     * Both inputs are expected to be already lowercased by the caller.
     *
     * @return score in [0.0, 1.0], or 0.0 when there is no reasonable match
     */
    public static double score(String text, String query) {
        if (text == null || query == null || query.isEmpty()) return 0.0;

        // 1. Exact substring match — highest confidence
        if (text.contains(query)) return 1.0;

        // 2. All space-separated query tokens appear in text
        //    e.g. "mik tro" → "mike trout"
        String[] queryTokens = query.split("\\s+");
        if (queryTokens.length > 1) {
            boolean allMatch = true;
            for (String t : queryTokens) {
                if (!text.contains(t) && !anyWordStartsWith(text, t)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return 0.9;
        }

        // 3. Any word in text starts with the full query
        //    e.g. "tro" → word "trout"
        if (anyWordStartsWith(text, query)) return 0.85;

        // 4. Characters of query appear in order as a subsequence
        //    e.g. "mktrt" ⊆ "mike trout" in order (lenient)
        if (query.length() >= 3 && isSubsequence(query, text)) return 0.6;

        // 5. Edit-distance checks (handles typos / transpositions)
        if (query.length() >= 3) {
            for (String word : text.split("\\s+")) {
                // Compare query against word prefix of comparable length
                int compareLen = Math.min(query.length() + 1, word.length());
                String prefix = word.substring(0, compareLen);
                if (editDistance(query, prefix) <= 1) return 0.5;
                // Longer queries: also allow distance-2 against full word
                if (query.length() > 4 && editDistance(query, word) <= 2) return 0.4;
            }
        }

        return 0.0;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static boolean anyWordStartsWith(String text, String prefix) {
        for (String word : text.split("\\s+")) {
            if (word.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if every character of {@code query} appears
     * in {@code text} in the same relative order (subsequence check).
     */
    private static boolean isSubsequence(String query, String text) {
        int qi = 0;
        for (int i = 0; i < text.length() && qi < query.length(); i++) {
            if (text.charAt(i) == query.charAt(qi)) qi++;
        }
        return qi == query.length();
    }

    /**
     * Classic dynamic-programming Levenshtein (edit) distance.
     * Made {@code public} so tests can call it directly.
     */
    public static int editDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                    Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[m][n];
    }
}

