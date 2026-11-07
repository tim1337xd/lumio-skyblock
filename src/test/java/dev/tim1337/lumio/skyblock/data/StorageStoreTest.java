package dev.tim1337.lumio.skyblock.data;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The storage is kept per SkyBlock profile, in a file of its own: what the server says tells which, and a page comes back as it was saved. */
class StorageStoreTest {
	private static final String PROFILE_A = "11111111-2222-3333-4444-555555555555";
	private static final String PROFILE_B = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

	@Test
	void theProfileLine() {
		assertEquals(PROFILE_A, StorageStore.profileOf("Profile ID: " + PROFILE_A));
		assertEquals(PROFILE_B, StorageStore.profileOf("  Profile ID: AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE "));
	}

	@Test
	void nothingElseIsAProfile() {
		assertNull(StorageStore.profileOf("You are playing on profile: Peach (Co-op)"));
		assertNull(StorageStore.profileOf("Profile ID: not-an-id"));
		assertNull(StorageStore.profileOf("Profile ID: ../../evil"));
		assertNull(StorageStore.profileOf("Bob: Profile ID: " + PROFILE_A));
	}

	@Test
	void eachProfileHasItsFile() {
		assertEquals("storage-" + PROFILE_A + ".json", StorageStore.fileName(PROFILE_A));
		assertFalse(StorageStore.fileName(PROFILE_A).equals(StorageStore.fileName(PROFILE_B)));
		assertEquals("storage.json", StorageStore.fileName(null));
	}

	@Test
	void savedAndLoadedPerProfile(@TempDir Path folder) throws IOException {
		Map<String, StorageStore.Saved> first = new LinkedHashMap<>();
		first.put("e1", new StorageStore.Saved("Ender Chest", 6, 1000L, List.of("{a}", "{b}")));
		Map<String, StorageStore.Saved> second = new LinkedHashMap<>();
		second.put("b3", new StorageStore.Saved("Jumbo Backpack", 4, 2000L, List.of("{c}")));
		Path fileA = folder.resolve(StorageStore.fileName(PROFILE_A));
		Path fileB = folder.resolve(StorageStore.fileName(PROFILE_B));
		Files.writeString(fileA, new Gson().toJson(StorageStore.toJson(first, Map.of())));
		Files.writeString(fileB, new Gson().toJson(StorageStore.toJson(second, Map.of())));
		assertEquals(first, StorageStore.fromJson(JsonParser.parseString(Files.readString(fileA)).getAsJsonObject()));
		assertEquals(second, StorageStore.fromJson(JsonParser.parseString(Files.readString(fileB)).getAsJsonObject()));
	}

	@Test
	void aPageNeverSeenIsNotInTheFile() {
		Map<String, StorageStore.Saved> pages = StorageStore.fromJson(JsonParser.parseString("{\"e1\":{\"title\":\"Ender Chest\",\"rows\":6,\"seen\":5,\"items\":[]}}").getAsJsonObject());
		assertTrue(pages.containsKey("e1"));
		assertFalse(pages.containsKey("e2"));
	}

	@Test
	void aBrokenPageIsLeftOutAndTheRestKept() {
		Map<String, StorageStore.Saved> pages = StorageStore.fromJson(JsonParser.parseString("{\"e1\":5,\"e2\":{\"title\":\"Fine\",\"rows\":6,\"seen\":5}}").getAsJsonObject());
		assertFalse(pages.containsKey("e1"));
		assertEquals("Fine", pages.get("e2").title());
		assertEquals(List.of(), pages.get("e2").items());
	}

	@Test
	void theMenusItemsComeBackAndAreNoPage() {
		Map<String, StorageStore.Saved> pages = new LinkedHashMap<>();
		pages.put("e1", new StorageStore.Saved("Ender Chest", 6, 5L, List.of("{a}")));
		var json = StorageStore.toJson(pages, Map.of("e1", "{icon}", "b3", "{head}"));
		assertEquals(Map.of("e1", "{icon}", "b3", "{head}"), StorageStore.menuFromJson(json));
		assertEquals(java.util.Set.of("e1"), StorageStore.fromJson(json).keySet(), "the menu's items are not read as a page");
	}

	@Test
	void aFileFromBeforeTheMenuWasKeptHasNone() {
		var json = JsonParser.parseString("{\"e1\":{\"title\":\"Ender Chest\",\"rows\":6,\"seen\":5,\"items\":[]}}").getAsJsonObject();
		assertTrue(StorageStore.menuFromJson(json).isEmpty());
		assertEquals(1, StorageStore.fromJson(json).size());
	}

	@Test
	void aBrokenMenuPartIsLeftOut() {
		var json = JsonParser.parseString("{\"menu\":{\"e1\":[1,2],\"e2\":\"{ok}\"}}").getAsJsonObject();
		assertEquals(Map.of("e2", "{ok}"), StorageStore.menuFromJson(json));
	}

	@Test
	void aMenuItemIsToldByItsNameOrALockedOneByItsPlace() {
		assertEquals("e3", StorageStore.keyOfMenuSlot(11, "Ender Chest Page 3"));
		assertEquals("b4", StorageStore.keyOfMenuSlot(30, "Empty Backpack Slot 4"));
		assertEquals("e5", StorageStore.keyOfMenuSlot(13, "Locked Page"));
		assertEquals("b2", StorageStore.keyOfMenuSlot(28, "Locked Backpack"));
		assertNull(StorageStore.keyOfMenuSlot(22, "Locked Page"), "not a page's place");
		assertNull(StorageStore.keyOfMenuSlot(13, "Close"));
	}
}
