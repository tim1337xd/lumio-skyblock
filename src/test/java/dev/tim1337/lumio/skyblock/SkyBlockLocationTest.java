package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.features.misc.keybinds.CustomIslands;
import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The islands the SkyBlock add-on gives the keybinds: every island the add-on works with is found by the names the player list shows. */
class SkyBlockLocationTest {
	@Test
	void everyIslandTheAddonKnowsIsAPlace() {
		// the islands of the terrain keeping and the activities, by the names the player list shows
		for (String area : List.of("Hub", "Dwarven Mines", "Crystal Hollows", "Deep Caverns", "Gold Mine", "The Park", "Spider's Den", "The End",
			"Crimson Isle", "The Farming Islands", "Garden", "Dungeon Hub", "Jerry's Workshop", "The Rift", "Backwater Bayou", "Galatea",
			"Torrhus Canyon", "Safari", "Lotus Atoll", "Mineshaft", "Private Island", "Kuudra", "Catacombs")) {
			assertNotNull(CustomIslands.builtIn(area, SkyBlockLocation.PLACES), area);
		}
	}

	@Test
	void areasAreMatchedToTheirIsland() {
		assertEquals(SkyBlockIsland.DUNGEONS, SkyBlockIsland.ofArea("Catacombs"));
		assertEquals(SkyBlockIsland.GARDEN, SkyBlockIsland.ofArea("Garden"));
		assertEquals("dungeons", CustomIslands.builtIn("The Catacombs", SkyBlockLocation.PLACES).key());
	}
}
