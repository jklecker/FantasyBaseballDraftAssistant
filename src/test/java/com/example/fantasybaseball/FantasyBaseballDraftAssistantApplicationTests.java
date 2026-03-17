package com.example.fantasybaseball;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test — verifies the Spring application context loads without errors.
 * All functional tests live in the dedicated test classes:
 *   - DraftServiceTest
 *   - ScoringServiceTest
 *   - CsvLoaderTest
 *   - DraftControllerIntegrationTest
 */
@SpringBootTest
class FantasyBaseballDraftAssistantApplicationTests {

    @Test
    void contextLoads() {
        // passes if the Spring context starts up without exceptions
    }
}
