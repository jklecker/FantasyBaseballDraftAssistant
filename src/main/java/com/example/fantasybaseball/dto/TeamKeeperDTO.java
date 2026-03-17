package com.example.fantasybaseball.dto;

import lombok.Data;
import java.util.List;

@Data
public class TeamKeeperDTO {
    private String teamName;
    private List<KeeperDTO> keepers;
}

