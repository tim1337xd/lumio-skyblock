package dev.tim1337.lumio.skyblock.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Which lines of the chat are SkyBlock's own: drops, coins and rewards, slayer and dungeon messages, level-ups, bazaar and auction notices, by how the game writes them. The formats are plain text from a data file, so
 * nobody's file can stall the game; what players write never counts, however it reads. It only sorts lines into a tab: no line is changed.
 */
public final class SkyBlockChatFormats {
	/** What a player writes starts with a name and a colon, or a channel: whatever follows, such as "RARE DROP!", is theirs and not the game's. */
	private static final Pattern PLAYER_LINE = Pattern.compile("^(?:(?:Party|P|Guild|G|Officer|O)\\s*[>»]|(?:From|To)\\s|(?:\\[[^\\]]{1,16}]\\s*)*[A-Za-z0-9_]{1,16}(?:\\s*\\[[^\\]]{1,16}])*\\s*:\\s)");
	private static final int MOST_FORMATS = 500;
	private static final int LONGEST_FORMAT = 120;

	private final List<String> starts;
	private final List<String> holds;

	private SkyBlockChatFormats(List<String> starts, List<String> holds) {
		this.starts = starts;
		this.holds = holds;
	}

	/** @return the formats a data file holds, next to those of the ones given; it is external data, so anything odd in it is left out */
	public static SkyBlockChatFormats read(JsonElement... files) {
		List<String> starts = new ArrayList<>();
		List<String> holds = new ArrayList<>();
		for (JsonElement file : files) {
			if (file != null && file.isJsonObject()) {
				JsonObject root = file.getAsJsonObject();
				addAll(root.get("starts"), starts);
				addAll(root.get("contains"), holds);
			}
		}
		return new SkyBlockChatFormats(List.copyOf(starts), List.copyOf(holds));
	}

	private static void addAll(JsonElement list, List<String> into) {
		if (list == null || !list.isJsonArray()) {
			return;
		}
		JsonArray array = list.getAsJsonArray();
		for (JsonElement entry : array) {
			if (into.size() >= MOST_FORMATS) {
				return;
			}
			if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
				String format = entry.getAsString();
				if (!format.isBlank() && format.length() <= LONGEST_FORMAT) {
					into.add(format);
				}
			}
		}
	}

	/** @return whether a line of the chat is one of the game's own in SkyBlock */
	public boolean matches(String plain) {
		String line = plain.stripLeading();
		if (PLAYER_LINE.matcher(line).find()) {
			return false;
		}
		for (String format : starts) {
			if (line.startsWith(format)) {
				return true;
			}
		}
		for (String format : holds) {
			if (line.contains(format)) {
				return true;
			}
		}
		return false;
	}
}
