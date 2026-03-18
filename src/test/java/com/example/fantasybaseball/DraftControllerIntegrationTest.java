package com.example.fantasybaseball;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for DraftController via MockMvc.
 * Each @Nested class that needs a clean draft state uses @DirtiesContext
 * so the Spring context (and DraftService singleton) is reset between groups.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DraftControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final String TWO_TEAMS_JSON =
            "{\"teamNames\":[\"Alpha\",\"Beta\"],\"snakeOrder\":true}";

    private static final String THREE_TEAMS_JSON =
            "{\"teamNames\":[\"Alpha\",\"Beta\",\"Gamma\"],\"snakeOrder\":true}";

    private void initialize(String json) throws Exception {
        mockMvc.perform(post("/draft/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    // ── Guard: draft not initialized ──────────────────────────────────────────

    @Nested
    @DisplayName("Endpoints return 400 when draft not initialized")
    class NotInitialized {

        @Test void state_400()           throws Exception { mockMvc.perform(get("/draft/state")).andExpect(status().isBadRequest()); }
        @Test void currentTeam_400()     throws Exception { mockMvc.perform(get("/draft/current-team")).andExpect(status().isBadRequest()); }
        @Test void recommendations_400() throws Exception { mockMvc.perform(get("/draft/recommendations").param("teamId", "1")).andExpect(status().isBadRequest()); }
        @Test void pick_400()            throws Exception { mockMvc.perform(post("/draft/pick").param("playerId", "1")).andExpect(status().isBadRequest()); }
        @Test void loadKeepers_400()     throws Exception {
            mockMvc.perform(post("/draft/load-keepers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"keepers\":[]}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /draft/initialize ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /draft/initialize")
    class Initialize {

        @Test
        @DisplayName("returns 200 with round=1, pick=1")
        void returnsInitialState() throws Exception {
            mockMvc.perform(post("/draft/initialize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TWO_TEAMS_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.round").value(1))
                    .andExpect(jsonPath("$.currentPick").value(1))
                    .andExpect(jsonPath("$.snakeOrder").value(true))
                    .andExpect(jsonPath("$.teams", hasSize(2)))
                    .andExpect(jsonPath("$.teams[0].name").value("Alpha"))
                    .andExpect(jsonPath("$.teams[1].name").value("Beta"));
        }

        @Test
        @DisplayName("available player pool is populated from CSV")
        void availablePlayersPopulated() throws Exception {
            mockMvc.perform(post("/draft/initialize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TWO_TEAMS_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availablePlayers", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("re-initializing resets the draft")
        void reInitResetsState() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(post("/draft/pick").param("playerId", "1")).andExpect(status().isOk());

            // Re-init with 3 teams
            mockMvc.perform(post("/draft/initialize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(THREE_TEAMS_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.round").value(1))
                    .andExpect(jsonPath("$.teams", hasSize(3)))
                    .andExpect(jsonPath("$.draftedPlayers", hasSize(0)));
        }
    }

    // ── GET /draft/state ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /draft/state")
    class State {

        @Test
        @DisplayName("returns full draft state after initialization")
        void returnsFullState() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/state"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.round").value(1))
                    .andExpect(jsonPath("$.currentPick").value(1))
                    .andExpect(jsonPath("$.teams", hasSize(2)));
        }
    }

    // ── GET /draft/current-team ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /draft/current-team")
    class CurrentTeam {

        @Test
        @DisplayName("first pick is team 1 (Alpha) in round 1")
        void firstPickIsTeam1() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/current-team"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Alpha"));
        }

        @Test
        @DisplayName("after team 1 picks, team 2 is on the clock")
        void afterPick_team2OnClock() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(post("/draft/pick").param("playerId", "1"));
            mockMvc.perform(get("/draft/current-team"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Beta"));
        }
    }

    // ── POST /draft/pick ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /draft/pick")
    class Pick {

        @Test
        @DisplayName("successful pick returns team name and updated pick counter")
        void successfulPick() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(post("/draft/pick").param("playerId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pickedByTeam").value("Alpha"))
                    .andExpect(jsonPath("$.round").value(1))
                    .andExpect(jsonPath("$.nextPick").value(2));
        }

        @Test
        @DisplayName("picking an unavailable player returns 400")
        void pickUnavailablePlayer_400() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(post("/draft/pick").param("playerId", "9999"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("picking an already-drafted player returns 400")
        void pickAlreadyDrafted_400() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(post("/draft/pick").param("playerId", "1"));
            mockMvc.perform(post("/draft/pick").param("playerId", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("snake order reverses in round 2 — Beta picks first")
        void snakeRound2BetaPicksFirst() throws Exception {
            initialize(TWO_TEAMS_JSON);
            // Round 1: Alpha picks, Beta picks
            mockMvc.perform(post("/draft/pick").param("playerId", "1"));
            mockMvc.perform(post("/draft/pick").param("playerId", "2"));
            // Round 2: Beta should be on the clock
            mockMvc.perform(get("/draft/current-team"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Beta"));
            // Beta picks in round 2
            mockMvc.perform(post("/draft/pick").param("playerId", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pickedByTeam").value("Beta"))
                    .andExpect(jsonPath("$.round").value(2));
        }
    }

    // ── POST /draft/load-keepers ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /draft/load-keepers")
    class LoadKeepers {

        @Test
        @DisplayName("keeper is removed from available pool and added to roster")
        void keeperAssignedCorrectly() throws Exception {
            initialize(TWO_TEAMS_JSON);
            String keeperPayload = """
                    {
                      "keepers": [
                        {"teamName": "Alpha", "playerId": 1, "round": 2}
                      ]
                    }
                    """;
            mockMvc.perform(post("/draft/load-keepers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(keeperPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.teams[0].roster", hasSize(1)))
                    .andExpect(jsonPath("$.teams[0].roster[0].id").value(1))
                    .andExpect(jsonPath("$.teams[0].keepers", hasSize(1)));
        }

        @Test
        @DisplayName("keeper player is no longer in the available pool after loading")
        void keeperNotInAvailablePool() throws Exception {
            initialize(TWO_TEAMS_JSON);
            String keeperPayload = """
                    {
                      "keepers": [{"teamName": "Alpha", "playerId": 2, "round": 1}]
                    }
                    """;
            mockMvc.perform(post("/draft/load-keepers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(keeperPayload))
                    .andExpect(status().isOk());

            // Check that player 2 is gone from available pool in /state
            mockMvc.perform(get("/draft/state"))
                    .andExpect(jsonPath("$.availablePlayers[*].id", not(hasItem(2))));
        }

        @Test
        @DisplayName("keeper player cannot be picked by another team")
        void keeperCannotBePicked() throws Exception {
            initialize(TWO_TEAMS_JSON);
            String keeperPayload = """
                    {
                      "keepers": [{"teamName": "Alpha", "playerId": 3, "round": 2}]
                    }
                    """;
            mockMvc.perform(post("/draft/load-keepers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(keeperPayload))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/draft/pick").param("playerId", "3"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /draft/players (fuzzy search) ────────────────────────────────────

    @Nested
    @DisplayName("GET /draft/players — fuzzy search")
    class PlayerSearch {

        @Test
        @DisplayName("exact substring match returns the player")
        void exactSubstringMatch() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/players").param("q", "Trout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", hasItem("Mike Trout")));
        }

        @Test
        @DisplayName("case-insensitive first-name prefix returns the player")
        void caseInsensitivePrefixMatch() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/players").param("q", "mik"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", hasItem("Mike Trout")));
        }

        @Test
        @DisplayName("typo (transposition) still finds the player")
        void typoMatchReturnsPlayer() throws Exception {
            initialize(TWO_TEAMS_JSON);
            // "truot" is one transposition away from "trout"
            mockMvc.perform(get("/draft/players").param("q", "truot"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", hasItem("Mike Trout")));
        }

        @Test
        @DisplayName("no-match query returns empty list")
        void noMatchReturnsEmpty() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/players").param("q", "zzzzzzzz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", equalTo(0)));
        }

        @Test
        @DisplayName("blank query returns all available players")
        void blankQueryReturnsAll() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/players"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", greaterThan(0)));
        }

        @Test
        @DisplayName("best match appears first (exact substring ranked above prefix)")
        void bestMatchRankedFirst() throws Exception {
            initialize(TWO_TEAMS_JSON);
            // "Judge" is an exact last-name match; results should include Aaron Judge
            mockMvc.perform(get("/draft/players").param("q", "judge"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Aaron Judge"));
        }

        @Test
        @DisplayName("drafted player does not appear in search results")
        void draftedPlayerNotInSearch() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(post("/draft/pick").param("playerId", "1"));
            mockMvc.perform(get("/draft/players").param("q", "Trout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", not(hasItem(1))));
        }
    }

    // ── GET /draft/recommendations ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /draft/recommendations")
    class Recommendations {

        @Test
        @DisplayName("returns up to 5 players")
        void returnsUpTo5Players() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/recommendations").param("teamId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", lessThanOrEqualTo(5)))
                    .andExpect(jsonPath("$.length()", greaterThan(0)));
        }

        @Test
        @DisplayName("unknown teamId returns 404")
        void unknownTeamId_404() throws Exception {
            initialize(TWO_TEAMS_JSON);
            mockMvc.perform(get("/draft/recommendations").param("teamId", "99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("drafted players do not appear in recommendations")
        void draftedPlayersNotInRecommendations() throws Exception {
            initialize(TWO_TEAMS_JSON);
            // Pick player 1
            mockMvc.perform(post("/draft/pick").param("playerId", "1"));
            // Recommendations for team 2 should not include player 1
            mockMvc.perform(get("/draft/recommendations").param("teamId", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", not(hasItem(1))));
        }
    }
}

