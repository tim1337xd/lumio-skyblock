package dev.tim1337.lumio.skyblock.arrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The arrow display: infinite with the Skeleton Master Chestplate in the tick it is worn, and a steady count without guessed shots. */
class ArrowDisplayTest {
	@Test
	void theChestplateMakesThemInfiniteAtOnce() {
		assertTrue(InfiniteArrows.from("SKELETON_MASTER_CHESTPLATE"), "put on");
		assertFalse(InfiniteArrows.from(null), "taken off");
		assertFalse(InfiniteArrows.from("SKELETON_MASTER_LEGGINGS"));
		assertFalse(InfiniteArrows.from("WISE_WITHER_CHESTPLATE"));
	}

	@Test
	void itHoldsWithoutACondition() {
		assertTrue(InfiniteArrows.SOURCES.stream().allMatch(source -> source.when().getAsBoolean()));
	}

	@Test
	void shotsThatUseNoArrowKeepTheCountSteady() {
		ArrowSettle settle = new ArrowSettle();
		long tick = 0;
		assertEquals(1200, settle.update(1200, tick));
		for (int shot = 0; shot < 20; shot++) {
			assertEquals(1200, settle.update(1200, ++tick));
		}
	}

	@Test
	void aCorrectionRightAfterShowsNoJump() {
		ArrowSettle settle = new ArrowSettle();
		long tick = 0;
		settle.update(1200, tick);
		assertEquals(1200, settle.update(1199, ++tick));
		assertEquals(1200, settle.update(1200, ++tick), "back within a tick: never shown");
		for (int i = 0; i < ArrowSettle.SETTLE_TICKS + 1; i++) {
			assertEquals(1200, settle.update(1200, ++tick));
		}
		settle.update(1150, ++tick);
		for (int i = 0; i < ArrowSettle.SETTLE_TICKS; i++) {
			settle.update(1150, ++tick);
		}
		assertEquals(1150, settle.update(1150, ++tick), "a real change shows once it held");
	}
}
