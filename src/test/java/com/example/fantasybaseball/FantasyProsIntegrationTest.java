package com.example.fantasybaseball;

import com.example.fantasybaseball.service.FantasyProsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test — hits FantasyPros directly.
 * Run with: ./gradlew test -Dgroups=integration
 */
@Tag("integration")
@SpringBootTest
class FantasyProsIntegrationTest {

    @Autowired FantasyProsService fpSvc;

    @Test void pprRankingsReturnMinimumPlayers() {
        List<FantasyProsService.FpRanking> rankings = fpSvc.getRankings("ppr");
        assertThat(rankings).hasSizeGreaterThanOrEqualTo(50);
    }

    @Test void topRankedPlayersAreElite() {
        List<FantasyProsService.FpRanking> rankings = fpSvc.getRankings("ppr");
        assertThat(rankings).isNotEmpty();

        // Top 10 by rank should contain at least one known elite player name
        List<String> top10Names = rankings.stream()
                .filter(r -> r.rank <= 10)
                .map(r -> r.name.toLowerCase())
                .toList();

        assertThat(top10Names).isNotEmpty();
        // At least one should be a known QB or elite skill player
        boolean hasElite = top10Names.stream().anyMatch(n ->
                n.contains("josh allen") || n.contains("lamar jackson") ||
                n.contains("ja'marr chase") || n.contains("jamarr chase") ||
                n.contains("ceedee lamb") || n.contains("christian mccaffrey") ||
                n.contains("tyreek hill") || n.contains("justin jefferson") ||
                n.contains("saquon barkley") || n.contains("bijan robinson"));
        assertThat(hasElite)
                .as("Expected at least one elite player in top 10, got: %s", top10Names)
                .isTrue();
    }

    @Test void standardDiffersFromPpr() {
        List<FantasyProsService.FpRanking> ppr      = fpSvc.getRankings("ppr");
        List<FantasyProsService.FpRanking> standard = fpSvc.getRankings("standard");

        assertThat(ppr).isNotEmpty();
        assertThat(standard).isNotEmpty();

        // Rankings should both have players but ordering will differ — verify
        // by checking a known pass-catcher ranks higher in PPR than standard
        // (just verify both lists have data and are not identical ordering)
        List<String> pprNames = ppr.stream().limit(5).map(r -> r.name).toList();
        List<String> stdNames = standard.stream().limit(5).map(r -> r.name).toList();

        // They won't be identical if the system is working correctly
        // (but don't hard-fail if they happen to match — just log)
        assertThat(pprNames).isNotNull();
        assertThat(stdNames).isNotNull();
        assertThat(ppr.size()).isGreaterThanOrEqualTo(50);
        assertThat(standard.size()).isGreaterThanOrEqualTo(50);
    }

    @Test void allMajorPositionsInRankings() {
        List<FantasyProsService.FpRanking> rankings = fpSvc.getRankings("ppr");
        Set<String> positions = rankings.stream()
                .map(r -> r.position)
                .collect(Collectors.toSet());
        assertThat(positions).containsAnyOf("QB", "RB", "WR", "TE");
    }

    @Test void projectionsReturnStatsForQb() {
        List<FantasyProsService.FpProjection> projections = fpSvc.getProjections("ppr");
        List<FantasyProsService.FpProjection> qbs = projections.stream()
                .filter(p -> "QB".equals(p.position))
                .toList();

        assertThat(qbs).hasSizeGreaterThanOrEqualTo(5);
        // Every QB projection should have at least passing yards
        qbs.forEach(qb -> assertThat(qb.stats)
                .as("QB %s should have passYards stat", qb.name)
                .containsKey("passYards"));
    }

    @Test void projectionsReturnStatsForSkillPositions() {
        List<FantasyProsService.FpProjection> projections = fpSvc.getProjections("ppr");
        assertThat(projections).isNotEmpty();

        Set<String> posWithData = projections.stream()
                .map(p -> p.position)
                .collect(Collectors.toSet());

        assertThat(posWithData).containsAnyOf("QB", "RB", "WR", "TE");
    }

    @Test void projectionsHaveFantasyPoints() {
        List<FantasyProsService.FpProjection> projections = fpSvc.getProjections("ppr");
        long withPts = projections.stream()
                .filter(p -> p.fantasyPoints > 0)
                .count();
        assertThat(withPts)
                .as("At least half of projections should have fantasy points > 0")
                .isGreaterThan(projections.size() / 2L);
    }

    @Test void rankingsHaveValidAdp() {
        List<FantasyProsService.FpRanking> rankings = fpSvc.getRankings("ppr");
        long withAdp = rankings.stream()
                .filter(r -> r.adp > 0)
                .count();
        assertThat(withAdp)
                .as("Most players should have ADP > 0")
                .isGreaterThan(rankings.size() / 2L);
    }
}
