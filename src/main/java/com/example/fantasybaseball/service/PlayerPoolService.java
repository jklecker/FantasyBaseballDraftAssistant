package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.util.CsvLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlayerPoolService {
    @Autowired
    private CsvLoader csvLoader;
    private List<Player> playerPool;

    public void loadPlayerPool(String csvPath) {
        playerPool = csvLoader.loadPlayers(csvPath);
    }

    public List<Player> getAvailablePlayers(List<Integer> draftedIds) {
        return playerPool.stream()
                .filter(p -> !draftedIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public List<Player> getAllPlayers() {
        return playerPool;
    }
}

