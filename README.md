# Fantasy Baseball Draft Assistant 🏟️

A **Spring Boot + Gradle** local draft assistant for a 12-team, H2H weekly-categories fantasy baseball league. Supports snake draft order, keeper players, live pick tracking, and category-aware player recommendations.

---

## Requirements

- Java 17+
- Gradle 8.5 (wrapper included)

---

## Getting Started

### 1. Run the app
```bash
./gradlew bootRun
```
The server starts at `http://localhost:8080`.

### 2. Prepare your player pool
Edit `src/main/resources/players.csv` with your league's projected stats.

**CSV column order:**
```
id, name, team, position,
R, H, 2B, 3B, HR, RBI, SB, BB, K,
IP, W, L, SV, pBB, pK, ERA, WHIP
```

---

## API Endpoints

### `POST /draft/initialize`
Start a new draft. Provide team names in snake-draft pick order.
```json
{
  "teamNames": ["Team A","Team B","Team C"],
  "snakeOrder": true
}
```

### `POST /draft/load-keepers`
Load keeper assignments before picking starts.
```json
{
  "keepers": [
    {"teamName": "Team A", "playerId": 1, "round": 2},
    {"teamName": "Team B", "playerId": 5, "round": 1}
  ]
}
```

### `POST /draft/pick?playerId={id}`
Submit the next pick. The system automatically assigns it to the correct team based on the snake draft order.

**Response:**
```json
{
  "pickedByTeam": "Team A",
  "round": 1,
  "nextPick": 2
}
```

### `GET /draft/state`
Returns full draft state: round, pick number, all team rosters, available players.

### `GET /draft/current-team`
Returns the team currently on the clock.

### `GET /draft/recommendations?teamId={id}`
Returns the top 5 recommended players for a given team, factoring in:
- Base stat scoring weights (R, HR, SB, K-penalty, ERA-penalty, etc.)
- Team category needs (e.g., boost SB if team is weak, penalise extra Ks)

---

## Scoring Model (H2H Weekly Categories)

| Category | Direction | Weight |
|---|---|---|
| R, RBI | Good | 1.0× |
| H | Good | 0.8× |
| HR | Good | 1.5× |
| SB | Good | 1.2× |
| 2B | Good | 0.5× |
| 3B | Good | 0.7× |
| BB (batting) | Good | 0.3× |
| K (batting) | **Bad** | −0.7× |
| IP | Good | 0.5× |
| W | Good | 1.0× |
| SV | Good | 1.5× |
| pK | Good | 0.7× |
| L | **Bad** | −1.0× |
| pBB | **Bad** | −0.5× |
| ERA | **Bad** | −2.0× |
| WHIP | **Bad** | −3.0× |

---

## Project Structure

```
src/main/java/com/example/fantasybaseball/
├── FantasyBaseballDraftAssistantApplication.java
├── controller/
│   └── DraftController.java
├── dto/
│   ├── InitializeDraftRequest.java
│   ├── KeeperDTO.java
│   ├── KeeperRequest.java
│   └── TeamKeeperDTO.java
├── model/
│   ├── DraftState.java
│   ├── Keeper.java
│   ├── Player.java
│   ├── Team.java
│   └── TeamStats.java
├── service/
│   ├── DraftService.java
│   ├── PlayerPoolService.java
│   └── ScoringService.java
└── util/
    └── CsvLoader.java

src/main/resources/
├── application.properties
├── keepers.json       ← example keeper assignments
└── players.csv        ← player pool (edit with your projections)
```

---

## Running Tests
```bash
./gradlew test
```

---

## Future Expansion Ideas
- Z-score normalization across the full player pool
- Projections API integration (FanGraphs, Baseball Savant)
- Auto-draft bot for CPU teams
- Simple Thymeleaf draft board UI
- Positional scarcity scoring
- Persistent draft state (H2/PostgreSQL)

