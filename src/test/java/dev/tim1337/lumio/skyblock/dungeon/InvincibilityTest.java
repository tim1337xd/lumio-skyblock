package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The masks' and the Phoenix's lines, and their invincibility and cooldown in server ticks. */
class InvincibilityTest {
	@Test
	void readsTheSaves() {
		assertEquals(Invincibility.Kind.BONZO, Invincibility.savedBy("Your Bonzo's Mask saved your life!"));
		assertEquals(Invincibility.Kind.BONZO, Invincibility.savedBy("Your ⚚ Bonzo's Mask saved your life!"));
		assertEquals(Invincibility.Kind.SPIRIT, Invincibility.savedBy("Second Wind Activated! Your Spirit Mask saved your life!"));
		assertEquals(Invincibility.Kind.PHOENIX, Invincibility.savedBy("Your Phoenix Pet saved you from certain death!"));
		assertNull(Invincibility.savedBy("Kiwi: Your Bonzo's Mask saved your life!"));
		assertEquals(162, Invincibility.loreCooldown("Cooldown: 162s"));
		assertEquals(-1, Invincibility.loreCooldown("Cooldown: soon"));
	}

	@Test
	void countsInServerTicks() {
		Invincibility timers = new Invincibility();
		assertEquals(Invincibility.State.READY, timers.state(Invincibility.Kind.SPIRIT, 1_000));
		timers.saved(Invincibility.Kind.SPIRIT, 1_000, -1);
		assertEquals(Invincibility.State.INVINCIBLE, timers.state(Invincibility.Kind.SPIRIT, 1_010));
		assertEquals(50, timers.ticksLeft(Invincibility.Kind.SPIRIT, 1_010));
		assertEquals(Invincibility.State.COOLDOWN, timers.state(Invincibility.Kind.SPIRIT, 1_060));
		assertEquals(540, timers.ticksLeft(Invincibility.Kind.SPIRIT, 1_060));
		assertEquals(Invincibility.State.READY, timers.state(Invincibility.Kind.SPIRIT, 1_600));
		timers.saved(Invincibility.Kind.BONZO, 2_000, 162);
		assertEquals(Invincibility.State.COOLDOWN, timers.state(Invincibility.Kind.BONZO, 2_000 + 162 * 20 - 1));
		assertEquals(Invincibility.State.READY, timers.state(Invincibility.Kind.BONZO, 2_000 + 162 * 20));
		assertEquals(1, timers.used(2_100));
		timers.reset();
		assertEquals(0, timers.used(2_100));
	}

	@Test
	void theIconIsTheMaskThePlayerOwns() {
		// the plain mask unless the starred one is known to be owned; the other one only if the item list lacks the first
		assertEquals(java.util.List.of("BONZO_MASK", "STARRED_BONZO_MASK"), Invincibility.iconIds(Invincibility.Kind.BONZO, false));
		assertEquals(java.util.List.of("STARRED_BONZO_MASK", "BONZO_MASK"), Invincibility.iconIds(Invincibility.Kind.BONZO, true));
		assertEquals("SPIRIT_MASK", Invincibility.iconIds(Invincibility.Kind.SPIRIT, false).getFirst());
		assertEquals("STARRED_SPIRIT_MASK", Invincibility.iconIds(Invincibility.Kind.SPIRIT, true).getFirst());
		assertEquals("STARRED_BONZO_MASK", Invincibility.starredId(Invincibility.Kind.BONZO));
	}

	@Test
	void thePhoenixIconIsThePetsHead() {
		// one entry per rarity in the item list: the highest it has is drawn
		java.util.List<String> ids = Invincibility.iconIds(Invincibility.Kind.PHOENIX, false);
		assertEquals("PHOENIX;9", ids.getFirst());
		assertEquals("PHOENIX;0", ids.getLast());
		assertEquals(ids, Invincibility.iconIds(Invincibility.Kind.PHOENIX, true));
		assertNull(Invincibility.starredId(Invincibility.Kind.PHOENIX));
	}

	@Test
	void theIconFitsItsBoxWithAnInset() {
		// a 16 px item in a 16 px box with 1.5 px on every side: 13 px, centered by the same numbers at every HUD size
		assertEquals(13f / 16f, Invincibility.iconScale(16, 1.5f, 16), 0.0001f);
		assertEquals(0f, Invincibility.iconScale(2, 1.5f, 16), 0.0001f);
	}
}
