package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Which storage pages exist: from the Storage menu's items once it was open, else the pages seen; and per profile. */
class StoragePagesTest {
	/** A Storage menu as Hypixel shows it with 4 of 9 ender chest pages unlocked and backpack slots that are full, empty or locked. */
	private static Map<String, String> menu() {
		Map<String, String> names = new LinkedHashMap<>();
		for (int page = 1; page <= 9; page++) {
			names.put("e" + page, page <= 4 ? "Ender Chest Page " + page : "Locked Page");
		}
		names.put("b1", "Backpack Slot 1");
		names.put("b2", "Empty Backpack Slot 2");
		names.put("b3", "Backpack Slot 3");
		names.put("b4", "Backpack Slot 4");
		for (int slot = 5; slot <= 18; slot++) {
			names.put("b" + slot, "Locked Backpack Slot");
		}
		return names;
	}

	@Test
	void onlyUnlockedPagesAndFullSlots() {
		assertEquals(List.of("e1", "e2", "e3", "e4", "b1", "b3", "b4"), StorageStore.shownKeys(menu(), Set.of("e1", "e2", "b2")));
	}

	@Test
	void allNinePagesWhenAllAreUnlocked() {
		Map<String, String> names = new LinkedHashMap<>();
		for (int page = 1; page <= 9; page++) {
			names.put("e" + page, "Ender Chest Page " + page);
		}
		assertEquals(9, StorageStore.shownKeys(names, Set.of()).size());
	}

	@Test
	void beforeTheMenuOnlyThePagesSeen() {
		assertEquals(List.of("e2", "b10"), StorageStore.shownKeys(Map.of(), Set.of("b10", "e2")));
	}

	@Test
	void statesOfTheMenuItems() {
		assertEquals(StorageStore.MenuState.PAGE, StorageStore.stateOf("Ender Chest Page 3"));
		assertEquals(StorageStore.MenuState.LOCKED, StorageStore.stateOf("Locked Page"));
		assertEquals(StorageStore.MenuState.EMPTY, StorageStore.stateOf("Empty Backpack Slot 4"));
		assertEquals(StorageStore.MenuState.PAGE, StorageStore.stateOf("Backpack Slot 12"));
	}

	@Test
	void eachProfileKeepsItsOwnPages() {
		assertNotEquals(StorageStore.fileName("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"), StorageStore.fileName("1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"));
	}
}
