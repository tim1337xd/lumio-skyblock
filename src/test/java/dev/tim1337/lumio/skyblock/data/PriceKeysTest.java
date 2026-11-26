package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class PriceKeysTest {
	private static final Function<String, String> NO_SHARDS = id -> null;

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	/** A Bazaar answer in Hypixel's form, with the products named. */
	private static JsonObject bazaar(String... products) {
		StringBuilder text = new StringBuilder("{\"success\":true,\"products\":{");
		for (int i = 0; i < products.length; i++) {
			String id = products[i];
			text.append(i > 0 ? "," : "").append('"').append(id).append("\":{\"product_id\":\"").append(id).append("\",")
				.append("\"sell_summary\":[{\"amount\":4,\"pricePerUnit\":3415677.9,\"orders\":1}],")
				.append("\"buy_summary\":[{\"amount\":15,\"pricePerUnit\":5199995.3,\"orders\":1}],")
				.append("\"quick_status\":{\"productId\":\"").append(id).append("\",\"sellPrice\":3415677.2,\"buyPrice\":5199995.5,")
				.append("\"sellMovingWeek\":10817,\"buyMovingWeek\":16393}}");
		}
		return json(text.append("}}").toString());
	}

	@Test
	void aBazaarIdResolvesToBazaarPrices() {
		SkyBlockPrices.readBazaar(bazaar("MAGMA_LORD_FRAGMENT", "ENCHANTMENT_ULTIMATE_WISE_5", "ENCHANTMENT_SHARPNESS_6"));
		PriceKeys.Target target = PriceKeys.of("MAGMA_LORD_FRAGMENT", json("{\"id\":\"MAGMA_LORD_FRAGMENT\"}"), NO_SHARDS);
		assertEquals("MAGMA_LORD_FRAGMENT", target.id());
		assertTrue(SkyBlockPrices.onBazaar(target.id()), "looked up on the Bazaar first");
		assertEquals(5199995.3, SkyBlockPrices.bazaarBuy(target.id()), 1e-6, "instant buy: the lowest sell offer");
		assertEquals(3415677.9, SkyBlockPrices.bazaarSell(target.id()), 1e-6, "instant sell: the highest buy order");
		assertEquals(10817 + 16393, SkyBlockPrices.bazaarVolumeWeek(target.id()), 1e-6);
		assertEquals("MAGMA_LORD_FRAGMENT", SkyBlockPrices.priceIdOfListed("MAGMA_LORD_FRAGMENT"), "the item window and the pinned list ask the same");
	}

	@Test
	void aSingleEnchantBookResolvesToItsEnchantmentsProduct() {
		SkyBlockPrices.readBazaar(bazaar("ENCHANTMENT_ULTIMATE_WISE_5", "ENCHANTMENT_SHARPNESS_6"));
		PriceKeys.Target book = PriceKeys.of("ENCHANTED_BOOK", json("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"ultimate_wise\":5}}"), NO_SHARDS);
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", book.id(), "an ultimate enchantment by its own product");
		assertFalse(book.summed());
		assertTrue(SkyBlockPrices.onBazaar(book.id()));
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", SkyBlockPrices.priceIdOfListed("ULTIMATE_WISE;5"), "the item list's book is the same product");
	}

	@Test
	void aBookOfSeveralEnchantmentsIsTheirSum() {
		SkyBlockPrices.readBazaar(bazaar("ENCHANTMENT_ULTIMATE_WISE_5", "ENCHANTMENT_SHARPNESS_6"));
		PriceKeys.Target book = PriceKeys.of("ENCHANTED_BOOK", json("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"ultimate_wise\":5,\"sharpness\":6}}"), NO_SHARDS);
		assertTrue(book.summed());
		assertEquals(Set.of("ENCHANTMENT_ULTIMATE_WISE_5", "ENCHANTMENT_SHARPNESS_6"), Set.copyOf(book.sum()));
		assertEquals(2 * 5199995.3, SkyBlockPrices.bazaarSum(book.sum(), true), 1e-6);
		assertTrue(Double.isNaN(SkyBlockPrices.bazaarSum(List.of("ENCHANTMENT_SHARPNESS_6", "ENCHANTMENT_NOT_SOLD_1"), true)),
			"one enchantment without a price: no price for the book");
	}

	@Test
	void anUnknownIdGivesNoPriceAndNoError() {
		PriceKeys.Target target = PriceKeys.of("NO_SUCH_ITEM_EVER", json("{\"id\":\"NO_SUCH_ITEM_EVER\"}"), NO_SHARDS);
		assertEquals("NO_SUCH_ITEM_EVER", target.id());
		assertFalse(SkyBlockPrices.onBazaar(target.id()));
		assertFalse(SkyBlockPrices.onAMarket(target.id()));
		assertEquals(0d, SkyBlockPrices.bazaarBuy(target.id()));
		assertFalse(SkyBlockPrices.listedLowest(target.id()) > 0d);
		assertEquals("NO_SUCH_ITEM_EVER", SkyBlockPrices.priceIdOfListed("NO_SUCH_ITEM_EVER"));
		assertEquals(null, PriceKeys.of(null, null, NO_SHARDS).id(), "no id at all");
		assertEquals("ENCHANTED_BOOK", PriceKeys.of("ENCHANTED_BOOK", json("{\"enchantments\":\"broken\"}"), NO_SHARDS).id(), "broken data");
	}

	@Test
	void aPetByItsKindAndRarity() {
		PriceKeys.Target pet = PriceKeys.of("PET", json("{\"id\":\"PET\",\"petInfo\":\"{\\\"type\\\":\\\"ENDERMAN\\\",\\\"tier\\\":\\\"LEGENDARY\\\",\\\"exp\\\":0}\"}"), NO_SHARDS);
		assertEquals("PET_ENDERMAN", pet.id(), "SkyCofl's tag, asked with the level and rarity");
		assertEquals("ENDERMAN;4", pet.listId(), "the lowest BIN list's key");
		assertFalse(pet.ownAuction(), "never the cheapest of any rarity");
		assertTrue(pet.exact());
	}

	@Test
	void runesPotionsAndShards() {
		PriceKeys.Target rune = PriceKeys.of("RUNE", json("{\"id\":\"RUNE\",\"runes\":{\"ZAP\":3}}"), NO_SHARDS);
		assertEquals("RUNE_ZAP_3", rune.listId());
		assertFalse(rune.exact(), "SkyCofl does not know a rune by that id");
		assertEquals("RUNE_ZAP_3", SkyBlockPrices.priceIdOfListed("ZAP_RUNE;3"));
		assertEquals("POTION_magic_find", PriceKeys.of("POTION", json("{\"id\":\"POTION\",\"potion\":\"magic_find\",\"potion_level\":4}"), NO_SHARDS).id());
		Map<String, String> shards = PriceKeys.shardProducts(json("{\"attributes\":[{\"bazaarName\":\"SHARD_GROVE\",\"internalName\":\"ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1\"}]}"));
		assertEquals("SHARD_GROVE", PriceKeys.of("ATTRIBUTE_SHARD", json("{\"id\":\"ATTRIBUTE_SHARD\",\"attributes\":{\"nature_elemental\":1}}"), shards::get).id());
		assertEquals("SHARD_GROVE", PriceKeys.forListed("ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1", id -> false, shards::get));
	}

	@Test
	void anItemWithAnAppliedRuneIsTheItemNotTheRune() {
		// a Heroic Hyperion with 5 stars, gems, enchantments, scrolls, a kill counter and a rune applied: priced as the Hyperion, by its own data
		PriceKeys.Target hyperion = PriceKeys.of("HYPERION", json("{\"id\":\"HYPERION\",\"modifier\":\"heroic\",\"upgrade_level\":5,"
			+ "\"gems\":{\"COMBAT_0\":\"PERFECT\",\"SAPPHIRE_0\":\"FLAWLESS\"},\"enchantments\":{\"ultimate_wise\":5,\"sharpness\":7},"
			+ "\"ability_scroll\":[\"IMPLOSION_SCROLL\",\"SHADOW_WARP_SCROLL\",\"WITHER_SHIELD_SCROLL\"],\"stats_book\":12000,\"runes\":{\"BITE\":3}}"), NO_SHARDS);
		assertEquals("HYPERION", hyperion.id());
		assertEquals("HYPERION", hyperion.listId());
		assertTrue(hyperion.exact(), "its own numbers, by all it carries");
		assertTrue(hyperion.ownAuction());
		// the rune item itself is still priced as the rune
		assertEquals("RUNE_BITE_3", PriceKeys.of("RUNE", json("{\"id\":\"RUNE\",\"runes\":{\"BITE\":3}}"), NO_SHARDS).id());
		assertEquals("RUNE_BITE_3", PriceKeys.of("UNIQUE_RUNE", json("{\"id\":\"UNIQUE_RUNE\",\"runes\":{\"BITE\":3}}"), NO_SHARDS).id());
	}

	@Test
	void auctionItemsWithManyModifiersKeepTheirId() {
		assertEquals("NECRON_CHESTPLATE", PriceKeys.of("NECRON_CHESTPLATE",
			json("{\"id\":\"NECRON_CHESTPLATE\",\"upgrade_level\":10,\"dungeon_item_level\":5,\"modifier\":\"ancient\",\"runes\":{\"GRAND_SEARING\":3}}"), NO_SHARDS).id(),
			"a starred dungeon piece with a rune");
		assertEquals("ATOMSPLIT_KATANA", PriceKeys.of("ATOMSPLIT_KATANA", json("{\"id\":\"ATOMSPLIT_KATANA\",\"rarity_upgrades\":1,\"modifier\":\"fabled\"}"), NO_SHARDS).id(),
			"a recombobulated item");
		assertEquals("DIVAN_HELMET", PriceKeys.of("DIVAN_HELMET", json("{\"id\":\"DIVAN_HELMET\",\"skin\":\"DIVAN_HELMET_SKIN\",\"gems\":{\"AMBER_0\":\"FINE\"}}"), NO_SHARDS).id(),
			"an item with a skin");
		assertEquals("HYPERION", PriceKeys.of("HYPERION", json("{\"id\":\"HYPERION\",\"petInfo\":\"{\\\"type\\\":\\\"ENDERMAN\\\"}\"}"), NO_SHARDS).id(),
			"only a pet item is priced as a pet");
	}

	@Test
	void anItemWithModifiersKeepsItsId() {
		assertEquals("HYPERION", PriceKeys.of("HYPERION", json("{\"id\":\"HYPERION\",\"hot_potato_count\":10}"), NO_SHARDS).id());
		assertEquals("ENCHANTED_DIAMOND", PriceKeys.of("ENCHANTED_DIAMOND", json("{\"id\":\"ENCHANTED_DIAMOND\"}"), NO_SHARDS).id());
	}
}
