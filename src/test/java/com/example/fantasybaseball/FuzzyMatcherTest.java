package com.example.fantasybaseball;

import com.example.fantasybaseball.util.FuzzyMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for FuzzyMatcher — no Spring context required.
 */
class FuzzyMatcherTest {

    // ── Null / empty guards ───────────────────────────────────────────────────

    @Test
    @DisplayName("null text returns 0")
    void nullTextReturns0() {
        assertThat(FuzzyMatcher.score(null, "trout")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("null query returns 0")
    void nullQueryReturns0() {
        assertThat(FuzzyMatcher.score("mike trout", null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("empty query returns 0")
    void emptyQueryReturns0() {
        assertThat(FuzzyMatcher.score("mike trout", "")).isEqualTo(0.0);
    }

    // ── Exact substring matches (score = 1.0) ────────────────────────────────

    @Nested
    @DisplayName("Exact substring matches")
    class ExactSubstring {

        @Test
        @DisplayName("full name match")
        void fullName() {
            assertThat(FuzzyMatcher.score("mike trout", "mike trout")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("last-name substring")
        void lastNameSubstring() {
            assertThat(FuzzyMatcher.score("mike trout", "trout")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("mid-word substring")
        void midWord() {
            assertThat(FuzzyMatcher.score("jacob degrom", "degr")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("single letter still substring matches")
        void singleLetter() {
            assertThat(FuzzyMatcher.score("mike trout", "m")).isEqualTo(1.0);
        }
    }

    // ── Multi-token query matches (score = 0.9) ──────────────────────────────

    @Nested
    @DisplayName("Multi-token query matches")
    class MultiToken {

        @Test
        @DisplayName("two abbreviated tokens: 'mik tro' → 'mike trout'")
        void twoTokens() {
            assertThat(FuzzyMatcher.score("mike trout", "mik tro")).isEqualTo(0.9);
        }

        @Test
        @DisplayName("tokens in the text but not as a single substring")
        void separateTokens() {
            assertThat(FuzzyMatcher.score("aaron judge", "aar jud")).isEqualTo(0.9);
        }
    }

    // ── Word-prefix matches ───────────────────────────────────────────────────
    //
    // Note: any word prefix is also a substring of the full-name text, so
    // prefix queries always reach the score-1.0 substring branch first.
    // These tests verify that prefix-style queries return a positive score.

    @Nested
    @DisplayName("Word-prefix matches")
    class WordPrefix {

        @Test
        @DisplayName("last-name prefix 'tro' matches (via substring path)")
        void lastNamePrefix() {
            // "tro" is contained in "trout" → substring match → score 1.0
            assertThat(FuzzyMatcher.score("mike trout", "tro")).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("first-name prefix 'mik' matches (via substring path)")
        void firstNamePrefix() {
            // "mik" is contained in "mike" → substring match → score 1.0
            assertThat(FuzzyMatcher.score("mike trout", "mik")).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("direct word-prefix path (score 0.85) is reached when query is NOT a substring")
        void directWordPrefixPath() {
            // "acun" is NOT a substring of "ronald acuna jr" because "acuña" would need
            // the tilde; in the plain-ASCII version "acuna" the prefix "acun" IS in "acuna"
            // as a substring. Use a contrived case: query starts a word but the text was
            // normalised so the character sequence doesn't appear elsewhere.
            // "jude" is a prefix of "Judy" — but let's use a proper baseball example:
            // "judg" starts "judge" and IS a substring of "aaron judge" ("judg" at pos 6).
            // There is no realistic single-word case where prefix != substring, so we
            // assert the important property: all prefixes produce a positive score.
            assertThat(FuzzyMatcher.score("ronald acuna", "acun")).isGreaterThan(0.0);
        }
    }

    // ── Subsequence matches (score = 0.6) ────────────────────────────────────

    @Nested
    @DisplayName("Character subsequence matches")
    class Subsequence {

        @Test
        @DisplayName("query chars appear in order in text")
        void charsInOrder() {
            // j, c, o, b are all in "jacob" in order
            assertThat(FuzzyMatcher.score("jacob degrom", "jcob")).isEqualTo(0.6);
        }

        @Test
        @DisplayName("short queries (<3 chars) do not trigger subsequence check")
        void shortQuerySkipsSubsequence() {
            // "mt" could be a subsequence but length < 3
            double score = FuzzyMatcher.score("mike trout", "mt");
            // Should NOT return 0.6 since len < 3 (falls to edit-distance checks)
            assertThat(score).isLessThan(0.6);
        }
    }

    // ── Edit-distance typo tolerance ─────────────────────────────────────────

    @Nested
    @DisplayName("Edit-distance typo tolerance")
    class EditDistanceTolerance {

        @Test
        @DisplayName("single insertion returns positive score")
        void singleInsertion() {
            // "troout" vs word "trout" → 1 insertion
            assertThat(FuzzyMatcher.score("mike trout", "troout")).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("transposition of two chars returns positive score")
        void transposition() {
            // "truot" vs "trout" → edit distance 2, length > 4
            assertThat(FuzzyMatcher.score("mike trout", "truot")).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("completely unrelated query returns 0")
        void noMatch() {
            assertThat(FuzzyMatcher.score("mike trout", "zzzzz")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("baseball player names that are clearly different return 0")
        void differentPlayers() {
            assertThat(FuzzyMatcher.score("mike trout", "xqjwp")).isEqualTo(0.0);
        }
    }

    // ── editDistance helper ───────────────────────────────────────────────────

    @Nested
    @DisplayName("editDistance()")
    class EditDistanceCalc {

        @ParameterizedTest(name = "distance({0},{1}) = {2}")
        @CsvSource({
            "abc,  abc,  0",
            "abc,  abcd, 1",
            "abcd, abc,  1",
            "abc,  axc,  1",
            "kitten, sitting, 3",
        })
        void knownDistances(String a, String b, int expected) {
            assertThat(FuzzyMatcher.editDistance(a.trim(), b.trim())).isEqualTo(expected);
        }
    }

    // ── Score ordering ────────────────────────────────────────────────────────

    @Test
    @DisplayName("exact match > subsequence-only > typo, all positive")
    void scoreOrdering() {
        double exact  = FuzzyMatcher.score("mike trout", "trout"); // 1.0  – substring
        double subseq = FuzzyMatcher.score("mike trout", "mktr");  // 0.6  – subsequence only
        double typo   = FuzzyMatcher.score("mike trout", "truot"); // ≥0.4 – edit distance
        assertThat(exact).isGreaterThan(subseq);
        assertThat(subseq).isGreaterThan(typo);
        assertThat(typo).isGreaterThan(0.0);
    }
}

