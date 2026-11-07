package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class FishingRodsTest {
	@Test
	void aRealRodCounts() {
		assertTrue(FishingRods.isRod("ROD_OF_THE_SEA", List.of("Increases fishing speed", "", "LEGENDARY FISHING ROD")));
		assertTrue(FishingRods.isRod("FISHING_ROD", List.of("COMMON FISHING ROD")));
		assertTrue(FishingRods.isRod("ELLES_LAVA_ROD", List.of("SPECIAL FISHING ROD", "")), "the category need not be the very last line");
		assertTrue(FishingRods.isRod("HELLFIRE_ROD", List.of("a MYTHIC FISHING ROD a")), "a recombobulated rod");
		assertTrue(FishingRods.isRod("SOME_ROD", List.of("EPIC DUNGEON FISHING ROD")));
	}

	@Test
	void theGrapplingHookNeverCounts() {
		assertFalse(FishingRods.isRod("GRAPPLING_HOOK", List.of("Travel around in style using this", "Grappling Hook.", "2 Second Cooldown", "", "UNCOMMON")));
		assertFalse(FishingRods.isRod("GRAPPLING_HOOK", List.of("UNCOMMON FISHING ROD")), "the list wins over the lore");
	}

	@Test
	void aWhipNeverCounts() {
		assertFalse(FishingRods.isRod("SOUL_WHIP", List.of("EPIC SWORD")));
		assertFalse(FishingRods.isRod("ZOMBIE_COMMANDER_WHIP", List.of("EPIC DUNGEON SWORD")));
		assertFalse(FishingRods.isRod("FLAMING_FLAY", List.of("LEGENDARY SWORD")));
	}

	@Test
	void textInTheLoreIsNoCategory() {
		assertFalse(FishingRods.isRod("SOME_ITEM", List.of("Use it like a LEGENDARY FISHING ROD to fish")));
		assertFalse(FishingRods.isRod("SOME_ITEM", List.of()));
		assertFalse(FishingRods.isRod(null, List.of("LEGENDARY FISHING ROD")), "not a SkyBlock item");
	}

	@Test
	void theIslandsInTheList() {
		for (String island : List.of("Hub", "Jerry's Workshop", "Crimson Isle", "Spider's Den", "Backwater Bayou", "Lotus Atoll")) {
			assertTrue(FishingRods.fishingIsland(island), island);
		}
	}

	@Test
	void theIslandsOutsideTheList() {
		for (String island : List.of("Dwarven Mines", "Private Island", "The Garden", "", "Somewhere new")) {
			assertFalse(FishingRods.fishingIsland(island), island);
		}
		assertFalse(FishingRods.fishingIsland(null));
	}
}
