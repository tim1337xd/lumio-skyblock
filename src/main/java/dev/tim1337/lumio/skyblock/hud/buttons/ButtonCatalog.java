package dev.tim1337.lumio.skyblock.hud.buttons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ready-made buttons of the editor's Quick add, in two groups, Warps and Menus, with the commands Hypixel accepts for them (the warps checked
 * against the item repository's island list: the Crimson Isle is /warp nether, the Crystal Hollows /warp crystals), and the names of known
 * commands for buttons the player typed.
 */
final class ButtonCatalog {
	/** A ready-made button: its group, name, command and icon. */
	record Ready(String group, String name, String command, ButtonIcon icon) {
	}

	static final String WARPS = "Warps";
	static final String MENUS = "Menus";
	static final List<Ready> ALL = List.of(
		new Ready(WARPS, "Island", "/warp home", ButtonIcon.symbol("tree", 0x34C759)),
		new Ready(WARPS, "Hub", "/warp hub", ButtonIcon.symbol("navigate", 0x64A8FF)),
		new Ready(WARPS, "Dungeon Hub", "/warp dungeon_hub", ButtonIcon.symbol("skull", 0xFF5E57)),
		new Ready(WARPS, "Garden", "/warp garden", ButtonIcon.symbol("leaf", 0x7ED957)),
		new Ready(WARPS, "Dwarven Mines", "/warp mines", ButtonIcon.symbol("pickaxe", 0x30C8E0)),
		new Ready(WARPS, "Crystal Hollows", "/warp crystals", ButtonIcon.symbol("gem", 0xC77DFF)),
		new Ready(WARPS, "Crimson Isle", "/warp nether", ButtonIcon.symbol("sun", 0xFF9F0A)),
		new Ready(WARPS, "The End", "/warp end", ButtonIcon.symbol("eye", 0xAF52DE)),
		new Ready(MENUS, "Bazaar", "/bz", ButtonIcon.symbol("coins", 0xFFD60A)),
		new Ready(MENUS, "Auction House", "/ah", ButtonIcon.symbol("crown", 0xFFB340)),
		new Ready(MENUS, "Pets", "/pets", ButtonIcon.symbol("paw", 0xFF6EC7)),
		new Ready(MENUS, "Wardrobe", "/wardrobe", ButtonIcon.symbol("shield", 0x64A8FF)),
		new Ready(MENUS, "Storage", "/storage", ButtonIcon.symbol("chest", 0xD9A066)),
		new Ready(MENUS, "Crafting", "/craft", ButtonIcon.symbol("grid", 0xE8E8EE)),
		new Ready(MENUS, "Potion bag", "/potionbag", ButtonIcon.symbol("drop", 0x34C759)),
		new Ready(MENUS, "SkyBlock menu", "/sbmenu", ButtonIcon.symbol("star", 0xC77DFF)));
	/** The starter set: three warps and five menus, and the spots they go on (the top row, left to right). */
	static final List<String> STARTER = List.of("/warp home", "/warp hub", "/warp dungeon_hub", "/bz", "/ah", "/pets", "/wardrobe", "/storage");
	private static final int MOST_SUGGESTIONS = 5;
	private static final int MOST_GUESSED_NAME = 24;

	private ButtonCatalog() {
	}

	/** @return the ready-made button with this command (case and spaces around it do not matter), or null */
	static Ready byCommand(String command) {
		String clean = InventoryButton.clean(command);
		if (clean == null) {
			return null;
		}
		for (Ready ready : ALL) {
			if (ready.command().equalsIgnoreCase(clean)) {
				return ready;
			}
		}
		return null;
	}

	/** @return the ready-made buttons whose command or name starts with what is typed, for the suggestions under the command field */
	static List<Ready> suggestions(String typed) {
		String text = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
		List<Ready> found = new ArrayList<>();
		if (text.isEmpty()) {
			return found;
		}
		String command = text.startsWith("/") ? text : "/" + text;
		for (Ready ready : ALL) {
			if (found.size() < MOST_SUGGESTIONS && (ready.command().startsWith(command) || ready.name().toLowerCase(Locale.ROOT).startsWith(text))) {
				found.add(ready);
			}
		}
		return found;
	}

	/** @return the ready-made buttons that match a search in the Quick add panel, by name or command */
	static List<Ready> search(String typed) {
		String text = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return ALL;
		}
		List<Ready> found = new ArrayList<>();
		for (Ready ready : ALL) {
			if (ready.name().toLowerCase(Locale.ROOT).contains(text) || ready.command().contains(text)) {
				found.add(ready);
			}
		}
		return found;
	}

	/** @return a name for a command without one: its words without the slash, "/warp wizard" gives "Warp wizard" */
	static String guessName(String command) {
		String text = command == null ? "" : command.strip();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		if (text.isEmpty()) {
			return "Button";
		}
		if (text.length() > MOST_GUESSED_NAME) {
			text = text.substring(0, MOST_GUESSED_NAME);
		}
		return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
	}
}
