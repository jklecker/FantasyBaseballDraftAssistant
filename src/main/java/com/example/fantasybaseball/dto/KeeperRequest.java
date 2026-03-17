package com.example.fantasybaseball.dto;

import lombok.Data;
import java.util.List;

@Data
public class KeeperRequest {
    private List<KeeperDTO> keepers;
}

