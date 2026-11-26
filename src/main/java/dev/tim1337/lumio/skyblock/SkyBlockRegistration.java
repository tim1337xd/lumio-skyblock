package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.AddonContext;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.skyblock.chat.SkyBlockSuggestions;
import dev.tim1337.lumio.skyblock.data.EquipmentTracker;
import dev.tim1337.lumio.skyblock.data.HotmTree;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.dragons.DragonProfit;
import dev.tim1337.lumio.skyblock.experiments.ExperimentFormats;
import dev.tim1337.lumio.skyblock.experiments.ExperimentProfit;
import dev.tim1337.lumio.skyblock.experiments.SuperpairsSeenLines;
import dev.tim1337.lumio.skyblock.hud.WardrobeModelRenderer;
import dev.tim1337.lumio.skyblock.hud.WardrobeOverlay;
import dev.tim1337.lumio.skyblock.hud.RareDropToast;
import dev.tim1337.lumio.skyblock.profit.ProfitHudFeature;
import dev.tim1337.lumio.skyblock.slayer.SlayerBosses;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import dev.tim1337.lumio.api.experimental.util.WorldLabels;

import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

import static dev.tim1337.lumio.skyblock.SkyBlockFeatures.*;

/**
 * Registers everything of the SkyBlock add-on: its features and pages, its providers, through the add-on API; and its own HUD layers, ticks and
 * screen events, through Fabric API like any mod.
 */
final class SkyBlockRegistration {
	/** @return whether a menu belongs to the Experimentation Table */
	private static boolean inTableMenu(net.minecraft.client.gui.screens.inventory.ContainerScreen chest) {
		String title = dev.tim1337.lumio.api.experimental.util.TextColors.noColors(chest.getTitle().getString());
		return ExperimentFormats.menu(title) != null || ExperimentFormats.isOver(title) || title.strip().equals(ExperimentFormats.TABLE_TITLE);
	}

	/** The profit trackers, drawn over inventories too; a fixed array, so drawing them every frame makes nothing new. */
	private static final ProfitHudFeature[] PROFITS = {MINING_PROFIT, FARMING_PROFIT, SLAYER_PROFIT, FISHING_PROFIT, CORPSE_PROFIT, EXPERIMENT_PROFIT, DRAGON_PROFIT};

	private SkyBlockRegistration() {
	}

	static void register(AddonContext context) {
		for (AddonFeature feature : SkyBlockFeatures.ALL) {
			context.registerFeature(feature);
		}
		context.registerPage(SKYBLOCK_PAGE);
		for (AddonPanel hud : SkyBlockFeatures.HUDS) {
			context.registerHud(hud, "SkyBlock");
		}
		SkyBlockLocation location = new SkyBlockLocation();
		context.registerLocation(location);
		context.registerPlaceFeatures(new SkyBlockPlaceFeatures(location));
		context.registerHighlighter(new SkyBlockHighlighter());
		context.registerChatListener(new SkyBlockChatListener());
		context.registerSuggestions(SkyBlockSuggestions.provider());
		context.registerScreenHooks(new SkyBlockScreenHooks());
		// read-only state for a debug tool (the debug add-on lists them); nothing reads them in a normal game
		DebugHooks.view("skyblock.pets", PetTracker::describe);
		DebugHooks.view("skyblock.state", SkyBlock::describe);
		DebugHooks.view("skyblock.experiments", SkyBlockFeatures.EXPERIMENTS::debugLines);
		context.registerInfoValues(new SkyBlockInfoValues());
		context.registerStatusValues(new SkyBlockStatusValues());
		context.registerTabCards(new SkyBlockTabCards());
		context.registerSimulator(SkyBlockSimulation.INSTANCE);
		context.registerFeatureSets(new SkyBlockFeatureSets());
		context.registerSetup(new SkyBlockSetup());
		context.registerScoreboard(new SkyBlockScoreboard());
		context.registerInventoryColumn(SkyBlockScreenParts.EQUIPMENT);
		// first, so a face-down card's name comes right under the game's lines
		context.registerTooltipLines(new SuperpairsSeenLines(SkyBlockFeatures.EXPERIMENTS));
		context.registerTooltipLines(SkyBlockFeatures.TOOLTIP_LINES);
		context.registerTooltipLines(SkyBlockFeatures.DUNGEON_PARTY_FINDER);
		context.registerTooltipLines(SkyBlockFeatures.ITEM_LIST.hover());
		context.registerChatTab(SkyBlockFeatures.CHAT_TAB);
		context.registerBlockTint(SkyBlockScreenParts.TICK_GLIDE);
		// the add-on's releases take part in Lumio's update check (off unless the player turns it on; nothing is downloaded without Update)
		dev.tim1337.lumio.api.experimental.util.AddonUpdates.offer(SkyBlockAddon.ID, "tim1337xd", "lumio-skyblock", "lumio-skyblock");
		registerEvents();
	}

	/** The add-on's own HUD layers, ticks and events, through Fabric API. */
	private static void registerEvents() {
		PetTracker.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("pet_hud"), PET::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("deployables_hud"), DEPLOYABLES::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("arrows_hud"), ARROWS::render);
		// the rare drop moment: a toast at the top
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("rare_drop_toast"), RareDropToast::render);
		// remember deployables the player places, so only the player's own are shown
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) {
				DEPLOYABLES.onUse(player, player.getItemInHand(hand));
				GRAPPLING_HOOK.onUse(player, player.getItemInHand(hand));
				EXPERIMENT_REWARDS.onUse(Minecraft.getInstance(), player.getItemInHand(hand));
				SkyBlockFeatures.HOTSPOT_HELPER.onUse(player, player.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				DUNGEON_WAYPOINTS.used(hit.getBlockPos());
				DUNGEON_PUZZLES.used(hit.getBlockPos());
				DEPLOYABLES.onUse(player, player.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
		// profit tracking: item gains are detected, the current activity is determined, and the HUD panels display both
		ItemGains.register();
		// storage: pages are cached while open, and the overlay is drawn over the storage menu
		StorageTracker.register();
		dev.tim1337.lumio.skyblock.dungeon.DungeonVisualRules.register();
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.TOOLTIP_LINES::tick);
		// the one queue of Lumio's on-screen titles: the next waiting one, when its turn has come
		ClientTickEvents.END_CLIENT_TICK.register(dev.tim1337.lumio.skyblock.hud.TitleQueue::tick);
		// the dungeon run: floor, party, numbers, phase and room, read only while in the Catacombs
		ClientTickEvents.END_CLIENT_TICK.register(dev.tim1337.lumio.skyblock.dungeon.Dungeon::tick);
		dev.tim1337.lumio.skyblock.dungeon.DungeonTicks.register();
		// Lumio's lines after a block of Hypixel's, and a corpse loot list that never got its closing bar
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			dev.tim1337.lumio.skyblock.data.ItemGains.tickCorpse();
			dev.tim1337.lumio.skyblock.chat.AfterBlock.tick();
		});
		DUNGEON_WAYPOINTS.register();
		DUNGEON_ANNOUNCEMENTS.register();
		SkyBlockFeatures.DUNGEON_AUTO_REQUEUE.register();
		// "Block wrong clicks" of the two F7 solvers: the key to click anyway, and the device solver's look at the player's own clicks
		SkyBlockFeatures.DUNGEON_TERMINALS.register();
		SkyBlockFeatures.DUNGEON_F7_DEVICES.register();
		SkyBlockFeatures.DUNGEON_PARTY_FINDER.register();
		LevelRenderEvents.COLLECT_SUBMITS.register(DUNGEON_WAYPOINTS::draw);
		ClientTickEvents.END_CLIENT_TICK.register(DUNGEON_HIGHLIGHTS::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_teammate_names"), DUNGEON_HIGHLIGHTS::drawNames);
		ClientTickEvents.END_CLIENT_TICK.register(DUNGEON_DOORS::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.DUNGEON_THORN::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.DUNGEON_LIVID::tick);
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(SkyBlockFeatures.DUNGEON_LIVID::collect);
		ClientTickEvents.END_CLIENT_TICK.register(DUNGEON_PUZZLES::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(DUNGEON_PUZZLES::draw);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_puzzle_labels"), DUNGEON_PUZZLES::drawLabels);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_boss"), SkyBlockFeatures.DUNGEON_THORN::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(DUNGEON_BLOOD_CAMP::draw);
		ClientTickEvents.END_CLIENT_TICK.register(DUNGEON_BLOOD_CAMP::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.PARTY_COMMANDS::tick);
		// what the profit trackers counted: a file of its own per profile, apart from the settings
		for (ProfitHudFeature profit : PROFITS) {
			dev.tim1337.lumio.skyblock.data.ProfitData.store().add(profit.id(), profit.stored());
		}
		dev.tim1337.lumio.skyblock.data.ProfitData.store().add(SkyBlockFeatures.POWDER_HELPER.id(), SkyBlockFeatures.POWDER_HELPER.stored());
		ClientTickEvents.END_CLIENT_TICK.register(dev.tim1337.lumio.skyblock.data.ProfitData::tick);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dev.tim1337.lumio.skyblock.data.ProfitData.stopping());
		SkyBlockFeatures.DUNGEON_ABILITIES.register();
		SkyBlockFeatures.DUNGEON_INVINCIBILITY.register();
		dev.tim1337.lumio.skyblock.dungeon.f7.F7State.listen();
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.DUNGEON_F7::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> SkyBlockFeatures.DUNGEON_F7_DEVICES.tick(client, dev.tim1337.lumio.skyblock.dungeon.f7.F7State.phase()));
		LevelRenderEvents.COLLECT_SUBMITS.register(SkyBlockFeatures.DUNGEON_F7_DEVICES::draw);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.DUNGEON_TERRACOTTA::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SkyBlockFeatures.DUNGEON_TERRACOTTA::draw);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_f7_device_labels"), SkyBlockFeatures.DUNGEON_F7_DEVICES::drawLabels);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.DUNGEON_ABILITIES::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_alert"), dev.tim1337.lumio.skyblock.dungeon.DungeonAlert::draw);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_blood_camp"), DUNGEON_BLOOD_CAMP::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_auto_requeue"), SkyBlockFeatures.DUNGEON_AUTO_REQUEUE::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_blood_labels"), DUNGEON_BLOOD_CAMP::drawLabels);
		LevelRenderEvents.COLLECT_SUBMITS.register(DUNGEON_DOORS::draw);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_splits"), DUNGEON_SPLITS::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_map"), DUNGEON_MAP::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_score"), DUNGEON_SCORE::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_invincibility"), SkyBlockFeatures.DUNGEON_INVINCIBILITY::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_f7"), SkyBlockFeatures.DUNGEON_F7::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("dungeon_terminal_status"), SkyBlockFeatures.DUNGEON_TERMINAL_STATUS::render);
		WardrobeOverlay.register();
		PictureInPictureRendererRegistry.register(context -> new WardrobeModelRenderer());
		EXPERIMENTS.register();
		EXPERIMENTS.trackWith(EXPERIMENT_REWARDS);
		EXPERIMENT_PROFIT.showTiles((bucket, mode) -> new String[][]{{"Tables", ProfitTracker.count(bucket.events)},
			{"ULTRA-RARE", ProfitTracker.count(bucket.counters.getOrDefault(ExperimentProfit.ULTRA_RARE, 0L))},
			{"Since last", bucket.counters.getOrDefault(ExperimentProfit.DRY, 0L) + " tries"},
			{"XP", ProfitTracker.count(bucket.counters.getOrDefault(ExperimentProfit.XP, 0L))}});
		DRAGON_PROFIT.showTiles(DragonProfit::tiles);
		ClientTickEvents.END_CLIENT_TICK.register(client -> DRAGON_REWARDS.tick());
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("last_dragons_hud"), LAST_DRAGONS::render);
		ClientTickEvents.END_CLIENT_TICK.register(GRAPPLING_HOOK::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.BITE_TIMER::tick);
		SkyBlockFeatures.PICKUP_LOG.register();
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.PICKUP_LOG::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("pickup_log_hud"), SkyBlockFeatures.PICKUP_LOG::render);
		ClientTickEvents.END_CLIENT_TICK.register(BREAK_RESET_FIX::tick);
		ClientTickEvents.END_CLIENT_TICK.register(dev.tim1337.lumio.skyblock.hud.SpeedBar::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("grappling_hook_hud"), GRAPPLING_HOOK::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			EXPERIMENTS.tick(client);
			EXPERIMENT_REWARDS.tick(client);
			ProfitActivity.experiments(EXPERIMENTS.isEnabled() && client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen chest && inTableMenu(chest));
		});
		STORAGE.register();
		ClientTickEvents.END_CLIENT_TICK.register(ARROWS::tick);
		// only scan the inventory while a profit tracker is enabled
		ItemGains.wantedWhen(() -> MINING_PROFIT.isEnabled() || FARMING_PROFIT.isEnabled() || SLAYER_PROFIT.isEnabled() || FISHING_PROFIT.isEnabled() || DRAGON_PROFIT.isEnabled());
		PetTracker.wantedWhen(() -> PET.isEnabled() || EXPERIMENTS.isEnabled());
		ProfitActivity.register();
		for (ProfitHudFeature profit : PROFITS) {
			ClientTickEvents.END_CLIENT_TICK.register(profit::tick);
			HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id(profit.id() + "_hud"), profit::render);
		}
		ClientTickEvents.END_CLIENT_TICK.register(SEA_CREATURES::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SkyBlockFeatures.FISHING_ALERTS::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("sea_creatures_hud"), SEA_CREATURES::render);
		// before the item list, so its dimming of the slots lies over the numbers as over the items
		PET_CANDY.register();
		// before the item list: the buttons say where they are before the item search places its panels
		SkyBlockFeatures.INVENTORY_BUTTONS.register();
		ITEM_LIST.register();
		// after the item list, whose favorite key it shares
		ITEM_PROTECTION.register();
		CORPSES.register();
		KEEP_TERRAIN.register();
		ETHERWARP.register();
		MINING_ABILITY.registerPickobulus();
		TICK_GLIDE.register();
		SkyBlockFeatures.POWDER_HELPER.register();
		SkyBlockFeatures.HOLLOWS_MAP.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("crystal_hollows_map_hud"), SkyBlockFeatures.HOLLOWS_MAP::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("powder_helper_hud"), SkyBlockFeatures.POWDER_HELPER::render);
		SkyBlockFeatures.PRECISION_MINING.register();
		SkyBlockFeatures.HOTSPOT_HELPER.register();
		SkyBlockFeatures.SLOT_BINDS.register();
		SkyBlockFeatures.LOADOUT_KEYS.register();
		SkyBlockFeatures.DUNGEON_ALERTS.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("commissions_hud"), COMMISSIONS::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("mineshaft_pity_hud"), MINESHAFT_PITY::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("pest_hud"), PESTS::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("mining_ability_hud"), MINING_ABILITY::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("tick_glide_hud"), TICK_GLIDE::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("hotm_tree_hud"), HOTM_TREE::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("visitor_hud"), VISITORS::render);
		ClientTickEvents.END_CLIENT_TICK.register(SlayerBosses::tick);
		MINIBOSSES.register();
		TARANTULA_HELPER.register();
		VOIDGLOOM_HELPER.register();
		BOSS_LABEL.register();
		COCOONS.register();
		ENCHANT_COLORS.register();
		// whether the player farms is looked up first: the Mouse lock and the Farming Keys read it in the same tick
		dev.tim1337.lumio.skyblock.farming.FarmingWatch.register();
		MOUSE_LOCK.register();
		FARMING_KEYS.register();
		ClientTickEvents.END_CLIENT_TICK.register(PEST_HIGHLIGHT::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("mouse_lock_hud"), MOUSE_LOCK::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("farming_keys_hud"), FARMING_KEYS::render);
		WorldLabels.add(BOSS_LABEL::render);
		WorldLabels.add(MINIBOSSES::drawDropLabels);
		WorldLabels.add(COCOONS::render);
		SkyBlockSuggestions.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("slayer_phase_hud"), SLAYER_PHASE::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("farming_hud"), FARMING_HUD::render);
		ClientTickEvents.END_CLIENT_TICK.register(PESTS::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("equipment_hud"), EQUIPMENT::render);
		EquipmentTracker.register();
		HotmTree.register();
		// the equipment panel next to the inventory, drawn after the screen
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (screen instanceof InventoryScreen inventory) {
				EQUIPMENT.renderInInventory(graphics, inventory);
			}
		});
		// behind the items: Croesus' runs and a run's chests not opened yet, the equipped pet in the Pets menu
		SlotLayer.addBehind((screen, graphics, mouseX, mouseY) -> {
			DUNGEON_CHESTS.drawBehind(screen, graphics);
			PET.drawBehind(screen, graphics);
		});
		// the profit trackers are drawn on top of inventories; clicking them switches the time range and price mode (like SkyHanni)
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			for (ProfitHudFeature profit : PROFITS) {
				profit.renderInInventory(graphics, mouseX, mouseY);
			}
			SEA_CREATURES.renderInInventory(graphics, mouseX, mouseY);
			DUNGEON_CHESTS.draw(screen, graphics, mouseX, mouseY);
			SkyBlockFeatures.DUNGEON_INVINCIBILITY.drawSlots(screen, graphics, mouseX, mouseY);
			SkyBlockFeatures.DUNGEON_TERMINALS.drawOnMenu(screen, graphics);
			COMMISSIONS.highlightInMenu(screen, graphics);
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> {
				for (ProfitHudFeature profit : PROFITS) {
					if (profit.clickInInventory(event.x(), event.y(), ScreenInput.button(event))) {
						return false;
					}
				}
				return !SEA_CREATURES.clickInInventory(event.x(), event.y(), ScreenInput.button(event));
			});
		});
	}

	private static net.minecraft.resources.Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(LumioApi.MOD_ID, path);
	}
}
