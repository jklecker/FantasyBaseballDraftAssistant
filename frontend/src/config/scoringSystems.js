/**
 * Football scoring system definitions.
 * Baseball scoring is handled by the backend scoring-config.json presets.
 */

export const FOOTBALL_SCORING_PRESETS = {
  espn_standard: {
    key: 'espn_standard',
    name: 'ESPN Standard',
    sport: 'football',
    description: 'ESPN standard (non-PPR) scoring',
    passYards: 0.04,
    passTD: 4,
    passInt: -2,
    rushYards: 0.1,
    rushTD: 6,
    receptions: 0,
    recYards: 0.1,
    recTD: 6,
    fumbleLost: -2,
    twoPointConv: 2,
  },
  ppr: {
    key: 'ppr',
    name: 'PPR',
    sport: 'football',
    description: 'Full point per reception scoring',
    passYards: 0.04,
    passTD: 4,
    passInt: -2,
    rushYards: 0.1,
    rushTD: 6,
    receptions: 1,
    recYards: 0.1,
    recTD: 6,
    fumbleLost: -2,
    twoPointConv: 2,
  },
  half_ppr: {
    key: 'half_ppr',
    name: 'Half PPR',
    sport: 'football',
    description: 'Half point per reception scoring',
    passYards: 0.04,
    passTD: 4,
    passInt: -2,
    rushYards: 0.1,
    rushTD: 6,
    receptions: 0.5,
    recYards: 0.1,
    recTD: 6,
    fumbleLost: -2,
    twoPointConv: 2,
  },
};

export const FOOTBALL_PRESET_LIST = Object.values(FOOTBALL_SCORING_PRESETS);

/** Keys used when parsing a custom JSON upload */
export const CUSTOM_SCORING_KEYS = [
  'passYards', 'passTD', 'passInt',
  'rushYards', 'rushTD',
  'receptions', 'recYards', 'recTD',
  'fumbleLost', 'twoPointConv',
];
