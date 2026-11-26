package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.skyblock.arrows.ArrowsHudFeature;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.skyblock.chat.CommandSuggestionsFeature;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.farming.FarmingHudFeature;
import dev.tim1337.lumio.skyblock.farming.FarmingKeysFeature;
import dev.tim1337.lumio.skyblock.farming.MouseLockFeature;
import dev.tim1337.lumio.skyblock.farming.PestHudFeature;
import dev.tim1337.lumio.skyblock.farming.VisitorHudFeature;
import dev.tim1337.lumio.skyblock.fishing.SeaCreatureHudFeature;
import dev.tim1337.lumio.skyblock.hud.CelebrationsFeature;
import dev.tim1337.lumio.skyblock.hud.CustomScoreboardFeature;
import dev.tim1337.lumio.skyblock.hud.DeployableHudFeature;
import dev.tim1337.lumio.skyblock.hud.DropToastFeature;
import dev.tim1337.lumio.skyblock.hud.EquipmentHudFeature;
import dev.tim1337.lumio.skyblock.hud.ItemListFeature;
import dev.tim1337.lumio.skyblock.hud.PetCandyFeature;
import dev.tim1337.lumio.skyblock.hud.PetHudFeature;
import dev.tim1337.lumio.skyblock.hud.StorageOverlayFeature;
import dev.tim1337.lumio.skyblock.hud.WardrobeOverlayFeature;
import dev.tim1337.lumio.skyblock.mining.CommissionsHudFeature;
import dev.tim1337.lumio.skyblock.mining.CorpseHighlightFeature;
import dev.tim1337.lumio.skyblock.mining.CorpsesFeature;
import dev.tim1337.lumio.skyblock.mining.HotmTreeHudFeature;
import dev.tim1337.lumio.skyblock.mining.MineshaftPityHudFeature;
import dev.tim1337.lumio.skyblock.mining.MiningAbilityHudFeature;
import dev.tim1337.lumio.skyblock.mining.BreakResetFixFeature;
import dev.tim1337.lumio.skyblock.mining.TickGlideFeature;
import dev.tim1337.lumio.skyblock.profit.ProfitHudFeature;
import dev.tim1337.lumio.skyblock.slayer.MinibossFeature;
import dev.tim1337.lumio.skyblock.slayer.SlayerHelpersFeature;
import dev.tim1337.lumio.skyblock.misc.CocoonFeature;
import dev.tim1337.lumio.skyblock.misc.EnchantColorsFeature;
import dev.tim1337.lumio.skyblock.slayer.BossLabelFeature;
import dev.tim1337.lumio.skyblock.slayer.SlayerPhaseHudFeature;
import dev.tim1337.lumio.skyblock.slayer.TarantulaHelperFeature;
import dev.tim1337.lumio.skyblock.slayer.VoidgloomHelperFeature;
import dev.tim1337.lumio.skyblock.world.EtherwarpFeature;
import dev.tim1337.lumio.skyblock.world.ExplosionsHiderFeature;
import dev.tim1337.lumio.skyblock.world.KeepTerrainFeature;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Every feature of the SkyBlock add-on and its sidebar pages. They are made the first time this class is used, which is while the add-on is
 * initialized ({@link SkyBlockRegistration}), and registered with Lumio in {@link #ALL}'s order. Features look each other up through
 * these constants, so order matters: during construction a feature may only use features declared above it.
 */
public final class SkyBlockFeatures {
	private SkyBlockFeatures() {
	}

	public static final EquipmentHudFeature EQUIPMENT = new EquipmentHudFeature();
	public static final dev.tim1337.lumio.skyblock.chat.SkyBlockChatTabFeature CHAT_TAB = new dev.tim1337.lumio.skyblock.chat.SkyBlockChatTabFeature();
	public static final dev.tim1337.lumio.skyblock.tooltip.ItemTooltipLinesFeature TOOLTIP_LINES = new dev.tim1337.lumio.skyblock.tooltip.ItemTooltipLinesFeature();
	public static final PetHudFeature PET = new PetHudFeature();
	public static final DeployableHudFeature DEPLOYABLES = new DeployableHudFeature();
	public static final ArrowsHudFeature ARROWS = new ArrowsHudFeature();
	public static final ProfitHudFeature MINING_PROFIT = new ProfitHudFeature(ProfitActivity.MINING, "mining_profit",
		"What mining brings in, as coins.", 220f);
	public static final ProfitHudFeature FARMING_PROFIT = new ProfitHudFeature(ProfitActivity.FARMING, "farming_profit",
		"What farming brings in, as coins.", 220f);
	public static final ProfitHudFeature SLAYER_PROFIT = new ProfitHudFeature(ProfitActivity.SLAYER, "slayer_profit",
		"What slayers bring in, during a quest.", 220f);
	public static final ProfitHudFeature FISHING_PROFIT = new ProfitHudFeature(ProfitActivity.FISHING, "fishing_profit",
		"What fishing brings in, as coins.", 220f);
	public static final SeaCreatureHudFeature SEA_CREATURES = new SeaCreatureHudFeature();
	public static final dev.tim1337.lumio.skyblock.fishing.FishingAlertsFeature FISHING_ALERTS = new dev.tim1337.lumio.skyblock.fishing.FishingAlertsFeature();
	public static final dev.tim1337.lumio.skyblock.fishing.HotspotHelperFeature HOTSPOT_HELPER = new dev.tim1337.lumio.skyblock.fishing.HotspotHelperFeature();
	public static final dev.tim1337.lumio.skyblock.fishing.BiteTimerFeature BITE_TIMER = new dev.tim1337.lumio.skyblock.fishing.BiteTimerFeature();
	public static final ItemListFeature ITEM_LIST = new ItemListFeature();
	public static final CommissionsHudFeature COMMISSIONS = new CommissionsHudFeature();
	public static final CustomScoreboardFeature CUSTOM_SCOREBOARD = new CustomScoreboardFeature();
	public static final dev.tim1337.lumio.skyblock.experiments.ExperimentHelperFeature EXPERIMENTS = new dev.tim1337.lumio.skyblock.experiments.ExperimentHelperFeature();
	public static final ProfitHudFeature EXPERIMENT_PROFIT = new ProfitHudFeature(ProfitActivity.EXPERIMENTS, "experiment_profit",
		"What the Experimentation Table brings in.", 220f);
	public static final dev.tim1337.lumio.skyblock.experiments.ExperimentProfit EXPERIMENT_REWARDS = new dev.tim1337.lumio.skyblock.experiments.ExperimentProfit(EXPERIMENT_PROFIT);
	public static final ProfitHudFeature DRAGON_PROFIT = new ProfitHudFeature(ProfitActivity.DRAGONS, "dragon_profit",
		"What your dragons bring in, less eyes.", 220f);
	public static final dev.tim1337.lumio.skyblock.dragons.DragonProfit DRAGON_REWARDS = new dev.tim1337.lumio.skyblock.dragons.DragonProfit(DRAGON_PROFIT);
	public static final dev.tim1337.lumio.skyblock.dragons.LastDragonsHudFeature LAST_DRAGONS = new dev.tim1337.lumio.skyblock.dragons.LastDragonsHudFeature(DRAGON_REWARDS);
	public static final dev.tim1337.lumio.skyblock.misc.GrapplingHudFeature GRAPPLING_HOOK = new dev.tim1337.lumio.skyblock.misc.GrapplingHudFeature();
	public static final dev.tim1337.lumio.skyblock.hud.ItemPickupFeature PICKUP_LOG = new dev.tim1337.lumio.skyblock.hud.ItemPickupFeature();
	public static final CorpseHighlightFeature CORPSES = new CorpseHighlightFeature();
	public static final DropToastFeature DROP_TOASTS = new DropToastFeature();
	public static final CommandSuggestionsFeature COMMAND_SUGGESTIONS = new CommandSuggestionsFeature();
	public static final ProfitHudFeature CORPSE_PROFIT = new ProfitHudFeature(ProfitActivity.CORPSES, "corpse_profit",
		"What corpses bring in, less the keys.", 220f);
	/** Corpse highlighting and corpse profit, combined into one feature with two parts. */
	public static final CorpsesFeature CORPSE_TRACKER = new CorpsesFeature(CORPSES, CORPSE_PROFIT);
	public static final MineshaftPityHudFeature MINESHAFT_PITY = new MineshaftPityHudFeature();
	public static final PestHudFeature PESTS = new PestHudFeature();
	public static final MiningAbilityHudFeature MINING_ABILITY = new MiningAbilityHudFeature();
	public static final TickGlideFeature TICK_GLIDE = new TickGlideFeature();
	public static final dev.tim1337.lumio.skyblock.mining.PrecisionMiningFeature PRECISION_MINING = new dev.tim1337.lumio.skyblock.mining.PrecisionMiningFeature();
	public static final BreakResetFixFeature BREAK_RESET_FIX = new BreakResetFixFeature();
	public static final HotmTreeHudFeature HOTM_TREE = new HotmTreeHudFeature();
	public static final dev.tim1337.lumio.skyblock.mining.PowderHelperFeature POWDER_HELPER = new dev.tim1337.lumio.skyblock.mining.PowderHelperFeature();
	public static final dev.tim1337.lumio.skyblock.hud.buttons.InventoryButtonsFeature INVENTORY_BUTTONS = new dev.tim1337.lumio.skyblock.hud.buttons.InventoryButtonsFeature();
	public static final dev.tim1337.lumio.skyblock.mining.hollows.HollowsMapFeature HOLLOWS_MAP = new dev.tim1337.lumio.skyblock.mining.hollows.HollowsMapFeature();
	public static final VisitorHudFeature VISITORS = new VisitorHudFeature();
	public static final SlayerPhaseHudFeature SLAYER_PHASE = new SlayerPhaseHudFeature();
	public static final VoidgloomHelperFeature VOIDGLOOM_HELPER = new VoidgloomHelperFeature();
	public static final TarantulaHelperFeature TARANTULA_HELPER = new TarantulaHelperFeature();
	public static final SlayerHelpersFeature SLAYER_HELPERS = new SlayerHelpersFeature();
	public static final MinibossFeature MINIBOSSES = new MinibossFeature();
	public static final BossLabelFeature BOSS_LABEL = new BossLabelFeature();
	public static final FarmingHudFeature FARMING_HUD = new FarmingHudFeature();
	public static final MouseLockFeature MOUSE_LOCK = new MouseLockFeature();
	public static final FarmingKeysFeature FARMING_KEYS = new FarmingKeysFeature();
	public static final dev.tim1337.lumio.skyblock.farming.PestHighlightFeature PEST_HIGHLIGHT = new dev.tim1337.lumio.skyblock.farming.PestHighlightFeature();
	public static final StorageOverlayFeature STORAGE = new StorageOverlayFeature();
	public static final WardrobeOverlayFeature WARDROBE = new WardrobeOverlayFeature();
	public static final dev.tim1337.lumio.skyblock.hud.LoadoutKeysFeature LOADOUT_KEYS = new dev.tim1337.lumio.skyblock.hud.LoadoutKeysFeature();
	public static final KeepTerrainFeature KEEP_TERRAIN = new KeepTerrainFeature();
	public static final EtherwarpFeature ETHERWARP = new EtherwarpFeature();
	public static final PetCandyFeature PET_CANDY = new PetCandyFeature();
	public static final ExplosionsHiderFeature EXPLOSIONS_HIDER = new ExplosionsHiderFeature();
	public static final CelebrationsFeature CELEBRATIONS = new CelebrationsFeature();
	public static final CocoonFeature COCOONS = new CocoonFeature();
	public static final EnchantColorsFeature ENCHANT_COLORS = new EnchantColorsFeature();
	public static final dev.tim1337.lumio.skyblock.misc.ItemProtectionFeature ITEM_PROTECTION = new dev.tim1337.lumio.skyblock.misc.ItemProtectionFeature();
	public static final dev.tim1337.lumio.skyblock.chat.PartyCommandsFeature PARTY_COMMANDS = new dev.tim1337.lumio.skyblock.chat.PartyCommandsFeature();
	public static final dev.tim1337.lumio.skyblock.misc.SlotBindsFeature SLOT_BINDS = new dev.tim1337.lumio.skyblock.misc.SlotBindsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.waypoints.DungeonWaypointsFeature DUNGEON_WAYPOINTS = new dev.tim1337.lumio.skyblock.dungeon.waypoints.DungeonWaypointsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonHighlightsFeature DUNGEON_HIGHLIGHTS = new dev.tim1337.lumio.skyblock.dungeon.DungeonHighlightsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonDoorsFeature DUNGEON_DOORS = new dev.tim1337.lumio.skyblock.dungeon.DungeonDoorsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonSplitsFeature DUNGEON_SPLITS = new dev.tim1337.lumio.skyblock.dungeon.DungeonSplitsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonMapFeature DUNGEON_MAP = new dev.tim1337.lumio.skyblock.dungeon.DungeonMapFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonScoreFeature DUNGEON_SCORE = new dev.tim1337.lumio.skyblock.dungeon.DungeonScoreFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonAnnouncementsFeature DUNGEON_ANNOUNCEMENTS = new dev.tim1337.lumio.skyblock.dungeon.DungeonAnnouncementsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonLeapFeature DUNGEON_LEAP = new dev.tim1337.lumio.skyblock.dungeon.DungeonLeapFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonChestsFeature DUNGEON_CHESTS = new dev.tim1337.lumio.skyblock.dungeon.DungeonChestsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonThornFeature DUNGEON_THORN = new dev.tim1337.lumio.skyblock.dungeon.DungeonThornFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonLividFeature DUNGEON_LIVID = new dev.tim1337.lumio.skyblock.dungeon.DungeonLividFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonAlertsFeature DUNGEON_ALERTS = new dev.tim1337.lumio.skyblock.dungeon.DungeonAlertsFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonBloodCampFeature DUNGEON_BLOOD_CAMP = new dev.tim1337.lumio.skyblock.dungeon.DungeonBloodCampFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonAutoRequeueFeature DUNGEON_AUTO_REQUEUE = new dev.tim1337.lumio.skyblock.dungeon.DungeonAutoRequeueFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderFeature DUNGEON_PARTY_FINDER = new dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonAbilityFeature DUNGEON_ABILITIES = new dev.tim1337.lumio.skyblock.dungeon.DungeonAbilityFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonInvincibilityFeature DUNGEON_INVINCIBILITY = new dev.tim1337.lumio.skyblock.dungeon.DungeonInvincibilityFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.f7.TerminalSolverFeature DUNGEON_TERMINALS = new dev.tim1337.lumio.skyblock.dungeon.f7.TerminalSolverFeature();
	/** The boss timers; it keeps the saved name of the former Boss info panel, which it is what is left of. */
	public static final dev.tim1337.lumio.skyblock.dungeon.f7.BossTimersFeature DUNGEON_F7 = new dev.tim1337.lumio.skyblock.dungeon.f7.BossTimersFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.f7.F7DevicesFeature DUNGEON_F7_DEVICES = new dev.tim1337.lumio.skyblock.dungeon.f7.F7DevicesFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.f7.TerminalStatusFeature DUNGEON_TERMINAL_STATUS = new dev.tim1337.lumio.skyblock.dungeon.f7.TerminalStatusFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.f7.TerminalPracticeFeature DUNGEON_PRACTICE = new dev.tim1337.lumio.skyblock.dungeon.f7.TerminalPracticeFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonTerracottaFeature DUNGEON_TERRACOTTA = new dev.tim1337.lumio.skyblock.dungeon.DungeonTerracottaFeature();
	public static final dev.tim1337.lumio.skyblock.dungeon.DungeonPuzzlesFeature DUNGEON_PUZZLES = new dev.tim1337.lumio.skyblock.dungeon.DungeonPuzzlesFeature();

	/** The features in the order they are registered, which is their order in the settings file. */
	static final List<AddonFeature> ALL = List.of(TOOLTIP_LINES, CHAT_TAB, EQUIPMENT, PET, DEPLOYABLES, ARROWS, MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, SEA_CREATURES,
		ITEM_LIST, COMMISSIONS, CUSTOM_SCOREBOARD, CORPSES, DROP_TOASTS, COMMAND_SUGGESTIONS, CORPSE_PROFIT, CORPSE_TRACKER, MINESHAFT_PITY, PESTS,
		MINING_ABILITY, TICK_GLIDE, PRECISION_MINING, BREAK_RESET_FIX, HOTM_TREE, VISITORS, SLAYER_PHASE, VOIDGLOOM_HELPER, TARANTULA_HELPER, SLAYER_HELPERS, MINIBOSSES, FARMING_HUD,
		DUNGEON_PARTY_FINDER, FISHING_ALERTS, HOTSPOT_HELPER, BITE_TIMER, STORAGE, KEEP_TERRAIN, ETHERWARP, PET_CANDY, EXPLOSIONS_HIDER, CELEBRATIONS, BOSS_LABEL, COCOONS, MOUSE_LOCK, FARMING_KEYS, PEST_HIGHLIGHT, ENCHANT_COLORS, ITEM_PROTECTION, WARDROBE, LOADOUT_KEYS, EXPERIMENTS, EXPERIMENT_PROFIT, DRAGON_PROFIT, LAST_DRAGONS, GRAPPLING_HOOK, DUNGEON_WAYPOINTS, DUNGEON_HIGHLIGHTS, DUNGEON_DOORS, DUNGEON_SPLITS, DUNGEON_MAP, DUNGEON_SCORE, DUNGEON_ANNOUNCEMENTS, DUNGEON_LEAP, DUNGEON_CHESTS, DUNGEON_THORN, DUNGEON_LIVID, DUNGEON_PUZZLES, DUNGEON_BLOOD_CAMP, DUNGEON_AUTO_REQUEUE, DUNGEON_ABILITIES, DUNGEON_INVINCIBILITY, DUNGEON_TERMINALS, DUNGEON_F7, DUNGEON_F7_DEVICES, DUNGEON_TERMINAL_STATUS, DUNGEON_PRACTICE, DUNGEON_TERRACOTTA, PARTY_COMMANDS, SLOT_BINDS, DUNGEON_ALERTS, PICKUP_LOG, POWDER_HELPER, HOLLOWS_MAP, INVENTORY_BUTTONS);

	/** @return whether a feature is a part of another one (the corpse tracker's highlight and profit), not listed on its own */
	public static boolean isPart(FeatureView feature) {
		return feature == CORPSES || feature == CORPSE_PROFIT;
	}

	/** @return the add-on's feature with the id, or null */
	public static AddonFeature byId(String id) {
		for (AddonFeature feature : ALL) {
			if (feature.id().equals(id)) {
				return feature;
			}
		}
		return null;
	}

	/** Master switch for all SkyBlock features, and whether they are shown only while on SkyBlock or everywhere. */
	public static final Toggle SKYBLOCK_ON = make().toggle("skyblock_on",
		Component.literal("SkyBlock features"), true);
	public static final Toggle SKYBLOCK_ONLY = make().toggle("only_skyblock",
		Component.literal("Only in SkyBlock"), true);

	static {
		SKYBLOCK_ON.setDescription("Off: all of them are off and hidden here");
		SKYBLOCK_ONLY.visibleWhen(SKYBLOCK_ON::get);
	}

	public static final AddonPage SKYBLOCK_PAGE = AddonPage.create("skyblock", "SkyBlock",
		"For Hypixel SkyBlock. Off on other servers.", SystemColors.GREEN_RGB, "star")
		.lead("Master", SKYBLOCK_ON, SKYBLOCK_ONLY)
		// pages with eight or more features show them under the groups the features declare (pageGroup), in this order
		.category("Panels", PET, EQUIPMENT, DEPLOYABLES, ARROWS, PICKUP_LOG, CUSTOM_SCOREBOARD, ITEM_LIST, STORAGE, WARDROBE, LOADOUT_KEYS, INVENTORY_BUTTONS)
		.category("Profit", MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, EXPERIMENT_PROFIT, DRAGON_PROFIT, LAST_DRAGONS)
		.category("Slayer", SLAYER_PHASE, BOSS_LABEL, MINIBOSSES, SLAYER_HELPERS, VOIDGLOOM_HELPER, TARANTULA_HELPER)
		.category("Fishing", SEA_CREATURES, FISHING_ALERTS, HOTSPOT_HELPER, BITE_TIMER)
		.category("Mining", COMMISSIONS, MINESHAFT_PITY, MINING_ABILITY, HOTM_TREE, TICK_GLIDE, PRECISION_MINING, BREAK_RESET_FIX, CORPSE_TRACKER, POWDER_HELPER, HOLLOWS_MAP)
		.category("Farming", PESTS, PEST_HIGHLIGHT, VISITORS, FARMING_HUD, MOUSE_LOCK, FARMING_KEYS)
		.category("World", KEEP_TERRAIN, ETHERWARP, GRAPPLING_HOOK)
		.category("Misc", ITEM_PROTECTION, SLOT_BINDS, ENCHANT_COLORS, PET_CANDY, PARTY_COMMANDS, COMMAND_SUGGESTIONS, DROP_TOASTS, EXPERIMENTS, COCOONS, EXPLOSIONS_HIDER, CELEBRATIONS)
		.master(SKYBLOCK_ON)
		// every category is a page of its own under SkyBlock; the SkyBlock page shows the master switches and the categories as tiles
		.page("Panels", "Pet, equipment, storage, items, scoreboard.", SystemColors.TEAL_RGB, "layers", null)
		.page("Profit", "What your activities bring in, as coins.", SystemColors.YELLOW_RGB, "coins", null)
		.page("Slayer", "Helpers for slayer quests and bosses.", SystemColors.RED_RGB, "sword", SkyBlockArea::slayerQuest)
		.page("Fishing", "The sea creatures you catch.", SystemColors.BLUE_RGB, "fish", null)
		.page("Mining", "Mines, Hollows and Mineshafts helpers.",
			SystemColors.ORANGE_RGB, "pickaxe", SkyBlockArea::inMines)
		.pageNote("Mining", "Mining profit is on the Profit page.", "Profit")
		.page("Farming", "Pests, visitors and bonuses in the Garden.", SystemColors.GREEN_RGB, "wheat", SkyBlockArea::inGarden)
		.page("World", "How islands look, how you move.", SystemColors.PURPLE_RGB, "map", null)
		.page("Misc", "Drops, commands, Pet Candy and more.", SystemColors.GRAY_RGB, "gem", null)
		.overview();
	/** The Dungeons page: its own page under SkyBlock, sorted in tabs by when a feature is needed. */
	public static final AddonPage DUNGEONS_PAGE = AddonPage.create("skyblock_dungeons", "Dungeons",
		"In run order.", SystemColors.PURPLE_RGB, "skull")
		.category("Run", DUNGEON_MAP, DUNGEON_SCORE, DUNGEON_SPLITS, DUNGEON_ALERTS, DUNGEON_ANNOUNCEMENTS, DUNGEON_AUTO_REQUEUE, DUNGEON_PARTY_FINDER)
		.category("Clear", DUNGEON_WAYPOINTS, DUNGEON_HIGHLIGHTS, DUNGEON_DOORS, DUNGEON_PUZZLES, DUNGEON_BLOOD_CAMP, DUNGEON_LEAP)
		.category("Abilities", DUNGEON_ABILITIES, DUNGEON_INVINCIBILITY)
		.category("Bosses", DUNGEON_THORN, DUNGEON_LIVID, DUNGEON_TERRACOTTA)
		.category("F7 / M7", DUNGEON_F7, DUNGEON_TERMINAL_STATUS, DUNGEON_TERMINALS, DUNGEON_F7_DEVICES, DUNGEON_PRACTICE)
		// still being tested on Hypixel: the tag on the tab and on each of its modules, and the note at its top; take this line out when it is done
		.workInProgress("F7 / M7")
		.category("Loot", DUNGEON_CHESTS)
		.master(SKYBLOCK_ON)
		.tabbed()
		.here(dev.tim1337.lumio.skyblock.dungeon.Dungeon::in)
		.under(SKYBLOCK_PAGE);
	/** The SkyBlock categories as pages of their own, listed under the SkyBlock page in the sidebar. */
	public static final List<? extends AddonPage> SKYBLOCK_CATEGORIES = SKYBLOCK_PAGE.subPages();

	/** The SkyBlock elements of the HUD editor, listed in its SkyBlock group, in the order they were in the editor before. */
	static final List<AddonPanel> HUDS = List.of(EQUIPMENT, PET, DEPLOYABLES, ARROWS, MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, EXPERIMENT_PROFIT, DRAGON_PROFIT, LAST_DRAGONS,
		CORPSE_PROFIT, GRAPPLING_HOOK, SLAYER_PHASE, SEA_CREATURES, COMMISSIONS, MINESHAFT_PITY, PESTS, VISITORS, FARMING_HUD, MOUSE_LOCK, FARMING_KEYS, MINING_ABILITY, HOTM_TREE, DUNGEON_SPLITS, DUNGEON_MAP, DUNGEON_SCORE, DUNGEON_THORN, DUNGEON_BLOOD_CAMP, DUNGEON_AUTO_REQUEUE, DUNGEON_INVINCIBILITY, DUNGEON_F7, DUNGEON_TERMINAL_STATUS, PICKUP_LOG, POWDER_HELPER, HOLLOWS_MAP, BITE_TIMER);
}
