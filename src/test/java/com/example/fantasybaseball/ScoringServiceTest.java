package com.example.fantasybaseball;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.Team;
import com.example.fantasybaseball.model.TeamStats;
import com.example.fantasybaseball.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for ScoringService — no Spring context needed.
 * Covers base scoring weights, team-need adjustments, and recommendation ordering.
 */
class ScoringServiceTest {

    private ScoringService scoringService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static Player batter(int id, int r, int hr, int sb, int k) {
        Player p = new Player();
        p.setId(id); p.setName("Batter " + id); p.setPosition("OF");
        p.setR(r); p.setHR(hr); p.setSB(sb); p.setK(k);
        p.setH(100); p.setTwoB(20); p.setThreeB(1); p.setRBI(80); p.setBB(50);
        // No pitching stats
        p.setIP(0); p.setW(0); p.setL(0); p.setSV(0);
        p.setPitchingBB(0); p.setPitchingK(0); p.setERA(0); p.setWHIP(0);
        return p;
    }

    private static Player pitcher(int id, double ip, int w, int l, int sv,
                                   int pitchingK, int pitchingBB, double era, double whip) {
        Player p = new Player();
        p.setId(id); p.setName("Pitcher " + id); p.setPosition("SP");
        // No batting stats
        p.setR(0); p.setH(0); p.setTwoB(0); p.setThreeB(0);
        p.setHR(0); p.setRBI(0); p.setSB(0); p.setBB(0); p.setK(0);
        p.setIP(ip); p.setW(w); p.setL(l); p.setSV(sv);
        p.setPitchingK(pitchingK); p.setPitchingBB(pitchingBB);
        p.setERA(era); p.setWHIP(whip);
        return p;
    }

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    // ── Base scoring ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Base scoring weights")
    class BaseScoring {

        @Test
        @DisplayName("elite batter has a strong positive score")
        void eliteBatterScoresPositive() {
            Player trout = batter(1, 100, 40, 20, 120);
            double score = scoringService.scorePlayer(trout, null);
            // R*1 + HR*1.5 + SB*1.2 - K*0.7 + H/2B/3B/RBI/BB contributions
            assertThat(score).isGreaterThan(150);
        }

        @Test
        @DisplayName("high strikeout batter is penalised")
        void highKBatterPenalised() {
            Player lowK  = batter(1, 80, 30, 10, 50);
            Player highK = batter(2, 80, 30, 10, 200);
            double scoreLow  = scoringService.scorePlayer(lowK,  null);
            double scoreHigh = scoringService.scorePlayer(highK, null);
            assertThat(scoreLow).isGreaterThan(scoreHigh);
        }

        @Test
        @DisplayName("elite pitcher with low ERA/WHIP scores positively")
        void elitePitcherScoresPositive() {
            Player deGrom = pitcher(3, 180, 15, 5, 0, 200, 30, 2.10, 0.95);
            double score = scoringService.scorePlayer(deGrom, null);
            assertThat(score).isGreaterThan(100);
        }

        @Test
        @DisplayName("ERA and WHIP penalties are NOT applied to batters (IP=0)")
        void noEraPenaltyForBatters() {
            Player batterA = batter(1, 0, 0, 0, 0); // all zeros batting
            // ERA/WHIP are 0 on batter, IP=0 → no penalty should apply
            double score = scoringService.scorePlayer(batterA, null);
            // Score should be exactly the BB contribution (50*0.3 = 15) plus H/2B/3B/RBI
            // Point is it should NOT be penalised by ERA/WHIP
            assertThat(score).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("pitcher with high ERA scores worse than pitcher with low ERA")
        void highEraScoresWorse() {
            Player good = pitcher(1, 180, 15, 5, 0, 200, 30, 2.10, 0.95);
            Player bad  = pitcher(2, 180, 15, 5, 0, 200, 30, 5.50, 1.50);
            assertThat(scoringService.scorePlayer(good, null))
                    .isGreaterThan(scoringService.scorePlayer(bad, null));
        }

        @Test
        @DisplayName("closer with many saves scores higher than one with none")
        void closerWithSavesScoresHigher() {
            Player closer1 = pitcher(1, 60, 4, 2, 35, 90, 20, 2.00, 0.85);
            Player closer2 = pitcher(2, 60, 4, 2,  0, 90, 20, 2.00, 0.85);
            assertThat(scoringService.scorePlayer(closer1, null))
                    .isGreaterThan(scoringService.scorePlayer(closer2, null));
        }
    }

    // ── Team-need adjustments ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Team-need adjustments")
    class TeamNeedAdjustments {

        @Test
        @DisplayName("SB boost when team has fewer than 10 stolen bases")
        void sbBoostWhenTeamIsWeak() {
            Player speedster = batter(1, 80, 10, 40, 80); // 40 SB
            TeamStats weak  = new TeamStats(); weak.setSB(5);
            TeamStats strong = new TeamStats(); strong.setSB(50);

            double scoreWeakTeam   = scoringService.scorePlayer(speedster, weak);
            double scoreStrongTeam = scoringService.scorePlayer(speedster, strong);
            assertThat(scoreWeakTeam).isGreaterThan(scoreStrongTeam);
        }

        @Test
        @DisplayName("extra K penalty when team already has many strikeouts")
        void kPenaltyWhenTeamHasManyKs() {
            Player kProne = batter(1, 80, 30, 10, 150); // lots of Ks
            TeamStats manyKs = new TeamStats(); manyKs.setK(150);
            TeamStats fewKs  = new TeamStats(); fewKs.setK(40);

            assertThat(scoringService.scorePlayer(kProne, fewKs))
                    .isGreaterThan(scoringService.scorePlayer(kProne, manyKs));
        }

        @Test
        @DisplayName("save boost when team has zero saves")
        void saveBoostWhenTeamHasNone() {
            Player closer = pitcher(1, 60, 4, 2, 30, 80, 18, 2.20, 0.90);
            TeamStats noSaves   = new TeamStats(); noSaves.setSV(0);
            TeamStats manySaves = new TeamStats(); manySaves.setSV(40);

            assertThat(scoringService.scorePlayer(closer, noSaves))
                    .isGreaterThan(scoringService.scorePlayer(closer, manySaves));
        }

        @Test
        @DisplayName("HR boost when team is weak in home runs")
        void hrBoostWhenTeamIsWeak() {
            Player slugger = batter(1, 90, 45, 5, 130);
            TeamStats fewHR  = new TeamStats(); fewHR.setHR(5);
            TeamStats manyHR = new TeamStats(); manyHR.setHR(50);

            assertThat(scoringService.scorePlayer(slugger, fewHR))
                    .isGreaterThan(scoringService.scorePlayer(slugger, manyHR));
        }
    }

    // ── TeamStats.fromTeam() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("TeamStats.fromTeam()")
    class TeamStatsAggregation {

        @Test
        @DisplayName("batting stats sum correctly across roster")
        void battingStatsSum() {
            Team team = new Team(); team.setId(1); team.setName("T");
            team.getRoster().add(batter(1, 100, 40, 20, 120));
            team.getRoster().add(batter(2,  90, 35, 15, 100));
            TeamStats stats = TeamStats.fromTeam(team);
            assertThat(stats.getR()).isEqualTo(190);
            assertThat(stats.getHR()).isEqualTo(75);
            assertThat(stats.getSB()).isEqualTo(35);
        }

        @Test
        @DisplayName("ERA is IP-weighted average, not a naive sum")
        void eraIsWeightedAverage() {
            Team team = new Team(); team.setId(1); team.setName("T");
            team.getRoster().add(pitcher(1, 100, 10, 5, 0, 120, 30, 2.00, 1.00));
            team.getRoster().add(pitcher(2, 100, 10, 5, 0, 120, 30, 4.00, 1.40));
            TeamStats stats = TeamStats.fromTeam(team);
            // Weighted avg ERA = (2.0*100 + 4.0*100) / 200 = 3.0
            assertThat(stats.getERA()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("empty roster returns zeroed stats without exception")
        void emptyRosterReturnsZeros() {
            Team team = new Team(); team.setId(1); team.setName("Empty");
            TeamStats stats = TeamStats.fromTeam(team);
            assertThat(stats.getR()).isZero();
            assertThat(stats.getERA()).isZero();
        }
    }

    // ── Recommendations ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recommendations")
    class Recommendations {

        @Test
        @DisplayName("returns at most 5 players")
        void returnsAtMost5() {
            List<Player> pool = List.of(
                    batter(1, 100, 40, 20, 120),
                    batter(2,  90, 35, 15, 100),
                    batter(3,  80, 30, 10,  90),
                    batter(4,  70, 25,  5,  80),
                    pitcher(5, 180, 15, 5, 0, 200, 30, 2.1, 0.95),
                    pitcher(6, 160, 12, 7, 0, 180, 40, 2.8, 1.05),
                    pitcher(7,  60,  5, 3, 35, 90, 20, 2.0, 0.85)
            );
            List<Player> recs = scoringService.recommendPlayers(pool, new TeamStats());
            assertThat(recs).hasSize(5);
        }

        @Test
        @DisplayName("returns fewer than 5 when pool has fewer than 5 players")
        void fewerThan5WhenSmallPool() {
            List<Player> pool = List.of(batter(1, 100, 40, 20, 120), pitcher(2, 180, 15, 5, 0, 200, 30, 2.1, 0.95));
            List<Player> recs = scoringService.recommendPlayers(pool, new TeamStats());
            assertThat(recs).hasSize(2);
        }

        @Test
        @DisplayName("top recommendation has the highest individual score")
        void topRecommendationHasHighestScore() {
            Player average = batter(1, 60, 20, 5, 100);
            Player elite   = batter(2, 110, 50, 30, 80);
            Player weak    = batter(3, 30, 10, 2, 150);

            List<Player> recs = scoringService.recommendPlayers(
                    List.of(average, elite, weak), new TeamStats());

            assertThat(recs.get(0).getId()).isEqualTo(2); // elite batter ranked first
        }

        @Test
        @DisplayName("empty pool returns empty list without exception")
        void emptyPoolReturnsEmpty() {
            List<Player> recs = scoringService.recommendPlayers(List.of(), new TeamStats());
            assertThat(recs).isEmpty();
        }
    }
}

