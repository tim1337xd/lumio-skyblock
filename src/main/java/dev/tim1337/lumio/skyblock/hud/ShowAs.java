package dev.tim1337.lumio.skyblock.hud;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.Toggle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The "Show as" control of the alerts (dungeon Alerts, Drop alerts): Pop-up, Title and Chat line as chips, any of them together. One class,
 * so the modules look and behave the same.
 */
public final class ShowAs {
	/** What was chosen, as plain data. */
	public record Chosen(boolean popup, boolean title, boolean chat) {
		/**
		 * What a drop of this value shows as: a title only from the least value for one, and what would then show nowhere (a small drop with
		 * only Title chosen, or nothing chosen at all) shows as a pop-up, so no alert is lost.
		 */
		public Chosen forValue(double value, double titleFrom) {
			boolean asTitle = title && value >= titleFrom;
			return new Chosen(popup || !asTitle && !chat, asTitle, chat);
		}

		/** @return whether the sound is played when the alert is given out, not by the pop-up when its turn comes */
		public boolean soundAtOnce() {
			return !popup;
		}
	}

	private final Toggle popup;
	private final Toggle title;
	private final Toggle chat;
	private final MultiToggle setting;
	private final String key;

	/** @param key the setting's key, unique in its feature; the chips are saved under it */
	public ShowAs(String key, boolean popupFirst, boolean titleFirst, boolean chatFirst) {
		this.key = key;
		popup = make().toggle(key + "_popup", Component.literal("Pop-up"), popupFirst, Toggle.Style.CHECKBOX);
		title = make().toggle(key + "_title", Component.literal("Title"), titleFirst, Toggle.Style.CHECKBOX);
		chat = make().toggle(key + "_chat", Component.literal("Chat line"), chatFirst, Toggle.Style.CHECKBOX);
		setting = make().multi(key, Component.literal("Show as"), List.of(popup, title, chat)).asChips();
	}

	/** @return the setting, to add to a feature */
	public MultiToggle setting() {
		return setting;
	}

	public Chosen chosen() {
		return new Chosen(popup.get(), title.get(), chat.get());
	}

	public boolean title() {
		return title.get();
	}

	/**
	 * Takes over the one choice an older version saved under another key ("TITLE", "TOAST" or "CHAT"), when these chips were never saved.
	 * Called after the feature loaded its settings.
	 */
	public void takeOver(JsonObject json, String oldKey) {
		JsonElement old = json.get(oldKey);
		if (json.has(key) || old == null || !old.isJsonPrimitive()) {
			return;
		}
		Chosen was = fromOld(old.getAsString());
		if (was != null) {
			popup.set(was.popup());
			title.set(was.title());
			chat.set(was.chat());
		}
	}

	/** @return the chips for an old single choice, or null for a value that is none */
	public static Chosen fromOld(String value) {
		return switch (value) {
			case "TITLE" -> new Chosen(false, true, false);
			case "TOAST" -> new Chosen(true, false, false);
			case "CHAT" -> new Chosen(false, false, true);
			default -> null;
		};
	}
}
