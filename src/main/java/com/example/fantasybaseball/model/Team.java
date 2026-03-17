package com.example.fantasybaseball.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Team {
    private int id;
    private String name;
    private List<Player> roster = new ArrayList<>();
    private List<Keeper> keepers = new ArrayList<>();
}

