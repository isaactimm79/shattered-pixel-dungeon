/*
 * Realtime enemy ticking for Shattered Pixel Dungeon - Real-Time Mod
 */
package com.shatteredpixel.shatteredpixeldungeon.realtime;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.input.RealtimeInput;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;

public final class RealtimeManager {

    private RealtimeManager() {}

    public static void update(float dt) {
        if (!RealtimeInput.isEnabled()) return;
        if (Dungeon.level == null || Dungeon.hero == null) return;
        // Pause when a blocking UI is shown
        if (GameScene.showingWindow() || GameScene.interfaceBlockingHero()) return;

        for (Mob m : Dungeon.level.mobs.toArray(new Mob[0])) {
            if (m != null && m.isAlive()) {
                m.updateRealtime(dt);
            }
        }
    }
}
