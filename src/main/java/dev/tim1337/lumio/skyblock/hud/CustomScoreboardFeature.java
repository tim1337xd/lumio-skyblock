package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.ScoreLines;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.OrderedChoice;
import dev.tim1337.lumio.api.setting.TextInput;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.skyblock.data.Mayor;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;


import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A customizable Hypixel SkyBlock scoreboard, like SkyHanni's custom scoreboard: every server line is classified (date, location, purse, bits,
 * slayer quest, ...), and the parts are shown in the chosen order, disabled ones omitted. Server lines that match no part go into "Other lines",
 * so nothing is lost. The scoreboard panel renders these lines.
 */
public class CustomScoreboardFeature extends AddonFeature {
	private static OrderedChoice.Entry option(String id, String title, String icon, boolean on) {
		return new OrderedChoice.Entry(id, title, icon, on);
	}

	private final OrderedChoice parts = add(make().ordered("parts", Component.literal("Lines and their order"), List.of(
		new OrderedChoice.Entry("space", "Space", "minus", false, true),
		new OrderedChoice.Entry("separator", "Line", "minus", false, true),
		option("server", "Server and date", "info", false),
		option("date", "SkyBlock date", "clock", true),
		option("time", "Time of day", "sun", true),
		option("location", "Location", "pin", true),
		option("cold", "Cold (Glacite)", "snow", true),
		option("profile", "Profile", "person", false),
		option("level", "SkyBlock level", "star", false),
		option("mayor", "Mayor and minister", "people", true),
		option("purse", "Purse", "coins", true),
		option("bank", "Bank", "coins", true),
		option("interest", "Bank interest", "clock", false),
		option("bits", "Bits", "star", true),
		option("motes", "Motes", "sparkle", true),
		option("copper", "Copper and Sowdust", "wheat", true),
		option("heat", "Heat", "bolt", true),
		option("north_stars", "North Stars", "snow", true),
		option("powder", "Powder (in the mines)", "pickaxe", true),
		option("slayer", "Slayer quest", "sword", true),
		option("objective", "Objective", "list", true),
		option("other", "Other lines (events, timers)", "bell", true),
		option("footer", "Footer (www.hypixel.net)", "info", false))).defaultLayout(List.of(
		"date", "time", "location", "cold", "mayor", "space",
		"purse", "bank", "bits", "motes", "copper", "heat", "north_stars", "powder", "space",
		"slayer", "objective", "other")), "Content", "Switch lines on or off, drag to order");
	private final TextInput titleText = add(make().text("title_text", Component.literal("Title"), "", false), "Text",
		"Empty: as the server writes it. & for colors, such as &e&lSKYBLOCK");
	private final TextInput footerText = add(make().text("footer_text", Component.literal("Footer"), "&ewww.hypixel.net", false), "Text",
		"The line of the footer, & for colors");


	/** The lines of currencies, whose numbers are written short. */
	private static final List<String> SHORT_NUMBERS = List.of("purse", "bank", "bits", "motes", "copper", "north_stars", "powder");

	private static final Pattern SERVER = Pattern.compile("^\\d{2}/\\d{2}/\\d{2}");
	private static final Pattern DATE = Pattern.compile("^(?:Early |Late )?(?:Spring|Summer|Autumn|Fall|Winter)\\s+\\d+");
	private static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2}\\s?(?:am|pm)", Pattern.CASE_INSENSITIVE);

	public CustomScoreboardFeature() {
		super("custom_scoreboard", "Custom scoreboard", "The scoreboard of SkyBlock with the lines you choose, in your order. Its look is set in the scoreboard panel.", false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("list_check");
		summary("The SkyBlock scoreboard with the lines you choose");
		addResetButton();
	}

	/** @return the list of lines and their order, which the scoreboard's settings card offers too */
	public OrderedChoice partsSetting() {
		return parts;
	}

	/** @return whether the scoreboard lines are built here right now: enabled and on SkyBlock */
	public boolean active() {
		return isEnabled() && SkyBlock.inSkyBlock();
	}

	/** @return text written with & color codes, converted to the game's § codes */
	private static String colored(String text) {
		return text.indexOf('&') < 0 ? text : AMPERSAND_COLOR.matcher(text).replaceAll("§$1");
	}

	private static final Pattern AMPERSAND_COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

	/** @return the configured title, or null to keep the server's */
	public Component title() {
		String text = titleText.get().strip();
		return text.isEmpty() ? null : Component.literal(colored(text));
	}

	private static String plain(Component line) {
		return SkyBlockArea.plainLine(line.getString());
	}

	/**
	 * @param sidebar the server's scoreboard lines, top to bottom
	 * @return the displayed lines: the used parts in their order, with an empty line for every spacer
	 */
	public List<Component> lines(List<Component> sidebar) {
		Map<String, List<Component>> sorted = new HashMap<>();
		// slayer quest and objective are a heading with lines below it that belong to it
		String follow = null;
		int followLeft = 0;
		for (Component line : sidebar) {
			String text = plain(line);
			if (text.isEmpty()) {
				follow = null;
				continue;
			}
			if (follow != null && followLeft > 0) {
				sorted.computeIfAbsent(follow, key -> new ArrayList<>()).add(line);
				followLeft--;
				continue;
			}
			follow = null;
		// the location is recognized by its leading symbol (now a pin), which the plain text no longer contains
			String raw = line.getString();
			String part = SkyBlockArea.isLocationRaw(raw) ? "location" : partOf(text);
			if (part.equals("location")) {
				logLocation(line, raw);
			}
			if (part.equals("slayer")) {
				follow = "slayer";
				followLeft = 2;
			} else if (part.equals("objective")) {
				follow = "objective";
				followLeft = 1;
			}
			sorted.computeIfAbsent(part, key -> new ArrayList<>()).add(line);
		}
		List<Component> powder = powder();
		if (!powder.isEmpty()) {
			sorted.put("powder", powder);
		}

		// parts from the player list (the profile widget), and the mayor, which is fetched from the Hypixel API
		putTab(sorted, "profile", "Profile:", "Profile", "§a");
		putTab(sorted, "level", "SB Level:", "Level", "§b");
		putTab(sorted, "bank", "Bank:", "Bank", "§6");
		putTab(sorted, "interest", "Interest:", "Interest", "§e");
		for (String id : SHORT_NUMBERS) {
			sorted.computeIfPresent(id, (key, found) -> found.stream().map(ScoreValues::shortened).toList());
		}
		String mayor = Mayor.line();
		if (!mayor.isEmpty()) {
			sorted.put("mayor", List.of(Component.literal(mayor)));
		}
		// the footer is the configured text, whatever the server shows there
		String footer = footerText.get().strip();
		sorted.put("footer", footer.isEmpty() ? List.of() : List.of(Component.literal(colored(footer))));
		List<Component> out = new ArrayList<>();
		Component spaceWanted = null;
		for (String id : parts.enabledInOrder()) {
			if (id.startsWith("space#") || id.startsWith("separator#")) {
				// spacers and separators only between lines: never at the top, and never doubled where the part between them is currently absent (no slayer quest)
				spaceWanted = out.isEmpty() ? null : id.startsWith("space#") ? Component.empty() : ScoreLines.LINE;
				continue;
			}
			List<Component> lines = sorted.get(id);
			if (lines == null || lines.isEmpty()) {
				continue;
			}
			if (spaceWanted != null) {
				out.add(spaceWanted);
				spaceWanted = null;
			}
			out.addAll(lines);
		}
		return out;
	}

	/** Under the debug switch, once per change: what the location's line is really made of, for when its symbol is not found. */
	private void logLocation(Component line, String raw) {
		if (Debug.on() && !raw.equals(loggedLocation)) {
			loggedLocation = raw;
			Debug.log("Location line, symbol at {}: {}", ScoreLines.locationStart(raw), ScoreLines.describe(line));
		}
	}

	private String loggedLocation = "";

	/** @return the part a server line belongs to */
	private static String partOf(String text) {
		if (SERVER.matcher(text).find()) {
			return "server";
		}
		if (DATE.matcher(text).find()) {
			return "date";
		}
		if (TIME.matcher(text).find()) {
			return "time";
		}
		if (SkyBlockArea.isLocationLine(text)) {
			return "location";
		}
		// the zone is already known from the scoreboard (the mod's area detection uses it): the line containing its name is the location, whatever precedes it
		String zone = SkyBlockArea.zone();
		if (zone.length() >= 3 && text.length() <= zone.length() + 8 && text.contains(zone)) {
			return "location";
		}
		if (text.startsWith("Cold:") || text.contains("Cold:") && text.indexOf("Cold:") <= 4) {
			return "cold";
		}
		if (text.startsWith("Purse:") || text.startsWith("Piggy:")) {
			return "purse";
		}
		if (text.startsWith("Bits:")) {
			return "bits";
		}
		if (text.startsWith("Motes:")) {
			return "motes";
		}
		if (text.startsWith("Copper:") || text.startsWith("Sowdust:")) {
			return "copper";
		}
		if (text.startsWith("Heat:")) {
			return "heat";
		}
		if (text.startsWith("North Stars:")) {
			return "north_stars";
		}
		if (text.startsWith("Slayer Quest")) {
			return "slayer";
		}
		if (text.startsWith("Objective")) {
			return "objective";
		}
		if (text.contains("hypixel.net")) {
			return "footer";
		}
		return "other";
	}

	/** Adds a player list line starting with the prefix ("Bank: 12.3M"), shown with its own label in a color, when present. */
	private static void putTab(Map<String, List<Component>> sorted, String id, String prefix, String label, String color) {
		for (String line : SkyBlockArea.tab()) {
			if (line.startsWith(prefix)) {
				String value = line.substring(prefix.length()).strip();
				if (!value.isEmpty()) {
					sorted.put(id, List.of(Component.literal("§f" + label + ": " + color + value)));
				}
				return;
			}
		}
	}

	/** @return the mining powders from the player list widget, as lines in their colors */
	private static List<Component> powder() {
		if (!SkyBlockArea.inMines()) {
			return List.of();
		}
		Map<String, String> found = new LinkedHashMap<>();
		for (String line : SkyBlockArea.tab()) {
			for (String kind : new String[]{"Mithril", "Gemstone", "Glacite"}) {
				if (line.startsWith(kind + ":") && !found.containsKey(kind)) {
					found.put(kind, line.substring(kind.length() + 1).strip());
				}
			}
		}
		List<Component> out = new ArrayList<>();
		Map<String, String> colors = Map.of("Mithril", "§2", "Gemstone", "§d", "Glacite", "§b");
		found.forEach((kind, value) -> out.add(Component.literal("§f" + kind + ": " + colors.get(kind) + value)));
		return out;
	}
}
