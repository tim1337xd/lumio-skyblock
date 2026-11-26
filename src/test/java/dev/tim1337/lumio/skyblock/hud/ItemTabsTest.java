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
		List<ItemTabs.Tab> tabs = ItemTabs.of(List.of(recipe("crafting"), recipe("crafting"), recipe("forge"), recipe("npc_shop"), recipe("trade"), recipe("katgrade"),
			recipe("drops")), 3, List.of(), true);
		assertEquals(List.of(ItemTabs.Kind.RECIPE, ItemTabs.Kind.FORGE, ItemTabs.Kind.SHOP, ItemTabs.Kind.TRADE, ItemTabs.Kind.KAT, ItemTabs.Kind.MARKET,
			ItemTabs.Kind.DROPPED_BY, ItemTabs.Kind.USED_IN), kinds(tabs));
		assertEquals(2, tabs.getFirst().recipes().size());
		assertEquals("Drops", ItemTabs.Kind.DROPPED_BY.label);
	}

	@Test
	void aTabWithoutDataIsLeftOut() {
		assertTrue(ItemTabs.of(List.of(), 0, List.of(), false).isEmpty());
		assertEquals(List.of(ItemTabs.Kind.DROPPED_BY), kinds(ItemTabs.of(List.of(recipe("drops")), 0, List.of(), false)));
		assertEquals(List.of(ItemTabs.Kind.USED_IN), kinds(ItemTabs.of(List.of(), 1, List.of(), false)));
		assertEquals(List.of(ItemTabs.Kind.MARKET), kinds(ItemTabs.of(List.of(), 0, List.of(), true)));
	}

	@Test
	void aMobShowsItsDropsAndAnNpcItsShopFirst() {
		assertEquals(List.of(ItemTabs.Kind.DROPS), kinds(ItemTabs.of(List.of(), 0, List.of(recipe("drops")), false)));
		assertEquals(List.of(ItemTabs.Kind.SELLS), kinds(ItemTabs.of(List.of(), 0, List.of(recipe("npc_shop")), false)));
	}

	@Test
	void theWindowOpensOnTheRecipeElseOnTheFirstTabWithContent() {
		assertEquals(0, ItemTabs.opening(ItemTabs.of(List.of(recipe("crafting")), 2, List.of(), true)));
		assertEquals(0, ItemTabs.opening(ItemTabs.of(List.of(recipe("forge")), 0, List.of(), true)), "the forge, the first with content");
		List<ItemTabs.Tab> dropped = ItemTabs.of(List.of(recipe("drops")), 0, List.of(), true);
		assertEquals(ItemTabs.Kind.MARKET, dropped.get(ItemTabs.opening(dropped)).kind());
		assertEquals(0, ItemTabs.opening(List.of()));
		List<ItemTabs.Tab> mob = ItemTabs.of(List.of(), 0, List.of(recipe("drops")), false);
		assertEquals(ItemTabs.Kind.DROPS, mob.get(ItemTabs.opening(mob)).kind());
		// a recipe that is not first still wins
		List<ItemTabs.Tab> both = List.of(new ItemTabs.Tab(ItemTabs.Kind.MARKET, List.of()), new ItemTabs.Tab(ItemTabs.Kind.RECIPE, List.of(recipe("crafting"))));
		assertEquals(1, ItemTabs.opening(both));
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
