package dev.tim1337.lumio.skyblock.hud.buttons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The layouts of the buttons: one for all profiles, and, when the player keeps a layout per SkyBlock profile, one for each profile (a profile
 * starts with no buttons). Plain data, so it is unit tested.
 */
final class ButtonLayouts {
	/** The most profiles kept; a player has only a few. */
	static final int MOST_PROFILES = 30;
	/** A profile's id as the server names it. */
	private static final Pattern PROFILE = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");

	private final ButtonLayout global = new ButtonLayout();
	private final Map<String, ButtonLayout> profiles = new LinkedHashMap<>();

	/**
	 * @param perProfile whether each profile has its own layout
	 * @param profile    the profile played, or null while unknown (then the layout for all profiles is used)
	 * @return the layout in use
	 */
	ButtonLayout current(boolean perProfile, String profile) {
		if (!perProfile || profile == null || !PROFILE.matcher(profile).matches()) {
			return global;
		}
		ButtonLayout layout = profiles.get(profile);
		if (layout == null) {
			if (profiles.size() >= MOST_PROFILES) {
				return global;
			}
			layout = new ButtonLayout();
			profiles.put(profile, layout);
		}
		return layout;
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.add("global", global.toJson());
		JsonObject byProfile = new JsonObject();
		for (Map.Entry<String, ButtonLayout> entry : profiles.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				byProfile.add(entry.getKey(), entry.getValue().toJson());
			}
		}
		json.add("profiles", byProfile);
		return json;
	}

	void load(JsonElement json) {
		profiles.clear();
		global.load(List.of());
		if (json == null || !json.isJsonObject()) {
			return;
		}
		JsonObject object = json.getAsJsonObject();
		if (object.has("global") && object.get("global").isJsonArray()) {
			global.load(ButtonLayout.fromJson(object.getAsJsonArray("global")));
		}
		if (object.has("profiles") && object.get("profiles").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("profiles").entrySet()) {
				if (profiles.size() < MOST_PROFILES && PROFILE.matcher(entry.getKey()).matches() && entry.getValue().isJsonArray()) {
					ButtonLayout layout = new ButtonLayout();
					layout.load(ButtonLayout.fromJson((JsonArray) entry.getValue()));
					profiles.put(entry.getKey(), layout);
				}
			}
		}
	}
}
