package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scoreboard's line under the slayer boss, in every format it comes in, and what the panel makes of it. */
class SlayerProgressTest {
	@Test
	void kills() {
		SlayerProgress progress = SlayerProgress.of("5/18 Kills");
		assertEquals(SlayerProgress.State.KILLS, progress.state());
		assertEquals(5L, progress.done());
		assertEquals(18L, progress.needed());
		assertEquals("Boss in 13 kills", progress.text());
		assertEquals("Boss in 1 kill", SlayerProgress.of("17/18 Kills").text());
	}

	@Test
	void killsWithColorCodesAndOddSpaces() {
		assertEquals("Boss in 13 kills", SlayerProgress.of(" §e5§7/§c18 §7Kills").text());
		assertEquals("Boss in 13 kills", SlayerProgress.of("5/18 Kills").text());
		assertEquals("Boss in 13 kills", SlayerProgress.of("5 / 18  Kills ").text());
	}

	@Test
	void combatXp() {
		SlayerProgress progress = SlayerProgress.of("(1,840/2,400) Combat XP");
		assertEquals(SlayerProgress.State.XP, progress.state());
		assertEquals(1_840L, progress.done());
		assertEquals(2_400L, progress.needed());
		assertEquals("Boss in 560 XP", progress.text());
		assertEquals("Boss in 800 XP", SlayerProgress.of("(1,600/2,400) Combat XP").text());
	}

	@Test
	void combatXpWithK() {
		assertEquals("Boss in 1.2k XP", SlayerProgress.of("1.2k/2.4k Combat XP").text());
		assertEquals("Boss in 1.2k XP", SlayerProgress.of("(1.2k/2.4k) Combat XP").text());
		assertEquals("Boss in 22k XP", SlayerProgress.of("(0/22k) Combat XP").text());
		assertEquals("Boss in 1.5k XP", SlayerProgress.of("(900/2.4k) Combat XP").text());
	}

	@Test
	void theBossStates() {
		assertEquals(SlayerProgress.State.BOSS_UP, SlayerProgress.of("Slay the boss!").state());
		assertEquals("Slay the boss!", SlayerProgress.of("§eSlay the boss!").text());
		assertEquals(SlayerProgress.State.BOSS_SLAIN, SlayerProgress.of("Boss slain!").state());
		assertEquals("Boss slain!", SlayerProgress.of("§aBoss slain!").text());
		assertFalse(SlayerProgress.of("Slay the boss!").counting());
	}

	@Test
	void aMissingLineSaysNothing() {
		assertEquals(SlayerProgress.State.NONE, SlayerProgress.of(null).state());
		assertEquals(SlayerProgress.State.NONE, SlayerProgress.of("").state());
		assertEquals(SlayerProgress.State.NONE, SlayerProgress.of(" §7 ").state());
		assertEquals("", SlayerProgress.of("").text());
	}

	@Test
	void anUnknownLineIsToldApartAndSaysNothing() {
		assertEquals(SlayerProgress.State.UNKNOWN, SlayerProgress.of("Hurry up!").state());
		assertEquals("", SlayerProgress.of("Hurry up!").text());
		assertEquals(SlayerProgress.State.UNKNOWN, SlayerProgress.of("5/0 Kills").state());
	}

	@Test
	void aBarShare() {
		assertEquals(0.5f, SlayerProgress.of("9/18 Kills").share(), 0.001f);
		assertTrue(SlayerProgress.of("20/18 Kills").share() <= 1f, "never past its end");
	}
}
