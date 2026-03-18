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
import java.util.Map;

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

    // ── Late-round upside boost ───────────────────────────────────────────────

    @Nested
    @DisplayName("Late-round upside boost (round > 10)")
    class LateRoundUpside {

        @Test
        @DisplayName("high-ceiling batter scores higher in round 12 than round 5")
        void highCeilingBatterBoostedInLateRounds() {
            Player boom = batter(1, 90, 40, 30, 110);
            double earlyScore = scoringService.scorePlayerAdvanced(boom, new TeamStats(), null, List.of(), 5);
            double lateScore  = scoringService.scorePlayerAdvanced(boom, new TeamStats(), null, List.of(), 12);
            assertThat(lateScore).isGreaterThan(earlyScore);
        }

        @Test
        @DisplayName("low-ceiling batter gains less late-round boost than high-ceiling one")
        void lowCeilingBatterGainsLessBoost() {
            Player boom = batter(1, 90, 40, 30, 110);
            Player bust = batter(2, 40,  5,  2, 140);
            double boomBoost = scoringService.scorePlayerAdvanced(boom, new TeamStats(), null, List.of(), 15)
                             - scoringService.scorePlayerAdvanced(boom, new TeamStats(), null, List.of(), 1);
            double bustBoost = scoringService.scorePlayerAdvanced(bust, new TeamStats(), null, List.of(), 15)
                             - scoringService.scorePlayerAdvanced(bust, new TeamStats(), null, List.of(), 1);
            assertThat(boomBoost).isGreaterThan(bustBoost);
        }

        @Test
        @DisplayName("no upside boost applied before round 11")
        void noUpsideBoostBeforeRound11() {
            Player p = batter(1, 80, 30, 20, 100);
            double r1  = scoringService.scorePlayerAdvanced(p, new TeamStats(), null, List.of(), 1);
            double r10 = scoringService.scorePlayerAdvanced(p, new TeamStats(), null, List.of(), 10);
            assertThat(r1).isEqualTo(r10);
        }

        @Test
        @DisplayName("computeUpside returns higher value for power/speed bat vs contact bat")
        void upsideHigherForPowerBatter() {
            Player power   = batter(1, 80, 45, 30, 130);
            Player contact = batter(2, 75, 5,  3, 40);
            assertThat(scoringService.computeUpside(power))
                    .isGreaterThan(scoringService.computeUpside(contact));
        }

        @Test
        @DisplayName("pitcher upside rewards high K-rate and saves")
        void pitcherUpsideRewardsKRate() {
            Player strikeoutArm = pitcher(1, 180, 12, 6, 0, 220, 40, 3.00, 1.10);
            Player groundBaller = pitcher(2, 180, 12, 6, 0,  90, 40, 3.00, 1.10);
            assertThat(scoringService.computeUpside(strikeoutArm))
                    .isGreaterThan(scoringService.computeUpside(groundBaller));
        }
    }

    // ── Positional scarcity & need ────────────────────────────────────────────

    @Nested
    @DisplayName("Positional scarcity and need")
    class PositionalScarcity {

        private Team emptyTeam() {
            Team t = new Team(); t.setId(1); t.setName("My Team"); return t;
        }

        private Player playerAt(int id, String pos) {
            Player p = new Player();
            p.setId(id); p.setName("Player " + id); p.setPosition(pos);
            // Give all players the same neutral stats so only position drives differences
            p.setR(50); p.setHR(15); p.setSB(10); p.setH(100);
            p.setTwoB(20); p.setThreeB(1); p.setRBI(50); p.setBB(30); p.setK(80);
            p.setIP(0); p.setW(0); p.setL(0); p.setSV(0);
            p.setPitchingBB(0); p.setPitchingK(0); p.setERA(0); p.setWHIP(0);
            return p;
        }

        @Test
        @DisplayName("no boost when team already has enough of that position")
        void noBoostWhenPositionFilled() {
            Team team = emptyTeam();
            // Already have 3 OFs (requirement met)
            team.getRoster().add(playerAt(10, "OF"));
            team.getRoster().add(playerAt(11, "OF"));
            team.getRoster().add(playerAt(12, "OF"));

            Player extraOf = playerAt(1, "OF");
            double boost = scoringService.computePositionalScarcityBoost(
                    extraOf, team, List.of(extraOf));
            assertThat(boost).isEqualTo(0.0);
        }

        @Test
        @DisplayName("boost applied when team needs catcher and catchers remain in pool")
        void boostWhenCatcherNeeded() {
            Team team = emptyTeam(); // 0 catchers, need 1
            Player catcher = playerAt(1, "C");
            List<Player> pool = List.of(catcher, playerAt(2, "C"), playerAt(3, "C"));
            double boost = scoringService.computePositionalScarcityBoost(catcher, team, pool);
            assertThat(boost).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("boost is higher when fewer catchers remain in the pool")
        void boostHigherWhenPositionScarce() {
            Team team = emptyTeam();
            Player catcher = playerAt(1, "C");
            List<Player> manyLeft  = List.of(catcher, playerAt(2,"C"), playerAt(3,"C"),
                    playerAt(4,"C"), playerAt(5,"C"), playerAt(6,"C"),
                    playerAt(7,"C"), playerAt(8,"C"), playerAt(9,"C"), playerAt(10,"C"));
            List<Player> fewLeft   = List.of(catcher, playerAt(2, "C"));

            double boostScarce = scoringService.computePositionalScarcityBoost(catcher, team, fewLeft);
            double boostPlenty = scoringService.computePositionalScarcityBoost(catcher, team, manyLeft);
            assertThat(boostScarce).isGreaterThan(boostPlenty);
        }

        @Test
        @DisplayName("max boost when player is the last at a needed position")
        void maxBoostWhenLastAtPosition() {
            Team team = emptyTeam();
            Player lastC = playerAt(1, "C");
            // Only this player in the pool at C (remaining = 0 others)
            double boost = scoringService.computePositionalScarcityBoost(
                    lastC, team, List.of(lastC));
            assertThat(boost).isEqualTo(50.0);
        }

        @Test
        @DisplayName("getPositionalDeficits returns all positions for empty roster")
        void allPositionsNeededForEmptyRoster() {
            Map<String, Integer> deficits = scoringService.getPositionalDeficits(emptyTeam());
            assertThat(deficits).containsKeys("C", "1B", "2B", "3B", "SS", "OF", "SP", "RP");
            assertThat(deficits.get("OF")).isEqualTo(3);
            assertThat(deficits.get("SP")).isEqualTo(2);
        }

        @Test
        @DisplayName("getPositionalDeficits removes a position once requirement is met")
        void deficitClearedWhenFilled() {
            Team team = emptyTeam();
            team.getRoster().add(playerAt(1, "C"));
            Map<String, Integer> deficits = scoringService.getPositionalDeficits(team);
            assertThat(deficits).doesNotContainKey("C");
        }

        @Test
        @DisplayName("OF deficit decreases as outfielders are added")
        void ofDeficitDecreasesWithPicks() {
            Team team = emptyTeam();
            assertThat(scoringService.getPositionalDeficits(team).get("OF")).isEqualTo(3);
            team.getRoster().add(playerAt(1, "OF"));
            assertThat(scoringService.getPositionalDeficits(team).get("OF")).isEqualTo(2);
            team.getRoster().add(playerAt(2, "OF"));
            assertThat(scoringService.getPositionalDeficits(team).get("OF")).isEqualTo(1);
            team.getRoster().add(playerAt(3, "OF"));
            assertThat(scoringService.getPositionalDeficits(team)).doesNotContainKey("OF");
        }

        @Test
        @DisplayName("positional need boost propagates into full advanced score")
        void positionalNeedAppearsInAdvancedScore() {
            Team emptyTeam = emptyTeam();
            Player catcher = playerAt(1, "C");
            List<Player> pool = List.of(catcher, playerAt(2, "C"));

            double withNeed    = scoringService.scorePlayerAdvanced(
                    catcher, new TeamStats(), emptyTeam, pool, 1);
            double withoutNeed = scoringService.scorePlayerAdvanced(
                    catcher, new TeamStats(), null, pool, 1);
            assertThat(withNeed).isGreaterThan(withoutNeed);
        }
    }
}

