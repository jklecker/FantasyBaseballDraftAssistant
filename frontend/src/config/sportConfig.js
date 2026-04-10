export const SPORTS = {
  baseball: {
    id: 'baseball',
    label: 'Baseball',
    emoji: '⚾',
    positions: ['C', '1B', '2B', '3B', 'SS', 'OF', 'SP', 'RP'],
    positionGroups: {
      hitters: ['C', '1B', '2B', '3B', 'SS', 'OF'],
      pitchers: ['SP', 'RP'],
    },
    rosterRequirements: { C: 1, '1B': 1, '2B': 1, '3B': 1, SS: 1, OF: 3, SP: 2, RP: 1 },
    defaultScoringPreset: 'h2h_categories',
    scoringSource: 'backend', // scoring handled by backend presets
  },
  football: {
    id: 'football',
    label: 'Football',
    emoji: '🏈',
    positions: ['QB', 'RB', 'WR', 'TE', 'K', 'DST'],
    positionGroups: {
      skill: ['QB', 'RB', 'WR', 'TE'],
      specialist: ['K', 'DST'],
    },
    // ESPN standard PPR: 1 QB, 2 RB, 2 WR, 1 TE, 1 FLEX (RB/WR/TE), 1 K, 1 DST + 7 bench
    rosterRequirements: { QB: 1, RB: 2, WR: 2, TE: 1, FLEX: 1, K: 1, DST: 1 },
    flexPositions: ['RB', 'WR', 'TE'],
    defaultScoringPreset: 'ppr',
    scoringSource: 'frontend',
    // VBD replacement level: the N-th best player at each position is the baseline.
    // In a 12-team league: teams draft ~2 QBs, ~5 RBs, ~5 WRs, ~2 TEs, 1 K, 1 DST.
    // Replacement = last player you'd expect to be starting across all teams.
    replacementLevel: { QB: 14, RB: 37, WR: 45, TE: 14, K: 13, DST: 13 },
  },
};

export function getSportConfig(sport) {
  return SPORTS[sport] ?? SPORTS.baseball;
}

export function getPositionGroups(sport) {
  return getSportConfig(sport).positionGroups;
}

export function getRosterRequirements(sport) {
  return getSportConfig(sport).rosterRequirements;
}

export function isFootball(sport) {
  return sport === 'football';
}

export function isBaseball(sport) {
  return sport === 'baseball';
}
