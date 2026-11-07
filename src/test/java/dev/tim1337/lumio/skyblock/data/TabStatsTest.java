package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.TabCard;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The tab list's Stats card holds only stats. Snapshots as the Dwarven Mines and the Glacite Tunnels write them: the mobs of the area (from its
 * player-model mobs and events) can follow the stats without a heading of their own, and must never stand under Stats.
 */
class TabStatsTest {
	private static Component bold(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withBold(true));
	}

	private static Component line(String text) {
		return Component.literal(text);
	}

	private static List<String> rows(TabCard card) {
		List<String> rows = new ArrayList<>();
		for (TabCard.Row row : card.rows()) {
			rows.add(row.value() == null ? row.label().getString() : row.label().getString() + " | " + row.value().getString());
		}
		return rows;
	}

	private static TabCard card(List<TabCard> cards, String title) {
		return cards.stream().filter(card -> title.equals(card.title())).findFirst().orElse(null);
	}

	@Test
	void mobsOfTheGlaciteTunnelsGoToOther() {
		List<TabCard> cards = TabInfoCards.read(List.of(
			bold("Stats:"), line(" Speed: ✦400"), line(" Strength: ❁312"), line(" Mining Speed: ⸕2,450"), line(" Mining Fortune: ☘780"),
			line(" Cold: ❄-12"), line(" Goblin"), line(" Ice Walker"), line(" Weakling 12/50"), line(" Glacite Walker: 3 kills"), Component.empty()), false);
		assertEquals(List.of("Speed | ✦400", "Strength | ❁312", "Mining Speed | ⸕2,450", "Mining Fortune | ☘780", "Cold | ❄-12"),
			rows(card(cards, "Stats")));
		// shown exactly as the server sent them, in their order
		assertEquals(List.of("Goblin", "Ice Walker", "Weakling 12/50", "Glacite Walker | 3 kills"), rows(card(cards, TabStats.OTHER)));
	}

	@Test
	void aGoblinRaidAndTheDwarvenMines() {
		List<TabCard> cards = TabInfoCards.read(List.of(
			bold("Commissions:"), line(" Goblin Slayer: 40%"), Component.empty(),
			bold("Stats:"), line(" Health: ❤ 1,320"), line(" Defense: ❈ 650"), line(" Goblin Raid"), line(" Weakling"), Component.empty(),
			bold("Powders:"), line(" Mithril: 1,234,567"), Component.empty()), false);
		assertEquals(List.of("Health | ❤ 1,320", "Defense | ❈ 650"), rows(card(cards, "Stats")));
		assertEquals(List.of("Goblin Raid", "Weakling"), rows(card(cards, TabStats.OTHER)));
		// other cards are as they were, also a commission named after a mob
		assertEquals(List.of("Goblin Slayer | 40%"), rows(card(cards, "Commissions")));
		assertEquals(List.of("Mithril | 1,234,567"), rows(card(cards, "Powders")));
	}

	@Test
	void statsOnlyStayAsTheyAre() {
		List<TabCard> read = TabInfoCards.read(List.of(bold("Stats:"), line(" Speed: ✦400"), line(" Farming Fortune: ☘100"),
			line(" Combat Wisdom: ☯12"), Component.empty()), false);
		assertEquals(1, read.size());
		assertEquals(3, read.getFirst().rows().size());
	}

	@Test
	void labelsAreReadWithoutTheirSymbols() {
		assertEquals("mining fortune", TabStats.plain("☘ Mining  Fortune"));
		assertTrue(TabStats.isStat(new TabCard.Row(line("North Stars"), line("12"), -1f)));
		assertFalse(TabStats.isStat(new TabCard.Row(line("Ice Walker"), null, -1f)));
		assertFalse(TabStats.isStat(new TabCard.Row(line("Goblin"), line("12/50"), -1f)));
	}
}
