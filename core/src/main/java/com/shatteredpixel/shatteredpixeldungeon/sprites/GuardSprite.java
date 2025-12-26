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

package com.shatteredpixel.shatteredpixeldungeon.sprites;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.ShadowParticle;
import com.watabou.noosa.MovieClip;
import com.watabou.noosa.TextureFilm;

public class GuardSprite extends MobSprite {

	public GuardSprite() {
		super();

		texture( Assets.Sprites.GUARD );

		TextureFilm frames = new TextureFilm( texture, 12, 16 );

		idle = new Animation( 2, true );
		idle.frames( frames, 0, 0, 0, 1, 0, 0, 1, 1 );

		run = new MovieClip.Animation( 15, true );
		run.frames( frames, 2, 3, 4, 5, 6, 7 );

		attack = new MovieClip.Animation( 12, false );
		attack.frames( frames, 8, 9, 10 );

		die = new MovieClip.Animation( 8, false );
		die.frames( frames, 11, 12, 13, 14 );

		play( idle );

		// Custom hitbox: Guard sprite is 12x16 pixels
		// Use 10x13 hitbox (80% of sprite) centered for refined collision
		// Offset calculation: (12-10)/2 = 1, (16-13)/2 = 1.5
		setHitboxCustom(10f, 13f, 1f, 1.5f);
	}

	@Override
	public void play( Animation anim ) {
		if (anim == die) {
			emitter().burst( ShadowParticle.UP, 4 );
		}
		super.play( anim );
	}
}