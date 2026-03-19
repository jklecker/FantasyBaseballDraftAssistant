package com.example.fantasybaseball.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Loads and manages scoring configuration from scoring-config.json.
 * Provides access to scoring presets and their weights.
 */
@Data
@Component
public class ScoringConfigLoader {
    private String activePreset;
    private Map<String, ScoringPreset> presets = new HashMap<>();
    private Map<String, Object> advancedFeatures = new HashMap<>();

    public ScoringConfigLoader() {
        try {
            load();
        } catch (IOException e) {
            System.err.println("Failed to load scoring-config.json, using defaults: " + e.getMessage());
        }
    }

    /**
     * Load the scoring configuration from scoring-config.json
     */
    private void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("scoring-config.json");
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> data = mapper.readValue(resource.getInputStream(), Map.class);

        this.activePreset = (String) data.get("activePreset");
        
        // Load presets
        @SuppressWarnings("unchecked")
        Map<String, Object> presetsData = (Map<String, Object>) data.get("presets");
        if (presetsData != null) {
            for (Map.Entry<String, Object> entry : presetsData.entrySet()) {
                ScoringPreset preset = mapper.convertValue(entry.getValue(), ScoringPreset.class);
                this.presets.put(entry.getKey(), preset);
            }
        }

        // Load advanced features
        Object advFeatures = data.get("advancedFeatures");
        if (advFeatures != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> adv = (Map<String, Object>) advFeatures;
            this.advancedFeatures = adv;
        }

        System.out.println("Loaded scoring config with preset: " + activePreset);
    }

    /**
     * Get the active scoring preset
     * @return the currently active ScoringPreset, or null if not found
     */
    public ScoringPreset getActivePreset() {
        return presets.get(activePreset);
    }

    /**
     * Get a specific preset by name
     * @param presetName e.g., "h2h_categories", "espn_points_10team"
     * @return the ScoringPreset, or null if not found
     */
    public ScoringPreset getPreset(String presetName) {
        return presets.get(presetName);
    }

    /**
     * Get all available preset names
     */
    public Set<String> getAvailablePresets() {
        return presets.keySet();
    }

    /**
     * Check if a preset exists
     */
    public boolean hasPreset(String presetName) {
        return presets.containsKey(presetName);
    }
}

