# Team Selection Update

## Summary
Updated the Fantasy Baseball Draft Assistant to allow users to select any team (1-12) as "their team" instead of having Team 12 hardcoded as "Your Team". The keeper grid now dynamically updates to show the selected team with "(Your Team)" label.

## Changes Made

### 1. **Updated `makeKeeperGrid()` Function** (App.jsx, lines 43-60)
   - Changed from hardcoded "Team 1-11" + "Your Team" to flexible "Team 1-12" with dynamic labeling
   - Now accepts `myTeamId` parameter to determine which team gets the "(Your Team)" label
   - Added `teamId` and `isMyTeam` properties to each team object for easier row styling

**Before:**
```javascript
function makeKeeperGrid() {
  return Array.from({ length: 12 }, (_, i) => ({
    name: i < 11 ? `Team ${i + 1}` : 'Your Team',
    keepers: [...]
  }));
}
```

**After:**
```javascript
function makeKeeperGrid(myTeamId = null) {
  return Array.from({ length: 12 }, (_, i) => {
    const teamNum = i + 1;
    const isMyTeam = myTeamId && myTeamId === teamNum;
    return {
      name: `Team ${teamNum}${isMyTeam ? ' (Your Team)' : ''}`,
      teamId: teamNum,
      isMyTeam,
      keepers: [...]
    };
  });
}
```

### 2. **Updated Keeper Grid State Initialization** (App.jsx, line 121)
   - Initialize keeper grid with `null` for `myTeamId` to avoid issues on first render
   - Moved state initialization order to define `myTeamId` before `keeperGrid`

### 3. **Added Effect to Rebuild Keeper Grid** (App.jsx, lines 240-243)
   - Added `useEffect` that rebuilds the keeper grid whenever `myTeamId` changes
   - This ensures the "(Your Team)" label moves to the correct team when the user changes their selection

```javascript
// Rebuild keeper grid when myTeamId changes to update team labels
useEffect(() => {
  setKeeperGrid(makeKeeperGrid(myTeamId));
}, [myTeamId]);
```

### 4. **Updated Keeper Grid Row Rendering** (App.jsx, line 731)
   - Changed from checking `team.name === 'Your Team'` to using the `isMyTeam` flag
   - This makes the row styling more robust and not dependent on string matching

**Before:**
```javascript
className={team.name === 'Your Team' ? 'your-team-row' : ''}
```

**After:**
```javascript
className={team.isMyTeam ? 'your-team-row' : ''}
```

### 5. **Updated Tests** (App.test.jsx, lines 225-229)
   - Updated the keeper grid test to expect 12 teams (Team 1–12) instead of Team 1–11 + Your Team
   - Uses `getAllByText` to count all team rows with regex pattern `/^Team \d+/`

## How It Works

1. **Team Selection Dropdown**: Located at the top of the app, the user can select which team (1-12) is "their team"
2. **Keeper Grid Update**: When the user changes their team selection:
   - The `myTeamId` state updates
   - The `useEffect` hook detects the change
   - `makeKeeperGrid(myTeamId)` is called to rebuild the grid with the correct labels
   - The keeper grid rerenders with the new "(Your Team)" label positioned correctly
3. **Persistent Selection**: The `myTeamId` is saved to localStorage, so the user's selection persists across page refreshes

## User Experience

- **Before**: Team 12 was always labeled "Your Team" regardless of what the user selected
- **After**: The user can select any team (1-12) from the dropdown, and that team gets the "(Your Team)" label in the keeper grid
- All 12 teams are now clearly visible and labeled as "Team 1" through "Team 12"
- The selected team's row is highlighted with the `.your-team-row` CSS class for visual distinction

## Testing
All tests pass with the new implementation:
- ✅ 35 tests passed
- ✅ Keeper grid correctly renders 12 teams
- ✅ Team selection persists across page refreshes
- ✅ "(Your Team)" label moves dynamically based on selection

