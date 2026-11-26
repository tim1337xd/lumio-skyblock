package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The Pets menu: its titles, and the pet that is out by its lore or, without one, by the player list's widget. Items as Hypixel shows them. */
class PetMenuTest {
	private static final List<String> RESTING = List.of("Combat Pet", "", "Strength: +42", "", "Progress to Level 75: 41.2%", "",
		"Left-click to summon!", "Right-click to convert to an item!");
	private static final List<String> OUT = List.of("Combat Pet", "", "Strength: +60", "", "MAX LEVEL", "", "Click to despawn!");

	@Test
	void titles() {
		assertTrue(PetMenu.isTitle("Pets"));
		assertTrue(PetMenu.isTitle("Pets (1/3)"));
		assertTrue(PetMenu.isTitle("(2/3) Pets"));
		assertTrue(PetMenu.isTitle("Pets: \"dragon\""));
		assertFalse(PetMenu.isTitle("Pet Sitter"));
		assertFalse(PetMenu.isTitle("SkyBlock Menu"));
	}

	@Test
	void thePetOutByItsLore() {
		PetMenu.Found found = PetMenu.find(List.of("", "[Lvl 74] Tiger", "[Lvl 100] Golden Dragon ✦"), List.of(List.of(), RESTING, OUT), null, 0);
		assertEquals(2, found.slot());
		assertEquals("lore", found.reason());
	}

	@Test
	void nothingWhenNoneIsOut() {
		assertEquals(-1, PetMenu.find(List.of("[Lvl 74] Tiger", "[Lvl 12] Bee"), List.of(RESTING, RESTING), null, 0).slot());
	}

	@Test
	void byThePlayerListWithoutTheLore() {
		List<String> names = List.of("[Lvl 74] Tiger", "⭐ [Lvl 100] [324✦] Golden Dragon ✦", "[Lvl 99] Golden Dragon");
		List<List<String>> lores = List.of(RESTING, RESTING, RESTING);
		PetMenu.Found found = PetMenu.find(names, lores, "Golden Dragon", 100);
		assertEquals(1, found.slot(), "the same name and level; skin marks and the favorite star do not count");
		assertEquals("tab list", found.reason());
		assertEquals(-1, PetMenu.find(names, lores, "Golden Dragon", 50).slot(), "another level is another pet");
	}
}
