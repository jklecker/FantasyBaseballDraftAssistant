package com.example.fantasybaseball;

import com.example.fantasybaseball.dto.KeeperDTO;
import com.example.fantasybaseball.model.DraftState;
import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.Team;
import com.example.fantasybaseball.service.DraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for DraftService — no Spring context needed.
 * Covers snake draft order, round advancement, keeper loading, and error paths.
 */
class DraftServiceTest {

    private DraftService draftService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static List<Player> players(int... ids) {
        List<Player> list = new ArrayList<>();
        for (int id : ids) {
            Player p = new Player();
            p.setId(id);
            p.setName("Player " + id);
            p.setPosition("OF");
            list.add(p);
        }
        return list;
    }

    private static List<Team> teams(int count) {
        List<Team> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Team t = new Team();
            t.setId(i);
            t.setName("Team " + i);
            list.add(t);
        }
        return list;
    }

    @BeforeEach
    void setUp() {
        draftService = new DraftService();
    }

    // ── Initialization ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("draft not initialized until initializeDraft() is called")
        void notInitializedByDefault() {
            assertThat(draftService.isDraftInitialized()).isFalse();
            assertThat(draftService.getDraftState()).isNull();
            assertThat(draftService.getCurrentPickingTeam()).isNull();
        }

        @Test
        @DisplayName("after init: round=1, pick=1, available pool matches input")
        void initializesSetsCorrectDefaults() {
            draftService.initializeDraft(teams(4), players(1, 2, 3, 4), true);

            DraftState state = draftService.getDraftState();
            assertThat(state.getRound()).isEqualTo(1);
            assertThat(state.getCurrentPick()).isEqualTo(1);
            assertThat(state.getAvailablePlayers()).hasSize(4);
            assertThat(state.getDraftedPlayers()).isEmpty();
            assertThat(state.getTeams()).hasSize(4);
        }

        @Test
        @DisplayName("re-initializing resets state completely")
        void reInitResetsState() {
            draftService.initializeDraft(teams(2), players(1, 2), true);
            draftService.makePick(1);

            draftService.initializeDraft(teams(3), players(10, 20, 30), true);
            DraftState state = draftService.getDraftState();
            assertThat(state.getRound()).isEqualTo(1);
            assertThat(state.getCurrentPick()).isEqualTo(1);
            assertThat(state.getAvailablePlayers()).hasSize(3);
        }
    }

    // ── Snake Draft Order ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Snake draft order")
    class SnakeDraftOrder {

        @Test
        @DisplayName("round 1 (odd) picks in ascending team order")
        void round1_ascendingOrder() {
            draftService.initializeDraft(teams(3), players(1, 2, 3), true);

            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 1");
            draftService.makePick(1);
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 2");
            draftService.makePick(2);
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 3");
        }

        @Test
        @DisplayName("round 2 (even) picks in descending team order")
        void round2_descendingOrder() {
            draftService.initializeDraft(teams(3), players(1, 2, 3, 4, 5, 6), true);
            // Exhaust round 1
            draftService.makePick(1);
            draftService.makePick(2);
            draftService.makePick(3);

            assertThat(draftService.getDraftState().getRound()).isEqualTo(2);
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 3");
            draftService.makePick(4);
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 2");
            draftService.makePick(5);
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 1");
        }

        @Test
        @DisplayName("round 3 (odd) picks in ascending order again")
        void round3_ascendingAgain() {
            draftService.initializeDraft(teams(2), players(1, 2, 3, 4, 5, 6), true);
            // Round 1: Team1, Team2
            draftService.makePick(1); draftService.makePick(2);
            // Round 2: Team2, Team1
            draftService.makePick(3); draftService.makePick(4);
            // Round 3: ascending again
            assertThat(draftService.getDraftState().getRound()).isEqualTo(3);
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 1");
        }

        @Test
        @DisplayName("non-snake draft always picks in ascending order")
        void nonSnake_alwaysAscending() {
            draftService.initializeDraft(teams(3), players(1, 2, 3, 4, 5, 6), false);
            draftService.makePick(1); draftService.makePick(2); draftService.makePick(3);
            // Round 2 should still be Team 1 first
            assertThat(draftService.getCurrentPickingTeam().getName()).isEqualTo("Team 1");
        }
    }

    // ── Making Picks ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Making picks")
    class MakingPicks {

        @Test
        @DisplayName("pick removes player from available pool")
        void pickRemovesFromPool() {
            draftService.initializeDraft(teams(2), players(1, 2, 3), true);
            draftService.makePick(1);
            assertThat(draftService.getDraftState().getAvailablePlayers())
                    .extracting(Player::getId)
                    .containsExactlyInAnyOrder(2, 3);
        }

        @Test
        @DisplayName("pick adds player to drafted list")
        void pickAddsToDraftedList() {
            draftService.initializeDraft(teams(2), players(1, 2), true);
            draftService.makePick(1);
            assertThat(draftService.getDraftState().getDraftedPlayers())
                    .extracting(Player::getId)
                    .containsExactly(1);
        }

        @Test
        @DisplayName("pick adds player to the correct team's roster")
        void pickAddsToTeamRoster() {
            draftService.initializeDraft(teams(2), players(1, 2), true);
            Team team = draftService.makePick(1);
            assertThat(team.getName()).isEqualTo("Team 1");
            assertThat(team.getRoster()).extracting(Player::getId).containsExactly(1);
        }

        @Test
        @DisplayName("picking an unavailable player throws IllegalArgumentException")
        void pickUnavailablePlayerThrows() {
            draftService.initializeDraft(teams(2), players(1, 2), true);
            assertThatThrownBy(() -> draftService.makePick(99))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("picking an already-drafted player throws IllegalArgumentException")
        void pickAlreadyDraftedThrows() {
            draftService.initializeDraft(teams(2), players(1, 2, 3), true);
            draftService.makePick(1);
            assertThatThrownBy(() -> draftService.makePick(1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Keeper Loading ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Keeper loading")
    class KeeperLoading {

        @Test
        @DisplayName("keeper is removed from available pool and placed on team roster")
        void keeperRemovedFromPoolAndAddedToRoster() {
            draftService.initializeDraft(teams(2), players(1, 2, 3), true);

            KeeperDTO k = new KeeperDTO();
            k.setTeamName("Team 1"); k.setPlayerId(1); k.setRound(2);
            draftService.loadKeepers(List.of(k));

            assertThat(draftService.getDraftState().getAvailablePlayers())
                    .extracting(Player::getId).doesNotContain(1);
            assertThat(draftService.getDraftState().getTeams().get(0).getRoster())
                    .extracting(Player::getId).containsExactly(1);
        }

        @Test
        @DisplayName("keeper is marked as keeper=true on the player object")
        void keeperPlayerFlagIsSet() {
            draftService.initializeDraft(teams(2), players(1, 2), true);

            KeeperDTO k = new KeeperDTO();
            k.setTeamName("Team 1"); k.setPlayerId(1); k.setRound(3);
            draftService.loadKeepers(List.of(k));

            Player onRoster = draftService.getDraftState().getTeams().get(0).getRoster().get(0);
            assertThat(onRoster.isKeeper()).isTrue();
        }

        @Test
        @DisplayName("loading keepers before init throws IllegalStateException")
        void loadKeepersBeforeInitThrows() {
            KeeperDTO k = new KeeperDTO();
            k.setTeamName("Team 1"); k.setPlayerId(1); k.setRound(2);
            assertThatThrownBy(() -> draftService.loadKeepers(List.of(k)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("unknown team name in keeper is silently ignored")
        void unknownTeamInKeeperIsIgnored() {
            draftService.initializeDraft(teams(2), players(1, 2), true);

            KeeperDTO k = new KeeperDTO();
            k.setTeamName("Ghost Team"); k.setPlayerId(1); k.setRound(1);
            // Should not throw
            assertThatNoException().isThrownBy(() -> draftService.loadKeepers(List.of(k)));
            // Player still available
            assertThat(draftService.getDraftState().getAvailablePlayers())
                    .extracting(Player::getId).contains(1);
        }
    }
}

