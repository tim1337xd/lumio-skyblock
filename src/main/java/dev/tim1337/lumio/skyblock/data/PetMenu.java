package dev.tim1337.lumio.skyblock.data;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's Pets menu, as plain text: whether a title is it, and which of its items is the pet that is out. The summoned pet's lore offers "Click
 * to despawn!"; where no item says so, the pet of the player list's "Pet:" widget (its level and name) is looked for instead. Tested without the
 * game.
 */
public final class PetMenu {
	/**
	 * The menu's title: "Pets", with its page before or after it ("(1/3) Pets", "Pets (1/3)"), or with a search ("Pets: "dragon""); the same from
	 * the SkyBlock Menu.
	 */
	private static final Pattern TITLE = Pattern.compile("^(?:\\(\\d{1,3}/\\d{1,3}\\) )?Pets(?:: \".*\")?(?: \\(\\d{1,3}/\\d{1,3}\\))?$");
	/** The summoned pet's lore line, compared without colors and case. */
	private static final String DESPAWN = "click to despawn";
	/** A pet item's name: "[Lvl 100] Golden Dragon", maybe with a favorite star before it, a skin counter or a skin star. */
	private static final Pattern PET_NAME = Pattern.compile("^(?:⭐\\s*)?\\[Lvl\\s+(\\d{1,4})]\\s*(?:\\[\\d{1,6}✦]\\s*)?(.+?)(?:\\s*✦)?$");

	/** The slot chosen, and why; {@link #NONE} when no pet is out. */
	public record Found(int slot, String reason) {
	}

	public static final Found NONE = new Found(-1, "");

	private PetMenu() {
	}

	/** @param title the menu's title without colors */
	public static boolean isTitle(String title) {
		return TITLE.matcher(title.strip()).matches();
	}

	/**
	 * @param names    the menu's item names without colors, by slot ("" for an empty slot)
	 * @param lores    their lore lines without colors
	 * @param tabName  the pet of the player list's widget, or null where it is not shown
	 * @param tabLevel its level
	 * @return the slot of the pet that is out: by its lore, else by the player list
	 */
	public static Found find(List<String> names, List<List<String>> lores, String tabName, int tabLevel) {
		for (int i = 0; i < lores.size(); i++) {
			for (String line : lores.get(i)) {
				if (line.toLowerCase(Locale.ROOT).contains(DESPAWN)) {
					return new Found(i, "lore");
				}
			}
		}
		if (tabName == null || tabName.isEmpty()) {
			return NONE;
		}
		String wanted = kind(tabName);
		for (int i = 0; i < names.size(); i++) {
			Matcher matcher = PET_NAME.matcher(names.get(i).strip());
			if (matcher.matches() && Integer.parseInt(matcher.group(1)) == tabLevel && kind(matcher.group(2)).equals(wanted)) {
				return new Found(i, "tab list");
			}
		}
		return NONE;
	}

	/** @return a pet's name compared without skin marks and case: "golden dragon" */
	static String kind(String name) {
		Matcher matcher = PET_NAME.matcher("[Lvl 1] " + name.strip());
		return (matcher.matches() ? matcher.group(2) : name).strip().toLowerCase(Locale.ROOT);
	}
}
