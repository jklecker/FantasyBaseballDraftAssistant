package com.example.fantasybaseball.service;

import com.example.fantasybaseball.model.Player;
import com.example.fantasybaseball.util.CsvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlayerPoolService {

    private static final Logger log = LoggerFactory.getLogger(PlayerPoolService.class);

    // MLB full team name → abbreviation
    private static final Map<String, String> TEAM_ABBREVS = Map.ofEntries(
        Map.entry("Arizona Diamondbacks",  "ARI"), Map.entry("Atlanta Braves",        "ATL"),
        Map.entry("Baltimore Orioles",     "BAL"), Map.entry("Boston Red Sox",         "BOS"),
        Map.entry("Chicago Cubs",          "CHC"), Map.entry("Chicago White Sox",      "CWS"),
        Map.entry("Cincinnati Reds",       "CIN"), Map.entry("Cleveland Guardians",    "CLE"),
        Map.entry("Colorado Rockies",      "COL"), Map.entry("Detroit Tigers",         "DET"),
        Map.entry("Houston Astros",        "HOU"), Map.entry("Kansas City Royals",     "KC"),
        Map.entry("Los Angeles Angels",    "LAA"), Map.entry("Los Angeles Dodgers",    "LAD"),
        Map.entry("Miami Marlins",         "MIA"), Map.entry("Milwaukee Brewers",      "MIL"),
        Map.entry("Minnesota Twins",       "MIN"), Map.entry("New York Mets",          "NYM"),
        Map.entry("New York Yankees",      "NYY"), Map.entry("Oakland Athletics",      "OAK"),
        Map.entry("Athletics",             "OAK"), Map.entry("Philadelphia Phillies",  "PHI"),
        Map.entry("Pittsburgh Pirates",    "PIT"), Map.entry("San Diego Padres",       "SD"),
        Map.entry("San Francisco Giants",  "SF"),  Map.entry("Seattle Mariners",       "SEA"),
        Map.entry("St. Louis Cardinals",   "STL"), Map.entry("Tampa Bay Rays",         "TB"),
        Map.entry("Texas Rangers",         "TEX"), Map.entry("Toronto Blue Jays",      "TOR"),
        Map.entry("Washington Nationals",  "WSH")
    );

    // MLB position abbreviation → our roster slot
    private static final Map<String, String> POS_MAP = Map.of(
        "C",  "C",  "1B", "1B", "2B", "2B", "3B", "3B", "SS", "SS",
        "LF", "OF", "CF", "OF", "RF", "OF", "OF", "OF", "DH", "1B"
    );

    @Autowired private CsvLoader csvLoader;

    @Value("${players.csv.path:players.csv}")
    private String csvPath;

    /** Last completed MLB season to pull stats from. Override in application.properties. */
    @Value("${mlb.stats.season:2025}")
    private int statsSeason;

    // 5 s connect / 15 s read — prevents the MLB API from blocking Render startup
    private final RestTemplate restTemplate;

    public PlayerPoolService() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
    }
    private List<Player> playerPool = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            log.info("Fetching player pool from MLB Stats API (season {})…", statsSeason);
            playerPool = fetchFromMlbApi();
            log.info("Loaded {} players from MLB Stats API", playerPool.size());
        } catch (Exception e) {
            log.warn("MLB Stats API unavailable ({}), falling back to local CSV", e.getMessage());
            playerPool = csvLoader.loadPlayers(csvPath);
            log.info("Loaded {} players from CSV fallback", playerPool.size());
        }
    }

    // ── existing public surface (unchanged) ──────────────────────────────────

    public void loadPlayerPool(String classpathFilename) {
        playerPool = csvLoader.loadPlayers(classpathFilename);
    }

    public List<Player> getAvailablePlayers(List<Integer> draftedIds) {
        if (playerPool == null) return new ArrayList<>();
        return playerPool.stream()
                .filter(p -> !draftedIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public List<Player> getAllPlayers() {
        return playerPool != null ? playerPool : new ArrayList<>();
    }

    // ── MLB Stats API fetching ────────────────────────────────────────────────

    private List<Player> fetchFromMlbApi() {
        List<Player> batters  = fetchStatGroup("hitting");
        List<Player> pitchers = fetchStatGroup("pitching");
        List<Player> all = new ArrayList<>(batters);
        all.addAll(pitchers);
        for (int i = 0; i < all.size(); i++) all.get(i).setId(i + 1);
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<Player> fetchStatGroup(String group) {
        String url = String.format(
            "https://statsapi.mlb.com/api/v1/stats?stats=season&season=%d" +
            "&sportId=1&group=%s&gameType=R&limit=500", statsSeason, group);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return Collections.emptyList();

        List<Map<String, Object>> statsList = (List<Map<String, Object>>) response.get("stats");
        if (statsList == null || statsList.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> splits = (List<Map<String, Object>>) statsList.get(0).get("splits");
        if (splits == null) return Collections.emptyList();

        boolean isBatting = "hitting".equals(group);
        List<Player> players = new ArrayList<>();
        for (Map<String, Object> split : splits) {
            try {
                Player p = isBatting ? parseBatter(split) : parsePitcher(split);
                // skip cup-of-coffee appearances
                if (isBatting  && p.getH() < 10) continue;
                if (!isBatting && p.getIP() < 5)  continue;
                players.add(p);
            } catch (Exception e) {
                log.debug("Skipping split: {}", e.getMessage());
            }
        }
        return players;
    }

    @SuppressWarnings("unchecked")
    private Player parseBatter(Map<String, Object> split) {
        Map<String, Object> stat = (Map<String, Object>) split.get("stat");
        Map<String, Object> pi   = (Map<String, Object>) split.get("player");
        Map<String, Object> ti   = (Map<String, Object>) split.get("team");
        Map<String, Object> pos  = (Map<String, Object>) split.get("position");

        Player p = new Player();
        p.setName((String) pi.get("fullName"));
        p.setTeam(toAbbrev(ti != null ? (String) ti.get("name") : null));
        p.setPosition(POS_MAP.getOrDefault(
            pos != null ? (String) pos.get("abbreviation") : "", "OF"));
        p.setR(getInt(stat, "runs"));       p.setH(getInt(stat, "hits"));
        p.setTwoB(getInt(stat, "doubles")); p.setThreeB(getInt(stat, "triples"));
        p.setHR(getInt(stat, "homeRuns")); p.setRBI(getInt(stat, "rbi"));
        p.setSB(getInt(stat, "stolenBases")); p.setBB(getInt(stat, "baseOnBalls"));
        p.setK(getInt(stat, "strikeOuts"));
        p.setIP(0); p.setW(0); p.setL(0); p.setSV(0);
        p.setPitchingBB(0); p.setPitchingK(0); p.setERA(0); p.setWHIP(0);
        return p;
    }

    @SuppressWarnings("unchecked")
    private Player parsePitcher(Map<String, Object> split) {
        Map<String, Object> stat = (Map<String, Object>) split.get("stat");
        Map<String, Object> pi   = (Map<String, Object>) split.get("player");
        Map<String, Object> ti   = (Map<String, Object>) split.get("team");

        Player p = new Player();
        p.setName((String) pi.get("fullName"));
        p.setTeam(toAbbrev(ti != null ? (String) ti.get("name") : null));
        p.setPosition(getInt(stat, "gamesStarted") >= 5 ? "SP" : "RP");
        p.setR(0); p.setH(0); p.setTwoB(0); p.setThreeB(0);
        p.setHR(0); p.setRBI(0); p.setSB(0); p.setBB(0); p.setK(0);
        p.setIP(getDbl(stat, "inningsPitched"));
        p.setW(getInt(stat, "wins")); p.setL(getInt(stat, "losses"));
        p.setSV(getInt(stat, "saves"));
        p.setPitchingBB(getInt(stat, "baseOnBalls"));
        p.setPitchingK(getInt(stat, "strikeOuts"));
        p.setERA(getDbl(stat, "era")); p.setWHIP(getDbl(stat, "whip"));
        return p;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String toAbbrev(String name) {
        if (name == null) return "FA";
        return TEAM_ABBREVS.getOrDefault(name, name);
    }

    private int getInt(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number)  return ((Number) v).intValue();
        if (v instanceof String)  try { return (int) Math.round(Double.parseDouble((String) v)); }
                                  catch (NumberFormatException ignored) {}
        return 0;
    }

    private double getDbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number)  return ((Number) v).doubleValue();
        if (v instanceof String)  try { return Double.parseDouble((String) v); }
                                  catch (NumberFormatException ignored) {}
        return 0.0;
    }
}
