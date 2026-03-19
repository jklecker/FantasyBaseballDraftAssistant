package com.example.fantasybaseball.service;

import com.example.fantasybaseball.config.ScoringConfigLoader;
import com.example.fantasybaseball.config.ScoringPreset;
import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.Team;
import com.example.fantasybaseball.model.TeamStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scores players for H2H weekly categories.
 * Batting (good): R, H, 2B, 3B, HR, RBI, SB, BB
 * Batting (bad):  K
 * Pitching (good): IP, W, SV, K
 * Pitching (bad):  L, BB, ERA, WHIP
 *
 * Advanced scoring also factors in:
 *  - Positional need: urgency boost when my team still needs a position slot
 *  - Positional scarcity: further boost when few quality options remain in the pool
 *  - Late-round upside boost (rounds > 10 reward high-ceiling players)
 */
@Service
public class ScoringService {

    @Autowired
    private ScoringConfigLoader scoringConfig;

    /**
     * Standard H2H roster requirements (primary slots only).
     * UTIL and bench are flex — they don't drive positional urgency.
     */
    public static final Map<String, Integer> ROSTER_REQUIREMENTS = Map.of(
            "C",  1,
            "1B", 1,
            "2B", 1,
            "3B", 1,
            "SS", 1,
            "OF", 3,
            "SP", 2,
            "RP", 1
    );

    /**
     * Returns positions (and how many more) the team still needs to fill.
     * e.g. {"C":1, "OF":2} means still need 1 catcher and 2 outfielders.
     */
    public Map<String, Integer> getPositionalDeficits(Team team) {
        Map<String, Integer> filled = new HashMap<>();
        for (Player p : team.getRoster()) {
            filled.merge(p.getPosition(), 1, Integer::sum);
        }
        Map<String, Integer> deficits = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> req : ROSTER_REQUIREMENTS.entrySet()) {
            int have = filled.getOrDefault(req.getKey(), 0);
            int need = req.getValue();
            if (have < need) deficits.put(req.getKey(), need - have);
        }
        return deficits;
    }

    /**
     * Positional scarcity boost for H2H drafting:
     *  1. If my team still needs this position: start with a base urgency bonus.
     *  2. Scale that bonus by how few players at this position remain in the pool.
     *     Few left + I need it = high urgency.  Plenty left = low urgency.
     */
    public double computePositionalScarcityBoost(Player p, Team myTeam, List<Player> available) {
        if (myTeam == null || available == null) return 0.0;
        Map<String, Integer> deficits = getPositionalDeficits(myTeam);
        int deficit = deficits.getOrDefault(p.getPosition(), 0);
        if (deficit == 0) return 0.0; // already have enough at this position

        long remainingAtPos = available.stream()
                .filter(a -> a.getId() != p.getId()
                          && a.getPosition().equals(p.getPosition()))
                .count();

        if (remainingAtPos == 0) return 50.0; // last one available — take it
        // urgency = need / supply, scaled to a 5–35 point bonus range
        double urgency = (double) deficit / remainingAtPos;
        return Math.min(5.0 + urgency * 30.0, 35.0);
    }

    /**
     * Compute a player's ceiling/upside score.
     * Used to favour breakout candidates in later rounds.
     */
    public double computeUpside(Player p) {
        if (p.getIP() > 0) {
            double kRate = p.getPitchingK() / p.getIP();
            return kRate * 20.0 + p.getSV() * 1.0 + p.getW() * 2.0;
        } else {
            return p.getHR() * 1.5 + p.getSB() * 1.2 + (p.getR() + p.getRBI()) * 0.3;
        }
    }


    public double scorePlayer(Player player, TeamStats teamStats) {
        // Load active scoring preset from config
        ScoringPreset preset = scoringConfig != null ? scoringConfig.getActivePreset() : null;
        if (preset == null) {
            return scorePlayerDefault(player, teamStats);
        }
        return scorePlayerWithPreset(player, teamStats, preset);
    }

    /**
     * Score a player using a specific preset key.
     * Useful for per-draft-session scoring configuration.
     */
    public double scorePlayer(Player player, TeamStats teamStats, String presetKey) {
        ScoringPreset preset = scoringConfig != null ? scoringConfig.getPreset(presetKey) : null;
        if (preset == null) {
            return scorePlayerDefault(player, teamStats);
        }
        return scorePlayerWithPreset(player, teamStats, preset);
    }

    /**
     * Internal method to score with a given preset object.
     */
    private double scorePlayerWithPreset(Player player, TeamStats teamStats, ScoringPreset preset) {

        double score = 0;

        // --- Batting stats using config weights ---
        score += player.getR()      * preset.getBattingWeight("R");
        score += player.getH()      * preset.getBattingWeight("H");
        score += player.getTwoB()   * preset.getBattingWeight("2B");
        score += player.getThreeB() * preset.getBattingWeight("3B");
        score += player.getHR()     * preset.getBattingWeight("HR");
        score += player.getRBI()    * preset.getBattingWeight("RBI");
        score += player.getSB()     * preset.getBattingWeight("SB");
        score += player.getBB()     * preset.getBattingWeight("BB");
        score += player.getK()      * preset.getBattingWeight("K");

        // --- Pitching stats using config weights ---
        score += player.getIP()           * preset.getPitchingWeight("IP");
        score += player.getW()            * preset.getPitchingWeight("W");
        score += player.getL()            * preset.getPitchingWeight("L");
        score += player.getSV()           * preset.getPitchingWeight("SV");
        score += player.getPitchingBB()   * preset.getPitchingWeight("BB");
        score += player.getPitchingK()    * preset.getPitchingWeight("K");
        if (player.getIP() > 0) {
            score += player.getERA()      * preset.getPitchingWeight("ERA");
            score += player.getWHIP()     * preset.getPitchingWeight("WHIP");
        }

        // --- Team need adjustments from config ---
        if (teamStats != null && preset.hasTeamNeedAdjustments()) {
            // SB adjustment
            Map<String, Double> sbAdj = preset.getTeamNeedAdjustment("SB");
            if (!sbAdj.isEmpty() && teamStats.getSB() < sbAdj.get("threshold")) {
                score += player.getSB() * sbAdj.get("boost");
            }
            // K adjustment
            Map<String, Double> kAdj = preset.getTeamNeedAdjustment("K");
            if (!kAdj.isEmpty() && teamStats.getK() > kAdj.get("threshold")) {
                score += player.getK() * kAdj.get("penalty");
            }
            // SV adjustment
            Map<String, Double> svAdj = preset.getTeamNeedAdjustment("SV");
            if (!svAdj.isEmpty() && teamStats.getSV() == 0) {
                score += player.getSV() * svAdj.get("boost");
            }
            // HR adjustment
            Map<String, Double> hrAdj = preset.getTeamNeedAdjustment("HR");
            if (!hrAdj.isEmpty() && teamStats.getHR() < hrAdj.get("threshold")) {
                score += player.getHR() * hrAdj.get("boost");
            }
        }

        return score;
    }

    /**
     * Fallback scoring with hardcoded defaults (for backward compatibility if config fails to load).
     */
    private double scorePlayerDefault(Player player, TeamStats teamStats) {
        double score = 0;

        // --- Batting (defaults) ---
        score += player.getR()      * 1.0;
        score += player.getH()      * 0.8;
        score += player.getTwoB()   * 0.5;
        score += player.getThreeB() * 0.7;
        score += player.getHR()     * 1.5;
        score += player.getRBI()    * 1.0;
        score += player.getSB()     * 1.2;
        score += player.getBB()     * 0.3;
        score -= player.getK()      * 0.7;

        // --- Pitching (defaults) ---
        score += player.getIP()       * 0.5;
        score += player.getW()        * 1.0;
        score -= player.getL()        * 1.0;
        score += player.getSV()       * 1.5;
        score -= player.getPitchingBB() * 0.5;
        score += player.getPitchingK()  * 0.7;
        if (player.getIP() > 0) {
            score -= player.getERA()  * 2.0;
            score -= player.getWHIP() * 3.0;
        }

        // --- Team need adjustments (defaults) ---
        if (teamStats != null) {
            if (teamStats.getSB() < 10)  score += player.getSB() * 1.5;
            if (teamStats.getK() > 100)  score -= player.getK() * 0.5;
            if (teamStats.getSV() == 0)  score += player.getSV() * 1.0;
            if (teamStats.getHR() < 10)  score += player.getHR() * 0.5;
        }

        return score;
    }

    /**
     * Full advanced scoring for H2H:
     *  - Base category score (weighted stats + team need adjustments)
     *  - Positional scarcity boost (need a C and only 2 left? urgency!)
     *  - Late-round upside boost (round > 10: favour high-ceiling players)
     */
    public double scorePlayerAdvanced(Player player, TeamStats myTeamStats,
                                      Team myTeam, List<Player> available, int currentRound) {
        double score = scorePlayer(player, myTeamStats);

        // Positional need + scarcity
        score += computePositionalScarcityBoost(player, myTeam, available);

        // Late-round upside: grows from round 11, capped at +100%
        if (currentRound > 10) {
            double upsideFactor = Math.min((currentRound - 10) * 0.1, 1.0);
            score += computeUpside(player) * upsideFactor;
        }

        return score;
    }

    /**
     * Full advanced scoring with a specific preset key.
     */
    public double scorePlayerAdvanced(Player player, TeamStats myTeamStats,
                                      Team myTeam, List<Player> available, int currentRound, String presetKey) {
        double score = scorePlayer(player, myTeamStats, presetKey);

        // Positional need + scarcity
        score += computePositionalScarcityBoost(player, myTeam, available);

        // Late-round upside: grows from round 11, capped at +100%
        if (currentRound > 10) {
            double upsideFactor = Math.min((currentRound - 10) * 0.1, 1.0);
            score += computeUpside(player) * upsideFactor;
        }

        return score;
    }

    /**
     * Return the top 5 available players for the team on the clock.
     * Ranks by: BPA category score + positional need/scarcity + late-round upside.
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats,
                                         Team myTeam, int currentRound) {
        return recommendPlayers(available, teamStats, myTeam, currentRound, 5);
    }

    /**
     * Return top N players by advanced score.
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats,
                                         Team myTeam, int currentRound, int limit) {
        return available.stream()
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound)
                ).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /**
     * Return top N players by advanced score using a specific preset key.
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats,
                                         Team myTeam, int currentRound, int limit, String presetKey) {
        return available.stream()
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound, presetKey)
                ).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /**
     * Return top N pitchers (SP/RP or players with IP > 0).
     */
    public List<Player> recommendPitchers(List<Player> available, TeamStats teamStats,
                                          Team myTeam, int currentRound, int limit) {
        return available.stream()
                .filter(p -> p.getIP() > 0 || "SP".equals(p.getPosition()) || "RP".equals(p.getPosition()))
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound)
                ).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /**
     * Return top N pitchers using a specific preset key.
     */
    public List<Player> recommendPitchers(List<Player> available, TeamStats teamStats,
                                          Team myTeam, int currentRound, int limit, String presetKey) {
        return available.stream()
                .filter(p -> p.getIP() > 0 || "SP".equals(p.getPosition()) || "RP".equals(p.getPosition()))
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound, presetKey)
                ).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /**
     * Return top N hitters (non-pitchers).
     */
    public List<Player> recommendBatters(List<Player> available, TeamStats teamStats,
                                         Team myTeam, int currentRound, int limit) {
        return available.stream()
                .filter(p -> !(p.getIP() > 0 || "SP".equals(p.getPosition()) || "RP".equals(p.getPosition())))
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound)
                ).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /**
     * Return top N hitters using a specific preset key.
     */
    public List<Player> recommendBatters(List<Player> available, TeamStats teamStats,
                                         Team myTeam, int currentRound, int limit, String presetKey) {
        return available.stream()
                .filter(p -> !(p.getIP() > 0 || "SP".equals(p.getPosition()) || "RP".equals(p.getPosition())))
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound, presetKey)
                ).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /**
     * Backward-compatible overload (no positional context, round 1).
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats) {
        return recommendPlayers(available, teamStats, null, 1);
    }
}
