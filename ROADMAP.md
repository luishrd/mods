# Implementation Roadmap

### Step 1: Add Heading Compass

Show cardinal direction player is facing.

**Display:**

```
NW ◆ ⬆ to spawn
```

Centered on screen, hardcoded spawn arrow on right.

### Step 2: Add Position Coordinates

Show current player location.

**Display:**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ ⬆ spawn
```

Fixed-width position on left, heading in middle, spawn on right.

### Step 3: Add Time Countdown (Normal Weather Only)

Calculate and display countdown to next bed/mobs/dawn event.

**Assumptions for this step:**

- Normal weather (no thunderstorms)
- Overworld only
- Ignore nearby monsters
- Ignore dimension restrictions

**Simple rules:**

- Bed available: tick 12541 (night starts)
- Mobs spawn: tick 13000 (shortly after night)
- Dawn: tick 23000 (day starts)

Show whichever event is soonest.

**Display:**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ Bed  in 04:32 ◆ ⬆ spawn
X: -99999 Y: -99999 H: -999 ◆ NW ◆ Mobs in 04:32 ◆ ⬆ spawn
X: -99999 Y: -99999 H: -999 ◆ NW ◆ Dawn in 04:32 ◆ ⬆ spawn
```

**Research needed for this step:**

Web search for exact tick when bed becomes available in normal conditions. I'll verify tick 12541 is correct.

### Step 4: Implement `/go spawn` and `/go stop`

Enable navigation toggle functionality.

**New features:**

- Auto-create "spawn" pin on first player join
- `/go spawn` activates navigation to spawn
- `/go stop` deactivates navigation
- Navigation section collapses when not active

**Display when navigating:**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ Bed  in 04:32 ◆ ⬆ spawn
```

**Display when not navigating:**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ Mobs in 04:32
```

Layout shift happens only on `/go` toggle.

### Step 5: Implement `/pin <name>` with `--force`

Allow players to create custom pins.

**Features:**

- `/pin <name>` creates pin at current location
- Shows confirmation if pin exists
- `/pin <name> --force` overwrites without confirmation

**Messages:**

```
§aPin 'base' created at X:450 Y:-300 H:70 [Overworld]
```

```
§ePin 'base' already exists at X:100 Y:64 H:200
§eCurrent location: X:450 Y:-300 H:70
§eUse '/pin base --force' to overwrite
```

### Step 6: Implement `/pins` and `/pins remove`

Full pin management system.

**Features:**

- `/pins` lists all pins in current dimension
- Clickable `[Go]` and `[Remove]` buttons in chat
- `/pins remove <name>` deletes a pin

**Display:**

```
§6━━━━━ Your pins in Overworld ━━━━━
§a⬩ spawn §7X:100 Y:64 Z:200 §e[Go] §c[Remove]
§a⬩ base §7X:450 Y:-300 Z:70 §e[Go] §c[Remove]
§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Step 7: (Future) Advanced Bed/Mob Logic

Add complex game state handling.

**Research and implement:**

- Thunderstorm detection → allow bed anytime
- Dimension restrictions → disable bed in Nether/End
- Nearby monster detection → show warning
- Mob spawning light level rules
- Moon phase effects

This becomes polish/advanced features after core system works.

---

Each step adds clear value, and we defer the complex weather/dimension/monster logic to the end when the core navigation is already functional.
