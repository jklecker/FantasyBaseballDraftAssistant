#!/usr/bin/env python3
"""
Decodes ESPN stat IDs by finding known players and printing all their projection values.
"""
import json, urllib.request, urllib.parse

SWID    = "{39BD2A28-F6F2-4C1C-9EF2-258CF14DA25A}"
SEASON  = 2026
API     = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/flb/seasons/{}/players".format(SEASON)

# Players we want to find: closer (for SV), elite batter (for HR/R/RBI/SB), SP (already have Abbott)
TARGETS = {"Clase", "Hader", "Judge", "Ohtani", "Trout", "Acuna", "Soto"}

# Sort by % owned descending so top players come first
FANTASY_FILTER = json.dumps({
    "filterSlotIds": {"value": [0,1,2,3,4,5,6,7,8,9,10,11,12,13,17,18,19]},
    "sortPercOwned": {"sortPriority": 1, "sortAsc": False}
})

def make_headers():
    return {
        "Accept": "application/json",
        "User-Agent": "Mozilla/5.0",
        "X-Fantasy-Source": "kona",
        "X-Fantasy-Platform": "kona-PROD",
        "Referer": "https://fantasy.espn.com/baseball/players/projections",
        "Cookie": "SWID={}".format(SWID),
        "X-Fantasy-Filter": FANTASY_FILTER,
    }

def fetch(offset):
    url = API + "?view=projections&view=kona_player_info&limit=500&offset={}".format(offset)
    resp = urllib.request.urlopen(urllib.request.Request(url, headers=make_headers()), timeout=30)
    data = json.loads(resp.read())
    return data if isinstance(data, list) else data.get("players", [])

found = {}
offset = 0
prev_names = None
while len(found) < len(TARGETS):
    batch = fetch(offset)
    if not batch:
        break
    cur_names = set()
    for item in batch:
        if not item:
            continue
        name = (item.get("fullName")
                or (item.get("playerPoolEntry") or {}).get("player", {}).get("fullName") or "")
        cur_names.add(name)
        last = name.split()[-1] if name else ""
        if last in TARGETS and last not in found:
            stats_raw = (item.get("stats")
                         or (item.get("playerPoolEntry") or {}).get("player", {}).get("stats") or [])
            for s in stats_raw:
                if s.get("statSourceId") == 1 and s.get("stats"):
                    found[last] = {"name": name, "pos": item.get("defaultPositionId"),
                                   "stats": s["stats"]}
                    break
    # Detect cycling (API returning same page)
    if cur_names == prev_names:
        print("API is cycling the same page -- stopping.")
        break
    prev_names = cur_names
    offset += len(batch)
    print("Scanned {} players, found: {}  (sample names: {})".format(
        offset, list(found.keys()),
        [n for n in list(cur_names)[:3] if n]))
    if len(batch) == 0 or offset > 5000:
        break

print("\n" + "="*70)
for last, p in found.items():
    print("\n{} (posId={})".format(p["name"], p["pos"]))
    for k, v in sorted(p["stats"].items(), key=lambda x: int(x[0])):
        print("  [{:>3}] = {}".format(k, round(v, 3)))
