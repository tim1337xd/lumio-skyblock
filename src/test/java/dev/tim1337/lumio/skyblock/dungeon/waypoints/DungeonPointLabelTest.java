package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** What a dungeon point's label says, by the Label setting, with the point's number from its place in its own room's list. */
class DungeonPointLabelTest {
	@Test
	void eachModeSaysItsPart() {
		assertEquals("3", DungeonPointLabel.text(DungeonPointLabel.Mode.NUMBER, "Chest", 3));
		assertEquals("Chest", DungeonPointLabel.text(DungeonPointLabel.Mode.TITLE, "Chest", 3));
		assertEquals("3 · Chest", DungeonPointLabel.text(DungeonPointLabel.Mode.BOTH, "Chest", 3));
		assertEquals("", DungeonPointLabel.text(DungeonPointLabel.Mode.OFF, "Chest", 3));
	}

	@Test
	void withoutATitleTheNumber() {
		assertEquals("4", DungeonPointLabel.text(DungeonPointLabel.Mode.TITLE, "", 4));
		assertEquals("4", DungeonPointLabel.text(DungeonPointLabel.Mode.BOTH, "  ", 4));
		assertEquals("4", DungeonPointLabel.text(DungeonPointLabel.Mode.TITLE, null, 4));
	}

	@Test
	void colorCodesAreNotShown() {
		assertEquals("2", DungeonPointLabel.text(DungeonPointLabel.Mode.TITLE, "§a2", 9));
		assertEquals("Bat", DungeonPointLabel.plain("§l§dBat "));
	}

	@Test
	void numberedPerRoom() {
		DungeonLabelLayer layer = new DungeonLabelLayer();
		java.util.List<DungeonWaypoint> temple = java.util.List.of(DungeonWaypoint.block(0, 70, 0, "", DungeonWaypoint.Type.SECRET),
			DungeonWaypoint.block(1, 70, 0, "Lever", DungeonWaypoint.Type.LEVER), DungeonWaypoint.block(2, 70, 0, "", DungeonWaypoint.Type.SECRET));
		layer.points(temple, new long[3], DungeonPointLabel.Mode.TITLE);
		assertEquals(java.util.List.of("1", "Lever", "3"), layer.texts());
		// another room starts at 1 again
		layer.points(temple.subList(2, 3), new long[1], DungeonPointLabel.Mode.TITLE);
		assertEquals(java.util.List.of("1"), layer.texts());
		// the setting changes what the same points say
		layer.points(temple.subList(2, 3), new long[1], DungeonPointLabel.Mode.BOTH);
		assertEquals(java.util.List.of("1"), layer.texts());
	}
}
