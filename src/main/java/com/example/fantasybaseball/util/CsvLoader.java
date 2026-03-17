package com.example.fantasybaseball.util;

import com.example.fantasybaseball.model.Player;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the player pool from a CSV file on the classpath.
 * Expected columns (0-based index):
 *   0=id, 1=name, 2=team, 3=position,
 *   4=R, 5=H, 6=2B, 7=3B, 8=HR, 9=RBI, 10=SB, 11=BB, 12=K,
 *   13=IP, 14=W, 15=L, 16=SV, 17=pBB, 18=pK, 19=ERA, 20=WHIP
 */
@Component
public class CsvLoader {

    private static final Logger log = LoggerFactory.getLogger(CsvLoader.class);

    public List<Player> loadPlayers(String classpathFilename) {
        List<Player> players = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(classpathFilename);
            try (CSVReader reader = new CSVReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                reader.readNext(); // skip header
                String[] line;
                while ((line = reader.readNext()) != null) {
                    if (line.length < 21) continue; // skip malformed rows
                    Player p = new Player();
                    p.setId(Integer.parseInt(line[0].trim()));
                    p.setName(line[1].trim());
                    p.setTeam(line[2].trim());
                    p.setPosition(line[3].trim());
                    p.setR(Integer.parseInt(line[4].trim()));
                    p.setH(Integer.parseInt(line[5].trim()));
                    p.setTwoB(Integer.parseInt(line[6].trim()));
                    p.setThreeB(Integer.parseInt(line[7].trim()));
                    p.setHR(Integer.parseInt(line[8].trim()));
                    p.setRBI(Integer.parseInt(line[9].trim()));
                    p.setSB(Integer.parseInt(line[10].trim()));
                    p.setBB(Integer.parseInt(line[11].trim()));
                    p.setK(Integer.parseInt(line[12].trim()));
                    p.setIP(Double.parseDouble(line[13].trim()));
                    p.setW(Integer.parseInt(line[14].trim()));
                    p.setL(Integer.parseInt(line[15].trim()));
                    p.setSV(Integer.parseInt(line[16].trim()));
                    p.setPitchingBB(Integer.parseInt(line[17].trim()));
                    p.setPitchingK(Integer.parseInt(line[18].trim()));
                    p.setERA(Double.parseDouble(line[19].trim()));
                    p.setWHIP(Double.parseDouble(line[20].trim()));
                    players.add(p);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load players from '{}': {}", classpathFilename, e.getMessage(), e);
        }
        return players;
    }
}
