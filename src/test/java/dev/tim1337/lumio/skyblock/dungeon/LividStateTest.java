package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.DyeColor;

import org.junit.jupiter.api.Test;

/** The real Livid: named by the arena's wool, nothing before that, its entity kept through data updates, and never hidden with the fakes. */
class LividStateTest {
	@Test
	void everyWoolNamesItsLivid() {
		assertEquals("Vendetta", LividState.nameFor(DyeColor.WHITE));
		assertEquals("Crossed", LividState.nameFor(DyeColor.MAGENTA));
		assertEquals("Arcade", LividState.nameFor(DyeColor.YELLOW));
		assertEquals("Smile", LividState.nameFor(DyeColor.LIME));
		assertEquals("Doctor", LividState.nameFor(DyeColor.GRAY));
		assertEquals("Purple", LividState.nameFor(DyeColor.PURPLE));
		assertEquals("Scream", LividState.nameFor(DyeColor.BLUE));
		assertEquals("Frog", LividState.nameFor(DyeColor.GREEN));
		assertEquals("Hockey", LividState.nameFor(DyeColor.RED));
		assertNull(LividState.nameFor(DyeColor.ORANGE), "any other color names none");
	}

	@Test
	void nothingBeforeTheWoolSaysIt() {
		LividState state = new LividState();
		assertFalse(state.known());
		assertEquals("", state.entityName(), "no default Livid: no label, no outline");
		state.wool(DyeColor.ORANGE);
		assertFalse(state.known());
		assertFalse(state.fake(5, "Hockey Livid"), "nothing hidden while the real one is unknown");
		state.wool(DyeColor.RED);
		assertEquals("Hockey Livid", state.entityName());
	}

	@Test
	void theReferenceSurvivesDataUpdates() {
		LividState state = new LividState();
		state.wool(DyeColor.RED);
		state.found(42);
		// a data update keeps the same entity with the same name: still kept, frame after frame
		for (int update = 0; update < 100; update++) {
			assertTrue(state.keeps(true, "Hockey Livid"));
		}
		assertEquals(42, state.realId());
		assertFalse(state.keeps(false, "Hockey Livid"), "removed (a respawn makes a new entity): looked for again");
		state.lost();
		state.found(77);
		assertEquals(77, state.realId());
	}

	@Test
	void hidingOthersNeverHidesTheRealOne() {
		LividState state = new LividState();
		state.wool(DyeColor.RED);
		state.found(42);
		assertFalse(state.fake(42, "Hockey Livid"));
		assertFalse(state.fake(43, "Hockey Livid"), "by name too, before its entity is kept again");
		assertTrue(state.fake(44, "Frog Livid"));
		assertTrue(state.fake(45, "Vendetta Livid"));
		assertFalse(state.fake(46, "Kiwi"), "a teammate is no Livid");
	}

	@Test
	void theWorldChanges() {
		LividState state = new LividState();
		state.wool(DyeColor.LIME);
		state.found(9);
		state.reset();
		assertFalse(state.known());
		assertEquals(-1, state.realId());
	}

	@Test
	void theTracerShowsOnlyWhileTheRealLividIsKnownAndAlive() {
		LividState state = new LividState();
		// the start of the fight: no color read yet, nothing to point at
		assertFalse(state.tracerActive(true, true, true, false));
		state.wool(DyeColor.RED);
		// the color is known, his entity not found yet
		assertFalse(state.tracerActive(true, true, false, false));
		state.found(42);
		assertTrue(state.tracerActive(true, true, true, false));
		// blind (the start phase), the switch off, he is dead, or the fight is over: nothing
		assertFalse(state.tracerActive(true, true, true, true));
		assertFalse(state.tracerActive(false, true, true, false));
		assertFalse(state.tracerActive(true, true, false, false));
		assertFalse(state.tracerActive(true, false, true, false));
		// his entity is gone
		state.lost();
		assertFalse(state.tracerActive(true, true, true, false));
		// the world changes
		state.found(42);
		state.reset();
		assertFalse(state.tracerActive(true, true, true, false));
	}

	@Test
	void theTracerTakesTheRealLividsColorOrTheOwnOne() {
		LividState state = new LividState();
		// not known yet: the own color
		assertEquals(0x123456, state.tracerRgb(0xFF123456, true));
		state.wool(DyeColor.RED);
		assertEquals(DyeColor.RED.getTextColor() & 0xFFFFFF, state.tracerRgb(0x123456, true));
		assertEquals(0x123456, state.tracerRgb(0x123456, false));
	}
}
