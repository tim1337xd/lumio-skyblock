package dev.tim1337.lumio.skyblock.experiments;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.profit.ProfitHudFeature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What the table brings in, for its profit panel: the rewards as the summary menu lists them, kept until the server says they were claimed, and the experience bottles thrown near the table as a cost. It only reads: the menu, the chat and the
 * inventory.
 */
public final class ExperimentProfit {
	public static final String XP = "xp";
	public static final String ULTRA_RARE = "ultra_rare";
	public static final String DRY = "dry";
	public static final String BOTTLES = "bottles";
	private static final String SUPERPAIRS = "Superpairs";
	/** The most a thrown bottle counts away from where the table was opened, and how long the throw takes to show in the inventory. */
	private static final double NEAR_TABLE_SQUARED = 15.0 * 15.0;
	private static final int THROW_TICKS = 4;

	private final ProfitHudFeature panel;
	private List<String> pending = List.of();
	private List<String> lastClaimed = List.of();
	private boolean ultraThisGame;
	private Vec3 tablePlace;
	/** Where the table was last opened, for the panel to know whether the player is still at it. */
	private static Vec3 lastTable;
	/** The panel shows within this many blocks of the table. */
	private static final double PANEL_REACH_BLOCKS = 8.0;

	/** @return whether the player stands near the table that was opened last */
	public static boolean nearTable(Minecraft client) {
		return lastTable != null && client.player != null && client.player.position().distanceToSqr(lastTable) <= PANEL_REACH_BLOCKS * PANEL_REACH_BLOCKS;
	}
	private String thrownId;
	private int thrownBefore;
	private int thrownWait;

	public ExperimentProfit(ProfitHudFeature panel) {
		this.panel = panel;
	}

	private ProfitTracker tracker() {
		return panel.tracker();
	}

	/** The table's menu was opened: the player stands next to it. */
	void tableOpened(Minecraft client) {
		if (client.player != null) {
			tablePlace = client.player.position();
			lastTable = tablePlace;
		}
	}

	/** An ULTRA-RARE book was uncovered in this game. */
	void ultraRareFound() {
		if (!panel.isEnabled()) {
			return;
		}
		ultraThisGame = true;
		tracker().count(ULTRA_RARE, 1L);
		tracker().setLifetimeCount(DRY, 0L);
		LumioApi.saveSoon();
	}

	/** Called every tick: reads a summary menu, and sees a thrown bottle leave the inventory. */
	public void tick(Minecraft client) {
		if (!panel.isEnabled() || client.player == null) {
			return;
		}
		if (client.gui.screen() instanceof ContainerScreen chest && ExperimentFormats.isOver(ExperimentHelperFeature.plain(chest.getTitle()))) {
			readSummary(chest);
		}
		if (thrownWait > 0 && --thrownWait == 0) {
			int gone = thrownBefore - countOf(client.player.getInventory(), thrownId);
			if (gone > 0) {
				tracker().count(BOTTLES, gone);
				tracker().addCost(SkyBlockPrices.price(thrownId, panel.priceMode()) * gone);
				LumioApi.saveSoon();
			}
			thrownId = null;
		}
	}

	private void readSummary(ContainerScreen chest) {
		var slots = chest.getMenu().slots;
		for (int place : new int[]{ExperimentFormats.SUPERPAIRS_OVER_SLOT, ExperimentFormats.ADDON_OVER_SLOT}) {
			if (place >= slots.size()) {
				continue;
			}
			Slot slot = slots.get(place);
			List<String> lines = ExperimentFormats.rewardLines(ExperimentHelperFeature.loreOf(slot.getItem()));
			if (!lines.isEmpty()) {
				pending = lines;
				return;
			}
		}
	}

	/** A message from the server: when it says the rewards were claimed, the summary read before counts, once. */
	public void onMessage(Component message) {
		if (!panel.isEnabled()) {
			return;
		}
		String game = ExperimentFormats.claimedGame(TextColors.noColors(message.getString()));
		if (game == null) {
			return;
		}
		if (pending.isEmpty() || pending.equals(lastClaimed)) {
			ExperimentHelperFeature.unknown("rewards claimed without a summary", message.getString());
			return;
		}
		lastClaimed = pending;
		pending = List.of();
		for (String line : lastClaimed) {
			count(line);
		}
		if (SUPERPAIRS.equals(game)) {
			tracker().addEvent();
			if (!ultraThisGame) {
				tracker().count(DRY, 1L);
			}
			ultraThisGame = false;
		}
		LumioApi.saveSoon();
	}

	private void count(String line) {
		ExperimentFormats.Reward reward = ExperimentFormats.reward(line);
		if (reward == null) {
			if (!line.contains("clicks")) {
				ExperimentHelperFeature.unknown("reward line", line);
			}
			return;
		}
		switch (reward.kind()) {
			case XP -> tracker().count(XP, reward.amount());
			case BOOK -> add(ExperimentFormats.bookId(reward.name()), reward.name());
			case BOTTLE -> add(ExperimentFormats.bottleId(reward.name()), reward.name());
			case OTHER -> add(SkyBlockPrices.idForName(reward.name()), reward.name());
		}
	}

	private void add(String id, String name) {
		if (id == null || id.isEmpty()) {
			ExperimentHelperFeature.unknown("reward without an item id", name);
			return;
		}
		tracker().add(id, name, 1L);
	}

	/** The player used an item: if it is an experience bottle near the table, what it took from the inventory is looked at a moment later. Only looked at: the use goes on as it is. */
	public void onUse(Minecraft client, ItemStack held) {
		String id = ItemGains.idOf(held);
		if (!panel.isEnabled() || thrownId != null || !ExperimentFormats.isBottleId(id) || tablePlace == null || client.player == null
			|| client.player.position().distanceToSqr(tablePlace) > NEAR_TABLE_SQUARED) {
			return;
		}
		thrownId = id;
		thrownBefore = countOf(client.player.getInventory(), id);
		thrownWait = THROW_TICKS;
	}

	private static int countOf(Inventory inventory, String id) {
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (id.equals(ItemGains.idOf(stack))) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
