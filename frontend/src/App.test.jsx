import React from 'react';
import { render, screen, fireEvent, waitFor, within, act } from '@testing-library/react';
import '@testing-library/jest-dom';
import { vi } from 'vitest';
import App from './App';

// ── mock fetch ────────────────────────────────────────────────────────────────

beforeEach(() => {
  global.fetch = vi.fn().mockResolvedValue({
    ok: false, status: 400,
    json: async () => ({}), text: async () => '',
  });
});
afterEach(() => vi.clearAllMocks());

function mockFetch(handlers) {
  global.fetch = vi.fn().mockImplementation((url) => {
    for (const [pattern, response] of Object.entries(handlers)) {
      if (url.includes(pattern)) {
        return Promise.resolve({ ok: true, status: 200,
          json: async () => response, text: async () => JSON.stringify(response) });
      }
    }
    return Promise.resolve({ ok: false, status: 400,
      json: async () => ({}), text: async () => 'Not found' });
  });
}

// ── Rendering ─────────────────────────────────────────────────────────────────

describe('App renders', () => {
  test('shows the app heading', () => {
    render(<App />);
    expect(screen.getByText(/Fantasy Draft Assistant/i)).toBeInTheDocument();
  });

  test('Draft Board tab is active by default', () => {
    render(<App />);
    expect(screen.getByRole('tab', { name: /Draft Board/i })).toHaveAttribute('aria-selected', 'true');
  });

  test('Keepers tab is labelled optional', () => {
    render(<App />);
    expect(screen.getByRole('tab', { name: /Keepers \(optional\)/i })).toBeInTheDocument();
  });

  test('Drafted tab is present', () => {
    render(<App />);
    expect(screen.getByRole('tab', { name: /Drafted/i })).toBeInTheDocument();
  });

  test('Submit Pick button starts disabled', () => {
    render(<App />);
    expect(screen.getByRole('button', { name: /Submit Pick/i })).toBeDisabled();
  });
});

// ── Tab navigation ────────────────────────────────────────────────────────────

describe('Tab navigation', () => {
  test('clicking Keepers tab shows the keeper grid', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Keepers \(optional\)/i }));
    expect(screen.getByTestId('keepers-tab')).toBeInTheDocument();
    expect(screen.getByTestId('keeper-grid')).toBeInTheDocument();
  });

  test('clicking Drafted tab shows the drafted content', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Drafted/i }));
    expect(screen.getByTestId('drafted-tab')).toBeInTheDocument();
  });

  test('clicking Draft Board tab returns to draft content', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Keepers \(optional\)/i }));
    fireEvent.click(screen.getByRole('tab', { name: /Draft Board/i }));
    expect(screen.getByTestId('draft-tab')).toBeInTheDocument();
  });

  test('switching tabs clears banners', async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Keepers \(optional\)/i }));
    fireEvent.click(screen.getByRole('button', { name: /Submit All Keepers/i }));
    await waitFor(() => expect(screen.getByTestId('error-msg')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('tab', { name: /Draft Board/i }));
    expect(screen.queryByTestId('error-msg')).not.toBeInTheDocument();
  });
});

// ── Submit pick — enabled on search results ───────────────────────────────────

describe('Draft tab — submit pick', () => {
  test('Submit Pick is disabled with no input', () => {
    render(<App />);
    expect(screen.getByRole('button', { name: /Submit Pick/i })).toBeDisabled();
  });

  test('Submit Pick becomes enabled as soon as text is typed', () => {
    render(<App />);
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'M' } });
    expect(screen.getByRole('button', { name: /Submit Pick/i })).toBeEnabled();
  });

  test('shows team name in the pick form header', async () => {
    mockFetch({
      '/draft/state':            { round: 2, currentPick: 3, teams: [{ id: 1, name: 'The Lumber Co.', roster: [] }], snakeOrder: true },
      '/draft/current-team':     { id: 1, name: 'The Lumber Co.', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
    render(<App />);
    await waitFor(() =>
      expect(screen.getByTestId('picking-for')).toHaveTextContent('The Lumber Co.')
    );
  });

  test('shows "Will pick:" hint when results exist without explicit selection', async () => {
    mockFetch({ '/draft/players': [{ id: 1, name: 'Mike Trout', position: 'OF', team: 'LAA' }] });
    render(<App />);
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'Mi' } });
    await waitFor(() => expect(screen.getByTestId('pending-pick-hint')).toHaveTextContent('Will pick: Mike Trout'));
  });

  test('selecting from dropdown shows selected-player div and hides hint', async () => {
    mockFetch({ '/draft/players': [{ id: 1, name: 'Mike Trout', position: 'OF', team: 'LAA' }] });
    render(<App />);
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'Mi' } });
    await waitFor(() => screen.getByTestId('search-dropdown'));
    fireEvent.click(within(screen.getByTestId('search-dropdown')).getByText(/Mike Trout/i));
    expect(screen.getByTestId('selected-player')).toHaveTextContent('Mike Trout');
    expect(screen.queryByTestId('pending-pick-hint')).not.toBeInTheDocument();
  });

  test('submitting uses top result when no explicit selection', async () => {
    mockFetch({
      '/draft/players': [{ id: 1, name: 'Mike Trout', position: 'OF', team: 'LAA' }],
      '/draft/pick': { pickedByTeam: 'Team 1', round: 1, nextPick: 2 },
      '/draft/state': { round: 1, currentPick: 2, teams: [], snakeOrder: true },
      '/draft/current-team': { id: 2, name: 'Team 2', roster: [], keepers: [] },
    });
    render(<App />);
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'Mi' } });
    // Wait until search results are loaded and the hint is visible
    await waitFor(() => screen.getByTestId('pending-pick-hint'));
    fireEvent.click(screen.getByRole('button', { name: /Submit Pick/i }));
    await waitFor(() =>
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/draft/pick?playerId=1'), expect.any(Object)
      )
    );
  });
});

// ── On the clock & positional needs ──────────────────────────────────────────

describe('On the clock display', () => {
  test('shows team name, round, and positional needs', async () => {
    mockFetch({
      '/draft/state':            { round: 3, currentPick: 2, teams: [{ id: 1, name: 'Team Alpha', roster: [] }], snakeOrder: true },
      '/draft/current-team':     { id: 1, name: 'Team Alpha', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': { C: 1, OF: 2 },
    });
    render(<App />);
    // 'Team Alpha' appears in both the clock-team span and the picking-for label
    await waitFor(() => expect(screen.getAllByText('Team Alpha')[0]).toBeInTheDocument());
    expect(screen.getByText(/Round 3/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/Still needs/i)).toBeInTheDocument());
  });

  test('shows Upside Mode after round 10', async () => {
    mockFetch({
      '/draft/state':            { round: 11, currentPick: 1, teams: [{ id: 1, name: 'T1', roster: [] }], snakeOrder: true },
      '/draft/current-team':     { id: 1, name: 'T1', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
    render(<App />);
    await waitFor(() => expect(screen.getByText(/Upside Mode/i)).toBeInTheDocument());
  });
});

// ── Team Tracker ──────────────────────────────────────────────────────────────

describe('Team Tracker', () => {
  test('shows all teams and highlights team on the clock', async () => {
    mockFetch({
      '/draft/state': { round: 1, currentPick: 1, snakeOrder: true,
        teams: [{ id: 1, name: 'Alpha', roster: [] }, { id: 2, name: 'Bravo', roster: [] }] },
      '/draft/current-team':     { id: 1, name: 'Alpha', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
    render(<App />);
    // 'Alpha' appears in the clock-team span, picking-for label, and team-card heading
    await waitFor(() => expect(screen.getAllByText('Alpha')[0]).toBeInTheDocument());
    expect(screen.getAllByText('Bravo').length).toBeGreaterThan(0);
    const cards = document.querySelectorAll('.team-card');
    expect(cards[0].classList).toContain('on-clock');
  });

  test('shows keeper icon for keeper players', async () => {
    mockFetch({
      '/draft/state': { round: 1, currentPick: 2, snakeOrder: true,
        teams: [{ id: 1, name: 'Alpha', roster: [{ id: 1, name: 'Trout', position: 'OF', keeper: true }] }] },
      '/draft/current-team':     { id: 2, name: 'Bravo', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
    render(<App />);
    await waitFor(() => expect(screen.getByText('🔒')).toBeInTheDocument());
  });
});

// ── Keeper grid ───────────────────────────────────────────────────────────────

describe('Keeper grid', () => {
  beforeEach(() => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Keepers \(optional\)/i }));
  });

  test('renders 12 team rows (Team 1–12)', () => {
    const grid = screen.getByTestId('keeper-grid');
    const teamRows = within(grid).getAllByText(/^Team \d+/);
    expect(teamRows.length).toBe(12);
  });

  test('shows Submit All Keepers button', () => {
    expect(screen.getByRole('button', { name: /Submit All Keepers/i })).toBeInTheDocument();
  });

  test('shows error when submitting with no keepers filled in', () => {
    fireEvent.click(screen.getByRole('button', { name: /Submit All Keepers/i }));
    expect(screen.getByTestId('error-msg')).toHaveTextContent(/at least one keeper/i);
  });

  test('player search in a slot calls /draft/players', async () => {
    mockFetch({ '/draft/players': [{ id: 3, name: 'Jose Ramirez', position: '3B', team: 'CLE' }] });
    const input = screen.getByTestId('keeper-player-0-0');
    fireEvent.change(input, { target: { value: 'Jo' } });
    await waitFor(() =>
      expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/draft/players?q=Jo'), expect.anything())
    );
  });

  test('selecting a player from keeper dropdown confirms it', async () => {
    mockFetch({ '/draft/players': [{ id: 3, name: 'Jose Ramirez', position: '3B', team: 'CLE' }] });
    const input = screen.getByTestId('keeper-player-0-0');
    fireEvent.change(input, { target: { value: 'Jo' } });
    await waitFor(() => screen.getByTestId('keeper-results-0-0'));
    fireEvent.click(within(screen.getByTestId('keeper-results-0-0')).getByText(/Jose Ramirez/i));
    expect(screen.getByText(/✓ Jose Ramirez/i)).toBeInTheDocument();
  });

  test('submitting keepers calls /draft/load-keepers', async () => {
    mockFetch({
      '/draft/players':      [{ id: 3, name: 'Jose Ramirez', position: '3B', team: 'CLE' }],
      '/draft/load-keepers': {},
      '/draft/state':        { round: 1, currentPick: 1, teams: [], snakeOrder: true },
    });
    const input = screen.getByTestId('keeper-player-0-0');
    fireEvent.change(input, { target: { value: 'Jo' } });
    await waitFor(() => screen.getByTestId('keeper-results-0-0'));
    fireEvent.click(within(screen.getByTestId('keeper-results-0-0')).getByText(/Jose Ramirez/i));
    fireEvent.change(screen.getByTestId('keeper-round-0-0'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /Submit All Keepers/i }));
    await waitFor(() =>
      expect(global.fetch).toHaveBeenCalledWith('/draft/load-keepers', expect.any(Object))
    );
  });
});

// ── Drafted tab ───────────────────────────────────────────────────────────────

describe('Drafted tab', () => {
  test('shows hint when draft not initialized', () => {
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Drafted/i }));
    expect(screen.getByText(/Draft not initialized yet/i)).toBeInTheDocument();
  });

  test('shows keeper section when keepers exist in draft state', async () => {
    mockFetch({
      '/draft/state': {
        round: 2, currentPick: 1, snakeOrder: true,
        draftedPlayers: [],
        teams: [{
          id: 1, name: 'Team A',
          keepers: [{ playerId: 99, teamId: 1, round: 2 }],
          roster: [{ id: 99, name: 'Aaron Judge', position: 'OF', keeper: true }],
        }],
      },
      '/draft/current-team':     { id: 1, name: 'Team A', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Drafted/i }));
    await waitFor(() => expect(screen.getByText('Aaron Judge')).toBeInTheDocument());
    expect(screen.getByText(/Kept In Rd/i)).toBeInTheDocument();
  });

  test('shows drafted picks in order with round numbers', async () => {
    mockFetch({
      '/draft/state': {
        round: 2, currentPick: 1, snakeOrder: true,
        draftedPlayers: [
          { id: 1, name: 'Mike Trout', position: 'OF', keeper: false },
          { id: 2, name: 'Jacob deGrom', position: 'SP', keeper: false },
        ],
        teams: [
          { id: 1, name: 'Team A', keepers: [], roster: [{ id: 1, name: 'Mike Trout', position: 'OF', keeper: false }] },
          { id: 2, name: 'Team B', keepers: [], roster: [{ id: 2, name: 'Jacob deGrom', position: 'SP', keeper: false }] },
        ],
      },
      '/draft/current-team':     { id: 1, name: 'Team A', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /Drafted/i }));
    await waitFor(() => expect(screen.getByText('Mike Trout')).toBeInTheDocument());
    expect(screen.getByText('Jacob deGrom')).toBeInTheDocument();
    expect(screen.getByText('#1')).toBeInTheDocument();
    expect(screen.getByText('#2')).toBeInTheDocument();
  });
});

// ── Projection rendering ─────────────────────────────────────────────────────

describe('Projection rendering', () => {
  test('Draft Board shows non-zero projected stats when API uses lowercase keys', async () => {
    const batter = {
      id: 101, name: 'Lower Batter', position: 'OF', team: 'LAA',
      r: 90, h: 160, twoB: 30, threeB: 4, hr: 35, rbi: 100, sb: 20, bb: 70, k: 120,
      ip: 0, w: 0, l: 0, sv: 0, pBB: 0, pK: 0, era: 0, whip: 0,
    };
    const pitcher = {
      id: 102, name: 'Lower Pitcher', position: 'SP', team: 'NYM',
      r: 0, h: 0, twoB: 0, threeB: 0, hr: 0, rbi: 0, sb: 0, bb: 0, k: 0,
      ip: 180, w: 14, l: 7, sv: 0, pBB: 50, pK: 220, era: 3.2, whip: 1.08,
    };

    mockFetch({
      '/draft/state': {
        round: 4, currentPick: 3, snakeOrder: true,
        teams: [{ id: 1, name: 'My Team', roster: [] }],
      },
      '/draft/current-team': { id: 1, name: 'My Team', roster: [], keepers: [] },
      '/draft/recommendations/board': { overall: [batter, pitcher], pitchers: [pitcher], batters: [batter] },
      '/draft/recommendations': [batter, pitcher],
      '/draft/positional-needs': {},
    });

    render(<App />);

    await waitFor(() => expect(screen.getByText('Lower Batter')).toBeInTheDocument());
    expect(screen.getByText(/R 90 \| H 160 \| 2B 30 \| 3B 4 \| HR 35 \| RBI 100 \| SB 20 \| BB 70 \| K 120/)).toBeInTheDocument();
    expect(screen.getByText(/IP 180\.0 \| W 14 \| SV 0 \| K 220 \| ERA 3\.20 \| WHIP 1\.08/)).toBeInTheDocument();
  });

  test('My Picks tables render batter and pitcher projected stats correctly', async () => {
    const batter = {
      id: 201, name: 'Board Batter', position: 'OF', team: 'SEA',
      r: 88, h: 155, twoB: 28, threeB: 3, hr: 32, rbi: 95, sb: 18, bb: 65, k: 115,
      ip: 0, w: 0, l: 0, sv: 0, pBB: 0, pK: 0, era: 0, whip: 0,
    };
    const pitcher = {
      id: 202, name: 'Board Pitcher', position: 'SP', team: 'ATL',
      r: 0, h: 0, twoB: 0, threeB: 0, hr: 0, rbi: 0, sb: 0, bb: 0, k: 0,
      ip: 172.2, w: 13, l: 8, sv: 0, pBB: 48, pK: 205, era: 3.45, whip: 1.12,
    };

    mockFetch({
      '/draft/state': {
        round: 6, currentPick: 2, snakeOrder: true,
        teams: [{ id: 1, name: 'My Team', roster: [] }],
      },
      '/draft/current-team': { id: 1, name: 'My Team', roster: [], keepers: [] },
      '/draft/recommendations/board': { overall: [batter, pitcher], pitchers: [pitcher], batters: [batter] },
      '/draft/recommendations': [batter, pitcher],
      '/draft/positional-needs': {},
    });

    render(<App />);
    fireEvent.click(screen.getByRole('tab', { name: /My Picks/i }));

    await waitFor(() => expect(screen.getAllByText('Board Batter').length).toBeGreaterThan(0));
    await waitFor(() => expect(screen.getAllByText('Board Pitcher').length).toBeGreaterThan(0));

    const batterRow = screen.getAllByText('Board Batter')[1].closest('tr');
    const pitcherRow = screen.getAllByText('Board Pitcher')[1].closest('tr');

    expect(within(batterRow).getByText('88')).toBeInTheDocument();
    expect(within(batterRow).getByText('155')).toBeInTheDocument();
    expect(within(batterRow).getByText('28')).toBeInTheDocument();
    expect(within(batterRow).getByText('3')).toBeInTheDocument();
    expect(within(batterRow).getByText('32')).toBeInTheDocument();
    expect(within(batterRow).getByText('95')).toBeInTheDocument();
    expect(within(batterRow).getByText('18')).toBeInTheDocument();
    expect(within(batterRow).getByText('65')).toBeInTheDocument();
    expect(within(batterRow).getByText('115')).toBeInTheDocument();

    expect(within(pitcherRow).getByText('172.2')).toBeInTheDocument();
    expect(within(pitcherRow).getByText('13')).toBeInTheDocument();
    expect(within(pitcherRow).getByText('8')).toBeInTheDocument();
    expect(within(pitcherRow).getByText('48')).toBeInTheDocument();
    expect(within(pitcherRow).getByText('205')).toBeInTheDocument();
  });
});

// ── Keep-alive bar ────────────────────────────────────────────────────────────

describe('Keep-alive bar', () => {
  test('always rendered', () => {
    render(<App />);
    expect(screen.getByTestId('keepalive-bar')).toBeInTheDocument();
  });

  test('manual ping calls /ping and shows last contact', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({}), text: async () => 'pong' });
    render(<App />);
    fireEvent.click(screen.getByTitle(/Ping the server now/i));
    await waitFor(() => expect(screen.getByText(/Last contact/i)).toBeInTheDocument());
  });
});

// ── Fuzzy / client-side search ────────────────────────────────────────────────

describe('Fuzzy / client-side search', () => {
  const players = [
    { id: 1, name: 'Mike Trout',   position: 'OF', team: 'LAA' },
    { id: 2, name: 'Aaron Judge',  position: 'OF', team: 'NYY' },
    { id: 3, name: 'Jacob deGrom', position: 'SP', team: 'TEX' },
  ];

  function mockWithPlayers() {
    mockFetch({
      '/draft/state': {
        round: 1, currentPick: 1, snakeOrder: true,
        teams: [{ id: 1, name: 'T1', roster: [] }],
        availablePlayers: players,
      },
      '/draft/current-team':     { id: 1, name: 'T1', roster: [], keepers: [] },
      '/draft/recommendations':  [],
      '/draft/positional-needs': {},
    });
  }

  test('shows results from local Fuse index without calling /draft/players', async () => {
    mockWithPlayers();
    render(<App />);
    // Wait for state + team to appear, then flush all pending effects
    // (including the useEffect that builds the Fuse index from availablePlayers)
    await waitFor(() => expect(screen.getAllByText('T1')[0]).toBeInTheDocument());
    await act(async () => {});

    const callsBefore = global.fetch.mock.calls.filter(c => c[0].includes('/draft/players')).length;
    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'trout' } });
    await waitFor(() => expect(screen.getByTestId('search-dropdown')).toBeInTheDocument());

    // Mike Trout should appear in the dropdown (use within to avoid duplicate-text error)
    expect(within(screen.getByTestId('search-dropdown')).getByText(/Mike Trout/i)).toBeInTheDocument();
    // No extra /draft/players API call was made
    const callsAfter = global.fetch.mock.calls.filter(c => c[0].includes('/draft/players')).length;
    expect(callsAfter).toBe(callsBefore);
  });

  test('fuzzy: partial last-name prefix finds the player', async () => {
    mockWithPlayers();
    render(<App />);
    await waitFor(() => expect(screen.getAllByText('T1')[0]).toBeInTheDocument());
    await act(async () => {});

    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'tro' } });
    await waitFor(() => expect(screen.getByTestId('search-dropdown')).toBeInTheDocument());
    expect(within(screen.getByTestId('search-dropdown')).getByText(/Mike Trout/i)).toBeInTheDocument();
  });

  test('fuzzy: typing a different player returns different results', async () => {
    mockWithPlayers();
    render(<App />);
    await waitFor(() => expect(screen.getAllByText('T1')[0]).toBeInTheDocument());
    await act(async () => {});

    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'judge' } });
    await waitFor(() => expect(screen.getByTestId('search-dropdown')).toBeInTheDocument());
    expect(within(screen.getByTestId('search-dropdown')).getByText(/Aaron Judge/i)).toBeInTheDocument();
  });

  test('fuzzy: clearing search removes dropdown', async () => {
    mockWithPlayers();
    render(<App />);
    await waitFor(() => expect(screen.getAllByText('T1')[0]).toBeInTheDocument());
    await act(async () => {});

    const input = screen.getByPlaceholderText(/e\.g\. Mike Trout/i);
    fireEvent.change(input, { target: { value: 'trout' } });
    await waitFor(() => screen.getByTestId('search-dropdown'));
    fireEvent.change(input, { target: { value: '' } });
    expect(screen.queryByTestId('search-dropdown')).not.toBeInTheDocument();
  });

  test('fuzzy: no results for gibberish query — no dropdown shown', async () => {
    mockWithPlayers();
    render(<App />);
    await waitFor(() => expect(screen.getAllByText('T1')[0]).toBeInTheDocument());
    await act(async () => {});

    fireEvent.change(screen.getByPlaceholderText(/e\.g\. Mike Trout/i), { target: { value: 'zzzzzz' } });
    // Give enough time for any search to run
    await new Promise(r => setTimeout(r, 100));
    expect(screen.queryByTestId('search-dropdown')).not.toBeInTheDocument();
  });
});

