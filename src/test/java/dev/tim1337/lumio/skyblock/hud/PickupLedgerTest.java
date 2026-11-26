package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.tim1337.lumio.skyblock.data.SackMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The pickup log: gains and losses, adding up, what is ignored (data changes, menus, a new world), the sack summary and the hide list. */
class PickupLedgerTest {
	private static final long START = 100_000L;
	private static final Set<String> NONE = Set.of();

	/** A ledger that knows the inventory, past the world's grace time. */
	private static PickupLedger settled(Map<String, Long> inventory) {
		PickupLedger ledger = new PickupLedger();
		ledger.inventory(inventory, START, false, false, NONE);
		return ledger;
	}

	@Test
	void gainsAndLosses() {
		PickupLedger ledger = settled(Map.of("BOOSTER_COOKIE", 2L));
		ledger.inventory(Map.of("BOOSTER_COOKIE", 1L, "ENCHANTED_MITHRIL", 3L), START + 100, false, false, NONE);
		List<PickupLedger.Line> lines = ledger.lines();
		assertEquals(2, lines.size());
		assertEquals(-1, find(ledger, "BOOSTER_COOKIE").count);
		assertEquals(3, find(ledger, "ENCHANTED_MITHRIL").count);
		// an item gone altogether is a loss of all of it
		ledger.inventory(Map.of("BOOSTER_COOKIE", 1L), START + 10_000, false, false, NONE);
		assertEquals(-3, find(ledger, "ENCHANTED_MITHRIL").count);
	}

	@Test
	void changesWithinAFewSecondsAddUp() {
		PickupLedger ledger = settled(Map.of());
		ledger.inventory(Map.of("MITHRIL_ORE", 3L), START + 100, false, false, NONE);
		ledger.inventory(Map.of("MITHRIL_ORE", 8L), START + 1_100, false, false, NONE);
		assertEquals(1, ledger.lines().size());
		assertEquals(8, ledger.lines().getFirst().count);
		// later than the window: a new line
		ledger.inventory(Map.of("MITHRIL_ORE", 9L), START + 1_100 + PickupLedger.MERGE_MILLIS + 1, false, false, NONE);
		assertEquals(2, ledger.lines().size());
		// back to where it was within the window: the line goes
		ledger.inventory(Map.of("MITHRIL_ORE", 8L), START + 1_200 + PickupLedger.MERGE_MILLIS, false, false, NONE);
		assertEquals(1, ledger.lines().size());
	}

	@Test
	void dataChangesAndMovesAreNoChange() {
		// the same counts by id after a drill's fuel changed or an item moved to another slot
		PickupLedger ledger = settled(Map.of("DIVAN_DRILL", 1L, "ENCHANTED_DIAMOND", 64L));
		ledger.inventory(Map.of("ENCHANTED_DIAMOND", 64L, "DIVAN_DRILL", 1L), START + 500, false, false, NONE);
		assertTrue(ledger.lines().isEmpty());
	}

	@Test
	void notWhileAMenuIsOpenUnlessWanted() {
		PickupLedger ledger = settled(Map.of("ENCHANTED_DIAMOND", 10L));
		// moved into a chest
		ledger.inventory(Map.of("ENCHANTED_DIAMOND", 2L), START + 100, true, false, NONE);
		assertTrue(ledger.lines().isEmpty());
		// the menu closes and the cursor's item comes back: still nothing
		ledger.inventory(Map.of("ENCHANTED_DIAMOND", 3L), START + 200, false, false, NONE);
		assertTrue(ledger.lines().isEmpty());
		ledger.inventory(Map.of("ENCHANTED_DIAMOND", 5L), START + 200 + PickupLedger.MENU_GRACE_MILLIS + 1, false, false, NONE);
		assertEquals(2, find(ledger, "ENCHANTED_DIAMOND").count);
		// with "Also while a menu is open"
		PickupLedger also = settled(Map.of("ENCHANTED_DIAMOND", 10L));
		also.inventory(Map.of("ENCHANTED_DIAMOND", 2L), START + 100, true, true, NONE);
		assertEquals(-8, find(also, "ENCHANTED_DIAMOND").count);
	}

	@Test
	void theFirstSecondsInANewWorldAreIgnored() {
		PickupLedger ledger = settled(Map.of("ENCHANTED_DIAMOND", 10L));
		ledger.worldChanged(START + 1_000);
		// the server sends the inventory again, part by part
		ledger.inventory(Map.of(), START + 1_100, false, false, NONE);
		ledger.inventory(Map.of("ENCHANTED_DIAMOND", 10L), START + 2_000, false, false, NONE);
		ledger.purse(5_000, START + 2_000);
		assertTrue(ledger.lines().isEmpty());
		ledger.inventory(Map.of("ENCHANTED_DIAMOND", 11L), START + 1_000 + PickupLedger.WORLD_GRACE_MILLIS + 1, false, false, NONE);
		assertEquals(1, find(ledger, "ENCHANTED_DIAMOND").count);
		ledger.purse(17_340, START + 1_000 + PickupLedger.WORLD_GRACE_MILLIS + 2);
		assertEquals(12_340, find(ledger, PickupLedger.COINS).count);
	}

	@Test
	void linesFadeAndGo() {
		PickupLedger ledger = settled(Map.of());
		ledger.inventory(Map.of("MITHRIL_ORE", 1L), START + 100, false, false, NONE);
		PickupLedger.Line line = ledger.lines().getFirst();
		assertEquals(1f, PickupLedger.shown(line, START + 1_000, 5_000, 150), 1e-6);
		assertTrue(PickupLedger.shown(line, START + 100 + 5_200, 5_000, 150) < 1f);
		ledger.expire(START + 100 + 5_000 + PickupLedger.FADE_MILLIS + 1, 5_000);
		assertTrue(ledger.lines().isEmpty());
	}

	@Test
	void sackSummaryLines() {
		List<SackMessage.Entry> entries = SackMessage.entries(List.of("Added items:", "+1,284 Enchanted Mithril (Mining Sack)", "-3 Mithril (Mining Sack)",
			"This message can be disabled in the settings."));
		assertEquals(List.of(new SackMessage.Entry("Enchanted Mithril", 1_284), new SackMessage.Entry("Mithril", -3)), entries);
		assertTrue(SackMessage.is("[Sacks] +1,284 items. (Last 30s.)"));
		assertFalse(SackMessage.is("[NPC] Sacks: hello"));
		PickupLedger ledger = settled(Map.of());
		ledger.sack("ENCHANTED_MITHRIL", 1_284, START + 100);
		assertTrue(ledger.lines().getFirst().sack);
	}

	@Test
	void theHideList() {
		List<String> hide = List.of("COBBLESTONE", "*gemstone", "mithril");
		assertTrue(PickupLedger.hiddenBy(hide, "COBBLESTONE", "Cobblestone"));
		assertTrue(PickupLedger.hiddenBy(hide, "ROUGH_JADE_GEM", "Rough Jade Gemstone"));
		assertTrue(PickupLedger.hiddenBy(hide, "ENCHANTED_MITHRIL", "Enchanted Mithril"));
		assertFalse(PickupLedger.hiddenBy(hide, "BOOSTER_COOKIE", "Booster Cookie"));
		// hidden ids are not counted
		PickupLedger ledger = settled(Map.of());
		ledger.inventory(Map.of("COBBLESTONE", 64L), START + 100, false, false, Set.of("COBBLESTONE"));
		assertTrue(ledger.lines().isEmpty());
	}

	private static PickupLedger.Line find(PickupLedger ledger, String id) {
		PickupLedger.Line found = null;
		for (PickupLedger.Line line : ledger.lines()) {
			if (line.id.equals(id)) {
				found = line;
			}
		}
		return found;
	}
}
