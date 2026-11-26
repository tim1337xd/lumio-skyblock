package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only what comes in from outside is a gain: crafting, compacting and moving the player's own items change nothing. */
class ItemFlowTest {
	private static final String ROUGH = "ROUGH_JADE_GEM";
	private static final String FLAWED = "FLAWED_JADE_GEM";
	private static final String FINE = "FINE_JADE_GEM";
	private static final String WHEAT = "WHEAT";
	private static final String ENCHANTED_BREAD = "ENCHANTED_BREAD";
	private static final String FISH = "RAW_FISH";
	private static final String ENCHANTED_FISH = "ENCHANTED_RAW_FISH";
	private static final long NOW = 1_000_000L;

	private final Map<String, Long> profit = new HashMap<>();
	private final ItemFlow flow = new ItemFlow(id -> switch (id) {
		case FLAWED -> List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(ROUGH, 80))));
		case FINE -> List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(FLAWED, 80))));
		case ENCHANTED_BREAD -> List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(WHEAT, 60))));
		case ENCHANTED_FISH -> List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(FISH, 160))));
		default -> List.of();
	}, new ItemFlow.Out() {
		@Override
		public void gained(String id, long count, boolean sacks) {
			profit.merge(id, count, Long::sum);
		}

		@Override
		public void taken(String id, long count) {
			profit.merge(id, -count, Long::sum);
		}
	});

	private long of(String id) {
		return profit.getOrDefault(id, 0L);
	}

	private static Map<String, Long> summary(Object... pairs) {
		Map<String, Long> changes = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			changes.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
		}
		return changes;
	}

	@Test
	void miningRoughGemsAddsThem() {
		flow.landed(ROUGH, 30, NOW);
		flow.sacks(summary(ROUGH, 50), NOW + 1_000);
		assertEquals(80, of(ROUGH));
	}

	@Test
	void crafting80RoughInto1FlawedChangesNothing() {
		flow.left(ROUGH, 80, NOW);
		flow.landed(FLAWED, 1, NOW);
		assertEquals(0, of(FLAWED));
		assertEquals(0, of(ROUGH));
	}

	@Test
	void minedThenCompactedCountsTheMinedGemsOnly() {
		flow.landed(ROUGH, 80, NOW);
		flow.left(ROUGH, 80, NOW + 500);
		flow.landed(FLAWED, 1, NOW + 500);
		assertEquals(80, of(ROUGH));
		assertEquals(0, of(FLAWED));
	}

	@Test
	void theCompactorsLastGemWasTheOneReallyMined() {
		flow.left(ROUGH, 79, NOW);
		flow.landed(FLAWED, 1, NOW);
		assertEquals(1, of(ROUGH));
		assertEquals(0, of(FLAWED));
	}

	@Test
	void sackCraftingIsNoGain() {
		flow.sacks(summary(FLAWED, 1, ROUGH, -80), NOW);
		assertEquals(0, of(FLAWED));
	}

	@Test
	void craftingTwoStepsAtOnceIsNoGain() {
		flow.left(ROUGH, 6_400, NOW);
		flow.landed(FINE, 1, NOW);
		assertEquals(0, of(FINE));
		assertEquals(0, of(ROUGH));
		assertEquals(0, of(FLAWED));
	}

	@Test
	void enchantedCropsAndFishFromOwnItemsAreNoGain() {
		flow.left(WHEAT, 60, NOW);
		flow.landed(ENCHANTED_BREAD, 1, NOW);
		flow.sacks(summary(ENCHANTED_FISH, 1, FISH, -160), NOW);
		assertEquals(0, of(ENCHANTED_BREAD));
		assertEquals(0, of(ENCHANTED_FISH));
	}

	@Test
	void takingACraftApartIsNoGain() {
		flow.left(FLAWED, 1, NOW);
		flow.landed(ROUGH, 80, NOW);
		assertEquals(0, of(ROUGH));
	}

	@Test
	void movingItemsToASackAddsNothing() {
		flow.left(ROUGH, 64, NOW);
		flow.sacks(summary(ROUGH, 64), NOW + 20_000);
		assertEquals(0, of(ROUGH));
	}

	@Test
	void takingItemsOutOfASackAddsNothing() {
		flow.landed(ROUGH, 64, NOW);
		assertEquals(64, of(ROUGH), "the summary is not there yet");
		flow.sacks(summary(ROUGH, -64), NOW + 20_000);
		assertEquals(0, of(ROUGH));
	}

	@Test
	void aCraftFromTheSacksSeenBeforeItsSummaryIsTakenBack() {
		flow.landed(FLAWED, 1, NOW);
		flow.sacks(summary(ROUGH, -80), NOW + 10_000);
		assertEquals(0, of(FLAWED));
	}

	@Test
	void aRealDropStaysAGain() {
		flow.left(ROUGH, 10, NOW);
		flow.landed(FLAWED, 1, NOW);
		assertEquals(1, of(FLAWED), "ten gems cannot have made it");
		assertTrue(of(ROUGH) == 0);
	}

	@Test
	void whatLeftLongAgoExplainsNothing() {
		flow.left(ROUGH, 80, NOW);
		flow.landed(FLAWED, 1, NOW + ItemFlow.WINDOW_MILLIS + 1);
		assertEquals(1, of(FLAWED));
	}
}
