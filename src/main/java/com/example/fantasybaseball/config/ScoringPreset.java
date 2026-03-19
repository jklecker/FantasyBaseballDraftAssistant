package com.example.fantasybaseball.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.util.*;

/**
 * Represents a single scoring preset from scoring-config.json
 * Handles both batting and pitching stat weights, plus team need adjustments.
 */
@Data
public class ScoringPreset {
    private String name;
    private String type;
    private String description;
    private Map<String, Double> batting = new HashMap<>();
    private Map<String, Double> pitching = new HashMap<>();
    private Map<String, JsonNode> teamNeedAdjustments = new HashMap<>();

    /**
     * Get the weight for a batting stat.
     * @param stat e.g., "HR", "RBI", "K"
     * @return the multiplier for this stat, or 0.0 if not defined
     */
    public double getBattingWeight(String stat) {
        return batting.getOrDefault(stat, 0.0);
    }

    /**
     * Get the weight for a pitching stat.
     * @param stat e.g., "W", "SV", "ERA"
     * @return the multiplier for this stat, or 0.0 if not defined
     */
    public double getPitchingWeight(String stat) {
        return pitching.getOrDefault(stat, 0.0);
    }

    /**
     * Get team need adjustment config for a stat.
     * Returns a Map with "threshold" and "boost"/"penalty" keys.
     */
    public Map<String, Double> getTeamNeedAdjustment(String stat) {
        JsonNode node = teamNeedAdjustments.get(stat);
        if (node == null) return Map.of();
        
        Map<String, Double> result = new HashMap<>();
        if (node.has("threshold")) {
            result.put("threshold", node.get("threshold").asDouble());
        }
        if (node.has("boost")) {
            result.put("boost", node.get("boost").asDouble());
        }
        if (node.has("penalty")) {
            result.put("penalty", node.get("penalty").asDouble());
        }
        return result;
    }

    /**
     * Check if there are any team need adjustments defined.
     */
    public boolean hasTeamNeedAdjustments() {
        return teamNeedAdjustments != null && !teamNeedAdjustments.isEmpty();
    }
}

