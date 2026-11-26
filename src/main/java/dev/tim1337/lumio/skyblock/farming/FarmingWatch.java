package dev.tim1337.lumio.skyblock.farming;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;

/**
 * Looks once a tick whether the player wants to farm (see {@link FarmingCondition}) and shares the answer: the Mouse lock and the Farming Keys
 * both act on this one answer, so they can never disagree. Outside the Garden it returns after one cheap check. Whenever the answer flips, the
 * lines it was made from go to the debug log.
 */
public final class FarmingWatch {
	private static final FarmingCondition CONDITION = new FarmingCondition();
	private static final FarmingCondition.Input NOWHERE = new FarmingCondition.Input(false, false, false, false, false, false);
	private static String heldItem = "";
	private static boolean inGarden;
	private static boolean was;
	/** A place that cannot be read keeps what was known for this long, in ticks; after that the player is somewhere else. */
	private static final int MOST_UNREAD_TICKS = 100;
	private static int unreadTicks;

	private FarmingWatch() {
	}

	/** Starts the look, before the features that read it in the same tick. */
	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(FarmingWatch::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null) {
			CONDITION.tick(NOWHERE);
			heldItem = "";
			inGarden = false;
			was = false;
			return;
		}
		String island = SkyBlockArea.island();
		String place = SkyBlockArea.zone();
		// the island decides; the scoreboard's line only stands in for it, and an unreadable moment keeps what was known
		unreadTicks = island.isBlank() && place.isBlank() ? unreadTicks + 1 : 0;
		inGarden = unreadTicks <= MOST_UNREAD_TICKS && (FarmingCondition.inGarden(island, place, inGarden) || SkyBlockArea.inGarden());
		if (!inGarden) {
			CONDITION.tick(NOWHERE);
			heldItem = "";
			note(false, island, place, null);
			return;
		}
		String id = ItemGains.idOf(client.player.getMainHandItem());
		id = id == null ? "" : id;
		heldItem = client.player.getInventory().getSelectedSlot() + ":" + id;
		FarmingCondition.Input input = new FarmingCondition.Input(true, ProfitActivity.farmingTool(id), client.player.onGround(), client.player.getAbilities().flying,
			FarmingCondition.barn(client.player.getX(), client.player.getZ()), client.gui.screen() != null);
		note(CONDITION.tick(input), island, place, input);
	}

	/** For the debug log: the answer flipped, with the lines and facts it was made from. */
	private static void note(boolean now, String island, String place, FarmingCondition.Input input) {
		if (now == was) {
			return;
		}
		was = now;
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.FARMING, "Farming is {}: island \"{}\", place line \"{}\", {}", now ? "on" : "off", island, place,
				input == null ? "not in the Garden" : "tool " + input.tool() + ", on the ground " + input.onGround() + ", flying " + input.flying() + ", on the Barn "
					+ input.barn() + ", a screen open " + input.screenOpen());
		}
	}

	/** @return whether the player wants to farm now: in the Garden, a farming tool held, not flying, not on the Barn, no screen open */
	static boolean farming() {
		return CONDITION.farming();
	}

	/** @return the held item, as its hotbar slot and id, to tell a switch of items */
	static String heldItem() {
		return heldItem;
	}
}
