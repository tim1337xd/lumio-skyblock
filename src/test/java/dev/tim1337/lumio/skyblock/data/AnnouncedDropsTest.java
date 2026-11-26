package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drops announced in chat count once, with or without their item, and what was crafted still counts nothing. The lines are those of the game logs. */
class AnnouncedDropsTest {
	private static final long NOW = 1_000_000L;
	private static final String WASP = "SHRIVELED_WASP";
	private static final String THUNDER = "SHARD_THUNDER";
	private static final String WHEAT = "WHEAT";
	private static final String ENCHANTED_BREAD = "ENCHANTED_BREAD";

	/** What reached the trackers as drops, and what went on as any other gain. */
	private final Map<String, Long> drops = new HashMap<>();
	private final Map<String, Long> fromChat = new HashMap<>();
	private final List<String> names = new ArrayList<>();
	private final Map<String, Long> gains = new HashMap<>();
	private final ItemFlow flow = new ItemFlow(id -> ENCHANTED_BREAD.equals(id) ? List.of(new ItemFlow.Way(1, List.of(new ItemFlow.Part(WHEAT, 60)))) : List.of(),
		new ItemFlow.Out() {
			@Override
			public void gained(String id, long count, boolean sacks) {
				gains.merge(id, count, Long::sum);
			}

			@Override
			public void taken(String id, long count) {
				gains.merge(id, -count, Long::sum);
			}
		});
	private final AnnouncedDrops announced = new AnnouncedDrops(new AnnouncedDrops.Out() {
		@Override
		public void arrived(String key, long count) {
			flow.landed(key, count, NOW);
		}

		@Override
		public void dropped(String key, String name, long count, boolean chat) {
			drops.merge(key, count, Long::sum);
			if (chat) {
				fromChat.merge(key, count, Long::sum);
			}
			names.add(name);
		}
	});

	@Test
	void aLootShareLinePlusTheArrivingItemCountsOnce() {
		DropMessage.LootShare share = DropMessage.lootShare("§e§lLOOT SHARE! §fYou received §b3 §9Thunder Shards §ffor assisting §bSomeone§f!");
		assertNotNull(share);
		assertEquals("Thunder Shards", share.name());
		assertEquals(3, share.amount());
		announced.announce(THUNDER, "Thunder Shard", share.amount(), NOW);
		announced.landed(THUNDER, 3, NOW + 200);
		announced.tick(NOW + AnnouncedDrops.ARRIVE_MILLIS + 1);
		assertEquals(3L, drops.get(THUNDER), "the line and its item are one drop");
		assertTrue(fromChat.isEmpty());
		assertTrue(gains.isEmpty(), "and not counted again as an ordinary gain");
		// the same loot reported by the sacks later is no new gain either
		assertEquals(0L, announced.intoSacks(THUNDER, 0));
	}

	@Test
	void aLootShareThatNamesNothingTakesWhatLandsRightAfter() {
		DropMessage.LootShare share = DropMessage.lootShare("LOOT SHARE You received loot for assisting Someone!");
		assertNotNull(share);
		assertFalse(share.named());
		announced.lootShare(NOW);
		announced.landed("ENCHANTED_SPIDER_EYE", 2, NOW + 500);
		assertEquals(2L, drops.get("ENCHANTED_SPIDER_EYE"));
		announced.landed("ROTTEN_FLESH", 5, NOW + AnnouncedDrops.SHARE_MILLIS + 1);
		announced.tick(NOW + AnnouncedDrops.SHARE_MILLIS + AnnouncedDrops.HOLD_MILLIS + 2);
		assertNull(drops.get("ROTTEN_FLESH"), "what comes later is no loot share");
		assertEquals(5L, gains.get("ROTTEN_FLESH"), "it is an ordinary gain");
	}

	@Test
	void anRngMeterDropCountsOnce() {
		// the meter's line names the drop and comes first; the drop's own line follows, then the item
		assertNull(DropMessage.parse("§d§lRNG METER! §r§aReselected the §6Shriveled Wasp §afor §cSpider Slayer§a! §e§lCLICK HERE §r§ato select a new drop!"),
			"the meter's line is no drop of its own");
		DropMessage drop = DropMessage.parse("CRAZY RARE DROP! (Shriveled Wasp)");
		assertNotNull(drop);
		assertEquals("Shriveled Wasp", drop.name());
		// the item lands a moment before its line
		announced.landed(WASP, 1, NOW);
		announced.announce(WASP, drop.name(), drop.amount(), NOW + 300);
		announced.tick(NOW + 10_000);
		assertEquals(1L, drops.get(WASP));
		assertTrue(gains.isEmpty());
		assertTrue(fromChat.isEmpty());
	}

	@Test
	void aDropAnnouncedWithAFullInventoryCountsFromChat() {
		announced.announce(WASP, "Shriveled Wasp", 1, NOW);
		announced.tick(NOW + AnnouncedDrops.ARRIVE_MILLIS - 1);
		assertTrue(drops.isEmpty(), "it waits for its item a moment");
		announced.tick(NOW + AnnouncedDrops.ARRIVE_MILLIS);
		assertEquals(1L, drops.get(WASP));
		assertEquals(1L, fromChat.get(WASP), "marked as counted from chat");
		// the stash's item reported by the sacks later counts nothing more
		assertEquals(0L, announced.intoSacks(WASP, 1));
		assertEquals(1L, drops.get(WASP));
	}

	@Test
	void craftingStillCountsNothing() {
		// 60 wheat left the inventory and an Enchanted Bread landed: no line announced it, so it goes on as before, and it was crafted
		flow.left(WHEAT, 60, NOW);
		announced.landed(ENCHANTED_BREAD, 1, NOW);
		announced.tick(NOW + AnnouncedDrops.HOLD_MILLIS);
		assertTrue(drops.isEmpty());
		assertEquals(0L, gains.getOrDefault(ENCHANTED_BREAD, 0L), "a craft is no gain");
	}

	@Test
	void aLineToldTwiceIsOneDrop() {
		announced.announce("NOT_JUST_A_PEST_VINYL", "Not Just a Pest Vinyl", 1, NOW);
		announced.announce("NOT_JUST_A_PEST_VINYL", "Not Just a Pest Vinyl", 1, NOW + 50);
		announced.tick(NOW + AnnouncedDrops.ARRIVE_MILLIS + 100);
		assertEquals(1L, drops.get("NOT_JUST_A_PEST_VINYL"));
	}

	@Test
	void theLinesOfTheLogs() {
		DropMessage plain = DropMessage.parse("RARE DROP! Enchanted String (+191 Magic Find)");
		assertEquals("Enchanted String", plain.name(), "the magic find without a percent sign");
		assertEquals(191, plain.magicFind());
		assertEquals("Enchanted Book", DropMessage.parse("RARE DROP! Enchanted Book (+507  Magic Find)").name());
		DropMessage chimera = DropMessage.parse("RARE DROP! Enchanted Book (Chimera I) (+507  Magic Find)");
		assertTrue(chimera.book());
		assertEquals("Chimera I", chimera.name());
		assertEquals("Toxic Arrow Poison", DropMessage.parse("RARE DROP! (64x Toxic Arrow Poison) (+354%  Magic Find)").name());
		assertEquals(64, DropMessage.parse("RARE DROP! (64x Toxic Arrow Poison) (+354%  Magic Find)").amount());
		assertEquals("Not Just a Pest Vinyl", DropMessage.parse("§6§lRARE DROP! §aNot Just a Pest Vinyl §e(§e+144.4)").name());
		assertEquals("Not Just a Pest Vinyl", DropMessage.parse("RARE DROP! Not Just a Pest Vinyl (Cocoaleech)").name());
		DropMessage wart = DropMessage.parse("§6§lRARE DROP! §9Mutant Nether Wart §8x9 §e(§e+80)");
		assertEquals("Mutant Nether Wart", wart.name());
		assertEquals(9, wart.amount());
		DropMessage moonflower = DropMessage.parse("RARE DROP! You dropped 24x Enchanted Moonflower!");
		assertEquals("Enchanted Moonflower", moonflower.name());
		assertEquals(24, moonflower.amount());
		assertEquals("Griffin Feather", DropMessage.parse("RARE DROP! You dug out a Griffin Feather!").name());
		DropMessage slug = DropMessage.parse("§6§lPET DROP! §5§lEPIC §5Slug §e(§e+164.4)");
		assertTrue(slug.pet());
		assertEquals("Slug", slug.name());
		assertEquals("Bite Rune I", DropMessage.parse("VERY RARE DROP! (◆ Bite Rune I) (+354%  Magic Find)").name());
		assertEquals(2, DropMessage.lootShare("LOOT SHARE You received 2 Alligator Shards for assisting Someone!").amount());
	}

	@Test
	void aDroppedFlashBookCountsOnceUnderItsEnchantmentWithItsPrice() {
		SkyBlockPrices.readBazaar(JsonParser.parseString("{\"products\":{\"ENCHANTMENT_FLASH_1\":{\"product_id\":\"ENCHANTMENT_FLASH_1\","
			+ "\"sell_summary\":[{\"amount\":1,\"pricePerUnit\":2500000.0,\"orders\":1}],\"buy_summary\":[{\"amount\":1,\"pricePerUnit\":2900000.0,\"orders\":1}],"
			+ "\"quick_status\":{\"sellPrice\":2500000.0,\"buyPrice\":2900000.0,\"sellMovingWeek\":10,\"buyMovingWeek\":10}}}}").getAsJsonObject());
		// the book that lands in the inventory is counted by its enchantment, as the line names it
		PriceKeys.Counted book = PriceKeys.counted("ENCHANTED_BOOK", JsonParser.parseString("{\"id\":\"ENCHANTED_BOOK\",\"enchantments\":{\"flash\":1}}").getAsJsonObject(),
			id -> null, id -> null);
		assertEquals("ENCHANTMENT_FLASH_1", book.key());
		assertEquals("Flash I", book.name(), "never \"Enchanted Book\"");
		DropMessage line = DropMessage.parse("RARE DROP! Enchanted Book (Flash I) (+300%  Magic Find)");
		assertEquals(book.key(), line.bookId(), "the line and the item are the same drop");
		announced.announce(line.bookId(), line.name(), line.amount(), NOW);
		announced.landed(book.key(), 1, NOW + 100);
		ProfitTracker tracker = new ProfitTracker();
		drops.forEach((key, count) -> tracker.addDrop(key, names.getFirst(), count, false));
		ProfitTracker.Entry entry = tracker.bucket(ProfitTracker.Range.SESSION).items.get("ENCHANTMENT_FLASH_1");
		assertEquals(1L, entry.count, "counted once");
		assertEquals("Flash I", entry.label());
		assertEquals(2_500_000d, entry.value(), 1e-6, "its price: the enchantment's Bazaar product");
		assertTrue(entry.announced, "always listed among the top drops");
	}

	@Test
	void shardLinesOfTheLogs() {
		DropMessage.ShardGain charmed = DropMessage.shards("§b§lCHARM! §7You charmed the §cFlaming Spider §7and received §a1 §aFlaming Spider Shard§7!");
		assertEquals(new DropMessage.ShardGain("Flaming Spider", 1), charmed);
		assertEquals(new DropMessage.ShardGain("Sphinx", 2),
			DropMessage.shards("§b§lCHARM! §7You charmed the §c§5Empyrean Sphinx §7and received §a2 §5Sphinx Shards§7!"), "the shard, not the mob");
		assertEquals(new DropMessage.ShardGain("Murkbat", 3), DropMessage.shards("§aYou caught §7x3 §9Murkbat §aShards§a!"));
		assertEquals(new DropMessage.ShardGain("Woodlouse", 1), DropMessage.shards("You caught x1 Woodlouse Shard!"));
		assertEquals(new DropMessage.ShardGain("Thunder", 3),
			DropMessage.shards("§e§lLOOT SHARE! §fYou received §b3 §9Thunder Shards §ffor assisting §bSomeone§f!"));
		assertNull(DropMessage.shards("LOOT SHARE You received loot for assisting Someone!"));
		assertNull(DropMessage.shards("VERY RARE DROP! (Primordial Shard) (+300% Magic Find)"), "a dungeon item that lands in the inventory");
		assertNull(DropMessage.shards("You bought Packrat Shard for 12k Coins!"));
		Map<String, String> known = PriceKeys.shardsByName(JsonParser.parseString(
			"{\"attributes\":[{\"bazaarName\":\"SHARD_PACKRAT_SKULL\",\"displayName\":\"Packrat\"},{\"bazaarName\":\"SHARD_NIGHT_SQUID\",\"displayName\":\"Inkling\"}]}").getAsJsonObject());
		assertEquals("SHARD_NIGHT_SQUID", PriceKeys.shardProduct("Inkling", known::get), "the item list's product where its name differs");
		assertEquals("SHARD_FLAMING_SPIDER", PriceKeys.shardProduct("Flaming Spider", known::get), "else as the Bazaar names most");
	}

	@Test
	void aDropWithoutAPriceIsStillListed() {
		ProfitTracker tracker = new ProfitTracker();
		for (int i = 0; i < 12; i++) {
			tracker.add("ITEM_" + i, "Item " + i, 100);
		}
		tracker.addDrop("ENCHANTMENT_NO_PRICE_EVER_1", "No Price Ever I", 1, true);
		List<ProfitTracker.Entry> top = tracker.top(ProfitTracker.Range.SESSION, 5);
		assertTrue(top.stream().anyMatch(entry -> entry.id.equals("ENCHANTMENT_NO_PRICE_EVER_1")), "worth 0, but not lost silently");
		assertEquals("No Price Ever I · from chat", tracker.bucket(ProfitTracker.Range.SESSION).items.get("ENCHANTMENT_NO_PRICE_EVER_1").label());
	}
}
