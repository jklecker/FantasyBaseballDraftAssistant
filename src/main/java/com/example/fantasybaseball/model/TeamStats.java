package com.example.fantasybaseball.model;

import lombok.Data;

@Data
public class TeamStats {
    private int R;
    private int H;
    private int twoB;
    private int threeB;
    private int HR;
    private int RBI;
    private int SB;
    private int BB;
    private int K;
    private double IP;
    private int W;
    private int L;
    private int SV;
    private int pitchingBB;
    private int pitchingK;
    private double ERA;
    private double WHIP;

    /**
     * Aggregate stats from a team's current roster.
     * ERA and WHIP are weighted averages by IP.
     */
    public static TeamStats fromTeam(Team team) {
        TeamStats stats = new TeamStats();
        double totalIP = 0;
        double eraWeighted = 0;
        double whipWeighted = 0;

        for (Player p : team.getRoster()) {
            stats.R += p.getR();
            stats.H += p.getH();
            stats.twoB += p.getTwoB();
            stats.threeB += p.getThreeB();
            stats.HR += p.getHR();
            stats.RBI += p.getRBI();
            stats.SB += p.getSB();
            stats.BB += p.getBB();
            stats.K += p.getK();
            stats.W += p.getW();
            stats.L += p.getL();
            stats.SV += p.getSV();
            stats.pitchingBB += p.getPitchingBB();
            stats.pitchingK += p.getPitchingK();
            stats.IP += p.getIP();
            if (p.getIP() > 0) {
                eraWeighted += p.getERA() * p.getIP();
                whipWeighted += p.getWHIP() * p.getIP();
                totalIP += p.getIP();
            }
        }
        if (totalIP > 0) {
            stats.ERA = eraWeighted / totalIP;
            stats.WHIP = whipWeighted / totalIP;
        }
        return stats;
    }
}
