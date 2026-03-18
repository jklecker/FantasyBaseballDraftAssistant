package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.Team;
import com.example.fantasybaseball.model.TeamStats;
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
        double score = 0;

        // --- Batting ---
        score += player.getR()      * 1.0;
        score += player.getH()      * 0.8;
        score += player.getTwoB()   * 0.5;
        score += player.getThreeB() * 0.7;
        score += player.getHR()     * 1.5;
        score += player.getRBI()    * 1.0;
        score += player.getSB()     * 1.2;
        score += player.getBB()     * 0.3;
        score -= player.getK()      * 0.7; // Ks are bad

        // --- Pitching ---
        score += player.getIP()       * 0.5;
        score += player.getW()        * 1.0;
        score -= player.getL()        * 1.0; // Losses are bad
        score += player.getSV()       * 1.5;
        score -= player.getPitchingBB() * 0.5; // BB bad
        score += player.getPitchingK()  * 0.7;
        if (player.getIP() > 0) {
            score -= player.getERA()  * 2.0; // High ERA bad
            score -= player.getWHIP() * 3.0; // High WHIP bad
        }

        // --- Team need adjustments ---
        if (teamStats != null) {
            // Boost SB if team is weak in stolen bases
            if (teamStats.getSB() < 10)  score += player.getSB() * 1.5;
            // Penalise more Ks if team already strikes out a lot
            if (teamStats.getK() > 100)  score -= player.getK() * 0.5;
            // Boost saves if team has none
            if (teamStats.getSV() == 0)  score += player.getSV() * 1.0;
            // Boost HR if team is weak in power
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
     * Return the top 5 available players for the team on the clock.
     * Ranks by: BPA category score + positional need/scarcity + late-round upside.
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats,
                                         Team myTeam, int currentRound) {
        return available.stream()
                .sorted(Comparator.comparingDouble(
                        (Player p) -> scorePlayerAdvanced(p, teamStats, myTeam, available, currentRound)
                ).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Backward-compatible overload (no positional context, round 1).
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats) {
        return recommendPlayers(available, teamStats, null, 1);
    }
}
