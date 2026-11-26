package dev.tim1337.lumio.skyblock.dungeon.f7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The F7 boss's phases, sections, and the timers counted from them. */
class F7ProgressTest {
	@Test
	void phasesAndSections() {
		F7Progress boss = new F7Progress();
		boss.read("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", 0);
		assertEquals(F7Progress.Phase.MAXOR, boss.phase());
		boss.read("[BOSS] Storm: Pathetic Maxor, just like expected.", 1_000);
		assertEquals(F7Progress.Phase.STORM, boss.phase());
		boss.read("[BOSS] Storm: I should have known that I stood no chance.", 2_000);
		boss.read("[BOSS] Goldor: Who dares trespass into my domain?", 2_100);
		assertEquals(1, boss.section());
		boss.read("Kiwi activated a terminal! (1/7)", 2_200);
		boss.read("Ash activated a terminal! (2/7)", 2_210);
		boss.read("Rae completed a device! (3/7)", 2_220);
		boss.read("Nox activated a lever! (4/7)", 2_230);
		assertEquals(4, boss.done());
		assertEquals(7, boss.total());
		assertTrue(boss.device());
		for (int i = 5; i <= 7; i++) {
			boss.read("Kiwi activated a terminal! (" + i + "/7)", 2_240 + i);
		}
		// all done, but the gate still stands
		assertEquals(1, boss.section());
		boss.read("The gate has been destroyed!", 2_300);
		assertEquals(2, boss.section());
		assertEquals(0, boss.done());
		assertEquals(2_300, boss.sectionStart());
		assertEquals(1, boss.devices());
		boss.read("The Core entrance is opening!", 4_000);
		assertEquals(F7Progress.Phase.NECRON, boss.phase());
		assertEquals(0, boss.section());
		boss.reset();
		assertEquals(F7Progress.Phase.NONE, boss.phase());
	}

	@Test
	void timers() {
		F7Progress boss = new F7Progress();
		boss.read("[BOSS] Storm: Pathetic Maxor, just like expected.", 1_000);
		assertEquals(20, F7Timers.stormPad(boss, 1_000));
		assertEquals(15, F7Timers.stormPad(boss, 1_025));
		assertEquals(500, F7Timers.lightning(boss, 1_020));
		assertEquals(-1, F7Timers.lightning(boss, 1_600));
		boss.read("[BOSS] Storm: I should have known that I stood no chance.", 2_000);
		assertEquals(-1, F7Timers.stormPad(boss, 2_001));
		assertEquals(54, F7Timers.terminalsOpen(boss, 2_010));
		boss.read("[BOSS] Goldor: Who dares trespass into my domain?", 2_100);
		assertEquals(60, F7Timers.goldorDeath(boss, 2_100));
		assertEquals(1, F7Timers.goldorDeath(boss, 2_159));
		assertEquals(60, F7Timers.goldorDeath(boss, 2_160));
		boss.read("[BOSS] Necron: I'm afraid, your journey ends now.", 5_000);
		assertEquals(40, F7Timers.necronDrop(boss, 5_020));
		assertEquals(-1, F7Timers.necronDrop(boss, 5_060));
	}
}
