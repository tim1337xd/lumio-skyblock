package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.HttpLimits;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The SkyBlock mayor and minister with their perks, from the public Hypixel API (no key needed), refreshed every twenty minutes while needed. */
public final class Mayor {
	private static volatile String line = "";
	private static volatile Set<String> perks = Set.of();
	private static volatile long askedAt;

	private Mayor() {
	}

	/** @return the scoreboard line "Mayor: Aatrox · Diana", or empty while unknown */
	public static String line() {
		askIfOld();
		return line;
	}

	/** @return whether the mayor or minister has a perk with this name ("Slashed Pricing"); false while unknown */
	public static boolean hasPerk(String name) {
		askIfOld();
		return perks.contains(name.toLowerCase(Locale.ROOT));
	}

	private static void askIfOld() {
		long now = System.currentTimeMillis();
		if (now - askedAt > 20 * 60_000L) {
			askedAt = now;
			Thread thread = new Thread(Mayor::fetch, "Lumio mayor");
			thread.setDaemon(true);
			thread.start();
		}
	}

	private static void fetch() {
		try {
			var connection = (HttpURLConnection) URI.create("https://api.hypixel.net/v2/resources/skyblock/election").toURL().openConnection();
			connection.setConnectTimeout(8000);
			connection.setReadTimeout(15000);
			connection.setRequestProperty("User-Agent", "Lumio (Minecraft mod)");
			try (var reader = new InputStreamReader(HttpLimits.limited(connection.getInputStream(), HttpLimits.DATA_BYTES), StandardCharsets.UTF_8)) {
				var root = JsonParser.parseReader(reader).getAsJsonObject();
				var mayor = root.getAsJsonObject("mayor");
				String name = mayor.get("name").getAsString();
				Set<String> found = new HashSet<>();
				if (mayor.has("perks") && mayor.get("perks").isJsonArray()) {
					for (var perk : mayor.getAsJsonArray("perks")) {
						if (perk.isJsonObject() && perk.getAsJsonObject().has("name")) {
							found.add(perk.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT));
						}
					}
				}
				String minister = "";
				if (mayor.has("minister") && mayor.get("minister").isJsonObject()) {
					var ministerJson = mayor.getAsJsonObject("minister");
					minister = ministerJson.has("name") ? ministerJson.get("name").getAsString() : "";
					// the minister has a single perk
					if (ministerJson.has("perk") && ministerJson.get("perk").isJsonObject() && ministerJson.getAsJsonObject("perk").has("name")) {
						found.add(ministerJson.getAsJsonObject("perk").get("name").getAsString().toLowerCase(Locale.ROOT));
					}
				}
				perks = Set.copyOf(found);
				line = "§fMayor: §d" + name + (minister.isEmpty() ? "" : " §8· §7" + minister);
			} finally {
				connection.disconnect();
			}
		} catch (Exception e) {
			// retry in a minute
			askedAt = System.currentTimeMillis() - 19 * 60_000L;
			Debug.log("Could not fetch the mayor: {}", e.toString());
		}
	}
}
