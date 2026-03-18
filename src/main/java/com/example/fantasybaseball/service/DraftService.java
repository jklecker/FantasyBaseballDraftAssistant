package com.example.fantasybaseball.service;

import com.example.fantasybaseball.dto.KeeperDTO;
import com.example.fantasybaseball.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Core draft engine. Manages state, snake-order advancement,
 * keeper assignment, and pick submission.
 */
@Service
public class DraftService {

    private DraftState draftState;

    public void initializeDraft(List<Team> teams, List<Player> players, boolean snakeOrder) {
        draftState = new DraftState();
        draftState.setTeams(new ArrayList<>(teams));
        draftState.setAvailablePlayers(new ArrayList<>(players));
        draftState.setDraftedPlayers(new ArrayList<>());
        draftState.setRound(1);
        draftState.setCurrentPick(1);
        draftState.setSnakeOrder(snakeOrder);
    }

    public boolean isDraftInitialized() {
        return draftState != null;
    }

    /**
     * Returns the Team currently on the clock based on the snake-draft order.
     * Odd rounds → ascending order. Even rounds → descending order.
     */
    public Team getCurrentPickingTeam() {
        if (draftState == null) return null;
        int numTeams = draftState.getTeams().size();
        int pick = draftState.getCurrentPick(); // 1-based
        int round = draftState.getRound();
        int idx;
        if (!draftState.isSnakeOrder() || round % 2 == 1) {
            idx = pick - 1; // ascending
        } else {
            idx = numTeams - pick; // descending
        }
        return draftState.getTeams().get(idx);
    }

    /**
     * Load keepers before the draft starts. Keepers are removed from the
     * available pool and assigned to their team rosters immediately.
     */
    public void loadKeepers(List<KeeperDTO> keepers) {
        if (draftState == null) {
            throw new IllegalStateException("Draft not initialized. Call /draft/initialize first.");
        }
        for (KeeperDTO k : keepers) {
            Team team = draftState.getTeams().stream()
                    .filter(t -> t.getName().equals(k.getTeamName()))
                    .findFirst().orElse(null);
            if (team == null) continue;

            // Find the player in the available pool
            Player keeperPlayer = draftState.getAvailablePlayers().stream()
                    .filter(p -> p.getId() == k.getPlayerId())
                    .findFirst().orElse(null);

            Keeper keeper = new Keeper();
            keeper.setPlayerId(k.getPlayerId());
            keeper.setTeamId(team.getId());
            keeper.setRound(k.getRound());
            team.getKeepers().add(keeper);

            // Remove from available pool and add to roster immediately
            if (keeperPlayer != null) {
                keeperPlayer.setKeeper(true);
                draftState.getAvailablePlayers().remove(keeperPlayer);
                team.getRoster().add(keeperPlayer);
            }
        }
    }

    /**
     * Submit a draft pick for a given player by the currently picking team.
     * The teamId is derived from the snake order automatically.
     */
    public Team makePick(int playerId) {
        if (draftState == null) {
            throw new IllegalStateException("Draft not initialized.");
        }
        Player picked = draftState.getAvailablePlayers().stream()
                .filter(p -> p.getId() == playerId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player " + playerId + " not available."));

        Team currentTeam = getCurrentPickingTeam();

        draftState.getAvailablePlayers().remove(picked);
        draftState.getDraftedPlayers().add(picked);
        currentTeam.getRoster().add(picked);

        advanceDraft();
        return currentTeam;
    }

    /**
     * Advances the pick counter, handling snake-round wrapping.
     * Also skips any rounds that are fully occupied by keepers for all teams.
     */
    private void advanceDraft() {
        int numTeams = draftState.getTeams().size();
        int pick = draftState.getCurrentPick();
        int round = draftState.getRound();

        if (pick >= numTeams) {
            draftState.setRound(round + 1);
            draftState.setCurrentPick(1);
        } else {
            draftState.setCurrentPick(pick + 1);
        }
    }

    /**
     * Submit a pick by player name. Tries exact match first (case-insensitive),
     * then falls back to partial substring match.
     */
    public Team makePickByName(String name) {
        if (draftState == null) {
            throw new IllegalStateException("Draft not initialized.");
        }
        String lower = name.trim().toLowerCase();
        Player picked = draftState.getAvailablePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseGet(() -> draftState.getAvailablePlayers().stream()
                        .filter(p -> p.getName().toLowerCase().contains(lower))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No available player found matching: " + name)));

        Team currentTeam = getCurrentPickingTeam();
        draftState.getAvailablePlayers().remove(picked);
        draftState.getDraftedPlayers().add(picked);
        currentTeam.getRoster().add(picked);
        advanceDraft();
        return currentTeam;
    }

    public DraftState getDraftState() {
        return draftState;
    }
}
