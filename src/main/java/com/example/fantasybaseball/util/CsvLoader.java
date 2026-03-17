package com.example.fantasybaseball.util;

import com.example.fantasybaseball.model.Player;
import com.opencsv.CSVReader;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvLoader {
    public List<Player> loadPlayers(String csvPath) {
        List<Player> players = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            String[] header = reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                Player p = new Player();
                p.setId(Integer.parseInt(line[0]));
                p.setName(line[1]);
                p.setTeam(line[2]);
                p.setPosition(line[3]);
                p.setR(Integer.parseInt(line[4]));
                p.setH(Integer.parseInt(line[5]));
                p.setTwoB(Integer.parseInt(line[6]));
                p.setThreeB(Integer.parseInt(line[7]));
                p.setHR(Integer.parseInt(line[8]));
                p.setRBI(Integer.parseInt(line[9]));
                p.setSB(Integer.parseInt(line[10]));
                p.setBB(Integer.parseInt(line[11]));
                p.setK(Integer.parseInt(line[12]));
                p.setIP(Double.parseDouble(line[13]));
                p.setW(Integer.parseInt(line[14]));
                p.setL(Integer.parseInt(line[15]));
                p.setSV(Integer.parseInt(line[16]));
                p.setPitchingBB(Integer.parseInt(line[17]));
                p.setPitchingK(Integer.parseInt(line[18]));
                p.setERA(Double.parseDouble(line[19]));
                p.setWHIP(Double.parseDouble(line[20]));
                players.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return players;
    }
}

