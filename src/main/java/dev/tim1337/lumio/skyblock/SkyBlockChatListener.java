package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.chat.LearnedCommands;
import dev.tim1337.lumio.skyblock.data.ChatGuard;
import dev.tim1337.lumio.skyblock.data.HotmTree;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.PartyList;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.data.RecentChatNames;
import dev.tim1337.lumio.skyblock.hud.RareDropToast;
import dev.tim1337.lumio.skyblock.slayer.SlayerBosses;

import net.minecraft.network.chat.Component;

/**
 * The SkyBlock trackers' chat readers. Only the corpse waypoints read player messages (party shares), and the command suggestions the name of who wrote one; everything else reacts to the server's
 * messages only (see {@link ChatGuard}), and every reader runs guarded, so one that fails can never interrupt the game's handling of a message.
 */
final class SkyBlockChatListener implements ChatListener {
	@Override
	public void onServerMessage(Component message) {
		// corpses shared by party members, as waypoints: the only reader of player messages
		ChatGuard.read("corpse waypoints", () -> SkyBlockFeatures.CORPSES.onMessage(message));
		// everything else reacts to the server's messages only: a player typing the same words must not change the trackers
		if (ChatGuard.fromPlayer(message)) {
			// but who wrote is kept for the command suggestions: only the sender in front of the line, never what it says
			ChatGuard.read("recent chat names", () -> RecentChatNames.INSTANCE.read(TextColors.noColors(message.getString()).stripLeading()));
			return;
		}
		// arrows: how many are left, as the server says
		ChatGuard.read("arrows", () -> SkyBlockFeatures.ARROWS.onMessage(message));
		// which profile's storage is kept
		ChatGuard.read("storage profile", () -> StorageTracker.onMessage(message));
		// the party's members, for the command suggestions: learned from the server's party messages only
		ChatGuard.read("party list", () -> PartyList.INSTANCE.onMessage(message));
		// subcommands from help messages and the names seen, for the command suggestions
		ChatGuard.read("learned commands", () -> LearnedCommands.onMessage(message));
		// pets: summoned, despawned, or changed by an autopet rule
		ChatGuard.read("pet tracker", () -> PetTracker.onMessage(message));
		// rare drops, for their toast
		ChatGuard.read("rare drop toast", () -> RareDropToast.onMessage(message));
		ChatGuard.read("experiment rewards", () -> SkyBlockFeatures.EXPERIMENT_REWARDS.onMessage(message));
		ChatGuard.read("dragon tracker", () -> SkyBlockFeatures.DRAGON_REWARDS.onMessage(message));
		ChatGuard.read("grappling hook", () -> SkyBlockFeatures.GRAPPLING_HOOK.onMessage(message));
		// sacks: items added to them, for the profit trackers
		ChatGuard.read("sack tracker", () -> ItemGains.onMessage(message));
		// Heart of the Mountain tree changes
		ChatGuard.read("HotM tree", () -> HotmTree.onMessage(message));
		// mineshaft found, for the pity counter
		ChatGuard.read("mineshaft pity", () -> SkyBlockFeatures.MINESHAFT_PITY.onMessage(message));
		// pest spawns, for the time between them
		ChatGuard.read("pest tracker", () -> SkyBlockFeatures.PESTS.onMessage(message));
		// Pesthunter Phillip's fortune bonus
		ChatGuard.read("farming panel", () -> SkyBlockFeatures.FARMING_HUD.onMessage(message));
		// sea creature catches and double hooks
		ChatGuard.read("sea creature tracker", () -> SkyBlockFeatures.SEA_CREATURES.onMessage(message));
		// pickaxe ability: used, expired, ready
		ChatGuard.read("pickaxe ability timer", () -> SkyBlockFeatures.MINING_ABILITY.onMessage(message));
		// slayer boss kills, for bosses per hour
		ChatGuard.read("slayer tracker", () -> SkyBlockFeatures.SLAYER_PROFIT.onMessage(message));
		ChatGuard.read("slayer bosses", () -> SlayerBosses.onMessage(message));
		ChatGuard.read("boss drops", () -> SkyBlockFeatures.MINIBOSSES.onMessage(message));
		ChatGuard.read("cocoons", () -> SkyBlockFeatures.COCOONS.onMessage(message));
		ChatGuard.read("mouse lock", () -> SkyBlockFeatures.MOUSE_LOCK.onMessage(message));
		ChatGuard.read("slayer panel", () -> SkyBlockFeatures.SLAYER_PHASE.onMessage(message));
	}

	@Override
	public void onChatLine(Component message) {
		// pet messages are also read where they enter the chat, so the tracker sees exactly what the chat shows
		if (!ChatGuard.fromPlayer(message)) {
			ChatGuard.read("pet tracker", () -> PetTracker.onMessage(message));
		}
	}
}
