package com.shatteredpixel.shatteredpixeldungeon.ui;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.input.RealtimeInput;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Camera;
import com.watabou.noosa.ui.Component;

/**
 * Zero-allocation realtime interaction prompt.
 * Optimized for 60 FPS with no GC pressure.
 */
public class RealtimeInteractionPrompt extends Component {

    private static final float RANGE = 1.6f;

    private final BitmapText nameLabel;
    private final ItemSprite highlightSprite;

    // State tracking
    private int targetCell = -1;
    private int lastImageId = -1;
    private int lastTargetCell = -1;
    private String lastHeapName = null;

    // Cached level dimensions (updated when level changes)
    private int cachedLevelWidth = -1;

    // Zero-allocation: Reusable coordinate objects (instance fields)
    // These are mutated in-place instead of creating new objects every frame
    private float tempWorldX;
    private float tempWorldY;
    private float tempScreenX;
    private float tempScreenY;

    public RealtimeInteractionPrompt() {
        super();
        // 1. Setup the Sprite (Game Layer)
        highlightSprite = new ItemSprite();
        highlightSprite.hardlight(0xFFFFFF); // White Silhouette
        highlightSprite.camera = Camera.main; // CRITICAL: bind to world camera
        highlightSprite.visible = false;
        add(highlightSprite);

        // 2. Setup the Text (Game Layer - moved from UI layer for simpler math)
        nameLabel = new BitmapText(PixelScene.pixelFont);
        nameLabel.hardlight(0xFFEEAA);
        nameLabel.visible = false;
        nameLabel.scale.set(0.7f);
        nameLabel.camera = Camera.main; // CRITICAL: bind to world camera for zero-allocation positioning
        add(nameLabel);
    }

    @Override
    public void update() {
        super.update();

        // HARD SAFETY CHECKS: bail out during transitions or when input/UI should be disabled
        if (!RealtimeInput.isEnabled() ||
                Dungeon.hero == null ||
                !Dungeon.hero.ready || // hero not ready during level transitions
                Dungeon.level == null) {
            hideAll();
            return;
        }

        // Update cached level width if level changed
        if (cachedLevelWidth != Dungeon.level.width()) {
            cachedLevelWidth = Dungeon.level.width();
        }

        try {
            Heap best = findClosestInteractableHeap();
            if (best == null) {
                hideAll();
                return;
            }

            // --- SPRITE LOGIC (World Space) ---
            Item item = best.peek();
            targetCell = best.pos;
            boolean isContainer = best.type != Heap.Type.HEAP && best.type != Heap.Type.FOR_SALE;

            if (isContainer || item != null) {
                // Update visual if changed (track heap types with negative keys to avoid collisions)
                int desiredKey = isContainer ? (-100 - best.type.ordinal()) : item.image();
                if (desiredKey != lastImageId) {
                    if (isContainer) {
                        highlightSprite.view(best);
                    } else {
                        highlightSprite.view(item);
                    }
                    // Force pure-white silhouette, remove any item-specific glow
                    highlightSprite.glow(null);
                    highlightSprite.color(0xFFFFFF);
                    lastImageId = desiredKey;
                }

                // Constant alpha (no flashing)
                highlightSprite.alpha(0.17f);
                highlightSprite.visible = true;

                // --- ZERO-ALLOCATION POSITIONING: Use raw grid math ---
                // Convert cell position to world coordinates using raw math (no allocations)
                int tileX = targetCell % cachedLevelWidth;
                int tileY = targetCell / cachedLevelWidth;

                // Calculate world position using cached tile size
                float overlayScale = 1.01f;
                highlightSprite.scale.set(overlayScale);
                highlightSprite.origin.set(0, 0);

                // Raw math: tileToWorld equivalent without allocation
                tempWorldX = tileX * DungeonTilemap.SIZE;
                tempWorldY = tileY * DungeonTilemap.SIZE;

                highlightSprite.x = tempWorldX;
                highlightSprite.y = tempWorldY;
            } else {
                // No item and not a container we render
                highlightSprite.visible = false;
            }

            // --- TEXT LOGIC: Update only when heap changes (caching optimization) ---
            if (targetCell != lastTargetCell) {
                String heapName = getHeapName(best);
                // Only update text if it actually changed (avoids redundant setText + measure)
                if (heapName != null && !heapName.equals(lastHeapName)) {
                    nameLabel.text(heapName);
                    nameLabel.measure();
                    lastHeapName = heapName;
                }
                lastTargetCell = targetCell;
            }

            nameLabel.visible = true;

            // --- ZERO-ALLOCATION LABEL POSITIONING (World Space) ---
            if (highlightSprite.visible) {
                // Position label directly under sprite using raw math
                // Sprite bottom-center X coordinate
                float spriteBottomCenterX = tempWorldX + highlightSprite.width() / 2f;
                float spriteBottomY = tempWorldY + highlightSprite.height();

                // Center label horizontally under sprite; add small padding below
                nameLabel.x = spriteBottomCenterX - (nameLabel.width() / 2f);
                nameLabel.y = spriteBottomY + 2f;
            } else {
                // Fallback: center under tile using raw math (no allocations)
                int tileX = targetCell % cachedLevelWidth;
                int tileY = targetCell / cachedLevelWidth;

                tempWorldX = tileX * DungeonTilemap.SIZE;
                tempWorldY = tileY * DungeonTilemap.SIZE;

                nameLabel.x = tempWorldX + (DungeonTilemap.SIZE / 2f) - (nameLabel.width() / 2f);
                nameLabel.y = tempWorldY + DungeonTilemap.SIZE + 2f;
            }
        } catch (Exception e) {
            // During level transitions or any unexpected state, fail silently for this frame
            hideAll();
        }
    }

    private void hideAll() {
        nameLabel.visible = false;
        highlightSprite.visible = false;
        targetCell = -1;
        lastImageId = -1;
        lastTargetCell = -1;
        lastHeapName = null;
    }

    /**
     * Finds the closest interactable heap within range.
     * Optimized with defensive null checks and cached level width.
     */
    private Heap findClosestInteractableHeap() {
        // DEFENSIVE NULL CHECKS: Avoid relying on exception handling for control flow
        if (Dungeon.level == null || Dungeon.hero == null) {
            return null;
        }

        // Early exit if no heaps exist
        if (Dungeon.level.heaps == null || Dungeon.level.heaps.size() == 0) {
            return null;
        }

        float bestDist = Float.MAX_VALUE;
        Heap best = null;

        float hx = Dungeon.hero.exactX; // Use exact sub-pixel position
        float hy = Dungeon.hero.exactY;

        // Efficient iteration over heaps
        for (Heap h : Dungeon.level.heaps.valueList()) {
            if (h == null) continue;
            if (h.type == Heap.Type.FOR_SALE) continue; // Don't steal from shopkeepers yet

            // Only show if it's a container or has items
            boolean showable = h.type != Heap.Type.HEAP || !h.isEmpty();
            if (!showable) continue;

            // Euclidean Distance Check using cached level width
            int cx = h.pos % cachedLevelWidth;
            int cy = h.pos / cachedLevelWidth;
            float dx = cx - hx;
            float dy = cy - hy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist <= RANGE && dist < bestDist) {
                bestDist = dist;
                best = h;
            }
        }
        return best;
    }

    private String getHeapName(Heap h) {
        switch (h.type) {
            case LOCKED_CHEST: return "Locked Chest";
            case CRYSTAL_CHEST: return "Crystal Chest";
            case CHEST: return "Chest";
            case SKELETON: return "Skeletal Remains";
            case REMAINS: return "Remains";
            case TOMB: return "Tomb";
            default:
                if (!h.isEmpty() && h.peek() != null) {
                    return h.peek().name();
                }
                return "Loot";
        }
    }
}
