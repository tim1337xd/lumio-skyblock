package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The warps of the item repository's island list: each with its short names and its island. Plain data, so it is unit tested. */
public final class RepoWarps {
	private static final int MOST_WARPS = 300;
	private static final int MOST_NAME = 32;

	/** A warp, under one of its names. */
	public record Warp(String name, String island) {
	}

	private RepoWarps() {
	}

	/** @return every warp and short name of the file; what it cannot read is left out */
	public static List<Warp> of(JsonObject root) {
		List<Warp> out = new ArrayList<>();
		JsonObject areas = root.has("area_names") && root.get("area_names").isJsonObject() ? root.getAsJsonObject("area_names") : new JsonObject();
		JsonElement warps = root.get("island_warps");
		if (warps == null || !warps.isJsonArray()) {
			return out;
		}
		for (JsonElement element : warps.getAsJsonArray()) {
			if (!element.isJsonObject() || out.size() >= MOST_WARPS) {
				continue;
			}
			JsonObject warp = element.getAsJsonObject();
			if (!warp.has("warp")) {
				continue;
			}
			String mode = warp.has("mode") ? warp.get("mode").getAsString() : "";
			String island = areas.has(mode) ? areas.get(mode).getAsString() : "";
			add(out, warp.get("warp").getAsString(), island);
			if (warp.has("aliases") && warp.get("aliases").isJsonArray()) {
				for (JsonElement alias : warp.getAsJsonArray("aliases")) {
					add(out, alias.getAsString(), island);
				}
			}
		}
		return out;
	}

	private static void add(List<Warp> into, String name, String island) {
		if (!name.isEmpty() && name.length() <= MOST_NAME && into.size() < MOST_WARPS) {
			into.add(new Warp(name.toLowerCase(Locale.ROOT), island));
		}
	}
}
