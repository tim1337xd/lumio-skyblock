package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The kinds of recipes of the item repository, in the form of its files. */
class RepoRecipesTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static final JsonObject ENDER_DRAGON = json("""
		{"displayname":"§6Superior Ender Dragon (Boss)","internalname":"SUPERIOR_ENDER_DRAGON_BOSS","recipes":[{"level":100,"coins":1000,"xp":12,"combat_xp":700,"name":"§6Superior Ender Dragon","type":"drops",
		"drops":[{"id":"ENDER_PEARL","extra":["x5-10"],"chance":"100%"},{"id":"ASPECT_OF_THE_DRAGON","extra":["Requires §aweight"],"chance":"0.5%"},{"id":"SUPERIOR_FRAGMENT:3","extra":[],"chance":"§a40%"}]}]}""");
	private static final JsonObject ZEALOT = json("""
		{"displayname":"§9Zealot (Monster)","internalname":"ZEALOT_MONSTER","recipes":[{"level":55,"coins":10,"xp":40,"combat_xp":40,"name":"§cZealot","type":"drops",
		"drops":[{"id":"ENDER_PEARL","extra":["x1-2"],"chance":"100%"},{"id":"SUMMONING_EYE","extra":[],"chance":"0.238%"}]}]}""");
	private static final JsonObject FORGE = json("""
		{"displayname":"§5Amber Material","internalname":"AMBER_MATERIAL","crafttext":"","recipes":[{"type":"forge","inputs":["GOLDEN_PLATE:1","FINE_AMBER_GEM:12"],"count":2,"overrideOutputId":"AMBER_MATERIAL","duration":21600}]}""");
	private static final JsonObject NPC = json("""
		{"displayname":"§9Combat Merchant (NPC)","internalname":"ADVENTURER_NPC","recipes":[{"type":"npc_shop","cost":["SKYBLOCK_COIN:8.0"],"result":"ROTTEN_FLESH:1"},{"type":"npc_shop","cost":["SKYBLOCK_COIN:8.0","BONE:2"],"result":"ARROW:4"}]}""");

	private static List<ItemRepo.Recipe> read(String id, JsonObject json) {
		return RepoRecipes.of(id, json, null);
	}

	@Test
	void aDragonDropsWithItsChances() {
		List<ItemRepo.Recipe> recipes = read("SUPERIOR_ENDER_DRAGON_BOSS", ENDER_DRAGON);
		assertEquals(1, recipes.size());
		ItemRepo.Recipe drops = recipes.getFirst();
		assertEquals("drops", drops.type());
		assertEquals("Superior Ender Dragon", drops.source());
		assertEquals("SUPERIOR_ENDER_DRAGON_BOSS", drops.sourceId());
		assertEquals("Level 100 · 1,000 coins · 12 XP · 700 Combat XP", drops.note());
		assertEquals(3, drops.outputs().size());
		assertEquals("ASPECT_OF_THE_DRAGON", drops.outputs().get(1).id());
		assertEquals("0.5% · Requires weight", drops.outputs().get(1).note());
		assertEquals("100% · x5-10", drops.outputs().getFirst().note());
		assertEquals(3.0, drops.outputs().get(2).count(), "a count in the id");
		assertEquals("40%", drops.outputs().get(2).note(), "colors are left out");
	}

	@Test
	void aZealotDrops() {
		ItemRepo.Recipe drops = read("ZEALOT_MONSTER", ZEALOT).getFirst();
		assertEquals("Zealot", drops.source());
		assertEquals("SUMMONING_EYE", drops.outputs().get(1).id());
		assertEquals("0.238%", drops.outputs().get(1).note());
	}

	@Test
	void aForgeItem() {
		ItemRepo.Recipe forge = read("AMBER_MATERIAL", FORGE).getFirst();
		assertEquals("forge", forge.type());
		assertEquals(List.of("GOLDEN_PLATE", "FINE_AMBER_GEM"), forge.inputs().stream().map(ItemRepo.Stack::id).toList());
		assertEquals(12.0, forge.inputs().get(1).count());
		assertEquals("6h", forge.note());
		assertEquals(2.0, forge.outputs().getFirst().count());
	}

	@Test
	void anNpcShop() {
		List<ItemRepo.Recipe> shop = read("ADVENTURER_NPC", NPC);
		assertEquals(2, shop.size());
		assertEquals("npc_shop", shop.getFirst().type());
		assertEquals("Combat Merchant", shop.getFirst().source());
		assertEquals("ADVENTURER_NPC", shop.getFirst().sourceId());
		assertEquals("ROTTEN_FLESH", shop.getFirst().outputs().getFirst().id());
		assertEquals(8.0, shop.getFirst().inputs().getFirst().count());
		assertEquals(4.0, shop.get(1).outputs().getFirst().count());
	}

	@Test
	void aTradeAndKat() {
		ItemRepo.Recipe trade = read("ANVIL", json("""
			{"displayname":"Anvil","recipes":[{"type":"trade","cost":"EMERALD:25","result":"ANVIL"}]}""")).getFirst();
		assertEquals("trade", trade.type());
		assertEquals("EMERALD", trade.inputs().getFirst().id());
		ItemRepo.Recipe kat = read("ARMADILLO;1", json("""
			{"displayname":"Armadillo","recipes":[{"type":"katgrade","coins":10000,"time":10800,"input":"ARMADILLO;0","output":"ARMADILLO;1","items":["ENCHANTED_COBBLESTONE:64"]}]}""")).getFirst();
		assertEquals("katgrade", kat.type());
		assertEquals(2, kat.inputs().size());
		assertEquals("10,000 coins, 3h", kat.note());
	}

	@Test
	void craftingKeepsItsShapeAndWhatItRequires() {
		ItemRepo.Recipe grid = read("REFINED", json("""
			{"displayname":"Refined","crafttext":"Requires: §aHotM 2","recipe":{"A1":"","A2":"MITHRIL_ORE:32","A3":"","B1":"","B2":"","B3":"","C1":"","C2":"","C3":""}}""")).getFirst();
		assertTrue(grid.crafting());
		assertEquals(9, grid.inputs().size());
		assertEquals(null, grid.inputs().getFirst());
		assertEquals("MITHRIL_ORE", grid.inputs().get(1).id());
		assertEquals("Requires: HotM 2", grid.note());
		ItemRepo.Recipe listed = read("OTHER", json("""
			{"displayname":"Other","recipes":[{"type":"crafting","A1":"STICK:2","count":4}]}""")).getFirst();
		assertEquals(4.0, listed.outputs().getFirst().count());
	}

	@Test
	void anUnknownKindIsCountedNotLost() {
		List<String> unknown = new ArrayList<>();
		List<ItemRepo.Recipe> recipes = RepoRecipes.of("X", json("""
			{"displayname":"X","recipes":[{"type":"new_kind"},{"type":"forge","inputs":[{"not":"a string"}]},{"type":"forge","inputs":["A:1"]}]}"""), unknown::add);
		assertEquals(List.of("new_kind", "forge (not readable)"), unknown);
		assertEquals(1, recipes.size());
	}

	@Test
	void tellsWhatAFileIs() {
		assertEquals(RepoRecipes.Kind.NPC, RepoRecipes.kindOf("ADVENTURER_NPC", true));
		assertEquals(RepoRecipes.Kind.MOB, RepoRecipes.kindOf("ZEALOT_MONSTER", true));
		assertEquals(RepoRecipes.Kind.MOB, RepoRecipes.kindOf("AGARIMOO_SC", true));
		assertEquals(RepoRecipes.Kind.MOB, RepoRecipes.kindOf("SUPERIOR_ENDER_DRAGON_BOSS", true));
		assertEquals(RepoRecipes.Kind.ITEM, RepoRecipes.kindOf("SOME_BOSS", false));
		assertEquals(RepoRecipes.Kind.ITEM, RepoRecipes.kindOf("ASPECT_OF_THE_DRAGON", false));
	}
}
