package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.ScoreLines;
import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's location in Hypixel SkyBlock and the server's status information, read from the two places it is shown: the sidebar scoreboard
 * lines (zone, purse, slayer quest) and the player list (island, and widgets enabled via /widgets, such as commissions). Both are read once per
 * second, not every frame.
 */
public final class SkyBlockArea {
	private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+)");
	/** A commission line in the player list: its name, and its progress or completion. */
	private static final Pattern COMMISSION = Pattern.compile("^(.+?):\\s*(DONE|COMPLETED|[\\d.]+%)$", Pattern.CASE_INSENSITIVE);

	/** A mining commission: its name, progress from 0 to 1, and whether it is complete. */
	public record Commission(String name, float progress, boolean done) {
	}

	private static long readAt;
	private static List<String> sidebar = List.of();
	private static List<String> tab = List.of();
	private static String zone = "";
	private static String island = "";
	private static long purse = -1;
	private static boolean slayerQuest;
	private static String slayerBoss = "";
	private static boolean bossSlain;
	private static String slayerProgress = "";
	/** When the slayer quest progress last changed, i.e. a quest mob was killed. */
	private static long slayerProgressAt;
	private static boolean bossUp;
	private static List<Commission> commissions = List.of();

	private SkyBlockArea() {
	}

	private static void refresh() {
		long now = System.currentTimeMillis();
		if (now - readAt < 1000) {
			return;
		}
		readAt = now;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			sidebar = List.of();
			tab = List.of();
			zone = "";
			island = "";
			purse = -1;
			slayerQuest = false;
			bossSlain = false;
			commissions = List.of();
			return;
		}
		readSidebar(client);
		readTab(client);
	}

	/**
	 * @return a scoreboard line as plain text: without colors and invisible characters (the server puts invisible characters and emoji into its lines,
	 *         also before the location symbol, so a line showing "⏣ Glacite Tunnels" did not start with the symbol in its text)
	 */
	public static String plainLine(String line) {
		String text = UNSEEN.matcher(TextColors.noColors(line)).replaceAll("");
		return text.indexOf("  ") < 0 ? text.strip() : SPACES.matcher(text).replaceAll(" ").strip();
	}

	/**
	 * Characters removed from a line: invisible ones (format, lone surrogates, private use) and the symbols the server uses as separators, but not the
	 * SkyBlock symbols (location, cold, time of day, powders). Compiled once: it is a large pattern and used to be compiled for every line read.
	 */
	private static final Pattern UNSEEN = Pattern.compile("[\\p{Cf}\\p{Cs}\\p{Co}\\p{Cn}]|[\\p{So}&&[^⏣❄☀☽✎⸕⸎♲✦✧⚔⛏❤❈☠◆◇✿⁂]]");
	private static final Pattern SPACES = Pattern.compile(" {2,}");
	private static final Pattern FORMATS = Pattern.compile("\\p{Cf}");

	/** @return whether a plain scoreboard line is the location: "⏣ Glacite Tunnels", or "ф" in the Rift */
	public static boolean isLocationLine(String line) {
		int sign = Math.max(line.indexOf('⏣'), line.indexOf('ф'));
		return sign >= 0 && sign <= 8;
	}

	/**
	 * @return whether a raw scoreboard line (with its symbols) is the location: a leading symbol ("⏣", "ф", or the pin emoji Hypixel uses now)
	 *         followed by a name without a colon ("📍 Cliffside Veins")
	 */
	public static boolean isLocationRaw(String raw) {
		if (ScoreLines.locationStart(raw) >= 0 && raw.indexOf(':') < 0) {
			return true;
		}
		String text = FORMATS.matcher(TextColors.noColors(raw)).replaceAll("").strip();
		if (text.isEmpty()) {
			return false;
		}
		int first = text.codePointAt(0);
		if (Character.isLetterOrDigit(first)) {
			return false;
		}
		String name = plainLine(text.substring(Character.charCount(first)));
		while (!name.isEmpty() && !Character.isLetterOrDigit(name.codePointAt(0))) {
			name = name.substring(Character.charCount(name.codePointAt(0))).strip();
		}
		return !name.isEmpty() && Character.isUpperCase(name.codePointAt(0)) && name.indexOf(':') < 0 && name.length() <= 40;
	}

	/** @return the location name of a location line (see {@link #isLocationRaw}), without the leading symbol */
	public static String locationName(String raw) {
		String name = plainLine(raw);
		while (!name.isEmpty() && !Character.isLetterOrDigit(name.codePointAt(0))) {
			name = name.substring(Character.charCount(name.codePointAt(0))).strip();
		}
		return name;
	}

	/** @return the location of a scoreboard location line, without its symbol */
	private static String locationOf(String line) {
		int sign = line.indexOf('⏣') >= 0 ? line.indexOf('⏣') : line.indexOf('ф');
		return line.substring(sign + 1).strip();
	}

	private static void readSidebar(Minecraft client) {
		List<String> lines = new ArrayList<>();
		String rawZone = "";
		try {
			Scoreboard scoreboard = client.level.getScoreboard();
			Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (objective != null) {
				List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
				entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
				for (PlayerScoreEntry entry : entries) {
					if (entry.isHidden()) {
						continue;
					}
					String line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString();
					lines.add(plainLine(line));
					// the location is recognized by its leading symbol, which the plain line no longer contains
					if (rawZone.isEmpty() && isLocationRaw(line)) {
						rawZone = locationName(line);
					}
				}
			}
		} catch (RuntimeException ignored) {
		}
		sidebar = lines;
		String foundZone = rawZone;
		long foundPurse = -1;
		boolean quest = false;
		boolean slain = false;
		String boss = "";
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (isLocationLine(line) && foundZone.isEmpty()) {
				foundZone = locationOf(line);
			}
			Matcher matcher = PURSE.matcher(line);
			if (matcher.find()) {
				try {
					foundPurse = Long.parseLong(matcher.group(1).replace(",", ""));
				} catch (NumberFormatException ignored) {
				}
			}
			if (line.strip().contains("Slayer Quest")) {
				quest = true;
				if (i + 1 < lines.size()) {
					boss = lines.get(i + 1).strip();
				}
				// below the boss: the quest progress ("(120/1,500) Combat XP"), or that the boss has spawned ("Slay the boss!")
				if (i + 2 < lines.size()) {
					String progress = lines.get(i + 2).strip();
					if (!progress.equals(slayerProgress)) {
						if (!slayerProgress.isEmpty()) {
							slayerProgressAt = System.currentTimeMillis();
						}
						slayerProgress = progress;
					}
					bossUp = progress.toLowerCase(Locale.ROOT).contains("slay the boss");
				}
			}
			if (line.contains("Boss slain!")) {
				slain = true;
			}
		}
		zone = foundZone;
		purse = foundPurse;
		slayerQuest = quest;
		slayerBoss = boss;
		bossSlain = slain;
	}

	private static void readTab(Minecraft client) {
		List<String> lines = new ArrayList<>();
		try {
			if (client.getConnection() != null) {
				List<PlayerInfo> infos = new ArrayList<>(client.getConnection().getListedOnlinePlayers());
				infos.sort(Comparator.<PlayerInfo>comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
					.thenComparingInt(info -> -info.getTabListOrder())
					.thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
					.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
				for (PlayerInfo info : infos) {
					Component shown = client.gui.hud.getTabList().getNameForDisplay(info);
					lines.add(TextColors.noColors(shown.getString()).strip());
				}
			}
		} catch (RuntimeException ignored) {
		}
		tab = lines;
		String foundIsland = "";
		List<Commission> found = new ArrayList<>();
		boolean inCommissions = false;
		for (String line : lines) {
			if (line.startsWith("Area:") || line.startsWith("Dungeon:")) {
				foundIsland = line.substring(line.indexOf(':') + 1).strip();
			}
			if (line.equalsIgnoreCase("Commissions:")) {
				inCommissions = true;
				continue;
			}
			if (inCommissions) {
				if (line.isEmpty() || line.endsWith(":")) {
					inCommissions = false;
					continue;
				}
				Matcher matcher = COMMISSION.matcher(line);
				if (matcher.matches()) {
					String state = matcher.group(2).toUpperCase(Locale.ROOT);
					boolean done = state.equals("DONE") || state.equals("COMPLETED");
					float progress = 1f;
					if (!done) {
						try {
							progress = Float.parseFloat(state.substring(0, state.length() - 1)) / 100f;
						} catch (NumberFormatException ignored) {
							progress = 0f;
						}
					}
					found.add(new Commission(matcher.group(1).strip(), Math.max(0f, Math.min(1f, progress)), done));
				}
			}
		}
		island = foundIsland;
		commissions = found;
	}

	/** @return the sidebar scoreboard lines, without colors */
	public static List<String> sidebar() {
		refresh();
		return sidebar;
	}

	/** @return the player list lines, without colors */
	public static List<String> tab() {
		refresh();
		return tab;
	}

	/** @return the zone within the island as the scoreboard shows it after its symbol: "Dwarven Village" */
	public static String zone() {
		refresh();
		return zone;
	}

	/** @return the island as the player list shows it after "Area:": "Dwarven Mines" */
	public static String island() {
		refresh();
		return island;
	}

	/** @return the purse coins, or -1 if the scoreboard does not show them */
	public static long purse() {
		refresh();
		return purse;
	}

	/** @return whether a slayer quest is active: the scoreboard shows it from start until it is turned in */
	public static boolean slayerQuest() {
		refresh();
		return slayerQuest;
	}

	/** @return whether the slayer quest progressed within the given milliseconds, i.e. a quest mob was killed and slayer is being done */
	public static boolean slayerProgressWithin(long millis) {
		refresh();
		return slayerProgressAt > 0 && System.currentTimeMillis() - slayerProgressAt < millis;
	}

	/** @return milliseconds since a slayer quest mob was last killed, or -1 if none since the game started */
	public static long slayerProgressAge() {
		refresh();
		return slayerProgressAt == 0 ? -1 : System.currentTimeMillis() - slayerProgressAt;
	}

	/** @return the slayer quest's progress line as the scoreboard shows it: "(1,840/2,400) Combat XP", or "" */
	public static String slayerProgress() {
		refresh();
		return slayerProgress;
	}

	/** @return whether the slayer boss has spawned and can be slain */
	public static boolean slayerBossUp() {
		refresh();
		return bossUp;
	}

	/** @return the slayer quest boss as the scoreboard shows it: "Revenant Horror IV" */
	public static String slayerBoss() {
		refresh();
		return slayerBoss;
	}

	/** @return whether the slayer boss is slain and the quest can be turned in */
	public static boolean bossSlain() {
		refresh();
		return bossSlain;
	}

	/** @return the mining commissions from the player list widget (enabled via /widgets) */
	public static List<Commission> commissions() {
		refresh();
		return commissions;
	}

	private static boolean matches(String text, String... words) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String word : words) {
			if (lower.contains(word)) {
				return true;
			}
		}
		return false;
	}

	// Location flags, derived from island and zone only when they are re-read (once per second) rather than on every query: panels query every
	// frame, and each query used to be dozens of name lookups.
	private static String placesIsland;
	private static String placesZone;
	private static boolean mines;
	private static boolean dwarvenMines;
	private static boolean garden;

	private static void places() {
		refresh();
		// the strings are only new objects when re-read: identical instances mean the same location, nothing to recompute
		if (island == placesIsland && zone == placesZone) {
			return;
		}
		placesIsland = island;
		placesZone = zone;
		mines = matches(island, "dwarven mines", "crystal hollows", "mineshaft", "deep caverns", "gold mine", "glacite")
			|| matches(zone, "dwarven", "crystal hollows", "mineshaft", "glacite", "mithril", "royal mines", "lava springs", "cliffside veins", "rampart", "upper mines",
			"goblin", "jungle", "magma fields", "precursor", "khazad", "forge", "gold mine", "deep caverns", "gunpowder", "lapis", "pigmen", "slimehill", "diamond reserve", "obsidian sanctuary");
		dwarvenMines = matches(island, "dwarven mines", "mineshaft", "glacite") || matches(zone, "dwarven", "glacite", "royal mines", "lava springs", "cliffside veins",
			"rampart", "upper mines", "goblin burrows", "forge", "mineshaft", "fossil research", "great glacite lake");
		garden = matches(island, "garden") || matches(zone, "garden", "plot");
	}

	/** @return whether the player is on a mining island: Dwarven Mines, Crystal Hollows, a mineshaft, Deep Caverns or Gold Mine */
	public static boolean inMines() {
		places();
		return mines;
	}

	/** @return whether the player is in the Dwarven Mines or the Glacite Tunnels, where commissions are */
	public static boolean inDwarvenMines() {
		places();
		return dwarvenMines;
	}

	/** @return whether the player is in a Glacite Mineshaft, where the corpses are */
	public static boolean inMineshaft() {
		return island().toLowerCase(Locale.ROOT).contains("mineshaft") || zone().toLowerCase(Locale.ROOT).contains("mineshaft");
	}

	/** @return whether the player is in the Garden */
	public static boolean inGarden() {
		places();
		return garden;
	}
}
