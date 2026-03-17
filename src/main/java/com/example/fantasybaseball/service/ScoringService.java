package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.TeamStats;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scores players for H2H weekly categories.
 * Batting (good): R, H, 2B, 3B, HR, RBI, SB, BB
 * Batting (bad):  K
 * Pitching (good): IP, W, SV, K
 * Pitching (bad):  L, BB, ERA, WHIP
 */
@Service
public class ScoringService {

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
     * Return the top 5 available players ranked by score for a specific team.
     */
    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats) {
        return available.stream()
                .sorted(Comparator.comparingDouble((Player p) -> scorePlayer(p, teamStats)).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }
}
