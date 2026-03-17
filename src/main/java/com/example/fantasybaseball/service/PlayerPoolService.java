package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.util.CsvLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlayerPoolService {

    @Autowired
    private CsvLoader csvLoader;

    @Value("${players.csv.path:players.csv}")
    private String defaultCsvPath;

    private List<Player> playerPool = new ArrayList<>();

    /** Auto-load from classpath on startup using the configured path. */
    @PostConstruct
    public void init() {
        loadPlayerPool(defaultCsvPath);
    }

    public void loadPlayerPool(String classpathFilename) {
        playerPool = csvLoader.loadPlayers(classpathFilename);
    }

    public List<Player> getAvailablePlayers(List<Integer> draftedIds) {
        if (playerPool == null) return new ArrayList<>();
        return playerPool.stream()
                .filter(p -> !draftedIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public List<Player> getAllPlayers() {
        return playerPool != null ? playerPool : new ArrayList<>();
    }
}

