package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.data.FishingRods;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Fishing is told from the catch itself, not from a list of islands: a catch on an island the mod does not know is counted, and so is a sea
 * creature the bundled list does not know, under the name its line gives.
 */
class UnknownCatchTest {
	private static final long JUST_NOW = 300L;

	@Test
	void aCatchOnAnUnknownIslandIsCounted() {
		// a creature's line is recognized by the line alone: nothing about where the player is goes into it
		for (SeaCreatures.Creature creature : SeaCreatures.all()) {
			assertEquals(creature, SeaCreatures.caughtIn(creature.message()), creature.name());
		}
		// and what counts as fishing knows no islands any more: a real rod, and the cast itself
		for (Method method : FishingRods.class.getDeclaredMethods()) {
			assertTrue(!method.getName().toLowerCase(Locale.ROOT).contains("island"), "no list of islands decides what fishing is: " + method.getName());
		}
	}

	@Test
	void anUnknownCreaturesLineIsCountedWithItsName() {
		assertEquals("Atoll Croaker", UnknownCatch.of("An inquisitive Atoll Croaker takes the bait!", true, JUST_NOW));
		assertEquals("Lotus Guardian", UnknownCatch.of("A Lotus Guardian emerges, ready to protect the Atoll.", true, JUST_NOW));
		assertEquals("Drowned Captain", UnknownCatch.of("A Drowned Captain takes hold of your bobber!", true, JUST_NOW));
		assertEquals("Sea Emperor", UnknownCatch.of("The Sea Emperor arises from the depths.", true, JUST_NOW));
		assertEquals("Scarecrow", UnknownCatch.of("Phew! It's only a Scarecrow.", true, JUST_NOW));
		assertEquals("Reef Lurker", UnknownCatch.of("Something stirs below, a Reef Lurker of a new island!", true, JUST_NOW));
	}

	@Test
	void aLineThatNamesNothingIsStillACatch() {
		assertEquals(UnknownCatch.NAMELESS, UnknownCatch.of("the water bubbles and something climbs out", true, JUST_NOW));
		assertNotNull(UnknownCatch.of("What even is that?! A... gorF?", true, JUST_NOW));
	}

	@Test
	void otherLinesWhileFishingAreNoCreature() {
		assertNull(UnknownCatch.of("An inquisitive Atoll Croaker takes the bait!", false, JUST_NOW), "not in the green of catch lines");
		assertNull(UnknownCatch.of("An inquisitive Atoll Croaker takes the bait!", true, 20_000L), "long after the bobber was out");
		assertNull(UnknownCatch.of("An inquisitive Atoll Croaker takes the bait!", true, -1L), "the bobber never was out");
		assertNull(UnknownCatch.of("GOOD CATCH! You found a Light Bait.", true, JUST_NOW), "treasure");
		assertNull(UnknownCatch.of("It's a Double Hook!", true, JUST_NOW));
		assertNull(UnknownCatch.of("You received 12 coins", true, JUST_NOW), "a line with a number");
		assertNull(UnknownCatch.of("Steve: nice catch", true, JUST_NOW), "what a player wrote has a colon after the name");
		assertNull(UnknownCatch.of("", true, JUST_NOW));
	}
}
