package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.*;
import com.example.fantasybaseball.dto.KeeperDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DraftService {
    private DraftState draftState;

    public void initializeDraft(List<Team> teams, List<Player> players, boolean snakeOrder) {
        draftState = new DraftState();
        draftState.setTeams(teams);
        draftState.setAvailablePlayers(players);
        draftState.setDraftedPlayers(new ArrayList<>());
        draftState.setRound(1);
        draftState.setCurrentPick(1);
        draftState.setSnakeOrder(snakeOrder);
    }

    public void loadKeepers(List<KeeperDTO> keepers) {
        for (KeeperDTO k : keepers) {
            Team team = draftState.getTeams().stream()
                .filter(t -> t.getName().equals(k.getTeamName()))
                .findFirst().orElse(null);
            if (team != null) {
                Keeper keeper = new Keeper();
                keeper.setPlayerId(k.getPlayerId());
                keeper.setTeamId(team.getId());
                keeper.setRound(k.getRound());
                team.getKeepers().add(keeper);
                draftState.getAvailablePlayers().removeIf(p -> p.getId() == k.getPlayerId());
            }
        }
    }

    public void makePick(int playerId, int teamId) {
        Player picked = draftState.getAvailablePlayers().stream()
            .filter(p -> p.getId() == playerId)
            .findFirst().orElse(null);
        if (picked != null) {
            draftState.getAvailablePlayers().remove(picked);
            draftState.getDraftedPlayers().add(picked);
            Team team = draftState.getTeams().stream()
                .filter(t -> t.getId() == teamId)
                .findFirst().orElse(null);
            if (team != null) {
                team.getRoster().add(picked);
            }
            advanceDraft();
        }
    }

    private void advanceDraft() {
        int numTeams = draftState.getTeams().size();
        int pick = draftState.getCurrentPick();
        int round = draftState.getRound();
        if (pick == numTeams) {
            draftState.setRound(round + 1);
            draftState.setCurrentPick(1);
        } else {
            draftState.setCurrentPick(pick + 1);
        }
    }

    public DraftState getDraftState() {
        return draftState;
    }
}

