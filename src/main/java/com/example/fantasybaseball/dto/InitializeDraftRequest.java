package com.example.fantasybaseball.dto;

import lombok.Data;

import java.util.List;

/**
 * Request body for POST /draft/initialize.
 * Provide the ordered list of team names (position = draft order).
 * snakeOrder defaults to true.
 */
@Data
public class InitializeDraftRequest {
    private List<String> teamNames;
    private boolean snakeOrder = true;
}

