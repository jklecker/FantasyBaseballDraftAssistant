import React, { useState, useEffect, useCallback, useRef } from 'react';
import Fuse from 'fuse.js';

// ─── helpers ──────────────────────────────────────────────────────────────────

const API_BASE = process.env.REACT_APP_API_BASE || '';

async function apiFetch(url, opts = {}) {
  const res = await fetch(API_BASE + url, opts);
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(text || `HTTP ${res.status}`);
  }
  // 204 No Content has no body
  if (res.status === 204) return null;
  return res.json();
}

// ─── PlayerSearch ─────────────────────────────────────────────────────────────

function PlayerSearch({ label, value, onChange, onSelect, results }) {
  return (
    <div className="player-search" data-testid="player-search">
      {label && <label>{label}</label>}
      <input
        type="text"
        placeholder="e.g. Mike Trout"
        value={value}
        onChange={e => onChange(e.target.value)}
      />
      {results.length > 0 && (
        <ul className="search-dropdown" data-testid="search-dropdown">
          {results.map(p => (
            <li key={p.id} onClick={() => onSelect(p)}>
              {p.name} <span className="badge">{p.position}</span> {p.team}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ─── initialKeeperGrid ────────────────────────────────────────────────────────

function makeKeeperGrid() {
  return Array.from({ length: 12 }, (_, i) => ({
    name: i < 11 ? `Team ${i + 1}` : 'Your Team',
    keepers: [
      { search: '', results: [], player: null, round: '' },
      { search: '', results: [], player: null, round: '' },
    ],
  }));
}

// ─── buildDraftBoard ──────────────────────────────────────────────────────────
// Returns { keepers: [...], picks: [...] } for the Drafted tab.

function buildDraftBoard(draftState) {
  if (!draftState?.teams) return { keepers: [], picks: [] };

  const numTeams = draftState.teams.length || 1;

  // player id → team name (non-keepers)
  const playerTeamMap = {};
  draftState.teams.forEach(team =>
    team.roster?.forEach(p => { if (!p.keeper) playerTeamMap[p.id] = team.name; })
  );

  // keepers: pull from each team's roster where keeper===true, attach round from team.keepers
  const keepers = [];
  draftState.teams.forEach(team => {
    (team.roster || [])
      .filter(p => p.keeper)
      .forEach(p => {
        const kd = (team.keepers || []).find(k => k.playerId === p.id);
        keepers.push({ player: p, teamName: team.name, round: kd?.round ?? '—' });
      });
  });
  keepers.sort((a, b) => (a.round ?? 99) - (b.round ?? 99));

  // regular picks in draft order with round number
  const picks = (draftState.draftedPlayers || []).map((p, i) => ({
    player: p,
    teamName: playerTeamMap[p.id] || '?',
    overall: i + 1,
    round: Math.floor(i / numTeams) + 1,
  }));

  return { keepers, picks };
}

// ─── Main App ─────────────────────────────────────────────────────────────────

export default function App() {
  const [activeTab, setActiveTab]   = useState('draft');
  const [draftState, setDraftState] = useState(null);
  const [currentTeam, setCurrentTeam] = useState(null);
  const [recommendations, setRecs]  = useState([]);
  const [positionalNeeds, setNeeds] = useState({});
  const [statusMsg, setStatusMsg]   = useState('');
  const [errorMsg, setErrorMsg]     = useState('');

  // Keep-alive
  const [lastPing, setLastPing] = useState(null);
  const [pinging, setPinging]   = useState(false);

  // Draft pick form
  const [pickSearch, setPickSearch]   = useState('');
  const [pickResults, setPickResults] = useState([]);
  const [selectedPick, setSelectedPick] = useState(null);

  // Keeper grid — 12 teams × 2 slots each
  const [keeperGrid, setKeeperGrid] = useState(makeKeeperGrid);

  // Fuse.js index — rebuilt whenever the available player pool changes
  const fuseRef = useRef(null);

  // ── data fetchers ────────────────────────────────────────────────────────

  // Rebuild the client-side Fuse.js search index from a fresh state snapshot.
  const buildFuseIndex = (state) => {
    const players = state?.availablePlayers;
    if (players && players.length > 0) {
      fuseRef.current = new Fuse(players, {
        keys: ['name'],
        threshold: 0.45,
        distance: 200,
        minMatchCharLength: 2,
        includeScore: true,
      });
    } else {
      fuseRef.current = null;
    }
  };

  const loadState = useCallback(async () => {
    try {
      const state = await apiFetch('/draft/state');
      setDraftState(state);
      buildFuseIndex(state);
    } catch (_) {
      // Draft not initialized on the backend (e.g. after a Render restart).
      // Auto-initialize with 12 default teams so the app is immediately usable.
      try {
        const state = await apiFetch('/draft/auto-initialize', { method: 'POST' });
        setDraftState(state);
        buildFuseIndex(state);
        setStatusMsg('✅ Draft auto-initialized with 12 teams (Team 1–11 + My Team). Keepers are optional.');
      } catch (_2) { /* ignore */ }
    }
  }, []);

  const loadCurrentTeam = useCallback(async () => {
    try { setCurrentTeam(await apiFetch('/draft/current-team')); } catch (_) {}
  }, []);

  const loadRecommendations = useCallback(async (teamId, round) => {
    try { setRecs(await apiFetch(`/draft/recommendations?teamId=${teamId}&round=${round}`)); }
    catch (_) {}
  }, []);

  const loadPositionalNeeds = useCallback(async (teamId) => {
    try { setNeeds((await apiFetch(`/draft/positional-needs?teamId=${teamId}`)) || {}); }
    catch (_) { setNeeds({}); }
  }, []);

  // Load top-5 recommendations for the user's own team.
  const loadMyRecs = useCallback(async (teamId, round) => {
    if (!teamId || !round) return;
    setRecsLoading(true);
    try {
      const data = await apiFetch(`/draft/recommendations?teamId=${teamId}&round=${round}`);
      setMyRecs(data || []);
    } catch (_) {
      setMyRecs([]);
    }
    setRecsLoading(false);
  }, []);

  useEffect(() => { loadState(); loadCurrentTeam(); }, [loadState, loadCurrentTeam]);

  useEffect(() => {
    if (currentTeam && draftState) {
      loadRecommendations(currentTeam.id, draftState.round);
      loadPositionalNeeds(currentTeam.id);
    }
  }, [currentTeam, draftState, loadRecommendations, loadPositionalNeeds]);

  // Auto-detect which team is "My Team" (name === 'My Team' or the last team).
  useEffect(() => {
    if (draftState?.teams && !myTeamId) {
      const found = draftState.teams.find(t => t.name === 'My Team')
        ?? draftState.teams[draftState.teams.length - 1];
      if (found) setMyTeamId(found.id);
    }
  }, [draftState, myTeamId]);

  // Reload recommendations for My Team whenever the recs tab is visible
  // or whenever the draft state changes (i.e. a pick was made).
  useEffect(() => {
    if (activeTab === 'recs' && myTeamId && draftState?.round) {
      loadMyRecs(myTeamId, draftState.round);
    }
  }, [activeTab, myTeamId, draftState, loadMyRecs]);


  // ── keep-alive ───────────────────────────────────────────────────────────

  const ping = useCallback(async () => {
    setPinging(true);
    try { await fetch('/ping'); setLastPing(new Date()); } catch (_) {}
    setPinging(false);
  }, []);

  useEffect(() => {
    const id = setInterval(ping, 13 * 60 * 1000);
    return () => clearInterval(id);
  }, [ping]);

  // ── player search ────────────────────────────────────────────────────────
  // Uses the local Fuse.js index (instant + fuzzy) when a draft is in progress;
  // falls back to the backend API when no local index is available.

  const searchPlayers = async (q, setResults) => {
    if (q.length < 2) { setResults([]); return; }
    if (fuseRef.current) {
      // Client-side fuzzy search — no network round-trip
      const hits = fuseRef.current.search(q).slice(0, 8).map(r => r.item);
      setResults(hits);
      return;
    }
    // Fallback: backend fuzzy search (also handles misspellings via FuzzyMatcher)
    try {
      const data = await apiFetch(`/draft/players?q=${encodeURIComponent(q)}`);
      setResults((data || []).slice(0, 8));
    } catch (_) { setResults([]); }
  };

  // ── draft pick ───────────────────────────────────────────────────────────
  // Use explicitly selected player, or fall back to the top search result.

  const handleDraftPick = async () => {
    const playerToPick = selectedPick || pickResults[0];
    setErrorMsg('');
    try {
      let data;
      if (playerToPick?.id) {
        // Preferred: use the integer player ID (exact match, fast)
        data = await apiFetch(`/draft/pick?playerId=${playerToPick.id}`, { method: 'POST' });
      } else if (pickSearch.trim()) {
        // Fallback: let the backend fuzzy-match by name
        data = await apiFetch(
          `/draft/pick?playerName=${encodeURIComponent(pickSearch.trim())}`,
          { method: 'POST' }
        );
      } else {
        setErrorMsg('Type a player name first.');
        return;
      }
      setStatusMsg(`✅ ${playerToPick?.name ?? pickSearch} → ${data.pickedByTeam}  (Rd ${data.round})`);
      setSelectedPick(null);
      setPickSearch('');
      setPickResults([]);
      await loadState();
      await loadCurrentTeam();
    } catch (e) {
      setErrorMsg(`Pick failed: ${e.message}`);
    }
  };

  // Pick a player directly from the recommendations tab.
  const handlePickPlayer = async (player) => {
    setErrorMsg('');
    try {
      const data = await apiFetch(`/draft/pick?playerId=${player.id}`, { method: 'POST' });
      setStatusMsg(`✅ ${player.name} → ${data.pickedByTeam}  (Rd ${data.round})`);
      await loadState();
      await loadCurrentTeam();
    } catch (e) {
      setErrorMsg(`Pick failed: ${e.message}`);
    }
  };

  // ── keeper grid ──────────────────────────────────────────────────────────

  const updateKeeperSlot = (ti, ki, patch) =>
    setKeeperGrid(prev =>
      prev.map((team, idx) =>
        idx !== ti ? team : {
          ...team,
          keepers: team.keepers.map((k, i) => i !== ki ? k : { ...k, ...patch }),
        }
      )
    );

  const searchKeeperPlayer = async (ti, ki, q) => {
    updateKeeperSlot(ti, ki, { search: q, results: [], player: q ? null : undefined });
    if (q.length < 2) return;
    if (fuseRef.current) {
      const hits = fuseRef.current.search(q).slice(0, 5).map(r => r.item);
      updateKeeperSlot(ti, ki, { results: hits });
      return;
    }
    try {
      const data = await apiFetch(`/draft/players?q=${encodeURIComponent(q)}`);
      updateKeeperSlot(ti, ki, { results: (data || []).slice(0, 5) });
    } catch (_) {}
  };

  const submitKeeperGrid = async () => {
    const keepers = keeperGrid.flatMap(team =>
      team.keepers
        .filter(k => k.player && k.round)
        .map(k => ({ teamName: team.name, playerId: k.player.id, round: parseInt(k.round, 10) }))
    );
    if (!keepers.length) { setErrorMsg('Enter at least one keeper before submitting.'); return; }
    setErrorMsg('');
    try {
      await apiFetch('/draft/load-keepers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ keepers }),
      });
      setStatusMsg(`✅ ${keepers.length} keeper(s) loaded!`);
      setKeeperGrid(makeKeeperGrid());
      await loadState();
    } catch (e) {
      setErrorMsg(`Failed to load keepers: ${e.message}`);
    }
  };

  // ── derived ──────────────────────────────────────────────────────────────

  const isLateRound   = draftState && draftState.round > 10;
  const canSubmitPick = selectedPick !== null || pickSearch.trim().length > 0;
  const pendingPlayerLabel = !selectedPick && pickResults[0]
    ? `Will pick: ${pickResults[0].name}`
    : null;
  const { keepers: draftedKeepers, picks: draftedPicks } = buildDraftBoard(draftState);

  // ─────────────────────────────────────────────────────────────────────────

  return (
    <div className="app">
      <header className="app-header">
        <h1>⚾ Fantasy Baseball Draft Assistant</h1>
      </header>

      <nav className="tabs" role="tablist">
        {[
          { id: 'draft',   label: '📋 Draft Board' },
          { id: 'recs',    label: '🎯 My Picks' },
          { id: 'keepers', label: '🔒 Keepers (optional)' },
          { id: 'drafted', label: '📜 Drafted' },
        ].map(({ id, label }) => (
          <button
            key={id}
            role="tab"
            aria-selected={activeTab === id}
            className={`tab${activeTab === id ? ' active' : ''}`}
            onClick={() => { setActiveTab(id); setErrorMsg(''); setStatusMsg(''); }}
          >
            {label}
          </button>
        ))}
      </nav>

      {statusMsg && <div className="banner success" data-testid="status-msg">{statusMsg}</div>}
      {errorMsg  && <div className="banner error"   data-testid="error-msg">{errorMsg}</div>}

      {/* Keep-alive bar */}
      <div className="keepalive-bar" data-testid="keepalive-bar">
        <span className={`ping-dot${pinging ? ' pinging' : ''}`} title="Connection status">●</span>
        <span className="ping-label">
          {lastPing ? `Last contact ${lastPing.toLocaleTimeString()}` : 'Auto-ping every 13 min to stay alive'}
        </span>
        <button className="btn-ping" onClick={ping} disabled={pinging} title="Ping the server now">
          {pinging ? '…' : '🔄'}
        </button>
      </div>

      {/* ── DRAFT BOARD TAB ─────────────────────────────────────────────── */}
      {activeTab === 'draft' && (
        <div className="tab-content" data-testid="draft-tab">

          {currentTeam && draftState ? (
            <div className="on-the-clock">
              <div className="clock-main">
                <span className="clock-label">🕐 On the Clock</span>
                <span className="clock-team">{currentTeam.name}</span>
                <span className="clock-meta">
                  Round {draftState.round} · Pick {draftState.currentPick}
                  {isLateRound && <span className="upside-badge">🚀 Upside Mode</span>}
                </span>
              </div>
              {Object.keys(positionalNeeds).length > 0 && (
                <div className="needs-row">
                  <span className="needs-label">Still needs:</span>
                  {Object.entries(positionalNeeds).map(([pos, count]) => (
                    <span key={pos} className="needs-badge" title={`Need ${count} more ${pos}`}>
                      {pos}{count > 1 ? ` ×${count}` : ''}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <p className="hint">Draft not initialized — POST /draft/initialize to start.</p>
          )}

          {/* Pick form */}
          <section className="card">
            <h3>
              Draft a Player
              {currentTeam && (
                <span className="picking-for" data-testid="picking-for">
                  — picking for <strong>{currentTeam.name}</strong>
                </span>
              )}
            </h3>
            <PlayerSearch
              label="Player"
              value={pickSearch}
              onChange={q => { setPickSearch(q); setSelectedPick(null); searchPlayers(q, setPickResults); }}
              onSelect={p => { setSelectedPick(p); setPickSearch(p.name); setPickResults([]); }}
              results={pickResults}
            />
            {selectedPick && (
              <div className="selected-player" data-testid="selected-player">
                Selected: <strong>{selectedPick.name}</strong>
                <span className="badge">{selectedPick.position}</span> {selectedPick.team}
              </div>
            )}
            {pendingPlayerLabel && (
              <div className="pending-pick-hint" data-testid="pending-pick-hint">
                {pendingPlayerLabel}
              </div>
            )}
            <button className="btn-primary" onClick={handleDraftPick} disabled={!canSubmitPick}>
              Submit Pick
            </button>
          </section>

          {/* Recommendations */}
          {recommendations.length > 0 && (
            <section className="card">
              <h3>Top Picks for {currentTeam?.name}{isLateRound && ' — Upside Weighted 🚀'}</h3>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Player</th><th>Pos</th><th>Team</th>
                    <th>HR</th><th>SB</th><th>R</th><th>RBI</th>
                    <th>W</th><th>SV</th><th>ERA</th>
                  </tr>
                </thead>
                <tbody>
                  {recommendations.map(p => (
                    <tr key={p.id} className="clickable"
                      onClick={() => { setSelectedPick(p); setPickSearch(p.name); setPickResults([]); }}
                      title="Click to select">
                      <td>{p.name}</td>
                      <td><span className="badge">{p.position}</span></td>
                      <td>{p.team}</td>
                      <td>{p.HR}</td><td>{p.SB}</td><td>{p.R}</td><td>{p.RBI}</td>
                      <td>{p.W}</td><td>{p.SV}</td>
                      <td>{p.IP > 0 ? p.ERA.toFixed(2) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          {/* Team Tracker */}
          {draftState?.teams?.length > 0 && (
            <section className="card">
              <h3>Team Rosters</h3>
              <div className="team-grid">
                {draftState.teams.map(team => (
                  <div key={team.id}
                    className={`team-card${currentTeam?.id === team.id ? ' on-clock' : ''}`}>
                    <h4>
                      {currentTeam?.id === team.id && '🕐 '}{team.name}
                      <span className="pick-count">({team.roster.length} picks)</span>
                    </h4>
                    {team.roster.length === 0
                      ? <p className="empty-roster">—</p>
                      : <ol>{team.roster.map(p => (
                          <li key={p.id}>
                            <span className="badge">{p.position}</span> {p.name}
                            {p.keeper && <span className="keeper-tag">🔒</span>}
                          </li>
                        ))}</ol>
                    }
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {/* ── MY PICKS / RECOMMENDATIONS TAB ──────────────────────────────── */}
      {activeTab === 'recs' && (
        <div className="tab-content" data-testid="recs-tab">
          {!draftState ? (
            <p className="hint">Draft not initialized yet.</p>
          ) : (
            <section className="card">
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 12 }}>
                <h3 style={{ margin: 0 }}>🎯 Top Picks for</h3>
                <select
                  value={myTeamId || ''}
                  onChange={e => setMyTeamId(Number(e.target.value))}
                  style={{ fontSize: '1em', padding: '4px 8px', borderRadius: 6 }}
                >
                  {draftState.teams.map(t => (
                    <option key={t.id} value={t.id}>{t.name}</option>
                  ))}
                </select>
                <span style={{ color: '#888', fontSize: '0.85em' }}>Round {draftState.round}</span>
                <button
                  className="btn-ping"
                  onClick={() => myTeamId && loadMyRecs(myTeamId, draftState.round)}
                  disabled={recsLoading}
                  title="Refresh recommendations"
                  style={{ marginLeft: 4 }}
                >
                  {recsLoading ? '…' : '🔄 Refresh'}
                </button>
              </div>

              {/* On-the-clock banner */}
              {currentTeam && (
                <div style={{ marginBottom: 14 }}>
                  {currentTeam.id === myTeamId
                    ? <div className="banner success" style={{ margin: 0 }}>
                        ✅ It's YOUR turn! Round {draftState.round} · Pick {draftState.currentPick} — select a player below and click <strong>Draft</strong>.
                      </div>
                    : <div className="banner" style={{ margin: 0, background: '#f0f4ff', color: '#555', border: '1px solid #c5d3f5' }}>
                        ⏳ <strong>{currentTeam.name}</strong> is on the clock (Rd {draftState.round} · Pick {draftState.currentPick}). Plan your next pick below.
                      </div>
                  }
                </div>
              )}

              {myRecs.length === 0 ? (
                <p className="hint">
                  {recsLoading
                    ? 'Loading recommendations…'
                    : 'No recommendations yet — select your team above and click 🔄 Refresh.'}
                </p>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Player</th><th>Pos</th><th>MLB</th>
                      <th>HR</th><th>SB</th><th>R</th><th>RBI</th>
                      <th>W</th><th>SV</th><th>ERA</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {myRecs.map((p, i) => (
                      <tr key={p.id}>
                        <td className="pick-num">#{i + 1}</td>
                        <td><strong>{p.name}</strong></td>
                        <td><span className="badge">{p.position}</span></td>
                        <td>{p.team}</td>
                        <td>{p.HR > 0 ? p.HR : '—'}</td>
                        <td>{p.SB > 0 ? p.SB : '—'}</td>
                        <td>{p.R  > 0 ? p.R  : '—'}</td>
                        <td>{p.RBI > 0 ? p.RBI : '—'}</td>
                        <td>{p.W  > 0 ? p.W  : '—'}</td>
                        <td>{p.SV > 0 ? p.SV : '—'}</td>
                        <td>{p.IP > 0 ? (p.ERA || 0).toFixed(2) : '—'}</td>
                        <td>
                          <button
                            className="btn-primary"
                            style={{ padding: '4px 12px', fontSize: '0.85em' }}
                            onClick={() => handlePickPlayer(p)}
                            title={`Draft ${p.name}`}
                          >
                            Draft
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          )}
        </div>
      )}

      {/* ── KEEPERS TAB ─────────────────────────────────────────────────── */}
      {activeTab === 'keepers' && (
        <div className="tab-content" data-testid="keepers-tab">
          <section className="card">
            <h3>Keepers <span className="optional-tag">optional</span></h3>
            <p className="hint">
              Skip this tab entirely if your league doesn't use keepers.<br />
              Fill in the player name and the round their slot occupies for each team, then
              click <strong>Submit All Keepers</strong>. Empty slots are ignored.
            </p>

            <div className="keeper-grid-wrap" data-testid="keeper-grid">
              <table className="keeper-table">
                <thead>
                  <tr>
                    <th>Team</th>
                    <th>Keeper 1</th><th>Rd</th>
                    <th>Keeper 2</th><th>Rd</th>
                  </tr>
                </thead>
                <tbody>
                  {keeperGrid.map((team, ti) => (
                    <tr key={ti} className={team.name === 'Your Team' ? 'your-team-row' : ''}>
                      <td className="keeper-team-name">{team.name}</td>
                      {team.keepers.map((k, ki) => (
                        <React.Fragment key={ki}>
                          <td className="keeper-player-cell">
                            <div className="keeper-search-wrap">
                              <input
                                type="text"
                                className="keeper-player-input"
                                placeholder="e.g. Mike Trout"
                                value={k.search}
                                data-testid={`keeper-player-${ti}-${ki}`}
                                onChange={e => searchKeeperPlayer(ti, ki, e.target.value)}
                              />
                              {k.player && (
                                <span className="keeper-selected-name">
                                  ✓ {k.player.name}
                                  <button
                                    className="keeper-clear"
                                    onClick={() => updateKeeperSlot(ti, ki, { search: '', player: null, results: [] })}
                                  >✕</button>
                                </span>
                              )}
                              {k.results.length > 0 && (
                                <ul className="keeper-dropdown" data-testid={`keeper-results-${ti}-${ki}`}>
                                  {k.results.map(p => (
                                    <li key={p.id}
                                      onClick={() => updateKeeperSlot(ti, ki, {
                                        player: p, search: '', results: [],
                                      })}>
                                      {p.name} <span className="badge">{p.position}</span>
                                    </li>
                                  ))}
                                </ul>
                              )}
                            </div>
                          </td>
                          <td>
                            <input
                              type="number"
                              className="keeper-round-input"
                              placeholder="Rd"
                              min="1"
                              value={k.round}
                              data-testid={`keeper-round-${ti}-${ki}`}
                              onChange={e => updateKeeperSlot(ti, ki, { round: e.target.value })}
                            />
                          </td>
                        </React.Fragment>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <button className="btn-primary" style={{ marginTop: '16px' }} onClick={submitKeeperGrid}>
              🔒 Submit All Keepers to Draft
            </button>
          </section>
        </div>
      )}

      {/* ── DRAFTED TAB ─────────────────────────────────────────────────── */}
      {activeTab === 'drafted' && (
        <div className="tab-content" data-testid="drafted-tab">
          {!draftState ? (
            <p className="hint">Draft not initialized yet.</p>
          ) : (
            <>
              {/* Keepers section */}
              {draftedKeepers.length > 0 && (
                <section className="card">
                  <h3>🔒 Keepers</h3>
                  <table className="data-table">
                    <thead>
                      <tr><th>Team</th><th>Player</th><th>Pos</th><th>Kept In Rd</th></tr>
                    </thead>
                    <tbody>
                      {draftedKeepers.map((k, i) => (
                        <tr key={i}>
                          <td>{k.teamName}</td>
                          <td><strong>{k.player.name}</strong></td>
                          <td><span className="badge">{k.player.position}</span></td>
                          <td>{k.round}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              )}

              {/* Draft picks section */}
              {draftedPicks.length > 0 ? (
                <section className="card">
                  <h3>Draft Picks</h3>
                  <table className="data-table">
                    <thead>
                      <tr><th>#</th><th>Rd</th><th>Team</th><th>Player</th><th>Pos</th></tr>
                    </thead>
                    <tbody>
                      {draftedPicks.map((pick, i) => (
                        <tr key={i}
                          className={i > 0 && pick.round !== draftedPicks[i - 1].round ? 'round-divider' : ''}>
                          <td className="pick-num">#{pick.overall}</td>
                          <td>Rd {pick.round}</td>
                          <td>{pick.teamName}</td>
                          <td><strong>{pick.player.name}</strong></td>
                          <td><span className="badge">{pick.player.position}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              ) : (
                draftedKeepers.length === 0 && <p className="hint">No picks yet.</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
