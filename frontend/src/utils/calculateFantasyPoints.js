/**
 * Calculate fantasy points for a player given their projected stats
 * and a scoring settings object.
 *
 * Works for football. Baseball scoring is handled by the backend.
 *
 * @param {Object} playerStats  - raw stat projections (passYards, rushTD, receptions, etc.)
 * @param {Object} scoringSettings - scoring weights (same shape as FOOTBALL_SCORING_PRESETS entries)
 * @returns {number} projected fantasy points
 */
export function calculateFantasyPoints(playerStats, scoringSettings) {
  if (!playerStats || !scoringSettings) return 0;

  const s = scoringSettings;
  const p = playerStats;

  let pts = 0;
  pts += (p.passYards   ?? 0) * (s.passYards   ?? 0);
  pts += (p.passTD      ?? 0) * (s.passTD      ?? 0);
  pts += (p.passInt     ?? 0) * (s.passInt     ?? 0);
  pts += (p.rushYards   ?? 0) * (s.rushYards   ?? 0);
  pts += (p.rushTD      ?? 0) * (s.rushTD      ?? 0);
  pts += (p.receptions  ?? 0) * (s.receptions  ?? 0);
  pts += (p.recYards    ?? 0) * (s.recYards    ?? 0);
  pts += (p.recTD       ?? 0) * (s.recTD       ?? 0);
  pts += (p.fumbleLost  ?? 0) * (s.fumbleLost  ?? 0);
  pts += (p.twoPointConv ?? 0) * (s.twoPointConv ?? 0);

  return Math.round(pts * 10) / 10;
}
