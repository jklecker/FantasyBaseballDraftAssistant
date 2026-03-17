package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.Team;
import com.example.fantasybaseball.model.TeamStats;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScoringService {
    public double scorePlayer(Player player, TeamStats teamStats) {
        double score = 0;
        // Batting
        score += player.getR() * 1.0;
        score += player.getH() * 0.8;
        score += player.getTwoB() * 0.5;
        score += player.getThreeB() * 0.7;
        score += player.getHR() * 1.5;
        score += player.getRBI() * 1.0;
        score += player.getSB() * 1.2;
        score += player.getBB() * 0.3;
        score -= player.getK() * 0.7; // Ks are bad
        // Pitching
        score += player.getIP() * 0.5;
        score += player.getW() * 1.0;
        score -= player.getL() * 1.0; // Losses are bad
        score += player.getSV() * 1.2;
        score -= player.getPitchingBB() * 0.5; // BB bad
        score += player.getPitchingK() * 0.7;
        score -= player.getERA() * 2.0; // High ERA bad
        score -= player.getWHIP() * 2.0; // High WHIP bad
        // Adjust for team needs (example: boost SB if weak)
        if (teamStats.getSB() < 10) score += 2.0;
        if (teamStats.getK() > 100) score -= 2.0;
        // Positional scarcity (example: boost if position open)
        // ...implement as needed...
        return score;
    }

    public List<Player> recommendPlayers(List<Player> available, TeamStats teamStats, int rosterSize) {
        return available.stream()
                .sorted(Comparator.comparingDouble(p -> -scorePlayer(p, teamStats)))
                .limit(5)
                .collect(Collectors.toList());
    }
}

