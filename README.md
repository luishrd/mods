Seed: [-6300001494599353861]

This sends the spawn coordinates to the action bar every second (20 ticks = 1 second).

**Step 2: Build the mod**

```
./gradlew build
```

The compiled mod will be at `build/libs/hud-<version>.jar`

**Step 3: Test with local server**

Fabric provides a run configuration. Generate the run configurations:

```
./gradlew genSources
```

Start a test server from VSCode:

```
./gradlew runServer
```

This starts a Minecraft server on port 25565. Connect with your Minecraft client to `localhost:25565` to test.

**Step 4: Deploy to actual server**

Copy the jar from `build/libs/` to your server's `mods/` folder:

```
cp build/libs/hud-*.jar /path/to/your/server/mods/
```

## Design Documentation: Pin Navigation System

Perfect, let's document the complete design before implementation.

---

## Data Structure: O(1) Access

You're absolutely right about hash/dictionary access. Let me clarify:

**In Java, HashMap IS the equivalent of JavaScript objects for O(1) lookup:**

```javascript
// JavaScript
playerPins[uuid][dimension][pinName];
```

```java
// Java HashMap - also O(1)
playerPins.get(uuid).get(dimension).get(pinName)
```

**So our structure should be:**

```java
Map<String, Map<String, Map<String, PinLocation>>> playerPins
```

Breaking it down:

- Level 1: `Map<String, ...>` - Key = player UUID
- Level 2: `Map<String, ...>` - Key = dimension name
- Level 3: `Map<String, PinLocation>` - Key = pin name

**Access pattern:**

```java
playerPins.get(playerUuid).get("minecraft:overworld").get("base")
```

Three hash lookups = O(1) + O(1) + O(1) = O(1) total. Perfect.

---

## Action Bar Layout Design

### Layout States

**State 1: Not Navigating**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ 99:99 Bed
```

**State 2: Navigating (same dimension)**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ 99:99 Bed ◆ ⬆ base
```

**State 3: Navigating (different dimension)**

```
X: -99999 Y: -99999 H: -999 ◆ NW ◆ 99:99 Bed ◆ ⊗ base [Nether]
```

### Section Breakdown

**Section 1: Position** (always visible, fixed width: 31 chars)

```
X: -99999 Y: -99999 H: -999
```

Format: `"X:%7d Y:%7d H:%4d"`

**Section 2: Cardinal Heading** (always visible, fixed width: 8 chars)

```
 ◆ NW ◆
```

Shows which direction player is facing (N, NE, E, SE, S, SW, W, NW)
Format: `" ◆ %-2s ◆ "`
The `%-2s` left-aligns and pads to 2 chars, so "N" becomes "N "

**Section 3: Time Countdown** (always visible, width: ~11 chars)

```
99:99 Bed
```

Countdown to next sleep/mob/dawn event
Format: `"%2d:%02d %-4s"`

**Section 4: Navigation** (conditional, variable width)

```
 ◆ ⬆ base
```

Only shows when actively navigating
Arrow is fixed width (1 char), pin name is variable
Format: `" ◆ %s %s"` (separator, arrow, pin name)

---

## Command Specifications

### `/pin <name>`

Creates or prompts to overwrite a pin.

**First time:**

```
> /pin base
§aPin 'base' created at X:450 Y:-300 H:70 [Overworld]
```

**Pin already exists:**

```
> /pin base
§ePin 'base' already exists at X:100 Y:64 H:200
§eCurrent location: X:450 Y:-300 H:70
§eUse '/pin base --force' to overwrite
```

### `/pin <name> --force`

Overwrites existing pin without confirmation.

```
> /pin base --force
§aPin 'base' updated at X:450 Y:-300 H:70 [Overworld]
§7Previous location: X:100 Y:64 H:200
```

### `/pins`

Lists all pins in current dimension with clickable actions.

```
§6━━━━━ Your pins in Overworld ━━━━━
§a⬩ spawn §7X:100 Y:64 Z:200 §e[Go] §c[Remove]
§a⬩ base §7X:450 Y:-300 Z:70 §e[Go] §c[Remove]
§a⬩ village §7X:800 Y:65 Z:150 §e[Go] §c[Remove]
§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Clicking `[Go]` executes `/go <name>`
Clicking `[Remove]` executes `/pins remove <name>`

**Empty state:**

```
§6━━━━━ Your pins in Overworld ━━━━━
§7No pins in this dimension yet.
§7Use /pin <name> to create one!
§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### `/pins remove <name>`

Deletes a pin from current dimension.

```
> /pins remove village
§aPin 'village' removed from Overworld
```

**Pin doesn't exist:**

```
> /pins remove castle
§cNo pin named 'castle' in Overworld
```

### `/go <name>`

Starts navigation to a pin.

**Success (same dimension):**

```
> /go base
§aNavigating to 'base' at X:450 Y:-300 H:70
```

**Success (different dimension):**

```
> /go nether_fortress
§6Pin 'nether_fortress' is in the Nether
§6Travel there to see directions
```

**Pin doesn't exist:**

```
> /go castle
§cNo pin named 'castle' found
```

### `/go stop` or `/go clear`

Stops active navigation.

```
> /go stop
§aNavigation stopped
```

**When not navigating:**

```
> /go stop
§7Not currently navigating
```

---

## Navigation Behavior

### Same Dimension Navigation

When navigating to pin in same dimension:

- Calculate angle between player position and pin position
- Calculate distance
- Display arrow pointing toward pin
- Display pin name after arrow

**Distance-based arrow:**

- `< 1 block`: `⬤` (filled circle - arrived)
- `< 5 blocks`: `○` (empty circle - very close)
- `>= 5 blocks`: Directional arrow (⬆⬈➡⬊⬇⬋⬅⬉)

### Different Dimension Navigation

When navigating to pin in different dimension:

- Show `⊗` (cross circle) instead of directional arrow
- Show dimension name in brackets: `[Nether]`, `[End]`, `[Overworld]`
- When player enters correct dimension, automatically switch to normal navigation

**Dimension display names:**

- `minecraft:overworld` → `Overworld`
- `minecraft:the_nether` → `Nether`
- `minecraft:the_end` → `End`

---

## Heading Compass

Shows cardinal direction player is currently facing (independent of navigation).

**8-direction compass:**

- 0° ± 22.5° = `N `
- 45° ± 22.5° = `NE`
- 90° ± 22.5° = `E `
- 135° ± 22.5° = `SE`
- 180° ± 22.5° = `S `
- 225° ± 22.5° = `SW`
- 270° ± 22.5° = `W `
- 315° ± 22.5° = `NW`

Note the space padding on single-letter directions to maintain fixed width.

---

## Time Countdown

Shows countdown to next Minecraft time event.

**Event priority (show whichever is soonest):**

1. **Bed** - Can sleep (nighttime starts at tick 12541)
2. **Mobs** - Hostile mob spawning (nighttime)
3. **Dawn** - Sunrise (daytime starts at tick 23458)

**Display format:**

- Minutes:Seconds remaining
- Event name (Bed, Mobs, Dawn)
- Example: `04:32 Bed` means 4 minutes 32 seconds until can sleep

**Minecraft time:**

- 24000 ticks per day cycle
- 1000 ticks per minute
- 20 ticks per second
- Noon = 6000
- Sunset = 12000
- Midnight = 18000
- Sunrise = 0/24000

---

## File Structure

```
world/
  data/
    pins/
      <player-uuid>.json
      <player-uuid>.json
      ...
```

**File format (per player):**

```json
{
	"minecraft:overworld": {
		"spawn": { "x": 100, "y": 64, "z": 200, "dimension": "minecraft:overworld" },
		"base": { "x": 450, "y": 70, "z": -300, "dimension": "minecraft:overworld" }
	},
	"minecraft:the_nether": {
		"fortress": { "x": 50, "y": 80, "z": 25, "dimension": "minecraft:the_nether" }
	}
}
```
