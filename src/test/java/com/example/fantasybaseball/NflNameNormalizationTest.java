package com.example.fantasybaseball;

import com.example.fantasybaseball.service.NflDataMergeService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NflNameNormalizationTest {

    @Test void apostropheVariantsNormalizeSame() {
        assertThat(NflDataMergeService.normalize("D'Andre Swift"))
            .isEqualTo(NflDataMergeService.normalize("DAndre Swift"));
    }

    @Test void romanNumeralSuffixStripped() {
        assertThat(NflDataMergeService.normalize("Patrick Mahomes II"))
            .isEqualTo(NflDataMergeService.normalize("Patrick Mahomes"));
    }

    @Test void jrSuffixStripped() {
        assertThat(NflDataMergeService.normalize("Travis Kelce Jr."))
            .isEqualTo(NflDataMergeService.normalize("Travis Kelce"));
    }

    @Test void srSuffixStripped() {
        assertThat(NflDataMergeService.normalize("Odell Beckham Sr."))
            .isEqualTo(NflDataMergeService.normalize("Odell Beckham"));
    }

    @Test void iiiSuffixStripped() {
        assertThat(NflDataMergeService.normalize("Calvin Ridley III"))
            .isEqualTo(NflDataMergeService.normalize("Calvin Ridley"));
    }

    @Test void curlyApostropheSameAsStraight() {
        // Unicode right single quote \u2019
        assertThat(NflDataMergeService.normalize("D\u2019Andre Swift"))
            .isEqualTo(NflDataMergeService.normalize("D'Andre Swift"));
    }

    @Test void dstNormalized() {
        // D/ST suffix is stripped so "Team D/ST" matches bare "Team" across sources
        String result = NflDataMergeService.normalize("San Francisco 49ers D/ST");
        assertThat(result).contains("san francisco 49ers");
        assertThat(result).doesNotContain("/");
        // Both forms normalize to the same team name for matching purposes
        assertThat(result).isEqualTo(NflDataMergeService.normalize("San Francisco 49ers"));
    }

    @Test void nullReturnsEmpty() {
        assertThat(NflDataMergeService.normalize(null)).isEqualTo("");
    }

    @Test void extraWhitespaceCollapsed() {
        assertThat(NflDataMergeService.normalize("Josh  Allen"))
            .isEqualTo("josh allen");
    }

    @Test void jamarrChaseApostropheVariants() {
        assertThat(NflDataMergeService.normalize("Ja'Marr Chase"))
            .isEqualTo(NflDataMergeService.normalize("JaMarr Chase"));
    }
}
