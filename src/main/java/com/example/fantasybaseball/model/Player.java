package com.example.fantasybaseball.model;

import lombok.Data;
import java.util.Map;

@Data
public class Player {
    private int id;
    private String name;
    private String team;
    private String position;
    private String sport = "baseball";
    // Batting stats
    private int R;
    private int H;
    private int twoB;
    private int threeB;
    private int HR;
    private int RBI;
    private int SB;
    private int BB;
    private int K;
    // Pitching stats
    private double IP;
    private int W;
    private int L;
    private int SV;
    private int pitchingBB;
    private int pitchingK;
    private double ERA;
    private double WHIP;
    // Keeper status
    private boolean keeper;
    // Generic projected stats (football: passYards, rushTD, receptions, etc.)
    private Map<String, Double> stats;
    // Market / analytics (optional — null-safe in scoring)
    private Double adp;
    private Map<String, Double> pff;
    private Map<String, Double> nextGen;
}

