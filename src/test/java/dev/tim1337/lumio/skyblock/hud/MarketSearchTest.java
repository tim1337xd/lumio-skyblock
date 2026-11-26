package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Which market an item opens in, and the name its search finds it by, from the names the item list really has. */
class MarketSearchTest {
	@Test
	void theBazaarWinsThenTheAuctionHouse() {
		assertEquals(MarketSearch.Market.BAZAAR, MarketSearch.of(true, false));
		assertEquals(MarketSearch.Market.BAZAAR, MarketSearch.of(true, true), "an item on both opens the Bazaar");
		assertEquals(MarketSearch.Market.AUCTION, MarketSearch.of(false, true));
		assertEquals(MarketSearch.Market.NONE, MarketSearch.of(false, false));
	}

	@Test
	void plainNames() {
		assertEquals("Hyperion", MarketSearch.searchName("§6Hyperion"));
		assertEquals("Enchanted Diamond", MarketSearch.searchName("§aEnchanted Diamond"));
		assertEquals("Hyperion", MarketSearch.searchName("§dHyperion §6✪✪✪✪✪§c➎"), "stars and master stars go");
		assertEquals("Shadow Assassin Chestplate", MarketSearch.searchName("§6 Shadow Assassin Chestplate"), "the starred icon goes");
		assertEquals("Bonzo's Mask", MarketSearch.searchName("§9Bonzo's Mask"));
		assertEquals("Saber-Tooth Tiger Skin", MarketSearch.searchName("§5Saber-Tooth Tiger Skin"));
	}

	@Test
	void petsRunesAndBooks() {
		assertEquals("Ender Dragon", MarketSearch.searchName("§7[Lvl {LVL}] §6Ender Dragon"));
		assertEquals("Golden Dragon", MarketSearch.searchName("§7[Lvl 200] §6Golden Dragon"));
		assertEquals("Music Rune I", MarketSearch.searchName("§b◆ Music Rune I"));
		List<String> wise = List.of("§8Combinable in Anvil", "", "§d§lUltimate Wise V", "§7Reduces the ability mana cost of");
		assertEquals("Ultimate Wise V", MarketSearch.searchName("§aEnchanted Book", wise), "a book by its enchantment");
		List<String> sharpness = List.of("§8Combinable in Anvil", "", "§9Sharpness VI", "§7Increases melee damage dealt by");
		assertEquals("Sharpness VI", MarketSearch.searchName("§9Enchanted Book", sharpness));
		assertEquals("Enchanted Book", MarketSearch.searchName("§9Enchanted Book", List.of("§7Nothing here")), "a book without its line stays as it is");
		assertEquals("Hyperion", MarketSearch.searchName("§6Hyperion", wise), "only books read their lore");
	}

	@Test
	void booksHaveTheirBazaarId() {
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", MarketSearch.bookBazaarId("ULTIMATE_WISE;5"));
		assertNull(MarketSearch.bookBazaarId("HYPERION"));
		assertNull(MarketSearch.bookBazaarId(null));
	}

	@Test
	void theCommands() {
		assertEquals("/bz Enchanted Diamond", MarketSearch.command(MarketSearch.Market.BAZAAR, "§aEnchanted Diamond"));
		assertEquals("/ahs Ender Dragon", MarketSearch.command(MarketSearch.Market.AUCTION, "§7[Lvl {LVL}] §6Ender Dragon"));
		assertEquals("", MarketSearch.command(MarketSearch.Market.NONE, "Hyperion"));
		assertEquals("", MarketSearch.command(MarketSearch.Market.AUCTION, "§6✪✪"), "no name, no command");
	}

	@Test
	void oneRequestIsOneCommand() {
		long[] now = {10_000};
		var sender = new dev.tim1337.lumio.skyblock.data.CommandSender(new dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue(() -> now[0], span -> 0,
			new dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue.Gap()));
		List<String> sent = new java.util.ArrayList<>();
		sender.send("/ahs Hyperion");
		sender.poll(sent::add);
		sender.poll(sent::add);
		now[0] += 60_000;
		sender.poll(sent::add);
		assertEquals(List.of("ahs Hyperion"), sent);
	}
}
