package com.example.fantasybaseball;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.util.CsvLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests CsvLoader against the real players.csv on the classpath.
 * Uses a minimal Spring context to resolve the @Component bean.
 */
@SpringBootTest
class CsvLoaderTest {

    @Autowired
    private CsvLoader csvLoader;

    @Test
    @DisplayName("loads all rows from players.csv")
    void loadsAllRows() {
        List<Player> players = csvLoader.loadPlayers("players.csv");
        // Our sample CSV has 12 data rows
        assertThat(players).hasSize(12);
    }

    @Test
    @DisplayName("player fields are mapped to the correct columns")
    void playerFieldsAreCorrect() {
        List<Player> players = csvLoader.loadPlayers("players.csv");
        // Row 1: Mike Trout (id=1)
        Player trout = players.stream().filter(p -> p.getId() == 1).findFirst().orElseThrow();
        assertThat(trout.getName()).isEqualTo("Mike Trout");
        assertThat(trout.getTeam()).isEqualTo("LAA");
        assertThat(trout.getPosition()).isEqualTo("OF");
        assertThat(trout.getR()).isEqualTo(100);
        assertThat(trout.getHR()).isEqualTo(40);
        assertThat(trout.getSB()).isEqualTo(20);
        assertThat(trout.getIP()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("pitcher stats are mapped correctly")
    void pitcherFieldsAreCorrect() {
        List<Player> players = csvLoader.loadPlayers("players.csv");
        // Row 3: Jacob deGrom (id=3)
        Player deGrom = players.stream().filter(p -> p.getId() == 3).findFirst().orElseThrow();
        assertThat(deGrom.getName()).isEqualTo("Jacob deGrom");
        assertThat(deGrom.getPosition()).isEqualTo("SP");
        assertThat(deGrom.getIP()).isEqualTo(180.0);
        assertThat(deGrom.getW()).isEqualTo(15);
        assertThat(deGrom.getL()).isEqualTo(5);
        assertThat(deGrom.getPitchingK()).isEqualTo(200);
        assertThat(deGrom.getERA()).isEqualTo(2.10);
        assertThat(deGrom.getWHIP()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("closer SV column is loaded correctly")
    void closerSavesCorrect() {
        List<Player> players = csvLoader.loadPlayers("players.csv");
        // Row 10: Josh Hader (id=10)
        Player hader = players.stream().filter(p -> p.getId() == 10).findFirst().orElseThrow();
        assertThat(hader.getSV()).isEqualTo(35);
    }

    @Test
    @DisplayName("returns empty list (no exception) for a missing file")
    void missingFileReturnsEmptyList() {
        List<Player> players = csvLoader.loadPlayers("does-not-exist.csv");
        assertThat(players).isEmpty();
    }

    @Test
    @DisplayName("IDs are unique across all loaded players")
    void idsAreUnique() {
        List<Player> players = csvLoader.loadPlayers("players.csv");
        long distinctIds = players.stream().mapToInt(Player::getId).distinct().count();
        assertThat(distinctIds).isEqualTo(players.size());
    }
}

