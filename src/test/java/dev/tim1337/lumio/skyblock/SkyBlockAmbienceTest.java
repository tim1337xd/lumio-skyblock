package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;

import org.junit.jupiter.api.Test;

/** The calendar, and what fits an island in a season. */
class SkyBlockAmbienceTest {
	private static final long START = 1559829300000L;
	private static final long SEASON = 31L * 60L * 60L * 1000L;

	@Test
	void theYearHasFourSeasonsInOrder() {
		assertEquals(SkyBlockAmbience.SPRING, SkyBlockAmbience.seasonAt(START));
		assertEquals(SkyBlockAmbience.SPRING, SkyBlockAmbience.seasonAt(START + SEASON - 1L));
		assertEquals(SkyBlockAmbience.SUMMER, SkyBlockAmbience.seasonAt(START + SEASON));
		assertEquals(SkyBlockAmbience.AUTUMN, SkyBlockAmbience.seasonAt(START + SEASON * 2L));
		assertEquals(SkyBlockAmbience.WINTER, SkyBlockAmbience.seasonAt(START + SEASON * 3L));
		assertEquals(SkyBlockAmbience.SPRING, SkyBlockAmbience.seasonAt(START + SEASON * 4L));
		assertEquals(SkyBlockAmbience.WINTER, SkyBlockAmbience.seasonAt(START - 1L));
	}

	@Test
	void theHubFollowsTheSeason() {
		assertTrue(SkyBlockAmbience.has("ambience:snow", SkyBlockIsland.HUB, SkyBlockAmbience.WINTER));
		assertFalse(SkyBlockAmbience.has("ambience:snow", SkyBlockIsland.HUB, SkyBlockAmbience.SUMMER));
		assertTrue(SkyBlockAmbience.has("ambience:fireflies", SkyBlockIsland.HUB, SkyBlockAmbience.SUMMER));
		assertFalse(SkyBlockAmbience.has("ambience:fireflies", SkyBlockIsland.HUB, SkyBlockAmbience.WINTER));
		assertTrue(SkyBlockAmbience.has("ambience:autumn", SkyBlockIsland.HUB, SkyBlockAmbience.AUTUMN));
	}

	@Test
	void islandsHaveTheirOwnMood() {
		assertTrue(SkyBlockAmbience.has("mood:ash", SkyBlockIsland.CRIMSON_ISLE, SkyBlockAmbience.SPRING));
		assertTrue(SkyBlockAmbience.has("mood:sparks", SkyBlockIsland.KUUDRA, SkyBlockAmbience.SPRING));
		assertTrue(SkyBlockAmbience.has("mood:dust", SkyBlockIsland.DWARVEN_MINES, SkyBlockAmbience.SPRING));
		assertTrue(SkyBlockAmbience.has("mood:webs", SkyBlockIsland.SPIDERS_DEN, SkyBlockAmbience.SPRING));
		assertTrue(SkyBlockAmbience.has("ambience:snow", SkyBlockIsland.JERRYS_WORKSHOP, SkyBlockAmbience.SUMMER));
		assertTrue(SkyBlockAmbience.has("ambience:no_leaves", SkyBlockIsland.DWARVEN_MINES, SkyBlockAmbience.SUMMER));
		assertFalse(SkyBlockAmbience.has("ambience:snow", SkyBlockIsland.CRIMSON_ISLE, SkyBlockAmbience.WINTER));
	}

	@Test
	void anUnknownIslandOrPropertyHasNothing() {
		assertFalse(SkyBlockAmbience.has("mood:ash", null, SkyBlockAmbience.SPRING));
		assertFalse(SkyBlockAmbience.has("something", SkyBlockIsland.HUB, SkyBlockAmbience.SPRING));
	}
}
