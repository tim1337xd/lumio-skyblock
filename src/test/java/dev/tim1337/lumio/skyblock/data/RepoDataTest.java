package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What is read from the item repository's files, in their format, and the names read from chat. */
class RepoDataTest {
	private static final JsonObject ENCHANTS = JsonParser.parseString("""
		{"enchants_xp_cost":{"sharpness":[1,2,3,4,5,6,7],"aiming":[1,2,3,4,5],"turbo_coco":[1,2,3,4,5],"silk_touch":[15],"ultimate_wise":[1,2,3,4,5],"compact":[1]},
		"max_xp_table_levels":{"sharpness":5,"silk_touch":1},
		"enchant_mapping_id":["dragon_tracer","turbo_cocoa"],"enchant_mapping_item":["aiming","turbo_coco"],
		"enchants":{"SWORD":["sharpness","ultimate_wise","ultimate_soul_eater"],"HOE":["turbo_coco"]}}""").getAsJsonObject();

	@Test
	void levelsMakeTheTier() {
		EnchantTiers tiers = EnchantTiers.of(ENCHANTS);
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Sharpness", 7));
		assertEquals(EnchantTiers.Tier.GREAT, tiers.tier("Sharpness", 6));
		assertEquals(EnchantTiers.Tier.GOOD, tiers.tier("Sharpness", 5));
		assertEquals(EnchantTiers.Tier.POOR, tiers.tier("Sharpness", 4));
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Silk Touch", 1));
	}

	@Test
	void ultimatesWithAndWithoutTheirWord() {
		EnchantTiers tiers = EnchantTiers.of(ENCHANTS);
		assertEquals(EnchantTiers.Tier.ULTIMATE, tiers.tier("Ultimate Wise", 1));
		assertEquals(EnchantTiers.Tier.ULTIMATE, tiers.tier("Soul Eater", 5));
	}

	@Test
	void namesInTheLoreFindTheirData() {
		EnchantTiers tiers = EnchantTiers.of(ENCHANTS);
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Dragon Tracer", 5));
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Turbo-Cocoa", 5));
	}

	@Test
	void enchantsThatGrowGoToTen() {
		EnchantTiers tiers = EnchantTiers.of(ENCHANTS);
		assertEquals(EnchantTiers.Tier.PERFECT, tiers.tier("Compact", 10));
		assertEquals(EnchantTiers.Tier.GREAT, tiers.tier("Compact", 7));
		assertEquals(EnchantTiers.Tier.GOOD, tiers.tier("Compact", 5));
		assertEquals(EnchantTiers.Tier.POOR, tiers.tier("Compact", 1));
	}

	@Test
	void anUnknownEnchantHasNoTier() {
		assertNull(EnchantTiers.of(ENCHANTS).tier("Made Up", 5));
		assertNull(EnchantTiers.EMPTY.tier("Sharpness", 7));
	}

	@Test
	void levelsAreRead() {
		assertEquals(7, EnchantTiers.level("VII"));
		assertEquals(4, EnchantTiers.level("IV"));
		assertEquals(10, EnchantTiers.level("X"));
		assertEquals(5, EnchantTiers.level("5"));
		assertEquals(-1, EnchantTiers.level("Q"));
		assertEquals(-1, EnchantTiers.level("999"));
	}

	@Test
	void warpsWithTheirShortNamesAndIsland() {
		List<RepoWarps.Warp> warps = RepoWarps.of(JsonParser.parseString("""
			{"area_names":{"crimson_isle":"Crimson Isle"},"island_warps":[{"mode":"crimson_isle","warp":"nether","aliases":["isle","ci"]},{"warp":"odd"},{"mode":"x"}]}""").getAsJsonObject());
		assertEquals(List.of(new RepoWarps.Warp("nether", "Crimson Isle"), new RepoWarps.Warp("isle", "Crimson Isle"), new RepoWarps.Warp("ci", "Crimson Isle"), new RepoWarps.Warp("odd", "")), warps);
		assertTrue(RepoWarps.of(new JsonObject()).isEmpty());
	}

	@Test
	void guildMembersFromWhatTheGameShows() {
		SocialNames names = new SocialNames();
		names.read("Guild > Alice joined.");
		names.read("[MVP+] Bob ● [VIP] Cara ● Dan ●");
		names.read("Hello ● how are you today ●");
		assertEquals(List.of("Alice", "Bob", "Cara", "Dan"), List.copyOf(names.guild()));
		names.read("Bob left the guild!");
		assertFalse(names.guild().contains("Bob"));
	}

	@Test
	void friendsOnlyInTheirList() {
		SocialNames names = new SocialNames();
		names.read("Eve is in SkyBlock");
		assertTrue(names.friends().isEmpty(), "not after the list's header");
		names.read("Friends (Page 1 of 3)");
		names.read("[MVP+] Eve is in SkyBlock");
		names.read("Finn is currently offline");
		names.read("Friend > Gia joined.");
		assertEquals(List.of("Eve", "Finn", "Gia"), List.copyOf(names.friends()));
	}
}
