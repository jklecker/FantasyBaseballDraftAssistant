package com.example.fantasybaseball;

import com.example.fantasybaseball.service.NflPlayerService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test — hits Sleeper API directly.
 * Run with: ./gradlew test -Dgroups=integration
 */
@Tag("integration")
@SpringBootTest
class NflIntegrationTest {

    @Autowired NflPlayerService sleeperSvc;

    @Test void sleeperReturnsMinimumPlayers() {
        List<NflPlayerService.SleeperPlayer> players = sleeperSvc.getPlayers();
        assertThat(players).hasSizeGreaterThanOrEqualTo(100);
    }

    @Test void allPlayersHaveRequiredFields() {
        sleeperSvc.getPlayers().forEach(p -> {
            assertThat(p.fullName).as("fullName for %s", p.sleeperId).isNotBlank();
            assertThat(p.position).as("position for %s", p.fullName).isNotBlank();
            assertThat(p.team).as("team for %s", p.fullName).isNotBlank();
        });
    }

    @Test void allMajorPositionsRepresented() {
        Set<String> positions = sleeperSvc.getPlayers().stream()
                .map(p -> p.position)
                .collect(Collectors.toSet());
        assertThat(positions).containsAll(Set.of("QB", "RB", "WR", "TE"));
    }

    @Test void defensePlayersPresent() {
        long dstCount = sleeperSvc.getPlayers().stream()
                .filter(p -> "DEF".equals(p.position))
                .count();
        assertThat(dstCount).isGreaterThanOrEqualTo(20); // 32 teams
    }

    @Test void noNullTeams() {
        sleeperSvc.getPlayers().forEach(p ->
            assertThat(p.team).as("null team for %s", p.fullName).isNotNull()
        );
    }

    @Test void knownElitePlayersPresent() {
        List<String> names = sleeperSvc.getPlayers().stream()
                .map(p -> p.fullName.toLowerCase())
                .toList();
        // Use loose contains — name format may vary slightly
        assertThat(names).anyMatch(n -> n.contains("josh allen"));
        assertThat(names).anyMatch(n -> n.contains("ceedee lamb") || n.contains("lamb"));
    }
}
