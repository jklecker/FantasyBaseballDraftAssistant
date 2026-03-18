#!/usr/bin/env python3
"""
Import ESPN Fantasy Baseball projections into src/main/resources/players.csv.

Example:
  python tools/import_espn.py --swid "{...}" --season 2026
  python tools/import_espn.py --swid "{...}" --espn-s2 "AE..." --probe
"""

import argparse
import csv
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

SEASON = 2026
API_BASE = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/flb"
LIMIT = 500

# ESPN pro team id -> MLB abbreviation
TEAM_MAP = {
    0: "FA",
    1: "BAL", 2: "BOS", 3: "LAA", 4: "CWS", 5: "CLE", 6: "DET", 7: "KC", 8: "MIN", 9: "NYY",
    10: "OAK", 11: "SEA", 12: "TEX", 13: "TOR", 14: "ATL", 15: "CHC", 16: "CIN", 17: "HOU",
    18: "LAD", 19: "MIL", 20: "NYM", 21: "PHI", 22: "PIT", 23: "STL", 24: "SD", 25: "SF",
    26: "COL", 27: "MIA", 28: "ARI", 29: "WSH", 30: "TB",
}

# ESPN defaultPositionId -> our slot
POS_MAP = {
    1: "SP", 2: "C", 3: "1B", 4: "2B", 5: "3B", 6: "SS",
    7: "OF", 8: "OF", 9: "OF", 10: "1B", 11: "RP", 12: "RP",
}

# Verified via probe on 2026-03-17 (Judge/Soto/Ohtani/Hader/Clase)
# Batting
B_H = "1"
B_2B = "3"
B_3B = "4"
B_HR = "5"
B_R = "7"
B_RBI = "10"
B_K = "20"
B_BB = "21"
B_SB = "23"

# Pitching
P_IP = "37"
P_WHIP = "41"
P_BB = "45"
P_ERA = "47"
P_K = "48"
P_GS = "33"
P_W = "53"
P_L = "54"
P_SV = "57"

HEADER = [
    "id", "name", "team", "position",
    "R", "H", "2B", "3B", "HR", "RBI", "SB", "BB", "K",
    "IP", "W", "L", "SV", "pBB", "pK", "ERA", "WHIP",
]

# Get draft-relevant players ordered by ownership so paging is stable
FANTASY_FILTER = json.dumps({
    "filterSlotIds": {"value": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 17, 18, 19]},
    "sortPercOwned": {"sortPriority": 1, "sortAsc": False},
})


def si(stats, key, default=0):
    try:
        return int(round(float(stats.get(key, default))))
    except (TypeError, ValueError):
        return default


def sf(stats, key, default=0.0):
    try:
        return float(stats.get(key, default))
    except (TypeError, ValueError):
        return default


def build_cookie(swid, espn_s2):
    cookie = f"SWID={swid}"
    if espn_s2:
        cookie += f"; espn_s2={urllib.parse.unquote(espn_s2)}"
    return cookie


def headers(cookie):
    return {
        "Accept": "application/json",
        "User-Agent": "Mozilla/5.0",
        "X-Fantasy-Source": "kona",
        "X-Fantasy-Platform": "kona-PROD",
        "Referer": "https://fantasy.espn.com/baseball/players/projections",
        "X-Fantasy-Filter": FANTASY_FILTER,
        "Cookie": cookie,
    }


def find_projection_stats(obj):
    stats_list = obj.get("stats") or (obj.get("playerPoolEntry") or {}).get("player", {}).get("stats") or []
    for s in stats_list:
        if s.get("statSourceId") == 1 and s.get("stats"):
            return s["stats"]
    return None


def fetch_all_players(season, cookie):
    out = []
    offset = 0
    prev_names = None

    while True:
        url = f"{API_BASE}/seasons/{season}/players?view=projections&view=kona_player_info&limit={LIMIT}&offset={offset}"
        req = urllib.request.Request(url, headers=headers(cookie))
        resp = urllib.request.urlopen(req, timeout=30)
        data = json.loads(resp.read())
        batch = data if isinstance(data, list) else data.get("players", [])
        if not batch:
            break

        names = {
            (p.get("fullName") or (p.get("playerPoolEntry") or {}).get("player", {}).get("fullName") or "")
            for p in batch
        }
        if prev_names is not None and names == prev_names:
            # Defensive stop if backend loops the same page
            break
        prev_names = names

        out.extend(batch)
        offset += len(batch)
        if len(batch) < LIMIT:
            break

    return out


def build_rows(raw_players, min_pa, min_ip, probe):
    rows = []
    probed = False

    for obj in raw_players:
        name = obj.get("fullName") or (obj.get("playerPoolEntry") or {}).get("player", {}).get("fullName")
        if not name:
            continue

        pos_id = obj.get("defaultPositionId") or (obj.get("playerPoolEntry") or {}).get("player", {}).get("defaultPositionId") or 0
        team_id = obj.get("proTeamId") or (obj.get("playerPoolEntry") or {}).get("player", {}).get("proTeamId") or 0

        stats = find_projection_stats(obj)
        if not stats:
            continue

        if probe and not probed:
            print(f"[probe] {name} stat keys: {sorted(stats.keys(), key=lambda k: int(k))}")
            probed = True

        team = TEAM_MAP.get(team_id, "FA")
        # Treat players as pitchers only when ESPN marks them pitching slots,
        # or when position is unknown but they have meaningful IP.
        is_pitcher = (pos_id in (1, 11, 12)) or (pos_id == 0 and sf(stats, P_IP) > 0)

        if is_pitcher:
            ip = sf(stats, P_IP)
            if ip < min_ip:
                continue
            gs = sf(stats, P_GS)
            rows.append({
                "name": name,
                "team": team,
                "position": "SP" if gs >= 5 else "RP",
                "R": 0, "H": 0, "2B": 0, "3B": 0, "HR": 0, "RBI": 0, "SB": 0, "BB": 0, "K": 0,
                "IP": ip,
                "W": si(stats, P_W),
                "L": si(stats, P_L),
                "SV": si(stats, P_SV),
                "pBB": si(stats, P_BB),
                "pK": si(stats, P_K),
                "ERA": sf(stats, P_ERA),
                "WHIP": sf(stats, P_WHIP),
            })
        else:
            h = si(stats, B_H)
            bb = si(stats, B_BB)
            k = si(stats, B_K)
            pa_est = h + bb + k
            if pa_est < min_pa:
                continue
            rows.append({
                "name": name,
                "team": team,
                "position": POS_MAP.get(pos_id, "OF"),
                "R": si(stats, B_R),
                "H": h,
                "2B": si(stats, B_2B),
                "3B": si(stats, B_3B),
                "HR": si(stats, B_HR),
                "RBI": si(stats, B_RBI),
                "SB": si(stats, B_SB),
                "BB": bb,
                "K": k,
                "IP": 0.0, "W": 0, "L": 0, "SV": 0, "pBB": 0, "pK": 0, "ERA": 0.0, "WHIP": 0.0,
            })

    return rows


def write_csv(rows, out_path):
    bat = [r for r in rows if r["IP"] == 0.0]
    pit = [r for r in rows if r["IP"] > 0.0]
    bat.sort(key=lambda r: (r["HR"], r["RBI"], r["R"]), reverse=True)
    pit.sort(key=lambda r: (r["SV"], r["W"], r["pK"], r["IP"]), reverse=True)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(HEADER)
        for i, r in enumerate(bat + pit, start=1):
            w.writerow([
                i, r["name"], r["team"], r["position"],
                r["R"], r["H"], r["2B"], r["3B"], r["HR"], r["RBI"], r["SB"], r["BB"], r["K"],
                f"{r['IP']:.1f}", r["W"], r["L"], r["SV"], r["pBB"], r["pK"], f"{r['ERA']:.2f}", f"{r['WHIP']:.2f}",
            ])

    print(f"OK wrote {len(bat) + len(pit)} players -> {out_path}")
    print(f"   batters={len(bat)} pitchers={len(pit)}")


def main():
    p = argparse.ArgumentParser(description="Import ESPN projections to players.csv")
    p.add_argument("--swid", required=True)
    p.add_argument("--espn-s2", default="")
    p.add_argument("--season", type=int, default=SEASON)
    p.add_argument("--out", default="src/main/resources/players.csv")
    p.add_argument("--min-pa", type=int, default=80)
    p.add_argument("--min-ip", type=float, default=20.0)
    p.add_argument("--probe", action="store_true")
    args = p.parse_args()

    cookie = build_cookie(args.swid, args.espn_s2)
    raw = fetch_all_players(args.season, cookie)
    if not raw:
        print("ERROR: no players returned from ESPN API")
        sys.exit(1)

    rows = build_rows(raw, args.min_pa, args.min_ip, args.probe)
    if not rows:
        print("ERROR: no projected rows built (check SWID/espn_s2 or thresholds)")
        sys.exit(1)

    write_csv(rows, Path(args.out))


if __name__ == "__main__":
    main()

