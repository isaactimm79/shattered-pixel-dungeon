/*
 * Debug window: only add/show in debug builds.
 */
package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Chrome;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.DebugConfig;
import com.shatteredpixel.shatteredpixeldungeon.ui.StyledButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;

public class WndDebug extends Window {

    private static final int WIDTH = 120;
    private static final int BTN_HEIGHT = 20;
    private static final float GAP = 2f;

    private StyledButton btnInvincible;

    public WndDebug(){
        super();

        IconTitle title = new IconTitle();
        title.label("Debug Panel");
        title.setRect(0, 0, WIDTH, 0);
        add(title);

        btnInvincible = new StyledButton(Chrome.Type.GREY_BUTTON_TR, invincibilityLabel()){
            @Override
            protected void onClick() {
                DebugConfig.godMode = !DebugConfig.godMode;
                text(invincibilityLabel());
            }
        };
        btnInvincible.setRect(0, title.bottom() + GAP, WIDTH, BTN_HEIGHT);
        add(btnInvincible);

        resize(WIDTH, (int)btnInvincible.bottom());
    }

    private String invincibilityLabel(){
        return "Invincibility: " + (DebugConfig.godMode ? "ON" : "OFF");
    }
}
