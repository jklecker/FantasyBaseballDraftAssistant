# Fantasy Baseball Draft Assistant 🏟️

A **Spring Boot + Gradle** local draft assistant for a 12-team, H2H weekly-categories fantasy baseball league. Supports snake draft order, keeper players, live pick tracking, and category-aware player recommendations.

---

## Requirements

- Java 17+
- Gradle 8.5 (wrapper included — no Gradle install needed)

---

## Quick Start

```bash
# Clone and run
./gradlew bootRun
```

The server starts at **`http://localhost:8080`**.

> **Windows users:** use `gradlew.bat bootRun` or `.\gradlew bootRun` in PowerShell.

---

## Typical Draft Workflow

Here is a complete step-by-step example for a 3-team draft using `curl`.  
Swap the team names and player IDs for your real league.

### Step 1 — Initialize the draft

Provide team names in snake-order pick position (position 1 picks first in round 1).

```bash
curl -s -X POST http://localhost:8080/draft/initialize \
  -H "Content-Type: application/json" \
  -d '{
    "teamNames": ["The Lumber Co.", "Ace Factory", "Speed Demons"],
    "snakeOrder": true
  }'
```

**Response:**
```json
{
  "round": 1,
  "currentPick": 1,
  "teams": [
    {"id": 1, "name": "The Lumber Co.", "roster": [], "keepers": []},
    {"id": 2, "name": "Ace Factory",    "roster": [], "keepers": []},
    {"id": 3, "name": "Speed Demons",   "roster": [], "keepers": []}
  ],
  "availablePlayers": [ ... ],
  "draftedPlayers": [],
  "snakeOrder": true
}
```

---

### Step 2 — Load keepers (optional)

Each team can have up to 2 keepers. Specify the **round** their keeper slot occupies.
Keeper players are immediately removed from the available pool.

```bash
curl -s -X POST http://localhost:8080/draft/load-keepers \
  -H "Content-Type: application/json" \
  -d '{
    "keepers": [
      {"teamName": "The Lumber Co.", "playerId": 1, "round": 2},
      {"teamName": "Ace Factory",    "playerId": 5, "round": 1}
    ]
  }'
```

**Response:** updated `DraftState` with keepers reflected on team rosters and removed from the available pool.

---

### Step 3 — Check who is on the clock

```bash
curl -s http://localhost:8080/draft/current-team
```

**Response:**
```json
{
  "id": 1,
  "name": "The Lumber Co.",
  "roster": [],
  "keepers": [{"playerId": 1, "teamId": 1, "round": 2}]
}
```

---

### Step 4 — Get recommendations for the picking team

```bash
curl -s "http://localhost:8080/draft/recommendations?teamId=1"
```

**Response:** top 5 available players ranked by weighted score, adjusted for the team's current category needs.

```json
[
  {"id": 6, "name": "Ronald Acuna Jr.", "position": "OF", "R": 120, "HR": 38, "SB": 65, ...},
  {"id": 4, "name": "Jose Ramirez",     "position": "3B", "R": 95,  "HR": 35, "SB": 20, ...},
  {"id": 3, "name": "Jacob deGrom",     "position": "SP", "IP": 180, "W": 15, "ERA": 2.10, ...},
  ...
]
```

---

### Step 5 — Submit a pick

The system automatically assigns the pick to the team currently on the clock (snake order is handled internally).

```bash
curl -s -X POST "http://localhost:8080/draft/pick?playerId=6"
```

**Response:**
```json
{
  "pickedByTeam": "The Lumber Co.",
  "round": 1,
  "nextPick": 2
}
```

Repeat Steps 3–5 for each pick. After all 3 teams pick in round 1, round 2 automatically reverses order (snake).

---

### Step 6 — View full draft state at any time

```bash
curl -s http://localhost:8080/draft/state
```

Returns the complete state: current round, pick counter, every team's roster, and remaining available players.

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/draft/initialize` | Start a new draft with team names & snake-order flag |
| `POST` | `/draft/load-keepers` | Assign keeper players to teams before drafting |
| `POST` | `/draft/pick?playerId=` | Submit the next pick (team derived from snake order) |
| `GET`  | `/draft/current-team` | Get the team currently on the clock |
| `GET`  | `/draft/recommendations?teamId=` | Top 5 picks for a team based on needs |
| `GET`  | `/draft/state` | Full draft state dump |

### Error responses

All endpoints return `400 Bad Request` if the draft has not been initialized:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Draft not initialized. POST /draft/initialize first."
}
```

Picking a player not in the available pool returns `400`:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Player 99 not available."
}
```

---

## Configuration

`src/main/resources/application.properties`:

```properties
server.port=8080
players.csv.path=players.csv        # classpath-relative path to player pool CSV
spring.thymeleaf.check-template-location=false
```

---

## Player Pool CSV Format

Edit `src/main/resources/players.csv` with your league's projected stats.

**Column order (21 columns, header row required):**

```
id, name, team, position,
R, H, 2B, 3B, HR, RBI, SB, BB, K,
IP, W, L, SV, pBB, pK, ERA, WHIP
```

- Batters: fill pitching columns (IP, W, L, SV, pBB, pK, ERA, WHIP) with `0`
- Pitchers: fill batting columns (R, H, 2B, 3B, HR, RBI, SB, BB, K) with `0`

**Example rows:**
```csv
id,name,team,position,R,H,2B,3B,HR,RBI,SB,BB,K,IP,W,L,SV,pBB,pK,ERA,WHIP
1,Mike Trout,LAA,OF,100,150,30,2,40,100,20,80,120,0,0,0,0,0,0,0.00,0.00
3,Jacob deGrom,TEX,SP,0,0,0,0,0,0,0,0,0,180,15,5,0,30,200,2.10,0.95
10,Josh Hader,MIL,RP,0,0,0,0,0,0,0,0,0,60,5,3,35,20,90,2.00,0.85
```

---

## Scoring Model (H2H Weekly Categories)

Base weights applied to every player. Additional team-need bonuses are applied at recommendation time.

### Base Weights

| Category | Direction | Weight |
|----------|-----------|--------|
| R | ✅ Good | +1.0× |
| H | ✅ Good | +0.8× |
| 2B | ✅ Good | +0.5× |
| 3B | ✅ Good | +0.7× |
| HR | ✅ Good | +1.5× |
| RBI | ✅ Good | +1.0× |
| SB | ✅ Good | +1.2× |
| BB (batting) | ✅ Good | +0.3× |
| **K (batting)** | ❌ Bad | **−0.7×** |
| IP | ✅ Good | +0.5× |
| W | ✅ Good | +1.0× |
| SV | ✅ Good | +1.5× |
| pK | ✅ Good | +0.7× |
| **L** | ❌ Bad | **−1.0×** |
| **pBB** | ❌ Bad | **−0.5×** |
| **ERA** *(if IP > 0)* | ❌ Bad | **−2.0×** |
| **WHIP** *(if IP > 0)* | ❌ Bad | **−3.0×** |

### Team-Need Bonuses (applied at recommendation time)

| Condition | Bonus |
|-----------|-------|
| Team SB < 10 | +1.5× per SB the player has |
| Team K > 100 | −0.5× per K the player has |
| Team SV = 0 | +1.0× per SV the player has |
| Team HR < 10 | +0.5× per HR the player has |

---

## Running Tests

```bash
./gradlew test
```

Test report is generated at `build/reports/tests/test/index.html`.

### Test Coverage

| Test Class | What It Tests |
|------------|--------------|
| `DraftServiceTest` | Snake order, round advancement, keeper loading, invalid picks, non-snake draft |
| `ScoringServiceTest` | Batter/pitcher scoring, team-need adjustments, recommendation ordering |
| `CsvLoaderTest` | CSV parsing, malformed row handling |
| `DraftControllerIntegrationTest` | All REST endpoints via MockMvc, error responses, full draft flow |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/example/fantasybaseball/
│   │   ├── FantasyBaseballDraftAssistantApplication.java
│   │   ├── controller/
│   │   │   └── DraftController.java
│   │   ├── dto/
│   │   │   ├── InitializeDraftRequest.java
│   │   │   ├── KeeperDTO.java
│   │   │   ├── KeeperRequest.java
│   │   │   └── TeamKeeperDTO.java
│   │   ├── model/
│   │   │   ├── DraftState.java
│   │   │   ├── Keeper.java
│   │   │   ├── Player.java
│   │   │   ├── Team.java
│   │   │   └── TeamStats.java
│   │   ├── service/
│   │   │   ├── DraftService.java
│   │   │   ├── PlayerPoolService.java
│   │   │   └── ScoringService.java
│   │   └── util/
│   │       └── CsvLoader.java
│   └── resources/
│       ├── application.properties
│       ├── keepers.json        ← example keeper payload
│       └── players.csv         ← player pool (edit with your projections)
└── test/
    └── java/com/example/fantasybaseball/
        ├── DraftServiceTest.java
        ├── ScoringServiceTest.java
        ├── CsvLoaderTest.java
        └── DraftControllerIntegrationTest.java
```

---

## Future Expansion Ideas

- Z-score normalization across the full player pool
- Projections API integration (FanGraphs, Baseball Savant)
- Auto-draft bot for CPU teams
- Thymeleaf draft board UI (live view of rosters + recommendations)
- Positional scarcity scoring
- Persistent draft state (H2 / PostgreSQL)

