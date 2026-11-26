package dev.tim1337.lumio.skyblock.profit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The line in chat after a corpse's loot: loot messages as Hypixel writes them (the form SkyHanni's corpse tracker reads), one per corpse kind, read by
 * the same parser as the corpse profit panel and valued with a fixed price table.
 */
class CorpseProfitTest {
	private static final String BAR = "§a§l" + "▬".repeat(64);
	/** Prices of one item, by name; the keys by id. Anything else has no price. */
	private static final Map<String, Double> BY_NAME = Map.of(
		"Fine Onyx Gemstone", 40_000.0, "Tungsten", 250.0, "Umber", 300.0, "Suspicious Scrap", 120_000.0, "Glacite", 90.0,
		"Enchanted Book (Ice Cold I)", 1_100_000.0);
	private static final Map<String, Double> KEYS = Map.of("TUNGSTEN_KEY", 250_000.0, "UMBER_KEY", 260_000.0, "SKELETON_KEY", 1_800_000.0);
	private static final CorpseProfit.Prices PRICES = new CorpseProfit.Prices() {
		@Override
		public double value(String id, String name, long count, SkyBlockPrices.Mode mode) {
			for (Map.Entry<String, Double> price : BY_NAME.entrySet()) {
				if (name.endsWith(price.getKey())) {
					return price.getValue() * count;
				}
			}
			return 0;
		}

		@Override
		public double price(String id, SkyBlockPrices.Mode mode) {
			return KEYS.getOrDefault(id, 0.0);
		}
	};
	private static final List<ItemGains.Corpse> OPENED = new ArrayList<>();

	@BeforeAll
	static void listen() {
		ItemGains.listenCorpses(OPENED::add);
	}

	/** @return the corpse read from a loot message, line by line as the chat brings it */
	private static ItemGains.Corpse open(String kindLine, String... items) {
		OPENED.clear();
		ItemGains.onMessage(Component.literal(BAR));
		ItemGains.onMessage(Component.literal(kindLine));
		ItemGains.onMessage(Component.literal(""));
		ItemGains.onMessage(Component.literal("  §r§a§lREWARDS"));
		for (String item : items) {
			ItemGains.onMessage(Component.literal(item));
		}
		ItemGains.onMessage(Component.literal(BAR));
		assertEquals(1, OPENED.size());
		return OPENED.getFirst();
	}

	@Test
	void lapisNeedsNoKeyAndProfits() {
		ItemGains.Corpse corpse = open("  §r§b§l§r§9§lLAPIS §r§b§lCORPSE LOOT!", "    §r§bGlacite Powder §r§8x1,450", "    §r§9☠ Fine Onyx Gemstone §r§8x2",
			"    §r§fEnchanted Book (Ice Cold I§r§f)");
		CorpseProfit.Result result = CorpseProfit.of(corpse, SkyBlockPrices.Mode.BUY_ORDER, PRICES);
		assertNotNull(result);
		// the powder counts in no value; no key
		assertEquals(2, result.lines().size());
		assertNull(result.keyName());
		assertEquals(80_000 + 1_100_000, result.total(), 0.01);
		String line = CorpseProfit.chatLine(result).getString();
		assertEquals("Corpse profit: +1.18M", line);
	}

	@Test
	void vanguardCostsItsSkeletonKeyAndLoses() {
		ItemGains.Corpse corpse = open("  §r§b§l§r§f§lVANGUARD §r§b§lCORPSE LOOT!", "    §r§bGlacite Powder §r§8x3,000", "    §r§fGlacite §r§8x200");
		CorpseProfit.Result result = CorpseProfit.of(corpse, SkyBlockPrices.Mode.BUY_ORDER, PRICES);
		assertNotNull(result);
		assertEquals("Skeleton Key", result.keyName());
		assertEquals(200 * 90 - 1_800_000, result.total(), 0.01);
		assertEquals("Corpse loss: −1.78M", CorpseProfit.chatLine(result).getString());
	}

	@Test
	void anItemWithoutAPriceCountsNothingAndSaysSo() {
		ItemGains.Corpse corpse = open("  §r§b§l§r§7§lTUNGSTEN §r§b§lCORPSE LOOT!", "    §r§fTungsten §r§8x1,200", "    §r§6Shattered Locket");
		CorpseProfit.Result result = CorpseProfit.of(corpse, SkyBlockPrices.Mode.BUY_ORDER, PRICES);
		assertNotNull(result);
		CorpseProfit.Line locket = result.lines().get(1);
		assertFalse(locket.priced());
		assertEquals(0, locket.value(), 0.0);
		assertEquals(1_200 * 250 - 250_000, result.total(), 0.01);
		assertTrue(CorpseProfit.breakdown(result).getString().contains("Shattered Locket  no price"));
		assertTrue(CorpseProfit.breakdown(result).getString().contains("Tungsten Key"));
	}

	@Test
	void umberAndAKeySavedByThePerk() {
		ItemGains.onMessage(Component.literal("LUCKY! Your Resourceful perk saved your key from being consumed!"));
		ItemGains.Corpse corpse = open("  §r§b§l§r§6§lUMBER §r§b§lCORPSE LOOT!", "    §r§fUmber §r§8x1,000", "    §r§5Suspicious Scrap §r§8x2");
		CorpseProfit.Result result = CorpseProfit.of(corpse, SkyBlockPrices.Mode.BUY_ORDER, PRICES);
		assertNotNull(result);
		// the key was kept, so it costs nothing
		assertNull(result.keyName());
		assertEquals(1_000 * 300 + 2 * 120_000, result.total(), 0.01);
	}

	@Test
	void aLineThatCannotBeReadSaysNothing() {
		ItemGains.Corpse corpse = open("  §r§b§l§r§7§lTUNGSTEN §r§b§lCORPSE LOOT!", "    §r§fTungsten §r§8x99999999999999999999");
		assertFalse(corpse.complete());
		assertNull(CorpseProfit.of(corpse, SkyBlockPrices.Mode.BUY_ORDER, PRICES));
	}
}
