#!/usr/bin/env python3
"""
Convert FanGraphs projection CSVs to the format expected by players.csv.

Usage (run from the project root):
    python tools/import_fangraphs.py \
        --batters  path/to/fangraphs_batting.csv  \
        --pitchers path/to/fangraphs_pitching.csv \
        --out      src/main/resources/players.csv

Where to get the free CSVs:
    1. Go to https://www.fangraphs.com/projections
    2. Set Type = Steamer (or ZiPS — both work)
    3. Batting tab  → "Export Data" button → save as fangraphs_batting.csv
    4. Pitching tab → "Export Data" button → save as fangraphs_pitching.csv

Python 3.9+ required. No extra packages needed.
"""

import argparse
import csv
import sys
from pathlib import Path


# ── position normalisation ────────────────────────────────────────────────────

_POS_MAP = {
    "C":      "C",
    "1B":     "1B",
    "2B":     "2B",
    "3B":     "3B",
    "SS":     "SS",
    "LF":     "OF",
    "CF":     "OF",
    "RF":     "OF",
    "OF":     "OF",
    "DH":     "1B",   # treat DH as UTIL/1B
    "1B/DH":  "1B",
    "2B/SS":  "2B",
    "3B/SS":  "3B",
    "SS/2B":  "SS",
    "1B/3B":  "1B",
    "3B/1B":  "3B",
    "OF/1B":  "OF",
}

def normalise_pos(raw: str) -> str:
    """Take a FanGraphs Pos string like '2B/SS' and return the primary slot."""
    if not raw:
        return "OF"
    primary = raw.split("/")[0].strip().upper()
    return _POS_MAP.get(primary, "OF")


# ── helpers ───────────────────────────────────────────────────────────────────

def safe_int(val: str, default: int = 0) -> int:
    try:
        return int(round(float(val)))
    except (ValueError, TypeError):
        return default

def safe_float(val: str, default: float = 0.0) -> float:
    try:
        return float(val)
    except (ValueError, TypeError):
        return default


# ── readers ───────────────────────────────────────────────────────────────────

def read_batters(path: Path) -> list:
    rows = []
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for r in reader:
            name = r.get("Name", "").strip()
            if not name:
                continue
            rows.append({
                "name":     name,
                "team":     r.get("Team", "FA").strip() or "FA",
                "position": normalise_pos(r.get("Pos", r.get("Position", ""))),
                "R":   safe_int(r.get("R")),
                "H":   safe_int(r.get("H")),
                "2B":  safe_int(r.get("2B")),
                "3B":  safe_int(r.get("3B")),
                "HR":  safe_int(r.get("HR")),
                "RBI": safe_int(r.get("RBI")),
                "SB":  safe_int(r.get("SB")),
                "BB":  safe_int(r.get("BB")),
                "K":   safe_int(r.get("SO", r.get("K", "0"))),
                # pitching cols → zero
                "IP": 0.0, "W": 0, "L": 0, "SV": 0,
                "pBB": 0, "pK": 0, "ERA": 0.0, "WHIP": 0.0,
            })
    return rows


def read_pitchers(path: Path) -> list:
    rows = []
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for r in reader:
            name = r.get("Name", "").strip()
            if not name:
                continue

            ip   = safe_float(r.get("IP", "0"))
            bb   = safe_int(r.get("BB", "0"))
            h    = safe_int(r.get("H", "0"))
            sv   = safe_int(r.get("SV", "0"))
            gs   = safe_int(r.get("GS", "0"))

            # Use WHIP from the file if available, otherwise calculate it
            if r.get("WHIP"):
                whip = safe_float(r["WHIP"])
            else:
                whip = round((bb + h) / ip, 2) if ip > 0 else 0.0

            # SP if they have meaningful starts, RP otherwise
            position = "SP" if gs >= 5 else "RP"

            rows.append({
                "name":     name,
                "team":     r.get("Team", "FA").strip() or "FA",
                "position": position,
                # batting cols → zero
                "R": 0, "H": 0, "2B": 0, "3B": 0, "HR": 0,
                "RBI": 0, "SB": 0, "BB": 0, "K": 0,
                "IP":   ip,
                "W":    safe_int(r.get("W", "0")),
                "L":    safe_int(r.get("L", "0")),
                "SV":   sv,
                "pBB":  bb,
                "pK":   safe_int(r.get("SO", r.get("K", "0"))),
                "ERA":  safe_float(r.get("ERA", "0")),
                "WHIP": whip,
            })
    return rows


# ── writer ────────────────────────────────────────────────────────────────────

HEADER = [
    "id", "name", "team", "position",
    "R", "H", "2B", "3B", "HR", "RBI", "SB", "BB", "K",
    "IP", "W", "L", "SV", "pBB", "pK", "ERA", "WHIP",
]

def write_csv(players: list, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(HEADER)
        for i, p in enumerate(players, start=1):
            writer.writerow([
                i,
                p["name"], p["team"], p["position"],
                p["R"], p["H"], p["2B"], p["3B"], p["HR"],
                p["RBI"], p["SB"], p["BB"], p["K"],
                f'{p["IP"]:.1f}',
                p["W"], p["L"], p["SV"], p["pBB"], p["pK"],
                f'{p["ERA"]:.2f}', f'{p["WHIP"]:.2f}',
            ])
    print(f"✅  Wrote {len(players)} players → {out_path}")


# ── main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Convert FanGraphs Steamer/ZiPS CSVs → players.csv"
    )
    parser.add_argument("--batters",  required=True, help="FanGraphs batting projections CSV")
    parser.add_argument("--pitchers", required=True, help="FanGraphs pitching projections CSV")
    parser.add_argument(
        "--out",
        default="src/main/resources/players.csv",
        help="Output path (default: src/main/resources/players.csv)",
    )
    args = parser.parse_args()

    batters  = read_batters(Path(args.batters))
    pitchers = read_pitchers(Path(args.pitchers))
    all_players = batters + pitchers

    if not all_players:
        print("❌  No players found — check your input files.", file=sys.stderr)
        sys.exit(1)

    write_csv(all_players, Path(args.out))
    print(f"   {len(batters)} batters  +  {len(pitchers)} pitchers")


if __name__ == "__main__":
    main()

