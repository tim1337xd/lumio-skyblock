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
		"What mining brings in: the ores and gemstones that come into the inventory and the sacks, as coins, while you mine.", 220f);
	public static final ProfitHudFeature FARMING_PROFIT = new ProfitHudFeature(ProfitActivity.FARMING, "farming_profit",
		"What farming brings in: the crops that come into the inventory and the sacks, as coins, while you farm.", 220f);
	public static final ProfitHudFeature SLAYER_PROFIT = new ProfitHudFeature(ProfitActivity.SLAYER, "slayer_profit",
		"What the slayers bring in: the drops and the coins of the mobs, while a slayer quest is on.", 220f);
	public static final ProfitHudFeature FISHING_PROFIT = new ProfitHudFeature(ProfitActivity.FISHING, "fishing_profit",
		"What fishing brings in: the fish, the treasure and the drops of the sea creatures, as coins, while you fish.", 220f);
	public static final SeaCreatureHudFeature SEA_CREATURES = new SeaCreatureHudFeature();
	public static final ItemListFeature ITEM_LIST = new ItemListFeature();
	public static final CommissionsHudFeature COMMISSIONS = new CommissionsHudFeature();
	public static final CustomScoreboardFeature CUSTOM_SCOREBOARD = new CustomScoreboardFeature();
	public static final dev.tim1337.lumio.skyblock.experiments.ExperimentHelperFeature EXPERIMENTS = new dev.tim1337.lumio.skyblock.experiments.ExperimentHelperFeature();
	public static final ProfitHudFeature EXPERIMENT_PROFIT = new ProfitHudFeature(ProfitActivity.EXPERIMENTS, "experiment_profit",
		"What the Experimentation Table brings in: its enchanted books and bottles as coins, less the experience bottles you throw near it.", 220f);
	public static final dev.tim1337.lumio.skyblock.experiments.ExperimentProfit EXPERIMENT_REWARDS = new dev.tim1337.lumio.skyblock.experiments.ExperimentProfit(EXPERIMENT_PROFIT);
	public static final ProfitHudFeature DRAGON_PROFIT = new ProfitHudFeature(ProfitActivity.DRAGONS, "dragon_profit",
		"What your dragons bring in: what you pick up during a fight and shortly after, as coins, less the Summoning Eyes you placed.", 220f);
	public static final dev.tim1337.lumio.skyblock.dragons.DragonProfit DRAGON_REWARDS = new dev.tim1337.lumio.skyblock.dragons.DragonProfit(DRAGON_PROFIT);
	public static final dev.tim1337.lumio.skyblock.dragons.LastDragonsHudFeature LAST_DRAGONS = new dev.tim1337.lumio.skyblock.dragons.LastDragonsHudFeature(DRAGON_REWARDS);
	public static final dev.tim1337.lumio.skyblock.misc.GrapplingHudFeature GRAPPLING_HOOK = new dev.tim1337.lumio.skyblock.misc.GrapplingHudFeature();
	public static final CorpseHighlightFeature CORPSES = new CorpseHighlightFeature();
	public static final DropToastFeature DROP_TOASTS = new DropToastFeature();
	public static final CommandSuggestionsFeature COMMAND_SUGGESTIONS = new CommandSuggestionsFeature();
	public static final ProfitHudFeature CORPSE_PROFIT = new ProfitHudFeature(ProfitActivity.CORPSES, "corpse_profit",
		"What the corpses of the mineshafts bring in: their loot as coins, less the keys they use up.", 220f);
	/** Corpse highlighting and corpse profit, combined into one feature with two parts. */
	public static final CorpsesFeature CORPSE_TRACKER = new CorpsesFeature(CORPSES, CORPSE_PROFIT);
	public static final MineshaftPityHudFeature MINESHAFT_PITY = new MineshaftPityHudFeature();
	public static final PestHudFeature PESTS = new PestHudFeature();
	public static final MiningAbilityHudFeature MINING_ABILITY = new MiningAbilityHudFeature();
	public static final TickGlideFeature TICK_GLIDE = new TickGlideFeature();
	public static final BreakResetFixFeature BREAK_RESET_FIX = new BreakResetFixFeature();
	public static final HotmTreeHudFeature HOTM_TREE = new HotmTreeHudFeature();
	public static final VisitorHudFeature VISITORS = new VisitorHudFeature();
	public static final SlayerPhaseHudFeature SLAYER_PHASE = new SlayerPhaseHudFeature();
	public static final VoidgloomHelperFeature VOIDGLOOM_HELPER = new VoidgloomHelperFeature();
	public static final TarantulaHelperFeature TARANTULA_HELPER = new TarantulaHelperFeature();
	public static final SlayerHelpersFeature SLAYER_HELPERS = new SlayerHelpersFeature();
	public static final MinibossFeature MINIBOSSES = new MinibossFeature();
	public static final BossLabelFeature BOSS_LABEL = new BossLabelFeature();
	public static final FarmingHudFeature FARMING_HUD = new FarmingHudFeature();
	public static final MouseLockFeature MOUSE_LOCK = new MouseLockFeature();
	public static final StorageOverlayFeature STORAGE = new StorageOverlayFeature();
	public static final WardrobeOverlayFeature WARDROBE = new WardrobeOverlayFeature();
	public static final KeepTerrainFeature KEEP_TERRAIN = new KeepTerrainFeature();
	public static final EtherwarpFeature ETHERWARP = new EtherwarpFeature();
	public static final PetCandyFeature PET_CANDY = new PetCandyFeature();
	public static final ExplosionsHiderFeature EXPLOSIONS_HIDER = new ExplosionsHiderFeature();
	public static final CelebrationsFeature CELEBRATIONS = new CelebrationsFeature();
	public static final CocoonFeature COCOONS = new CocoonFeature();
	public static final EnchantColorsFeature ENCHANT_COLORS = new EnchantColorsFeature();
	public static final dev.tim1337.lumio.skyblock.misc.ItemProtectionFeature ITEM_PROTECTION = new dev.tim1337.lumio.skyblock.misc.ItemProtectionFeature();

	/** The features in the order they are registered, which is their order in the settings file. */
	static final List<AddonFeature> ALL = List.of(TOOLTIP_LINES, CHAT_TAB, EQUIPMENT, PET, DEPLOYABLES, ARROWS, MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, SEA_CREATURES,
		ITEM_LIST, COMMISSIONS, CUSTOM_SCOREBOARD, CORPSES, DROP_TOASTS, COMMAND_SUGGESTIONS, CORPSE_PROFIT, CORPSE_TRACKER, MINESHAFT_PITY, PESTS,
		MINING_ABILITY, TICK_GLIDE, BREAK_RESET_FIX, HOTM_TREE, VISITORS, SLAYER_PHASE, VOIDGLOOM_HELPER, TARANTULA_HELPER, SLAYER_HELPERS, MINIBOSSES, FARMING_HUD,
		STORAGE, KEEP_TERRAIN, ETHERWARP, PET_CANDY, EXPLOSIONS_HIDER, CELEBRATIONS, BOSS_LABEL, COCOONS, MOUSE_LOCK, ENCHANT_COLORS, ITEM_PROTECTION, WARDROBE, EXPERIMENTS, EXPERIMENT_PROFIT, DRAGON_PROFIT, LAST_DRAGONS, GRAPPLING_HOOK);

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
		"What is made for Hypixel SkyBlock. Nothing here does anything on other servers.", SystemColors.GREEN_RGB, "star")
		.lead("Master", SKYBLOCK_ON, SKYBLOCK_ONLY)
		.category("Panels", PET, EQUIPMENT, DEPLOYABLES, ARROWS, STORAGE, WARDROBE, ITEM_LIST, CUSTOM_SCOREBOARD)
		.category("Profit", MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, EXPERIMENT_PROFIT, DRAGON_PROFIT, LAST_DRAGONS)
		.category("Slayer", SLAYER_PHASE, BOSS_LABEL, MINIBOSSES, SLAYER_HELPERS, VOIDGLOOM_HELPER, TARANTULA_HELPER)
		.category("Fishing", SEA_CREATURES)
		.category("Mining", COMMISSIONS, MINESHAFT_PITY, CORPSE_TRACKER, MINING_ABILITY, TICK_GLIDE, BREAK_RESET_FIX, HOTM_TREE)
		.category("Farming", PESTS, VISITORS, FARMING_HUD, MOUSE_LOCK)
		.category("World", KEEP_TERRAIN, ETHERWARP, GRAPPLING_HOOK)
		.category("Misc", ITEM_PROTECTION, DROP_TOASTS, COMMAND_SUGGESTIONS, ENCHANT_COLORS, EXPERIMENTS, COCOONS, PET_CANDY, EXPLOSIONS_HIDER, CELEBRATIONS)
		.master(SKYBLOCK_ON)
		// every category is a page of its own under SkyBlock; the SkyBlock page shows the master switches and the categories as tiles
		.page("Panels", "Your pet, equipment, storage, wardrobe, the item list and the scoreboard.", SystemColors.TEAL_RGB, "layers", null)
		.page("Profit", "What your activities bring in, as coins per hour, per session and in total.", SystemColors.YELLOW_RGB, "coins", null)
		.page("Slayer", "Helpers for your slayer quests and their bosses.", SystemColors.RED_RGB, "sword", SkyBlockArea::slayerQuest)
		.page("Fishing", "The sea creatures you catch.", SystemColors.BLUE_RGB, "fish", null)
		.page("Mining", "Helpers for the Dwarven Mines, the Crystal Hollows and the Glacite Mineshafts. Panels only show while you mine.",
			SystemColors.ORANGE_RGB, "pickaxe", SkyBlockArea::inMines)
		.pageNote("Mining", "Mining profit is on the Profit page.", "Profit")
		.page("Farming", "Pests, visitors and bonuses in the Garden.", SystemColors.GREEN_RGB, "wheat", SkyBlockArea::inGarden)
		.page("World", "How the islands look and how you move around them.", SystemColors.PURPLE_RGB, "map", null)
		.page("Misc", "Everything else for SkyBlock: toasts for valuable drops, more command suggestions, Pet Candy on pets and fewer explosions.", SystemColors.GRAY_RGB, "gem", null)
		.overview();
	/** The SkyBlock categories as pages of their own, listed under the SkyBlock page in the sidebar. */
	public static final List<? extends AddonPage> SKYBLOCK_CATEGORIES = SKYBLOCK_PAGE.subPages();

	/** The SkyBlock elements of the HUD editor, listed in its SkyBlock group, in the order they were in the editor before. */
	static final List<AddonPanel> HUDS = List.of(EQUIPMENT, PET, DEPLOYABLES, ARROWS, MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, EXPERIMENT_PROFIT, DRAGON_PROFIT, LAST_DRAGONS,
		CORPSE_PROFIT, GRAPPLING_HOOK, SLAYER_PHASE, SEA_CREATURES, COMMISSIONS, MINESHAFT_PITY, PESTS, VISITORS, FARMING_HUD, MOUSE_LOCK, MINING_ABILITY, HOTM_TREE);
}
