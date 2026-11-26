package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The leap overlay's quadrants as Odin's menu places them, and the other orders. */
class LeapOrderTest {
	private static final DungeonPlayer MAGE = new DungeonPlayer("Kiwi", DungeonClass.MAGE, 40);
	private static final DungeonPlayer BERSERK = new DungeonPlayer("Rae", DungeonClass.BERSERK, 40);
	private static final DungeonPlayer TANK = new DungeonPlayer("Nox", DungeonClass.TANK, 40);
	private static final DungeonPlayer HEALER = new DungeonPlayer("Sol", DungeonClass.HEALER, 40);
	private static final DungeonPlayer ARCHER = new DungeonPlayer("Ace", DungeonClass.ARCHER, 40);

	@Test
	void oneToFourTeammatesKeepTheirPlaces() {
		// with fewer than four, each keeps its class's place and the others stay empty
		DungeonPlayer[] one = LeapOrder.place(List.of(BERSERK), LeapOrder.Sort.ODIN, null);
		assertEquals(BERSERK, one[1]);
		assertNull(one[0]);
		assertNull(one[2]);
		assertNull(one[3]);
		DungeonPlayer[] two = LeapOrder.place(List.of(HEALER, BERSERK), LeapOrder.Sort.ODIN, null);
		assertEquals(BERSERK, two[1]);
		assertEquals(HEALER, two[2]);
		assertNull(two[0]);
		assertNull(two[3]);
		DungeonPlayer[] three = LeapOrder.place(List.of(TANK, HEALER, BERSERK), LeapOrder.Sort.ODIN, null);
		assertEquals(BERSERK, three[1]);
		assertEquals(HEALER, three[2]);
		assertEquals(TANK, three[3]);
		assertNull(three[0]);
		DungeonPlayer[] four = LeapOrder.place(List.of(ARCHER, TANK, HEALER, BERSERK), LeapOrder.Sort.ODIN, null);
		assertEquals(4, java.util.Arrays.stream(four).filter(java.util.Objects::nonNull).count());
	}

	@Test
	void aDeadTeammateKeepsItsPlace() {
		DungeonPlayer dead = new DungeonPlayer("Sol", DungeonClass.HEALER, 40);
		dead.dead(true);
		DungeonPlayer[] placed = LeapOrder.place(List.of(BERSERK, dead), LeapOrder.Sort.ODIN, null);
		assertEquals(dead, placed[2], "greyed out in its place, not moved or left out");
		assertEquals(BERSERK, placed[1]);
	}

	@Test
	void odinsOrder() {
		// archer top left, berserk top right, healer bottom left; mage and tank both want bottom right, the tank ranks first
		DungeonPlayer[] placed = LeapOrder.place(List.of(MAGE, BERSERK, TANK, HEALER), LeapOrder.Sort.ODIN, null);
		assertEquals(MAGE, placed[0]);
		assertEquals(BERSERK, placed[1]);
		assertEquals(HEALER, placed[2]);
		assertEquals(TANK, placed[3]);
		DungeonPlayer[] withArcher = LeapOrder.place(List.of(ARCHER, BERSERK, HEALER, MAGE), LeapOrder.Sort.ODIN, null);
		assertEquals(ARCHER, withArcher[0]);
		assertEquals(BERSERK, withArcher[1]);
		assertEquals(HEALER, withArcher[2]);
		assertEquals(MAGE, withArcher[3]);
	}

	@Test
	void classAndNameOrders() {
		DungeonPlayer[] byClass = LeapOrder.place(List.of(MAGE, TANK, ARCHER), LeapOrder.Sort.CLASS, null);
		assertEquals(ARCHER, byClass[0]);
		assertEquals(MAGE, byClass[1]);
		assertEquals(TANK, byClass[2]);
		assertNull(byClass[3]);
		DungeonPlayer[] byName = LeapOrder.place(List.of(MAGE, TANK, ARCHER, HEALER), LeapOrder.Sort.NAME, null);
		assertEquals(ARCHER, byName[0]);
		assertEquals(MAGE, byName[1]);
		assertEquals(TANK, byName[2]);
		assertEquals(HEALER, byName[3]);
	}

	@Test
	void customOrder() {
		int[] quadrants = new int[DungeonClass.values().length];
		quadrants[DungeonClass.MAGE.ordinal()] = 3;
		quadrants[DungeonClass.TANK.ordinal()] = 0;
		quadrants[DungeonClass.BERSERK.ordinal()] = 1;
		quadrants[DungeonClass.HEALER.ordinal()] = 2;
		DungeonPlayer[] placed = LeapOrder.place(List.of(MAGE, TANK, BERSERK, HEALER), LeapOrder.Sort.CUSTOM, quadrants);
		assertEquals(TANK, placed[0]);
		assertEquals(BERSERK, placed[1]);
		assertEquals(HEALER, placed[2]);
		assertEquals(MAGE, placed[3]);
	}
}
