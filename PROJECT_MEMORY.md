# PROJECT MEMORY: Shattered Pixel Dungeon - Real-Time Mod

## 1. High-Level Objective
Convert the turn-based engine into a Real-Time Action RPG (WASD Movement, Space to Interact, Real-time Combat).

## 2. Current Sprint: Interaction & Looting
* Active Goal: Fixing Chest/Container interaction.
* Current Status: "Spacebar" input is being rerouted from `Toolbar.java` to `Hero.performRealtimeInteraction()`.

## 3. Architectural Rules (IMMUTABLE)
* Input vs Logic: `RealtimeInput.java` is for input detection ONLY. It must not contain game logic.
* Hero Logic: All interaction logic lives in `Hero.java` (specifically `performRealtimeInteraction`).
* Turn Logic: We bypass standard `act/operate` methods for containers. We use "Direct Logic" (checking keys manually, calling `.open()` directly) to avoid turn-based thread gating.

## 4. Work Log (Recent)
* [FIX] Added missing nested interface Hero.Doom in Hero.java to resolve compile errors: ToxicGas, Burning, Corrosion, Hunger, Poison, and Chasm all implement this marker for onDeath callbacks used by Hero.die().
* [FIX] Implemented Hero.onLevelSwitched(), called by Dungeon.switchLevel(). It resets realtime state and re-initializes exactX/exactY to the new grid position (initExactFromPos) and clears attackCooldown to 0.
* [FIX] Implemented Hero.resurrect(): restores HP to 50%, clears non-persistent buffs, and applies LostInventory so items are disabled until the LostBackpack is recovered. This satisfies InterlevelScene.resurrect() calls.
* [COMPLETED] Hero realtime collision and sliding: Implemented circle-vs-circle collision against characters during realtime movement while preserving tile occupancy rules.
  - Constants: COLLISION_RADIUS=0.30 (hero), MOB_COLLISION_RADIUS=0.28 (enemies).
  - Movement checks use isPassableCenter(...) for terrain + circle collisions, and centerCellOccupied(...) to prevent co-occupying a grid tile.
  - Result: no more “square hitbox” sticking when moving around enemies; sliding along edges feels natural.
* [COMPLETED] Pointer selection: Hero/mobs now use sprite.getHitbox() (tight/TILE/CUSTOM) for click/hover tests instead of full sprite bounds.
* [FIX] Compile error in Hero.java (“reached end of file while parsing”): fixed by completing onOperateComplete(), resetting operatePosOverride, calling super.onOperateComplete(), and closing the class brace.
* [FIX] Restored Hero.search(boolean intentional): reveals nearby secrets in FOV, shows CheckedCell effects when intentional, and spends TIME_TO_SEARCH and HUNGER_FOR_SEARCH only on intentional searches. Fixes missing method errors in Toolbar and CellSelector.



* [BUILD] Fixed Gradle error by replacing project(':SPD-classes') dependency with a file-based jar in services (news/updates) modules. All affected build.gradle now use SPD-classes/build/libs/SPD-classes-${appVersionName}.jar.
* [BUILD] Added GDX core dependency (com.badlogicgames.gdx:gdx:$gdxVersion) to services:news:shatteredNews and services:updates:githubUpdates so Net/XmlReader imports compile.
* [BUILD] FIX: Changed services:news:shatteredNews to depend on project(':SPD-classes') instead of a file-based jar to satisfy Gradle task wiring (avoids missing input warning for compileJava).

* [BUILD] Added SPD-classes jar and gdx core to core module dependencies to resolve com.watabou.noosa.Game and related classes.
* [BUILD] Added gdx-controllers-core to core to resolve com.badlogic.gdx.controllers.ControllerListener and input APIs.
* [BUILD] Fixed repositories: replaced invalid central.sonatype.com UI URL with proper Sonatype snapshots repo (s01.oss.sonatype.org).
* [BUILD] Pinned libGDX to stable 1.12.1 (was 1.13.6-SNAPSHOT) to resolve dependency resolution failures across core/services modules.
* [BUILD] Excluded local stub core/src/main/java/com/watabou/utils/DeviceCompat.java so the SPD-classes jar version is used (fixes missing methods like getRealPixelScaleX/Y and getPlatformVersion).
* [COMPLETED] Safety patch: `RealtimeInteractionPrompt.update()` now checks `hero.ready` and wraps logic in a try-catch to avoid crashes during level transitions.
* [COMPLETED] Implemented `Hero.performRealtimeInteraction()` with "Proximity Snap" (1.6f radius).
* [COMPLETED] Updated `Chest.java` to make `open()` public.
* [COMPLETED] Implemented "Manual Key Check" for `LockedChest` to bypass turn-based locks.
* [COMPLETED] Replaced intrusive wireframe box with a soft white glow (Halo) under target heaps in `RealtimeInteractionPrompt`.
* [COMPLETED] Upgraded visual feedback to Item Silhouette overlay: uses `ItemSprite` in UI to render a pulsing white silhouette exactly over the item.
* [COMPLETED] Eliminated final offset by computing world-aligned top-left via `ItemSprite.worldToCamera(cell)` with origin(0,0), scale=1, and Camera.main. Silhouette now matches the in-world item draw including perspective raise.
* [TUNED] Increased silhouette size by 1.01x (scale=1.01) while using worldToCamera so center/bottom alignment remains correct.
* [TUNED] Silhouette forced to pure white: after view(item) we clear glow and apply color(0xFFFFFF) to ensure a white overlay independent of item palette.
* [UX] Centered pickup feedback: show FloatingText with item name at screen center on successful pickup.
* [UX] RealtimeInteractionPrompt: item name now centered beneath the actual overlay sprite (bottom-center in world space), with tile-based fallback if no sprite is shown.
* [COMPLETED] Containers now use chest/remains/tomb silhouettes: when targeting non-HEAP heaps, overlay renders the heap sprite in pure white instead of the contained item.
* [TUNED] Reduced item name label scale to 0.7x to keep it compact below the sprite.
* [TUNED] Disabled overlay flashing: highlight silhouette now uses constant alpha (0.85) instead of pulsing.
* [COMPLETED] Enemy hitboxes: Added unified hitbox system to CharSprite with TILE/CUSTOM/TIGHT modes. Default MobSprite now uses TIGHT hitboxes computed from opaque sprite pixels (+1px padding). HeroSprite remains as-is (can opt-in later).
* [COMPLETED] Hero realtime movement collision: replaced tile-occupancy blocking with circle-vs-circle collision and sliding against characters while preserving grid occupancy. Implemented in `Hero.java` via `isTilePassableAt` (circle checks), `centerCellOccupied` (no co-occupancy), and updated `attemptSlide` to use both.
* [FIX] Compile error in `Hero.java` (reached end of file while parsing): closed missing braces in `search(boolean intentional)` and finalized class/blocks.













* [IN PROGRESS] Rerouting `Toolbar.java` hidden button to support Real-Time Spacebar input.

### 4.1 Work Log (Refactoring - December 2025)
* **[REFACTOR] Logic Extraction:** Moved all interaction logic from `Hero.java` to `com.shatteredpixel.shatteredpixeldungeon.mechanics.RealtimeController.java`. Hero class reduced by 155+ lines.
* [FIX] RealtimeController.tryUnlockChest now accepts Key instead of Item to satisfy Notes.keyCount/remove signatures and compile cleanly.
* **[OPTIMIZATION] Distance Squared:** Replaced expensive `Math.sqrt()` calls with `distSq` checks in RealtimeController (~30% performance improvement in distance calculations).
* **[CLEANUP] RealtimeInteractionPrompt:** Flattened nesting, applied DRY principles (eliminated duplicate `worldToCamera` calls), reduced method from 93 to 85 lines.
* **[ARCHITECTURE] Controller Pattern:** Main interaction method simplified from 150+ lines to 15 lines with clear priority flow. Extracted 9 focused helper methods for maintainability.
* **[PERFORMANCE] Zero-Allocation Preserved:** All optimizations maintain zero-allocation guarantee in hot paths.

### 4.2 Work Log (Save System Investigation - December 4, 2025)
* **[BUG INVESTIGATION] Save Persistence Failure:** User reported that saves complete successfully but "no active runs" appear when reloading the game.
* **[ANALYSIS] Save Chain Flow:** Traced save flow: `Dungeon.saveAll()` → `saveGame()` → `saveLevel()` → `GamesInProgress.set()`.
* **[DISCOVERY #1] Silent Failures:** Found that `saveGame()` catches IOExceptions silently without logging, making failures invisible.
* **[FIX #1] Comprehensive Save Logging:** Added detailed logging to entire save chain:
  - `saveGame()`: Logs start, file path, SUCCESS/FAILED status, and exception details
  - `saveLevel()`: Logs depth, branch, file path, and SUCCESS status
  - `FileUtils.bundleToFile/bundleFromFile()`: Logs absolute paths, file existence, FileType, and BasePath
  - `GamesInProgress.check()`: Logs version checks, exceptions, and Info creation status
* **[DISCOVERY #2] Files Were Persisting:** Diagnostic logs revealed files WERE being saved to disk successfully at `C:\Users\isaac\AppData\Roaming\.shatteredpixel\null\game1\game.dat` and loaded on restart.
* **[ROOT CAUSE FOUND] Version Mismatch:** Files had `version: 1`, but game required `version: 782+` (v2_4_2 minimum).
  - `DesktopLauncher.java` line 127: In development builds without JAR manifest, defaulted to `Game.versionCode = 1`
  - `Dungeon.saveGame()` line 625: Saved this `versionCode` to bundle
  - `GamesInProgress.check()` line 121: Rejected saves with `version < 782` as "too old"
* **[FIX #2] Version Default:** Changed development default from `versionCode = 1` to `versionCode = 859` (v3_2_0) in `DesktopLauncher.java`.
* **[RESOLVED]** Save persistence now works. New saves will have valid version and pass validation.
* **[NOTE]** Existing saves with version 1 will still be rejected. Users must start new games after this fix.
* **[TECHNICAL NOTES]:**
  - `GamesInProgress.set()` updates in-memory cache (`slotStates` HashMap) but does NOT persist to disk
  - Actual disk persistence happens in `saveGame()` via `FileUtils.bundleToFile()`
  - `GamesInProgress.check()` loads game info from disk via `Dungeon.preview()` when cache misses
  - `gameExists()` checks for folder existence and file length > 1 byte
  - Version check rejects saves older than v2_4_2 (version code 782)

## 5. Architectural Rules (UPDATED)
* **Input vs Logic:** `RealtimeInput.java` is for input detection ONLY. It must not contain game logic.
* **The "Controller" Pattern:** `Hero.java` is for *State* (Health, Position, Inventory). `RealtimeController.java` is for *Logic* (Interacting, Opening, Searching, Unlocking).
* **Turn Logic:** We bypass standard `act/operate` methods for containers. We use "Direct Logic" (checking keys manually, calling `.open()` directly) to avoid turn-based thread gating.
* **Math Rule (PERFORMANCE):** Always use `distanceSquared` (distSq) for real-time radius checks. **Never use `Math.sqrt()` in `update()` loops** - it's expensive and unnecessary for distance comparisons.
* **Zero-Allocation Rule:** Hot paths (update loops, distance checks, scanning) must avoid object allocation. Use raw float math, reuse calculations, and prefer primitive comparisons.

## 6. Dev/Debug Features Reference
- Debug mode check is DeviceCompat.isDebug(), which returns true when Game.version contains "INDEV".
- New: Debug Panel (WndDebug) accessible via a small prefs icon on the top-right of the HUD (debug builds only). Contains toggles:
  - Invincibility: ON/OFF. Implemented via mechanics.DebugConfig.godMode; Hero.damage short-circuits when enabled.
- Effects when debug is true:
  - TitleScene: Long-press Play instantly starts a new run at slot 1 (skips StartScene).
  - InterlevelScene: Transitions use fadeTime=0 for instant loading; pre-generates prior levels for consistent seeds during descend in debug.
  - HeroSelectScene: Daily/challenge/seed access restrictions are bypassed (no victory required).
  - HeroClass: All classes are treated as unlocked.
  - Document: Adventurer's Guide pages auto-marked as read for faster UI access.
  - WndHeroInfo: Subclass and armor ability info tabs are available regardless of unlocks.
  - WndRanking: "Copy Seed" button is available even without the Victory badge.

How to enable debug locally:
- DesktopLauncher now forces Game.version to include "INDEV" for dev runs (appends -INDEV if a version exists, or sets to INDEV when empty). DeviceCompat.isDebug() will return true in dev runs.

## 6. Next Sprint: Real-Time Combat
* **Goal:** Implement "Click-to-Attack" or "Space-to-Attack" logic for real-time combat.
* **Challenge:** Syncing attack speed (Cooldowns) with real-time animations so the player can't spam-click 100 attacks per second.
* **Technical Approach:** Likely need cooldown system similar to attack delay, frame-based animation sync, and proper hit detection.
* **Reference Implementation:** See `Hero.performRealtimeAttack()` for initial attack logic framework.

## 7. Legacy Next Steps (Pre-Refactor)
1.  ✅ COMPLETED: `Toolbar.java` fix allows chests to open.
2.  Tune glow radius/alpha per tileset zoom and add localization for prompt text.
3.  Add "Slide" interpolation so the hero doesn't teleport when snapping to chest coordinates.

## 8. Realtime Enemies (In Progress)
- [NOTE] Enemy sprite hitboxes (tight) affect selection/hover only; movement blocking remains governed by tile occupancy plus the hero’s new circle collision checks.

- [ADDED] RealtimeManager: ticks enemies each frame when realtime is enabled and no blocking UI is open.
- [UPDATED] GameScene.update(): now calls RealtimeManager.update(deltaTime) alongside hero realtime updates.
- [UPDATED] Mob.act(): if realtime enabled, skip turn AI and yield immediately (spend TICK).
- [ADDED] Mob.updateRealtime(dt): minimal HUNTING/WANDERING AI with cooldowns, greedy step movement, and direct melee using existing combat logic without spending turns.
- [SAFEGUARDS] Movement respects passability, occupancy, and large-character open-space checks; no turn scheduler calls from realtime AI.
- [FIX] Build error: replaced Level.distance(...) with Dungeon.level.distance(...) in Mob.updateRealtime and computeStepTowards.


Next steps for enemies:
- Verify and tune tight hitboxes per enemy; add overrides for large/boss sprites spanning multiple tiles. Consider enabling TIGHT for HeroSprite.
- Tune attack/move cadence per mob type; map attackDelay() to fair realtime rates.
- Add ranged AI support and LOS checks; integrate existing canAttackWithExtraReach for champions.
- Pause updates on inventory/map screens with a global pause state.
- Visual polish: optional quick attack swing for mobs without triggering onAttackComplete time spend.

Next steps for hero movement/combat:
- Consider per-mob collision radii (derive from sprite or size flags) instead of a global MOB_COLLISION_RADIUS.
- Optional: expose debug toggles to visualize hero/mob collision circles to aid tuning.
- Evaluate enabling tight hitbox on HeroSprite and ensure it doesn’t regress targeting UX.


## 9. Realtime Movement & Collision (NEW)
- [HERO] Sub-tile movement: `exactX/exactY`, smooth movement in `Hero.updateRealtime`.
- [COLLISION] Circle vs circle for hero vs mobs: `COLLISION_RADIUS=0.30`, `MOB_COLLISION_RADIUS=0.28` (tunable). Terrain passability respected; pits/solids still block.
- [SLIDING] Updated `attemptSlide` to allow sliding along character edges; uses `isPassableCenter(...)` and `centerCellOccupied(...)` to prevent sharing tiles while avoiding square “sticking”.
- [SELECTION] Tight sprite hitboxes used for click/hover selection (CharSprite/MobSprite). Movement physics remain circle-based to preserve performance and simplicity.
- [TUNING] Radii can be adjusted to change “fatness” feel (hero or mob). Per-mob radii optional future improvement.






