package com.example.fantasybaseball.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Serves the React SPA index.html at "/" and exposes a debug endpoint
 * that lists every file found under classpath:static/ so we can verify
 * the production JAR contains the right resources.
 */
@RestController
public class SpaController {

    /**
     * Serve the Vite-built index.html.
     * Reads bytes explicitly so there is no ambiguity about Content-Type,
     * charset, or chunked-encoding edge cases.
     * Cache-Control: no-store forces the browser to re-fetch on every load
     * (important when asset hashes change between deployments).
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() throws IOException {
        ClassPathResource res = new ClassPathResource("static/index.html");
        if (!res.exists()) {
            return ResponseEntity.status(503)
                    .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                    .body("<h1>503 – index.html not found in JAR</h1>"
                            + "<p>The Vite build was not packaged correctly. "
                            + "Check <a href='/debug/resources'>/debug/resources</a>.</p>");
        }
        String html;
        try (InputStream is = res.getInputStream()) {
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .body(html);
    }

    /**
     * Debug endpoint — lists every resource the JVM classloader can find
     * under the "static" classpath prefix.  Hit this on Render to confirm
     * the assets folder is present inside the JAR:
     *   GET https://your-render-url.onrender.com/debug/resources
     */
    @GetMapping(value = "/debug/resources", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> debugResources() {
        List<String> found = new ArrayList<>();
        String[] paths = { "static/", "static/index.html", "static/assets/" };
        for (String p : paths) {
            URL url = getClass().getClassLoader().getResource(p);
            found.add(p + " → " + (url != null ? url.toString() : "NOT FOUND"));
        }
        // List everything in static/ up to depth 2
        try {
            Enumeration<URL> urls = getClass().getClassLoader().getResources("static");
            while (urls.hasMoreElements()) {
                found.add("classpath root for 'static': " + urls.nextElement());
            }
        } catch (IOException ignored) {}

        return ResponseEntity.ok(String.join("\n", found));
    }
}
