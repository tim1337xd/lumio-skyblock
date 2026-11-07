package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detects whether the player is on Hypixel and in SkyBlock. No single signal is relied on, since any can be missing (a server joined via a link has
 * no address in the server list, another mod may replace the scoreboard): the address, the scoreboard, and SkyBlock-only signals (health and mana
 * in the action bar, pet messages) all count.
 */
public final class SkyBlock {
	/** Until when a SkyBlock-only signal was seen, so SkyBlock is detected even without the scoreboard. */
	private static volatile long evidenceUntil;
	private static long checkedAt;
	private static boolean sidebarSkyBlock;
	private static String sidebarTitle = "";

	private SkyBlock() {
	}

	/** Records a SkyBlock-only signal: counts as SkyBlock for the given time. */
	public static void evidence(long milliseconds) {
		evidenceUntil = Math.max(evidenceUntil, System.currentTimeMillis() + milliseconds);
	}

	private static boolean addressIsHypixel() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		if (server == null) {
			return false;
		}
		return server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel")
			|| server.name != null && server.name.toLowerCase(Locale.ROOT).contains("hypixel");
	}

	/** @return whether the player is on Hypixel; other servers use the same symbols for other things, and their text is left untouched */
	public static boolean onHypixel() {
		return addressIsHypixel() || inSkyBlock();
	}

	/** @return whether SkyBlock features are shown here: in SkyBlock, or everywhere when the global "Only in SkyBlock" switch is off */
	public static boolean allowed() {
		var only = SkyBlockFeatures.SKYBLOCK_ONLY;
		return only == null || !only.get() || inSkyBlock();
	}

	/** @return whether the player is in SkyBlock: its name is in the scoreboard title and its data in the lines. Rechecked periodically, not every frame. */
	public static boolean inSkyBlock() {
		if (System.currentTimeMillis() < evidenceUntil) {
			return true;
		}
		refresh();
		return sidebarSkyBlock;
	}

	private static void refresh() {
		long now = System.currentTimeMillis();
		if (now - checkedAt < 1000) {
			return;
		}
		checkedAt = now;
		sidebarSkyBlock = false;
		sidebarTitle = "";
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return;
		}
		sidebarTitle = sidebar.getDisplayName().getString();
		if (sidebarTitle.toUpperCase(Locale.ROOT).contains("SKYBLOCK")) {
			sidebarSkyBlock = true;
			return;
		}
		// the title may be written differently: the lines are SkyBlock's (purse, bits, location)
		try {
			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
				String line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString();
				if (line.contains("Purse:") || line.contains("Piggy:") || line.contains("Bits:") || line.contains("Motes:")) {
					sidebarSkyBlock = true;
					return;
				}
			}
		} catch (RuntimeException ignored) {
		}
	}

	/** @return the detection state, for the debug command */
	public static List<String> describe() {
		Minecraft client = Minecraft.getInstance();
		refresh();
		List<String> lines = new ArrayList<>();
		ServerData server = client.getCurrentServer();
		lines.add("Server: " + (server == null ? "none (a link, or a world)" : server.name + " / " + server.ip));
		lines.add("On Hypixel: " + onHypixel() + ", in SkyBlock: " + inSkyBlock() + (System.currentTimeMillis() < evidenceUntil ? " (seen from what it says)" : ""));
		lines.add("Scoreboard title: \"" + sidebarTitle + "\"");
		lines.add("Health: " + (SkyBlockStats.healthFresh() ? SkyBlockStats.health() + "/" + SkyBlockStats.maxHealth() : "not read")
			+ ", mana: " + (SkyBlockStats.manaFresh() ? SkyBlockStats.mana() + "/" + SkyBlockStats.maxMana() : "not read"));
		lines.add("Last text above the hotbar: " + SkyBlockStats.lastRaw());
		lines.add("Zone (scoreboard): \"" + SkyBlockArea.zone() + "\", island (player list): \"" + SkyBlockArea.island() + "\"");
		lines.add("In the mines: " + SkyBlockArea.inMines() + ", Dwarven Mines: " + SkyBlockArea.inDwarvenMines() + ", Garden: " + SkyBlockArea.inGarden());
		lines.add("Doing now: mining " + ProfitActivity.MINING.now() + ", farming " + ProfitActivity.FARMING.now() + ", slayer " + ProfitActivity.SLAYER.now()
			+ " (slayer quest " + SkyBlockArea.slayerQuest() + ")");
		lines.add(dev.tim1337.lumio.skyblock.hud.SpeedBar.debugLine());
		var pet = PetTracker.current();
		lines.add("Last message about a pet that was understood: " + PetTracker.lastPetText());
		lines.add("Pet: " + (pet == null ? "none known (open the pets menu, or summon one)" : pet.name() + " lvl " + pet.level()));
		return lines;
	}
}
