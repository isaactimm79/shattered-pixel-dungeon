/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2025 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.mechanics;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.input.RealtimeInput;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.SkeletonKey;

import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.PathFinder;

/**
 * Stateless controller for realtime interaction logic.
 * Handles spacebar interaction: items/containers first, then doors/stairs.
 * Optimized for zero-allocation performance.
 */
public class RealtimeController {

	// Use squared distances to avoid expensive Math.sqrt() calls
	private static final float PICKUP_RANGE_SQ = 1.5f * 1.5f;      // 2.25
	private static final float INTERACTION_RANGE_SQ = 1.6f * 1.6f; // 2.56

	/**
	 * Performs realtime interaction for the hero.
	 * Priority order: Items → Containers → Locked Doors → Stairs
	 *
	 * @param hero The hero performing the interaction
	 */
	public static void performInteraction(Hero hero) {
		if (!RealtimeInput.isEnabled() || Dungeon.level == null) return;

		GLog.i("[DEBUG] Spacebar pressed - performInteraction called");

		// A) Try item pickup first
		hero.waitOrPickup = true;
		boolean pickedUp = hero.pickup(null);
		GLog.i("[DEBUG] Item pickup attempt: %s", pickedUp ? "SUCCESS (returning early)" : "FAILED");
		if (pickedUp) return;

		// B) Try interacting with containers
		Heap target = scanForTarget(hero);
		GLog.i("[DEBUG] Container scan result: %s", target != null ? "Found heap at " + target.pos : "No containers found");
		if (target != null) {
			boolean interacted = tryInteractWithHeap(hero, target);
			GLog.i("[DEBUG] Container interaction: %s", interacted ? "SUCCESS" : "FAILED");
			if (interacted) return;
		}

		// C) Try unlocking adjacent doors
		boolean doorUnlocked = tryUnlockAdjacentDoors(hero);
		GLog.i("[DEBUG] Door unlock: %s", doorUnlocked ? "SUCCESS" : "FAILED");
		if (doorUnlocked) return;

		// D) Try level transitions (stairs/portals)
		boolean transitioned = tryLevelTransition(hero);
		GLog.i("[DEBUG] Level transition: %s", transitioned ? "SUCCESS" : "FAILED");
	}

	/**
	 * Attempts to activate a level transition if hero is standing on one.
	 * InterlevelScene handles all saving internally.
	 *
	 * @return true if transition was activated
	 */
	private static boolean tryLevelTransition(Hero hero) {
		LevelTransition transition = Dungeon.level.getTransition(hero.pos);
		if (transition == null) return false;
		if (!transition.inside(hero.pos)) return false;
		if (Dungeon.level.locked) return false;
		if (Dungeon.level.plants.containsKey(hero.pos)) return false;
		if (Dungeon.depth >= 26 && transition.type != LevelTransition.Type.REGULAR_ENTRANCE) return false;

		GLog.i("RealtimeController: Activating transition from depth %d to depth %d (type: %s)",
			Dungeon.depth, transition.destDepth, transition.type);
		return Dungeon.level.activateTransition(hero, transition);
	}

	/**
	 * Scans for unlockable doors in adjacent cells and attempts to unlock them.
	 *
	 * @return true if a door was unlocked
	 */
	private static boolean tryUnlockAdjacentDoors(Hero hero) {
		for (int off : PathFinder.NEIGHBOURS8) {
			int cell = hero.pos + off;
			if (!Dungeon.level.insideMap(cell)) continue;

			int tile = Dungeon.level.map[cell];
			if (tile == Terrain.LOCKED_DOOR) {
				if (tryUnlockIronDoor(hero, cell)) return true;
			} else if (tile == Terrain.CRYSTAL_DOOR) {
				if (tryUnlockCrystalDoor(hero, cell)) return true;
			} else if (tile == Terrain.LOCKED_EXIT) {
				if (tryUnlockExit(hero, cell)) return true;
			}
		}
		return false;
	}

	private static boolean tryUnlockIronDoor(Hero hero, int cell) {
		if (Notes.keyCount(new IronKey(Dungeon.depth)) <= 0) {
			GLog.w(Messages.get(hero, "locked_door"));
			return false;
		}

		Notes.remove(new IronKey(Dungeon.depth));
		GameScene.updateKeyDisplay();
		Sample.INSTANCE.play(Assets.Sounds.UNLOCK);
		Level.set(cell, Terrain.DOOR);
		GameScene.updateMap(cell);
		com.shatteredpixel.shatteredpixeldungeon.levels.features.Door.enter(cell);
		return true;
	}

	private static boolean tryUnlockCrystalDoor(Hero hero, int cell) {
		if (Notes.keyCount(new CrystalKey(Dungeon.depth)) <= 0) {
			GLog.w(Messages.get(hero, "locked_door"));
			return false;
		}

		Notes.remove(new CrystalKey(Dungeon.depth));
		GameScene.updateKeyDisplay();
		Level.set(cell, Terrain.EMPTY);
		Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
		CellEmitter.get(cell).start(Speck.factory(Speck.DISCOVER), 0.025f, 20);
		GameScene.updateMap(cell);
		return true;
	}

	private static boolean tryUnlockExit(Hero hero, int cell) {
		if (Notes.keyCount(new SkeletonKey(Dungeon.depth)) <= 0) {
			GLog.w(Messages.get(hero, "locked_door"));
			return false;
		}

		Notes.remove(new SkeletonKey(Dungeon.depth));
		GameScene.updateKeyDisplay();
		Sample.INSTANCE.play(Assets.Sounds.UNLOCK);
		Level.set(cell, Terrain.UNLOCKED_EXIT);
		GameScene.updateMap(cell);
		return true;
	}

	/**
	 * Scans for the closest interactable container within pickup range.
	 * Uses distance squared for zero-allocation performance.
	 *
	 * @return closest container or null
	 */
	private static Heap scanForTarget(Hero hero) {
		Heap best = null;
		float bestDistSq = PICKUP_RANGE_SQ;
		int w = Dungeon.level.width();

		int totalHeaps = 0;
		int skippedRegular = 0;
		int skippedFOV = 0;
		int scannedContainers = 0;

		for (Heap h : Dungeon.level.heaps.valueList()) {
			if (h == null) continue;
			totalHeaps++;

			if (h.type == Heap.Type.HEAP || h.type == Heap.Type.FOR_SALE) {
				skippedRegular++;
				continue;
			}
			if (!Dungeon.level.heroFOV[h.pos]) {
				skippedFOV++;
				continue;
			}

			scannedContainers++;

			// Calculate distance squared (zero allocation)
			int hx = h.pos % w;
			int hy = h.pos / w;
			float dx = hx - hero.exactX;
			float dy = hy - hero.exactY;
			float distSq = dx * dx + dy * dy;

			GLog.i("[DEBUG] Found container type=%s at pos=%d, dist²=%.2f (max=%.2f)",
				h.type.name(), h.pos, distSq, PICKUP_RANGE_SQ);

			if (distSq <= bestDistSq) {
				best = h;
				bestDistSq = distSq;
			}
		}

		GLog.i("[DEBUG] Scan summary: %d total heaps, %d regular (skipped), %d out of FOV (skipped), %d containers scanned",
			totalHeaps, skippedRegular, skippedFOV, scannedContainers);

		return best;
	}

	/**
	 * Attempts to interact with a heap (open container or pickup item).
	 * Uses distance squared check and position snapping for engine compatibility.
	 *
	 * @return true if interaction was successful
	 */
	private static boolean tryInteractWithHeap(Hero hero, Heap heap) {
		// Verify heap is within interaction range (using distance squared)
		int w = Dungeon.level.width();
		int hx = heap.pos % w;
		int hy = heap.pos / w;
		float dx = hx - hero.exactX;
		float dy = hy - hero.exactY;
		float distSq = dx * dx + dy * dy;

		GLog.i("[DEBUG] tryInteractWithHeap: type=%s, dist²=%.2f, max=%.2f", heap.type.name(), distSq, INTERACTION_RANGE_SQ);

		if (distSq > INTERACTION_RANGE_SQ) {
			GLog.i("[DEBUG] Container TOO FAR (%.2f > %.2f)", distSq, INTERACTION_RANGE_SQ);
			return false;
		}

		// Snap hero position for engine compatibility
		int savedPos = hero.pos;
		GLog.i("[DEBUG] Snapping hero from pos %d to heap pos %d", savedPos, heap.pos);
		try {
			hero.pos = heap.pos;
			if (hero.sprite != null) {
				hero.sprite.interruptMotion();
				hero.sprite.idle();
			}

			GLog.i("[DEBUG] Calling openHeap()");
			openHeap(hero, heap);
			return true;
		} finally {
			hero.pos = savedPos;
			GLog.i("[DEBUG] Restored hero pos to %d", savedPos);
		}
	}

	/**
	 * Opens a heap, handling locked containers with key checks.
	 */
	private static void openHeap(Hero hero, Heap heap) {
		GLog.i("[DEBUG] openHeap called for type=%s at pos=%d", heap.type.name(), heap.pos);

		switch (heap.type) {
			case LOCKED_CHEST:
				GLog.i("[DEBUG] Attempting to unlock LOCKED_CHEST");
				if (tryUnlockChest(hero, heap, new GoldenKey(Dungeon.depth))) {
					GLog.i("[DEBUG] Key found, calling heap.open()");
					heap.open(hero);
					GLog.i("Manual Unlock Success.");
				} else {
					GLog.w(Messages.get(hero, "locked_chest"));
				}
				break;

			case CRYSTAL_CHEST:
				GLog.i("[DEBUG] Attempting to unlock CRYSTAL_CHEST");
				if (tryUnlockChest(hero, heap, new CrystalKey(Dungeon.depth))) {
					GLog.i("[DEBUG] Key found, calling heap.open()");
					heap.open(hero);
					GLog.i("Manual Unlock Success.");
				} else {
					GLog.w(Messages.get(hero, "locked_chest"));
				}
				break;

			case CHEST:
			case TOMB:
			case SKELETON:
			case REMAINS:
				GLog.i("[DEBUG] Opening unlocked container type=%s, calling heap.open()", heap.type.name());
				heap.open(hero);
				GLog.i("Forced container open at %d", heap.pos);
				break;

			default:
				GLog.i("[DEBUG] Default case, calling heap.open()");
				heap.open(hero);
				break;
		}
	}

	/**
	 * Attempts to unlock a chest with a specific key type.
	 *
	 * @return true if key was available and consumed
	 */
	private static boolean tryUnlockChest(Hero hero, Heap heap, Key key) {
		if (Notes.keyCount(key) <= 0) return false;

		Notes.remove(key);
		GameScene.updateKeyDisplay();
		Sample.INSTANCE.play(Assets.Sounds.UNLOCK);
		return true;
	}

}
