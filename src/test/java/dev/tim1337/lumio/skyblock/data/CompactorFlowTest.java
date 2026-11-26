package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Personal Compactor, a Super Compactor and a Personal Deletor at work, seen as the inventory scan sees them: every scan's changes go through the
 * drop-line hold ({@link AnnouncedDrops}) and the flow ({@link ItemFlow}) into the trackers, as in the game. Whatever the compactor does, the profit is
 * what was collected, never more; a deleted item leaves only the tracker that counted it.
 */
class CompactorFlowTest {
	private static final String RAW = "MITHRIL_ORE";
	private static final String ENCHANTED = "ENCHANTED_MITHRIL";
	private static final String BLOCK = "REFINED_MITHRIL";
	private static final String COBBLE = "COBBLESTONE";
	private static final long ENCHANTED_TAKES = 160;
	private static final long BLOCK_TAKES = 160;
	/** The game's inventory scan runs every other tick. */
	private static final long SCAN_MILLIS = 100L;
	private static final Map<String, Double> VALUE = Map.of(RAW, 1.0, ENCHANTED, 160.0, BLOCK, 25_600.0, COBBLE, 1.0);

	private long now = 1_000_000L;
	private final Map<String, Long> inventory = new HashMap<>();
	private Map<String, Long> before = new HashMap<>();
	private final Set<String> deletes = new HashSet<>();
	/** How far the Super Compactor goes: one step (raw to enchanted) or two (on to the block). */
	private int compactorSteps;

	private final Tracker mining = new Tracker();
	private final Tracker fishing = new Tracker();
	private Tracker active = mining;

	/** One profit tracker: its items, and what it counted lately. */
	private static final class Tracker {
		final Map<String, Long> items = new HashMap<>();
		final CountedGains lately = new CountedGains();

		double value() {
			double total = 0;
			for (Map.Entry<String, Long> item : items.entrySet()) {
				total += item.getValue() * VALUE.getOrDefault(item.getKey(), 0.0);
			}
			return total;
		}

		long of(String id) {
			return items.getOrDefault(id, 0L);
		}
	}

	private final ItemFlow flow = new ItemFlow(id -> switch (id) {
		case ENCHANTED -> List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(RAW, ENCHANTED_TAKES))));
		case BLOCK -> List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(ENCHANTED, BLOCK_TAKES))));
		default -> List.of();
	}, new ItemFlow.Out() {
		@Override
		public void gained(String id, long count, boolean sacks) {
			active.items.merge(id, count, Long::sum);
			active.lately.counted(id, count, now);
		}

		@Override
		public void taken(String id, long count) {
			for (Tracker tracker : List.of(mining, fishing)) {
				long counted = tracker.lately.takeBack(id, count, now);
				tracker.items.merge(id, -counted, Long::sum);
			}
		}
	});

	private final AnnouncedDrops drops = new AnnouncedDrops(new AnnouncedDrops.Out() {
		@Override
		public void arrived(String key, long count) {
			flow.landed(key, count, now);
		}

		@Override
		public void dropped(String key, String name, long count, boolean fromChat) {
			active.items.merge(key, count, Long::sum);
		}
	});

	/** Picks up items: a deleted one is gone at once, the compactor works right away, as Hypixel's do. */
	private void pickUp(String id, long count) {
		if (deletes.contains(id)) {
			return;
		}
		inventory.merge(id, count, Long::sum);
		compact();
	}

	private void compact() {
		if (compactorSteps >= 1) {
			step(RAW, ENCHANTED, ENCHANTED_TAKES);
		}
		if (compactorSteps >= 2) {
			step(ENCHANTED, BLOCK, BLOCK_TAKES);
		}
	}

	private void step(String from, String into, long takes) {
		long have = inventory.getOrDefault(from, 0L);
		long made = have / takes;
		if (made > 0) {
			inventory.put(from, have - made * takes);
			inventory.merge(into, made, Long::sum);
		}
	}

	/** One inventory scan, as {@code ItemGains.tick} does it. */
	private void scan() {
		now += SCAN_MILLIS;
		for (Map.Entry<String, Long> entry : before.entrySet()) {
			long lost = entry.getValue() - inventory.getOrDefault(entry.getKey(), 0L);
			if (lost > 0) {
				String key = entry.getKey();
				long at = now;
				boolean deleted = deletes.contains(key);
				drops.inOrder(deleted ? () -> flow.deleted(key, lost, at) : () -> flow.left(key, lost, at));
			}
		}
		for (Map.Entry<String, Long> entry : inventory.entrySet()) {
			long gained = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
			if (gained > 0) {
				drops.landed(entry.getKey(), gained, now);
			}
		}
		before = new HashMap<>(inventory);
		drops.tick(now);
	}

	/** Mines for a while: so many items per scan, for so many scans. */
	private void mine(String id, long perScan, int scans) {
		for (int i = 0; i < scans; i++) {
			pickUp(id, perScan);
			scan();
		}
	}

	/** Lets the hold pass, so everything has reached the trackers. */
	private void settle() {
		for (int i = 0; i < 40; i++) {
			scan();
		}
	}

	private void startWith(String id, long count) {
		inventory.put(id, count);
		before = new HashMap<>(inventory);
	}

	@Test
	void collectCompactCollectAgainCountsWhatWasCollected() {
		compactorSteps = 1;
		// slow: every item seen in the inventory before the compactor takes it
		mine(RAW, 2, 100);
		settle();
		assertEquals(200.0, mining.value(), "collect: 200 ore, one compaction among them");
		mine(RAW, 2, 100);
		settle();
		assertEquals(400.0, mining.value(), "collect again: 400 ore in all, never the enchanted form on top");
		assertEquals(0, mining.of(ENCHANTED), "the compacted items are not counted again");
	}

	@Test
	void fastPickupWithinTheDropHoldCountsEachItemOnce() {
		compactorSteps = 1;
		// 15 a scan: many items land and are compacted while the hold still keeps them back for a drop line
		mine(RAW, 15, 64);
		settle();
		assertEquals(960.0, mining.value());
		mine(RAW, 15, 64);
		settle();
		assertEquals(1920.0, mining.value());
		assertEquals(0, mining.of(ENCHANTED));
	}

	@Test
	void anItemCompactedTheMomentItLandsIsCountedAsTheItem() {
		compactorSteps = 1;
		startWith(RAW, 150);
		// the 160th ore is compacted before any scan sees it
		mine(RAW, 10, 1);
		settle();
		assertEquals(10.0, mining.value(), "only the ten picked up");
		assertEquals(0, mining.of(ENCHANTED));
	}

	@Test
	void theSuperCompactorsTwoStepsCountNothingTwice() {
		compactorSteps = 2;
		inventory.put(RAW, 100L);
		startWith(ENCHANTED, 159);
		// 60 ore make the 160th enchanted, which makes the block, all at once
		mine(RAW, 20, 3);
		settle();
		assertEquals(60.0, mining.value());
		assertEquals(0, mining.of(BLOCK));
	}

	@Test
	void compactedIntoTheSacksIsNoGain() {
		startWith(RAW, 159);
		// the 160th ore is picked up and compacted, and the enchanted one goes straight into the sacks: the inventory only loses its 159
		inventory.put(RAW, 0L);
		scan();
		now += 20_000L;
		long at = now;
		drops.inOrder(() -> flow.sacks(Map.of(ENCHANTED, 1L), at));
		settle();
		assertEquals(1.0, mining.value(), "the one ore picked up, not the enchanted one");
	}

	@Test
	void aDeletedItemThatWasNeverCountedTakesNothing() {
		deletes.add(COBBLE);
		mine(RAW, 4, 10);
		mine(COBBLE, 8, 10);
		settle();
		assertEquals(40.0, mining.value(), "deleted on pickup: never seen, never counted, nothing taken");
	}

	@Test
	void aDeletedItemThatWasCountedLeavesTheProfitAgain() {
		mine(COBBLE, 8, 10);
		settle();
		assertEquals(80, mining.of(COBBLE));
		// the deletor is set up for it afterwards and deletes the stack
		deletes.add(COBBLE);
		inventory.put(COBBLE, 0L);
		scan();
		settle();
		assertEquals(0, mining.of(COBBLE));
	}

	@Test
	void aTakeBackLeavesOnlyTheTrackerThatCountedIt() {
		mine(COBBLE, 8, 5);
		settle();
		// fishing counted the same item earlier, in another activity; the take-back is not its own
		fishing.items.put(COBBLE, 500L);
		active = fishing;
		deletes.add(COBBLE);
		inventory.put(COBBLE, 0L);
		scan();
		settle();
		assertEquals(0, mining.of(COBBLE));
		assertEquals(500, fishing.of(COBBLE));
	}
}
