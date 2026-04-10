package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FootballPlayerService {

    private static final Logger log = LoggerFactory.getLogger(FootballPlayerService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<Player> getPlayers() {
        try {
            InputStream is = new ClassPathResource("football-players.json").getInputStream();
            return MAPPER.readValue(is, new TypeReference<List<Player>>() {});
        } catch (Exception e) {
            log.error("Failed to load football-players.json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
