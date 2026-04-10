/**
 * Baseball adapter — normalizes backend Player objects into the universal player model.
 *
 * Backend Player fields: id, name, position, team, R, H, twoB, threeB, HR, RBI, SB, BB, K,
 *                        IP, W, L, SV, pitchingBB, pitchingK, ERA, WHIP, keeper
 */

/**
 * Assign a mock ADP based on projected fantasy score.
 * Real ADP would come from FantasyPros integration.
 */
function estimateAdp(fantasyPoints, index) {
  // Simple heuristic: higher score → lower ADP (drafted earlier)
  // Index is the rank when sorted by fantasyPoints descending
  return index + 1;
}

/**
 * Calculate a rough fantasy score for baseball (mirrors backend default weights).
 * This is used client-side only for universal model normalisation.
 * Rankings come from backend; this gives a quick local estimate.
 */
function estimateFantasyPoints(p) {
  const isP = p.IP > 0 || p.position === 'SP' || p.position === 'RP';
  if (isP) {
    return (p.IP ?? 0) * 0.5 + (p.W ?? 0) * 1.0 - (p.L ?? 0) * 1.0 +
           (p.SV ?? 0) * 1.5 + (p.pitchingK ?? 0) * 0.7 -
           (p.pitchingBB ?? 0) * 0.5 -
           (p.ERA ?? 0) * 2.0 - (p.WHIP ?? 0) * 3.0;
  }
  return (p.R ?? 0) * 1.0 + (p.H ?? 0) * 0.8 + (p.twoB ?? 0) * 0.5 +
         (p.threeB ?? 0) * 0.7 + (p.HR ?? 0) * 1.5 + (p.RBI ?? 0) * 1.0 +
         (p.SB ?? 0) * 1.2 + (p.BB ?? 0) * 0.3 - (p.K ?? 0) * 0.7;
}

/**
 * Transform a single backend player to universal model.
 * @param {Object} backendPlayer
 * @param {number} rank - overall rank (0-based index in sorted list)
 * @param {number} posRank - rank within position group
 */
export function normalizeBaseballPlayer(backendPlayer, rank = 0, posRank = 0) {
  const p = backendPlayer;
  const fantasyPoints = estimateFantasyPoints(p);

  return {
    id: p.id,
    name: p.name,
    position: p.position,
    team: p.team,
    sport: 'baseball',

    stats: {
      // batting
      R: p.R, H: p.H, twoB: p.twoB, threeB: p.threeB,
      HR: p.HR, RBI: p.RBI, SB: p.SB, BB: p.BB, K: p.K,
      // pitching
      IP: p.IP, W: p.W, L: p.L, SV: p.SV,
      pitchingBB: p.pitchingBB, pitchingK: p.pitchingK,
      ERA: p.ERA, WHIP: p.WHIP,
    },

    projections: {
      fantasyPoints,
      rawStats: p,
    },

    rankings: {
      overall: rank + 1,
      position: posRank + 1,
    },

    adp: p.adp ?? estimateAdp(fantasyPoints, rank),

    pff: p.pff ?? {},
    nextGen: p.nextGen ?? {},

    isDrafted: p.keeper === true,
    keeper: p.keeper,
  };
}

/**
 * Normalize an array of backend players, sorted by estimated fantasy points.
 * Position ranks are computed per-position.
 */
export function normalizeBaseballPlayers(backendPlayers) {
  if (!backendPlayers?.length) return [];

  const sorted = [...backendPlayers].sort(
    (a, b) => estimateFantasyPoints(b) - estimateFantasyPoints(a)
  );

  // Build per-position rank counters
  const posCounters = {};

  return sorted.map((p, i) => {
    const pos = p.position;
    posCounters[pos] = (posCounters[pos] ?? 0);
    const posRank = posCounters[pos];
    posCounters[pos]++;
    return normalizeBaseballPlayer(p, i, posRank);
  });
}
