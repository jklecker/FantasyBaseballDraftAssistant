package com.example.fantasybaseball.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the React SPA's index.html for the root path.
 * This is a belt-and-suspenders complement to Spring Boot's
 * WelcomePageHandlerMapping — it guarantees the page loads even
 * when the static resource auto-config behaves unexpectedly.
 */
@RestController
public class SpaController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        Resource resource = new ClassPathResource("static/index.html");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}

