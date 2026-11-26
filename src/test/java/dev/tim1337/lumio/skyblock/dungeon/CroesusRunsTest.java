package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * Croesus' run list: its pages, the floor and state of a run, and the runs kept per profile and saved: the profit and marks of a run stay with it
 * while Croesus lists it, also over a restart, and go when it is no longer listed.
 */
class CroesusRunsTest {
	private static final CroesusRuns.RunMarks NONE = CroesusRuns.RunMarks.NONE;
	/** The first slots of the first page's top row. */
	private static final int FIRST = 10;

	private String profile = "profile-a";
	private int saves;

	private CroesusRuns runs() {
		return new CroesusRuns(() -> profile, () -> saves++);
	}

	@Test
	void runListAndPage() {
		assertEquals(1, CroesusRuns.page("Croesus"));
		assertEquals(2, CroesusRuns.page("(2/3) Croesus"));
		assertEquals(0, CroesusRuns.page("The Catacombs - Floor VII"));
		assertEquals(0, CroesusRuns.index(1, 10));
		assertEquals(7, CroesusRuns.index(1, 19));
		assertEquals(28, CroesusRuns.index(2, 10));
		assertEquals(-1, CroesusRuns.index(1, 17), "the column after the seventh holds no run");
		assertEquals(-1, CroesusRuns.index(1, 49), "the bottom row holds the buttons");
	}

	@Test
	void floorAndState() {
		assertEquals("F5", CroesusRuns.floorOf("The Catacombs", List.of("Floor V", "", "No chests opened yet!")));
		assertEquals("M7", CroesusRuns.floorOf("Master Mode The Catacombs", List.of("Floor VII", "Opened Chest: Wood")));
		assertEquals(CroesusRuns.KUUDRA_FLOOR, CroesusRuns.floorOf("Kuudra's Hollow", List.of("Infernal Tier")));
		assertNull(CroesusRuns.floorOf("Go Back", List.of("To Croesus")));
		assertTrue(CroesusRuns.unopened(List.of("Floor V", "No chests opened yet!")));
		assertFalse(CroesusRuns.unopened(List.of("Floor V", "Opened Chest: Wood")));
	}

	@Test
	void openedOrNotFromTheRunsLore() {
		// recorded run items of Croesus' list, without colors
		List<String> notOpened = List.of("Floor VII", "", "Chests expire in 2d 4h", "", "No chests opened yet!", "", "Click to view chests!");
		List<String> opened = List.of("Floor VII", "", "Opened Chest: Obsidian", "", "Click to view chests!");
		List<String> claimed = List.of("Floor V", "", "Opened Chest: Bedrock", "No more chests to open!");
		List<String> silent = List.of("Floor VII", "", "Click to view chests!");
		assertTrue(CroesusRuns.notOpened(notOpened, null), "green highlight");
		assertFalse(CroesusRuns.notOpened(opened, null), "no highlight");
		assertFalse(CroesusRuns.notOpened(claimed, null));
		assertFalse(CroesusRuns.notOpened(opened, NONE), "the lore wins over what was kept");
		// the lore says neither: by the kept marks
		assertTrue(CroesusRuns.notOpened(silent, NONE));
		assertFalse(CroesusRuns.notOpened(silent, new CroesusRuns.RunMarks("Obsidian", false, null)));
		assertFalse(CroesusRuns.notOpened(silent, null), "nothing known: no highlight");
	}

	@Test
	void profitKeptForTheRunItWasOpenedFrom() {
		CroesusRuns runs = runs();
		runs.hover(1, FIRST, "F7", 1_000L);
		runs.opened(5_480_000.0, 2_000L);
		assertEquals(5_480_000.0, runs.profit(1, FIRST, "F7"));
		// another floor at that place now: the runs moved on
		assertTrue(Double.isNaN(runs.profit(1, FIRST, "M3")));
		// chests opened long after the hover belong to no run of the list
		runs.hover(1, FIRST + 1, "F6", 3_000L);
		runs.opened(100.0, 60_000L);
		assertTrue(Double.isNaN(runs.profit(1, FIRST + 1, "F6")));
	}

	@Test
	void marksSurviveARestart() {
		CroesusRuns runs = runs();
		runs.hover(1, FIRST, "M7", 1_000L);
		runs.opened(4_000_000.0, 1_500L);
		runs.note(new CroesusRuns.RunMarks("Obsidian", false, null));
		runs.note(new CroesusRuns.RunMarks(null, true, "Bedrock"));
		JsonObject saved = runs.save();

		CroesusRuns restarted = runs();
		restarted.load(saved);
		restarted.hover(1, FIRST, "M7", 10_000L);
		CroesusRuns.RunMarks marks = restarted.opened(4_000_000.0, 10_500L);
		assertEquals("Obsidian", marks.opened());
		assertTrue(marks.kismet());
		assertEquals("Bedrock", marks.kismetKind());
		assertEquals(4_000_000.0, restarted.profit(1, FIRST, "M7"));
	}

	@Test
	void marksArePerProfile() {
		CroesusRuns runs = runs();
		runs.hover(1, FIRST, "M7", 1_000L);
		runs.opened(1.0, 1_500L);
		runs.note(new CroesusRuns.RunMarks("Obsidian", false, null));
		profile = "profile-b";
		runs.hover(1, FIRST, "M7", 2_000L);
		assertNull(runs.opened(1.0, 2_500L).opened(), "the other profile's run has nothing of the first's");
	}

	@Test
	void aRunEndedMovesTheOthersBack() {
		CroesusRuns runs = runs();
		runs.sync(1, List.of(listed(0, "M7", "Obsidian"), listed(1, "F7", null)), true);
		runs.runEnded("M5");
		runs.note(new CroesusRuns.RunMarks("Bedrock", false, null));
		// the list now has the new run in front; the kept runs line up without a shift
		assertEquals(0, CroesusRuns.alignment(kept(runs), List.of(listed(0, "M5", "Bedrock"), listed(1, "M7", "Obsidian"), listed(2, "F7", null))));
	}

	@Test
	void runsThatEndedWithoutLumioAreLinedUp() {
		List<CroesusRuns.Run> kept = List.of(run("M7", "Obsidian"), run("M7", null));
		// two runs came in front that Lumio never saw: the kept ones are two places further back
		List<CroesusRuns.Listed> page = List.of(listed(0, "F6", null), listed(1, "M7", null), listed(2, "M7", "Obsidian"), listed(3, "M7", null));
		assertEquals(2, CroesusRuns.alignment(kept, page));
		// a kept run Croesus does not list (in front): one place forward
		assertEquals(-1, CroesusRuns.alignment(List.of(run("F1", null), run("M7", "Obsidian")), List.of(listed(0, "M7", "Obsidian"))));
		// nothing fits: start again
		assertEquals(Integer.MIN_VALUE, CroesusRuns.alignment(List.of(run("F1", "Wood")), List.of(listed(0, "M7", "Obsidian"))));
	}

	@Test
	void theGameSaysOtherwiseSoItIsAnotherRun() {
		assertFalse(CroesusRuns.fits(run("M7", "Obsidian"), listed(0, "M7", null)), "kept as opened, the game says unopened");
		assertFalse(CroesusRuns.fits(run("M7", "Obsidian"), listed(0, "M7", "Bedrock")));
		assertTrue(CroesusRuns.fits(run("M7", null), listed(0, "M7", "Bedrock")), "opened since, not seen by Lumio");
	}

	@Test
	void runsNoLongerListedAreDropped() {
		CroesusRuns runs = runs();
		runs.sync(1, List.of(listed(0, "M7", null), listed(1, "M7", null), listed(2, "F7", null)), true);
		runs.hover(1, FIRST + 2, "F7", 1_000L);
		runs.opened(9.0, 1_500L);
		// the last run expired: the list holds two now
		runs.sync(1, List.of(listed(0, "M7", null), listed(1, "M7", null)), true);
		assertEquals(2, kept(runs).size());
		assertTrue(Double.isNaN(runs.profit(1, FIRST + 2, "F7")));
	}

	@Test
	void malformedSavesAreLeftOut() {
		JsonObject json = new JsonObject();
		json.addProperty("profile-a", "not a list");
		CroesusRuns runs = runs();
		runs.load(json);
		runs.load(null);
		assertTrue(kept(runs).isEmpty());
	}

	private static CroesusRuns.Listed listed(int index, String floor, String opened) {
		return new CroesusRuns.Listed(index, floor, new CroesusRuns.RunMarks(opened, false, null));
	}

	private static CroesusRuns.Run run(String floor, String opened) {
		return new CroesusRuns.Run(floor, Double.NaN, new CroesusRuns.RunMarks(opened, false, null));
	}

	/** @return the runs kept for the profile now, read through the save */
	private List<CroesusRuns.Run> kept(CroesusRuns runs) {
		JsonObject saved = runs.save();
		List<CroesusRuns.Run> found = new ArrayList<>();
		if (saved.has(profile)) {
			for (var element : saved.getAsJsonArray(profile)) {
				JsonObject entry = element.getAsJsonObject();
				found.add(new CroesusRuns.Run(entry.has("floor") ? entry.get("floor").getAsString() : null, Double.NaN,
					new CroesusRuns.RunMarks(entry.has("opened") ? entry.get("opened").getAsString() : null, entry.has("kismet"), null)));
			}
		}
		return found;
	}
}
