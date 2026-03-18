package com.example.fantasybaseball.controller;

import com.example.fantasybaseball.dto.InitializeDraftRequest;
import com.example.fantasybaseball.dto.KeeperRequest;
import com.example.fantasybaseball.model.DraftState;
import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.Team;
import com.example.fantasybaseball.model.TeamStats;
import com.example.fantasybaseball.service.DraftService;
import com.example.fantasybaseball.service.PlayerPoolService;
import com.example.fantasybaseball.service.ScoringService;
import com.example.fantasybaseball.util.FuzzyMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/draft")
public class DraftController {

    @Autowired
    private DraftService draftService;

    @Autowired
    private PlayerPoolService playerPoolService;

    @Autowired
    private ScoringService scoringService;

    /**
     * Initialize the draft with a list of team names and snake-order flag.
     * Example body:
     * { "teamNames": ["Team A","Team B",...], "snakeOrder": true }
     */
    @PostMapping("/initialize")
    public ResponseEntity<DraftState> initialize(@RequestBody InitializeDraftRequest request) {
        List<Player> players = playerPoolService.getAllPlayers();
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < request.getTeamNames().size(); i++) {
            Team t = new Team();
            t.setId(i + 1);
            t.setName(request.getTeamNames().get(i));
            teams.add(t);
        }
        draftService.initializeDraft(teams, players, request.isSnakeOrder());
        return ResponseEntity.ok(draftService.getDraftState());
    }

    /**
     * Submit the next pick in draft order (snake order auto-determines which team).
     * Supply either playerId (integer) OR playerName (string) — name is matched
     * case-insensitively against the available player pool as a fallback.
     */
    @PostMapping("/pick")
    public ResponseEntity<Map<String, Object>> pick(
            @RequestParam(required = false) Integer playerId,
            @RequestParam(required = false) String playerName) {
        requireInitialized();
        if (playerId == null && (playerName == null || playerName.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide either playerId or playerName");
        }
        try {
            Team team = (playerId != null)
                    ? draftService.makePick(playerId)
                    : draftService.makePickByName(playerName);
            DraftState state = draftService.getDraftState();
            return ResponseEntity.ok(Map.of(
                    "pickedByTeam", team.getName(),
                    "round", state.getRound(),
                    "nextPick", state.getCurrentPick()
            ));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Auto-initialize the draft with default team names (Team 1–11 + "My Team")
     * and snake order. If the draft is already initialized this is a no-op and
     * just returns the current state — safe to call on every page load.
     */
    @PostMapping("/auto-initialize")
    public ResponseEntity<DraftState> autoInitialize(
            @RequestParam(defaultValue = "12") int numTeams) {
        if (!draftService.isDraftInitialized()) {
            List<Player> players = playerPoolService.getAllPlayers();
            List<Team> teams = new ArrayList<>();
            for (int i = 1; i <= numTeams; i++) {
                Team t = new Team();
                t.setId(i);
                t.setName(i == numTeams ? "My Team" : "Team " + i);
                teams.add(t);
            }
            draftService.initializeDraft(teams, players, true);
        }
        return ResponseEntity.ok(draftService.getDraftState());
    }

    /**
     * Get top 5 recommendations for a specific team.
     * Factors in: BPA category score, team stat needs, positional scarcity, late-round upside.
     */
    @GetMapping("/recommendations")
    public List<Player> recommendations(@RequestParam int teamId,
                                        @RequestParam(defaultValue = "1") int round) {
        requireInitialized();
        DraftState state = draftService.getDraftState();

        Team team = state.getTeams().stream()
                .filter(t -> t.getId() == teamId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Team " + teamId + " not found"));

        TeamStats stats = TeamStats.fromTeam(team);
        return scoringService.recommendPlayers(
                state.getAvailablePlayers(), stats, team, round);
    }

    /**
     * Returns which roster positions the team still needs to fill and how many.
     * e.g. {"C":1, "OF":2, "SP":1}
     */
    @GetMapping("/positional-needs")
    public Map<String, Integer> positionalNeeds(@RequestParam int teamId) {
        requireInitialized();
        DraftState state = draftService.getDraftState();
        Team team = state.getTeams().stream()
                .filter(t -> t.getId() == teamId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Team " + teamId + " not found"));
        return scoringService.getPositionalDeficits(team);
    }

    /**
     * Search available players by name using fuzzy matching.
     * Supports exact substrings, word prefixes, character subsequences, and
     * edit-distance typo tolerance (1–2 character errors).
     * Results are returned sorted by match quality (best first).
     * If no query is supplied the full available pool is returned.
     */
    @GetMapping("/players")
    public List<Player> searchPlayers(@RequestParam(required = false) String q) {
        requireInitialized();
        List<Player> available = draftService.getDraftState().getAvailablePlayers();
        if (q == null || q.isBlank()) return available;
        String lower = q.trim().toLowerCase();
        return available.stream()
                .map(p -> Map.entry(p, FuzzyMatcher.score(p.getName().toLowerCase(), lower)))
                .filter(e -> e.getValue() > 0.0)
                .sorted(Map.Entry.<Player, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Returns full draft state: round, current pick, all rosters, available players.
     */
    @GetMapping("/state")
    public ResponseEntity<DraftState> state() {
        requireInitialized();
        return ResponseEntity.ok(draftService.getDraftState());
    }

    /**
     * Returns the team currently on the clock.
     */
    @GetMapping("/current-team")
    public ResponseEntity<Team> currentTeam() {
        requireInitialized();
        return ResponseEntity.ok(draftService.getCurrentPickingTeam());
    }

    /**
     * Load keepers before the draft. Each keeper is removed from the pool
     * and placed on the team's roster. Example body:
     * { "keepers": [{"teamName":"Team A","playerId":1,"round":2},...] }
     */
    @PostMapping("/load-keepers")
    public ResponseEntity<DraftState> loadKeepers(@RequestBody KeeperRequest request) {
        requireInitialized();
        try {
            draftService.loadKeepers(request.getKeepers());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(draftService.getDraftState());
    }

    private void requireInitialized() {
        if (!draftService.isDraftInitialized()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Draft not initialized. POST /draft/initialize first.");
        }
    }
}
