package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** With Coflnet's real answers of 2026-11-04 for these items: what it says for items like them, against what the id alone gives. */
class ExactPricesTest {
	private static final double EXACT = 0.5;
	private static final String PLAIN_HYPERION = "{\"id\":\"HYPERION\",\"uuid\":\"abc\",\"timestamp\":1700000000000}";
	private static final String UPGRADED = "{\"id\":\"HYPERION\",\"uuid\":\"abc\",\"timestamp\":1700000000000,\"modifier\":\"heroic\",\"rarity_upgrades\":1,\"hot_potato_count\":15,\"upgrade_level\":5,"
		+ "\"ability_scroll\":[\"IMPLOSION_SCROLL\",\"SHADOW_WARP_SCROLL\",\"WITHER_SHIELD_SCROLL\"],\"enchantments\":{\"sharpness\":6,\"ultimate_wise\":5}}";
	private static final String UPGRADED_FILTERS = "{\"sharpness\":\"6\",\"ultimate_wise\":\"5\",\"Recombobulated\":\"1\",\"Stars\":\"5\",\"AbilityScroll\":\"IMPLOSION_SCROLL SHADOW_WARP_SCROLL WITHER_SHIELD_SCROLL\"}";
	private static final String BIN_ID_ONLY = "{\"lowest\":466443000,\"uuid\":\"x\",\"secondLowest\":485000000}";
	private static final String BIN_LIKE_IT = "{\"lowest\":1049000000,\"uuid\":\"y\",\"secondLowest\":1049999999}";
	private static final String MEDIAN_LIKE_IT = "{\"min\":500000000,\"median\":1019000000,\"mean\":1016045180.1,\"mode\":1000000000,\"volume\":22.5,\"max\":1333333333}";
	private static final String NO_SUCH_ITEM = "{\"lowest\":0,\"uuid\":null,\"secondLowest\":0}";
	private static final String BREAKDOWN = "[{\"craftPrice\":[{\"price\":454000000,\"itemTag\":\"HYPERION\",\"attribute\":null,\"formattedReson\":\"Base Item price\",\"count\":1},"
		+ "{\"price\":876664,\"itemTag\":\"ENCHANTMENT_SHARPNESS_6\",\"attribute\":\"enchant_sharpness\",\"count\":1},{\"price\":820000,\"itemTag\":\"ENCHANTMENT_ULTIMATE_WISE_5\",\"attribute\":\"enchant_ultimate_wise\",\"count\":1},"
		+ "{\"price\":152999999,\"itemTag\":\"IMPLOSION_SCROLL\",\"attribute\":\"ability_scroll\",\"count\":1},{\"price\":454000000,\"itemTag\":\"HYPERION\",\"attribute\":\"id\",\"formattedReson\":\"Modifier id=Hyperion\",\"count\":1}]}]";
	private static final String PET = "{\"id\":\"PET\",\"petInfo\":\"{\\\"type\\\":\\\"GOLDEN_DRAGON\\\",\\\"exp\\\":210255385,\\\"tier\\\":\\\"LEGENDARY\\\"}\"}";

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void whatChangesTheValue() {
		assertFalse(ExactItem.relevant(json(PLAIN_HYPERION).keySet()), "a plain item is its id");
		assertTrue(ExactItem.relevant(json(UPGRADED).keySet()));
		assertTrue(ExactItem.relevant(json(PET).keySet()));
		assertFalse(ExactItem.relevant(Set.of("id")), "a material");
		assertTrue(ExactItem.relevant(Set.of("id", "gems")));
	}

	@Test
	void whatItIsPricedBy() {
		assertEquals("HYPERION", ExactItem.pricedId("HYPERION", json(UPGRADED)));
		assertEquals("PET_GOLDEN_DRAGON", ExactItem.pricedId("PET", json(PET)));
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", ExactItem.pricedId("ENCHANTED_BOOK", json("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"ultimate_wise\":5}}")));
		assertEquals("ENCHANTED_BOOK", ExactItem.pricedId("ENCHANTED_BOOK", json("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"ultimate_wise\":5,\"sharpness\":6}}")), "two enchants are one item");
		assertEquals("ENCHANTED_DIAMOND", ExactItem.pricedId("ENCHANTED_DIAMOND", json("{\"id\":\"ENCHANTED_DIAMOND\"}")));
	}

	@Test
	void onlyTheItemsOwnDataIsSent() {
		JsonObject item = ExactItem.request("HYPERION", "Heroic Hyperion", json(UPGRADED));
		assertEquals(Set.of("count", "tag", "itemName", "enchantments", "extraAttributes"), item.keySet());
		JsonObject extra = item.getAsJsonObject("extraAttributes");
		assertFalse(extra.has("uuid"), "which single item it is");
		assertFalse(extra.has("timestamp"));
		assertTrue(extra.has("hot_potato_count"));
		JsonObject signed = ExactItem.request("X", "X", json("{\"id\":\"X\",\"ownerName\":\"Steve123\",\"signed_by\":\"Steve123\",\"player_uuid\":\"abc\",\"gems\":{\"a\":1}}")).getAsJsonObject("extraAttributes");
		assertEquals(Set.of("id", "gems"), signed.keySet(), "nothing that could name a person");
	}

	@Test
	void theFiltersGoIntoTheAddress() {
		assertEquals("", ExactItem.query(json("{}"), "Hyperion", json(PLAIN_HYPERION)));
		assertEquals("?AbilityScroll=IMPLOSION_SCROLL%20SHADOW_WARP_SCROLL%20WITHER_SHIELD_SCROLL&Recombobulated=1&Stars=5&sharpness=6&ultimate_wise=5", ExactItem.query(json(UPGRADED_FILTERS), "Heroic Hyperion", json(UPGRADED)));
		assertEquals("", ExactItem.query(null, "Hyperion", json(PLAIN_HYPERION)));
	}

	@Test
	void aPetIsAskedForByLevelAndRarity() {
		assertEquals("?PetLevel=100&Rarity=LEGENDARY", ExactItem.query(json("{\"CostPerExpPlusBase\":\"210255385\"}"), "[Lvl 100] Golden Dragon", json(PET)));
	}

	@Test
	void theAnswers() {
		assertEquals(1_049_000_000d, ExactItem.coins(json(BIN_LIKE_IT), "lowest"), EXACT);
		assertEquals(1_019_000_000d, ExactItem.coins(json(MEDIAN_LIKE_IT), "median"), EXACT);
		assertTrue(Double.isNaN(ExactItem.coins(json(NO_SUCH_ITEM), "lowest")), "0 means there is none");
		assertTrue(Double.isNaN(ExactItem.coins(json("{\"lowest\":\"text\"}"), "lowest")));
		assertTrue(Double.isNaN(ExactItem.coins(json("{\"lowest\":1e30}"), "lowest")), "bounded");
		assertTrue(Double.isNaN(ExactItem.coins(null, "lowest")));
	}

	@Test
	void theCraftCostCountsTheItemOnce() {
		assertEquals(454_000_000d + 876_664d + 820_000d + 152_999_999d, ExactItem.craftCost(JsonParser.parseString(BREAKDOWN)), EXACT);
		assertTrue(Double.isNaN(ExactItem.craftCost(JsonParser.parseString("[]"))));
		assertTrue(Double.isNaN(ExactItem.craftCost(JsonParser.parseString("{}"))));
		assertTrue(Double.isNaN(ExactItem.craftCost(null)));
	}

	/** A world in which time and the background are in the test's hand. */
	private static final class Rig {
		long now = 1000L;
		final List<Runnable> waiting = new ArrayList<>();
		final List<String> asked = new ArrayList<>();
		boolean fails;
		final ExactPrices prices = new ExactPrices(url -> {
			asked.add(url);
			if (fails) {
				throw new IllegalStateException("429");
			}
			return JsonParser.parseString(url.contains("/bin") ? BIN_LIKE_IT : MEDIAN_LIKE_IT);
		}, (url, body) -> {
			asked.add(url);
			return JsonParser.parseString(url.endsWith("filters") ? UPGRADED_FILTERS : BREAKDOWN);
		}, waiting::add, () -> now);

		ExactPrices.Prices look(String key) {
			return prices.look("HYPERION", "Heroic Hyperion", json(UPGRADED), key, true);
		}

		void run() {
			List<Runnable> now = new ArrayList<>(waiting);
			waiting.clear();
			now.forEach(Runnable::run);
		}
	}

	@Test
	void onlyAfterAMomentOnTheItem() {
		Rig rig = new Rig();
		assertFalse(rig.look("a").answered());
		rig.now += 100L;
		rig.look("a");
		assertTrue(rig.waiting.isEmpty(), "passing over it asks for nothing");
		rig.now += 200L;
		rig.look("a");
		assertEquals(1, rig.waiting.size());
	}

	@Test
	void oneAtATimeAndKept() {
		Rig rig = new Rig();
		rig.look("a");
		rig.now += 300L;
		rig.look("a");
		rig.look("b");
		rig.now += 300L;
		rig.look("b");
		assertEquals(1, rig.waiting.size(), "one in flight: the other waits");
		rig.run();
		assertEquals(4, rig.asked.size(), "filters, lowest, median, craft");
		ExactPrices.Prices known = rig.look("a");
		assertEquals(1_049_000_000d, known.lowest(), EXACT);
		assertEquals(1_019_000_000d, known.median(), EXACT);
		rig.asked.clear();
		rig.now += 300L;
		rig.look("a");
		assertTrue(rig.waiting.isEmpty(), "kept: asked for once");
		rig.now += 30 * 60_000L;
		rig.look("a");
		assertEquals(1, rig.waiting.size(), "stale after some minutes");
	}

	@Test
	void aFailureShowsNothingAndPauses() {
		Rig rig = new Rig();
		rig.fails = true;
		rig.look("a");
		rig.now += 300L;
		rig.look("a");
		rig.run();
		assertFalse(rig.look("a").answered());
		rig.now += 1000L;
		rig.look("a");
		assertTrue(rig.waiting.isEmpty(), "no more asking for a while");
		rig.now += 60_000L;
		rig.look("a");
		assertEquals(1, rig.waiting.size());
	}

	@Test
	void theCraftLineOffAsksLess() {
		Rig rig = new Rig();
		rig.prices.look("HYPERION", "Heroic Hyperion", json(UPGRADED), "a", false);
		rig.now += 300L;
		rig.prices.look("HYPERION", "Heroic Hyperion", json(UPGRADED), "a", false);
		rig.run();
		assertEquals(3, rig.asked.size());
		assertTrue(Double.isNaN(rig.prices.look("HYPERION", "Heroic Hyperion", json(UPGRADED), "a", false).craft()));
	}

	@Test
	void theHoverDelayIsShort() {
		Rig rig = new Rig();
		rig.look("a");
		rig.now += 100L;
		rig.look("a");
		rig.run();
		assertEquals(0, rig.asked.size(), "not yet");
		rig.now += 60L;
		rig.look("a");
		rig.run();
		assertTrue(rig.asked.size() > 0, "after 150 ms");
	}

	@Test
	void pricesAreKeptHalfAnHourAndComeBackFromTheFile() {
		Rig rig = new Rig();
		rig.look("a");
		rig.now += 300L;
		rig.look("a");
		rig.run();
		ExactPrices.Prices known = rig.look("a");
		assertTrue(known.answered());
		JsonObject saved = ExactPricesFile.toJson(java.util.Map.of("a", known));
		ExactPrices.Prices back = ExactPricesFile.fromJson(JsonParser.parseString(saved.toString()).getAsJsonObject()).get("a");
		assertEquals(known.fetchedAt(), back.fetchedAt());
		assertEquals(known.lowest(), back.lowest());
		Rig later = new Rig();
		later.now = known.fetchedAt() + 29 * 60_000L;
		later.prices.adopt(java.util.Map.of("a", back));
		assertTrue(later.look("a").answered(), "still good");
		Rig tooLate = new Rig();
		tooLate.now = known.fetchedAt() + 31 * 60_000L;
		tooLate.prices.adopt(java.util.Map.of("a", back));
		assertTrue(!tooLate.look("a").answered(), "stale");
	}

	@Test
	void aBrokenFileLeavesOutWhatIsWrong() {
		var kept = ExactPricesFile.fromJson(JsonParser.parseString("{\"good\":[1.0,-1.0,-1.0,5],\"short\":[1.0],\"text\":\"x\",\"zero\":[1.0,1.0,1.0,0],\"bad\":[\"a\",\"b\",\"c\",\"d\"]}").getAsJsonObject());
		assertEquals(1, kept.size());
		assertTrue(Double.isNaN(kept.get("good").median()), "not known stays not known");
	}

	@Test
	void theInventoryIsAskedForSlowlyAndOnce() {
		Rig rig = new Rig();
		JsonObject data = new JsonObject();
		rig.prices.prefetch("TAG", "One", data, "one", false);
		rig.prices.prefetch("TAG", "One", data, "one", false);
		rig.prices.prefetch("TAG", "Two", data, "two", false);
		rig.prices.tick();
		rig.run();
		int afterOne = rig.asked.size();
		assertTrue(afterOne > 0);
		rig.now += 500L;
		rig.prices.tick();
		rig.run();
		assertEquals(afterOne, rig.asked.size(), "the next waits its turn");
		rig.now += 1500L;
		rig.prices.tick();
		rig.run();
		assertEquals(2 * afterOne, rig.asked.size(), "each once, no duplicate");
		rig.now += 5000L;
		rig.prices.tick();
		rig.run();
		assertEquals(2 * afterOne, rig.asked.size());
	}
}
