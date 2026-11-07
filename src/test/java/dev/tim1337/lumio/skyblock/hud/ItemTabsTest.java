package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.data.ItemRepo;

import org.junit.jupiter.api.Test;

import java.util.List;

class ItemTabsTest {
	private static ItemRepo.Recipe recipe(String type) {
		return new ItemRepo.Recipe(type, List.of(), List.of(new ItemRepo.Stack("X", 1)), "Here", "", "");
	}

	private static List<ItemTabs.Kind> kinds(List<ItemTabs.Tab> tabs) {
		return tabs.stream().map(ItemTabs.Tab::kind).toList();
	}

	@Test
	void anItemHasWhatIsKnownOfIt() {
		List<ItemTabs.Tab> tabs = ItemTabs.of(List.of(recipe("crafting"), recipe("crafting"), recipe("forge"), recipe("npc_shop"), recipe("trade"), recipe("katgrade"), recipe("drops")), 3, List.of());
		assertEquals(List.of(ItemTabs.Kind.RECIPE, ItemTabs.Kind.FORGE, ItemTabs.Kind.SHOP, ItemTabs.Kind.TRADE, ItemTabs.Kind.KAT, ItemTabs.Kind.USED_IN, ItemTabs.Kind.DROPPED_BY), kinds(tabs));
		assertEquals(2, tabs.getFirst().recipes().size());
	}

	@Test
	void aTabWithoutDataIsLeftOut() {
		assertTrue(ItemTabs.of(List.of(), 0, List.of()).isEmpty());
		assertEquals(List.of(ItemTabs.Kind.DROPPED_BY), kinds(ItemTabs.of(List.of(recipe("drops")), 0, List.of())));
		assertEquals(List.of(ItemTabs.Kind.USED_IN), kinds(ItemTabs.of(List.of(), 1, List.of())));
	}

	@Test
	void aMobShowsItsDropsAndAnNpcItsShopFirst() {
		assertEquals(List.of(ItemTabs.Kind.DROPS), kinds(ItemTabs.of(List.of(), 0, List.of(recipe("drops")))));
		assertEquals(List.of(ItemTabs.Kind.SELLS), kinds(ItemTabs.of(List.of(), 0, List.of(recipe("npc_shop")))));
	}

	@Test
	void pagesGoRound() {
		assertEquals(1, ItemTabs.stepped(0, 1, 3));
		assertEquals(2, ItemTabs.stepped(0, -1, 3));
		assertEquals(0, ItemTabs.stepped(2, 1, 3));
		assertEquals(0, ItemTabs.stepped(0, 1, 0));
		assertEquals(1, ItemTabs.pages(0, 5));
		assertEquals(1, ItemTabs.pages(5, 5));
		assertEquals(2, ItemTabs.pages(6, 5));
		assertEquals(1, ItemTabs.pages(6, 0));
	}
}
