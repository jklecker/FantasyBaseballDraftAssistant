package com.example.fantasybaseball.model;

import lombok.Data;
import java.util.List;

@Data
public class DraftState {
    private int round;
    private int currentPick;
    private List<Team> teams;
    private List<Player> availablePlayers;
    private List<Player> draftedPlayers;
    private boolean snakeOrder;
}

