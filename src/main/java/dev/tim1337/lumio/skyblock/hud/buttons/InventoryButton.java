package dev.tim1337.lumio.skyblock.hud.buttons;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * One inventory button: the one command it runs, its name, its icon, where it shows, and whether it shows only on SkyBlock and closes the
 * inventory after its click.
 */
record InventoryButton(String command, String name, ButtonIcon icon, ShowIn showIn, boolean onlySkyBlock, boolean closeAfter) {
	/** Where a button shows. */
	enum ShowIn {
		INVENTORY("Inventory"), CHESTS("Chests"), BOTH("Both");

		final String label;

		ShowIn(String label) {
			this.label = label;
		}
	}

	/** A command longer than the chat allows is no command. */
	static final int MOST_COMMAND = 256;
	static final int MOST_NAME = 32;

	/** @return a button for a command, named and given an icon as the ready-made ones are when it is one of them */
	static InventoryButton of(String command) {
		ButtonCatalog.Ready ready = ButtonCatalog.byCommand(command);
		return new InventoryButton(command, ready == null ? ButtonCatalog.guessName(command) : ready.name(), ready == null ? ButtonIcon.DEFAULT : ready.icon(),
			ShowIn.BOTH, true, false);
	}

	InventoryButton withCommand(String value) {
		return new InventoryButton(value, name, icon, showIn, onlySkyBlock, closeAfter);
	}

	InventoryButton withName(String value) {
		return new InventoryButton(command, value, icon, showIn, onlySkyBlock, closeAfter);
	}

	InventoryButton withIcon(ButtonIcon value) {
		return new InventoryButton(command, name, value, showIn, onlySkyBlock, closeAfter);
	}

	InventoryButton withShowIn(ShowIn value) {
		return new InventoryButton(command, name, icon, value, onlySkyBlock, closeAfter);
	}

	InventoryButton withOnlySkyBlock(boolean value) {
		return new InventoryButton(command, name, icon, showIn, value, closeAfter);
	}

	InventoryButton withCloseAfter(boolean value) {
		return new InventoryButton(command, name, icon, showIn, onlySkyBlock, value);
	}

	/** @return whether the button shows in this kind of window */
	boolean showsIn(boolean inventory) {
		return showIn == ShowIn.BOTH || (inventory ? showIn == ShowIn.INVENTORY : showIn == ShowIn.CHESTS);
	}

	/**
	 * @return a command as the player would type it ("/warp hub"), with its slash; null for text that is no single command: empty, too long, with a
	 *         line break or a color code, or more than one command
	 */
	static String clean(String text) {
		if (text == null) {
			return null;
		}
		String command = text.strip();
		if (command.startsWith("/")) {
			command = command.substring(1).stripLeading();
		}
		if (command.isEmpty() || command.length() + 1 > MOST_COMMAND) {
			return null;
		}
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c < ' ' || c == '§' || c == 0x7F) {
				return null;
			}
		}
		return "/" + command;
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("command", command);
		json.addProperty("name", name);
		json.addProperty("icon", icon.toText());
		json.addProperty("show_in", showIn.name().toLowerCase(Locale.ROOT));
		json.addProperty("only_skyblock", onlySkyBlock);
		json.addProperty("close_after", closeAfter);
		return json;
	}

	/** @return the button saved as JSON, or null when it is no valid button (no command, a broken one) */
	static InventoryButton fromJson(JsonObject json) {
		if (json == null || !json.has("command") || !json.get("command").isJsonPrimitive()) {
			return null;
		}
		String command = clean(json.get("command").getAsString());
		if (command == null) {
			return null;
		}
		String name = json.has("name") && json.get("name").isJsonPrimitive() ? json.get("name").getAsString().strip() : "";
		if (name.length() > MOST_NAME) {
			name = name.substring(0, MOST_NAME);
		}
		if (name.isEmpty()) {
			name = ButtonCatalog.guessName(command);
		}
		ButtonIcon icon = json.has("icon") && json.get("icon").isJsonPrimitive() ? ButtonIcon.parse(json.get("icon").getAsString()) : null;
		ShowIn showIn = ShowIn.BOTH;
		if (json.has("show_in") && json.get("show_in").isJsonPrimitive()) {
			for (ShowIn value : ShowIn.values()) {
				if (value.name().equalsIgnoreCase(json.get("show_in").getAsString())) {
					showIn = value;
				}
			}
		}
		boolean onlySkyBlock = !json.has("only_skyblock") || !json.get("only_skyblock").isJsonPrimitive() || json.get("only_skyblock").getAsBoolean();
		boolean closeAfter = json.has("close_after") && json.get("close_after").isJsonPrimitive() && json.get("close_after").getAsBoolean();
		return new InventoryButton(command, name, icon == null ? ButtonIcon.DEFAULT : icon, showIn, onlySkyBlock, closeAfter);
	}
}
