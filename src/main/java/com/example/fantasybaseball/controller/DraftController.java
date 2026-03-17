package com.example.fantasybaseball.controller;

import com.example.fantasybaseball.dto.KeeperDTO;
import com.example.fantasybaseball.dto.KeeperRequest;
import com.example.fantasybaseball.model.DraftState;
import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.model.TeamStats;
import com.example.fantasybaseball.service.DraftService;
import com.example.fantasybaseball.service.PlayerPoolService;
import com.example.fantasybaseball.service.ScoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/draft")
public class DraftController {
    @Autowired
    private DraftService draftService;
    @Autowired
    private PlayerPoolService playerPoolService;
    @Autowired
    private ScoringService scoringService;

    @PostMapping("/pick")
    public void pick(@RequestParam int playerId, @RequestParam int teamId) {
        draftService.makePick(playerId, teamId);
    }

    @GetMapping("/recommendations")
    public List<Player> recommendations(@RequestParam int teamId) {
        DraftState state = draftService.getDraftState();
        TeamStats stats = new TeamStats(); // Should aggregate from team roster
        List<Player> available = state.getAvailablePlayers();
        return scoringService.recommendPlayers(available, stats, state.getTeams().size());
    }

    @GetMapping("/state")
    public DraftState state() {
        return draftService.getDraftState();
    }

    @PostMapping("/load-keepers")
    public void loadKeepers(@RequestBody KeeperRequest request) {
        draftService.loadKeepers(request.getKeepers());
    }
}

