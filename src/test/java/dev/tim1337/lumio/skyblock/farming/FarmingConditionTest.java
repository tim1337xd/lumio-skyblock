package dev.tim1337.lumio.skyblock.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When the player wants to farm: the one condition the Mouse lock and the Farming Keys share, and how the Garden and the Barn are told. */
class FarmingConditionTest {
	private static FarmingCondition.Input standing() {
		return new FarmingCondition.Input(true, true, true, false, false, false);
	}

	@Test
	void inTheGardenWithAToolNotFlyingIsFarming() {
		assertTrue(new FarmingCondition().tick(standing()));
	}

	@Test
	void anyOneThingMissingEndsIt() {
		FarmingCondition.Input[] missing = {
			new FarmingCondition.Input(false, true, true, false, false, false),
			new FarmingCondition.Input(true, false, true, false, false, false),
			new FarmingCondition.Input(true, true, true, true, false, false),
			new FarmingCondition.Input(true, true, true, false, true, false),
			new FarmingCondition.Input(true, true, true, false, false, true)};
		String[] names = {"not in the Garden", "no farming tool", "flying", "on the Barn plot", "a screen open"};
		for (int i = 0; i < missing.length; i++) {
			FarmingCondition condition = new FarmingCondition();
			condition.tick(standing());
			assertFalse(condition.tick(missing[i]), names[i] + ": at once");
			assertFalse(condition.farming(), names[i]);
		}
	}

	@Test
	void aJumpStillCountsAndALongFallDoesNot() {
		FarmingCondition condition = new FarmingCondition();
		condition.tick(standing());
		FarmingCondition.Input inAir = new FarmingCondition.Input(true, true, false, false, false, false);
		for (int tick = 1; tick <= FarmingCondition.AIR_GRACE_TICKS; tick++) {
			assertTrue(condition.tick(inAir), "tick " + tick + " of a step or a jump");
		}
		assertFalse(condition.tick(inAir), "longer than a jump: in the air");
		assertTrue(condition.tick(standing()), "landed");
	}

	@Test
	void flyingEndsItAtOnceEvenRightOffTheGround() {
		FarmingCondition condition = new FarmingCondition();
		condition.tick(standing());
		assertFalse(condition.tick(new FarmingCondition.Input(true, true, false, true, false, false)));
	}

	@Test
	void theGardenStaysTheGardenWhenPestsAreAlive() {
		// the island decides, whatever the scoreboard's place line says
		for (String place : new String[] {"⏣ Plot - 7", "⏣ Plot - 7 ൠ x2", "⏣ The Garden", "⏣ The Garden ൠ x3", "§7⏣ §aThe Garden §4§lൠ§7 x5", ""}) {
			assertTrue(FarmingCondition.inGarden("Garden", place, false), place);
			assertTrue(FarmingCondition.inGarden("The Garden", place, false), place);
		}
		// without the island the place line stands in, with or without pests on it
		assertTrue(FarmingCondition.inGarden("", "⏣ Plot - 7", false));
		assertTrue(FarmingCondition.inGarden("", "§7⏣ §aPlot §7- §b12 §4§lൠ§7 x1", false));
		assertTrue(FarmingCondition.inGarden("", "⏣ The Garden ൠ x3", false));
		// another island is another island, also with a line that is still the Garden's for a moment
		assertFalse(FarmingCondition.inGarden("Hub", "⏣ Plot - 7", true));
		assertFalse(FarmingCondition.inGarden("", "⏣ Village", true));
	}

	@Test
	void aPlaceThatCannotBeReadKeepsWhatWasKnown() {
		assertTrue(FarmingCondition.inGarden("", "", true));
		assertTrue(FarmingCondition.inGarden(null, null, true));
		assertTrue(FarmingCondition.inGarden("", "§7 ⏣ ", true), "only symbols and colors: nothing to read");
		assertFalse(FarmingCondition.inGarden("", "", false));
	}

	@Test
	void symbolsCountsAndColorsAreNoWords() {
		assertEquals("the garden  x", FarmingCondition.words("§7⏣ §aThe Garden §4§lൠ§7 x5"));
		assertEquals("plot -", FarmingCondition.words(" ⏣ Plot - 7"));
		assertEquals("", FarmingCondition.words("§4§lൠ 12"));
	}

	@Test
	void theBarnIsOnlyTheMiddlePlotNeverALineOfText() {
		assertTrue(FarmingCondition.barn(0, 0));
		assertTrue(FarmingCondition.barn(-47.5, 47.5));
		assertFalse(FarmingCondition.barn(48, 0), "the next plot starts here");
		assertFalse(FarmingCondition.barn(0, -48.5));
		assertFalse(FarmingCondition.barn(100, 100));
		// the case that switched both features off: pests alive, the player on a plot without pests, the scoreboard says "The Garden ൠ x3"
		FarmingCondition condition = new FarmingCondition();
		boolean inGarden = FarmingCondition.inGarden("Garden", "⏣ The Garden ൠ x3", true);
		assertTrue(condition.tick(new FarmingCondition.Input(inGarden, true, true, false, FarmingCondition.barn(120, -80), false)));
	}
}
