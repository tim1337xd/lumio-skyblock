package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The profit trackers' store: a file per profile apart from the settings, written safely, with the last good state as a backup. */
class ProfitStoreTest {
	@TempDir
	Path folder;

	/** A stand-in tracker: one number, as a tracker's all-time total. */
	private static final class Counter implements ProfitStore.Part {
		long total;

		@Override
		public JsonObject save() {
			JsonObject json = new JsonObject();
			json.addProperty("total", total);
			return json;
		}

		@Override
		public void load(JsonObject json) {
			total = json == null ? 0 : json.get("total").getAsLong();
		}

		@Override
		public void merge(JsonObject json) {
			total += json.get("total").getAsLong();
		}
	}

	private ProfitStore store(Counter mining) {
		ProfitStore store = new ProfitStore(folder);
		store.add("mining_profit", mining);
		return store;
	}

	@Test
	void aFilePerProfile() {
		Counter mining = new Counter();
		ProfitStore store = store(mining);
		store.play("Mango");
		mining.total = 500;
		assertTrue(store.save());
		assertTrue(Files.isRegularFile(folder.resolve("mango.json")));
		// another profile starts at nothing and keeps its own
		store.play("Kiwi");
		assertEquals(0, mining.total);
		mining.total = 70;
		store.play("Mango");
		assertEquals(500, mining.total, "back on Mango: its values");
		store.play("Kiwi");
		assertEquals(70, mining.total);
		// a new game reads them again
		Counter again = new Counter();
		store(again).play("Mango");
		assertEquals(500, again.total);
	}

	@Test
	void whatWasCountedBeforeTheProfileWasKnownIsAdded() {
		Counter first = new Counter();
		ProfitStore store = store(first);
		store.play("Mango");
		first.total = 500;
		store.save();
		// the next game: something is counted before the server names the profile
		Counter mining = new Counter();
		ProfitStore next = store(mining);
		next.play(null);
		assertFalse(next.settled());
		mining.total = 30;
		next.save();
		assertTrue(Files.isRegularFile(folder.resolve("unknown.json")), "kept under no profile until one is known");
		next.play("Mango");
		assertTrue(next.settled());
		assertEquals(530, mining.total, "added to the profile's values, not in their place");
		assertFalse(Files.exists(folder.resolve("unknown.json")), "and gone from under no profile, so it is never added twice");
		Counter check = new Counter();
		store(check).play("Mango");
		assertEquals(530, check.total);
	}

	@Test
	void theSettingsFilesOldValuesAreTakenOnce() {
		// before the store: the tracker has its values from the settings file
		Counter mining = new Counter();
		mining.total = 9_000;
		ProfitStore store = store(mining);
		assertFalse(store.legacyTaken());
		store.play(null);
		assertFalse(store.settled(), "the settings file keeps them until a profile is known");
		store.play("Mango");
		assertTrue(store.settled());
		assertTrue(store.legacyTaken());
		assertEquals(9_000, mining.total);
		// an old settings file still holding them (a copy, a crash before it was saved) is not read again
		assertTrue(store(new Counter()).legacyTaken());
		Counter again = new Counter();
		store(again).play("Mango");
		assertEquals(9_000, again.total);
	}

	@Test
	void aBrokenFileFallsBackToTheLastGoodState() throws IOException {
		Counter mining = new Counter();
		ProfitStore store = store(mining);
		store.play("Mango");
		mining.total = 100;
		store.save();
		mining.total = 250;
		store.save();
		// the file before the last write is the backup
		assertEquals(100, ProfitStore.read(folder.resolve("mango.json.bak")).getAsJsonObject("mining_profit").get("total").getAsLong());
		// a crash left half a file: the backup is read, and a later write does not put the broken file over the backup
		Files.writeString(folder.resolve("mango.json"), "{\"mining_profit\": {\"tot");
		Counter after = new Counter();
		ProfitStore next = store(after);
		next.play("Mango");
		assertEquals(100, after.total, "the last good state");
		after.total = 130;
		next.save();
		assertEquals(100, ProfitStore.read(folder.resolve("mango.json.bak")).getAsJsonObject("mining_profit").get("total").getAsLong());
		Counter last = new Counter();
		store(last).play("Mango");
		assertEquals(130, last.total);
		// no temporary file is left behind
		try (var files = Files.list(folder)) {
			assertTrue(files.noneMatch(file -> file.getFileName().toString().endsWith(".tmp")));
		}
		assertNull(ProfitStore.read(folder.resolve("nobody.json")));
	}

	@Test
	void profileNamesMakeSafeFileNames() {
		assertEquals("mango", ProfitStore.fileName("Mango"));
		assertEquals("unknown", ProfitStore.fileName(null));
		assertEquals("unknown", ProfitStore.fileName("  "));
		assertEquals("etcpasswd", ProfitStore.fileName("../etc/passwd"));
		assertEquals(32, ProfitStore.fileName("a".repeat(80)).length());
	}

	@Test
	void trackersAddAndReplaceWhatTheyCounted() {
		ProfitTracker mango = new ProfitTracker();
		mango.addCoins(1_000);
		ProfitTracker early = new ProfitTracker();
		early.addCoins(250);
		// added: all time, and today as it is the same day
		mango.addSaved(early.toJson());
		assertEquals(1_250, mango.bucket(ProfitTracker.Range.LIFETIME).coins);
		assertEquals(1_250, mango.bucket(ProfitTracker.Range.TODAY).coins);
		assertEquals(1_000, mango.bucket(ProfitTracker.Range.SESSION).coins, "the session is the game's, it goes on");
		// another profile's values take their place; a profile without any starts at nothing
		ProfitTracker kiwi = new ProfitTracker();
		kiwi.addCoins(40);
		mango.replaceSaved(kiwi.toJson());
		assertEquals(40, mango.bucket(ProfitTracker.Range.LIFETIME).coins);
		mango.replaceSaved(null);
		assertEquals(0, mango.bucket(ProfitTracker.Range.LIFETIME).coins);
		assertEquals(0, mango.bucket(ProfitTracker.Range.TODAY).coins);
	}
}
