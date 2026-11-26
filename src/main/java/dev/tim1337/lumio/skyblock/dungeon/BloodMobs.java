package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.experimental.util.Logs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Which skulls belong to the blood room, by their texture hash (the end of the skin's address, which stays the same; the whole texture value carries a
 * time and does not): the Watcher's own, and his mobs'. Only heads on these lists count; anything else (the player's Soulweaver skulls, other
 * player-made heads, pets, items) is ignored. The lists are data in one resource file (NoammAddons, CC0, see CREDITS.md).
 */
public final class BloodMobs {
	private static final String DATA = "/assets/lumio-skyblock/skyblock/dungeon/blood-mob-textures.json";
	/** A mob must be this close to the Watcher, in blocks. */
	public static final double NEAR_WATCHER_BLOCKS = 20;

	private static Set<String> watcher;
	private static Set<String> mobs;

	private BloodMobs() {
	}

	/** @return whether a skull texture hash is one of the Watcher's */
	public static boolean watcher(String hash) {
		load();
		return watcher.contains(hash);
	}

	/** @return whether a skull texture hash is one of the blood mobs' */
	public static boolean mob(String hash) {
		load();
		return mobs.contains(hash);
	}

	/** A head first seen this close to the Watcher (across, in blocks) comes from him: he throws his mobs out from where he floats. */
	public static final double THROWN_FROM_BLOCKS = 5;
	/** A head that came from the Watcher counts once it is this much farther from him than where it was first seen, in blocks across. */
	public static final double AWAY_BLOCKS = 0.5;

	/**
	 * Whether a head not on the list is one the Watcher threw out: an armor stand's skull that was first seen right at him and has since
	 * moved away from him. The skulls of a player's Soulweaver Gloves fly toward a mob, so they come no farther from the Watcher than they
	 * started (or start far from him); heads worn by players and heads lying as items are no armor stands and never get here.
	 *
	 * @param firstDistance how far from the Watcher it was first seen, across, in blocks
	 * @param nowDistance   how far from him it is now
	 */
	public static boolean thrownOut(double firstDistance, double nowDistance) {
		return firstDistance <= THROWN_FROM_BLOCKS && nowDistance - firstDistance >= AWAY_BLOCKS;
	}

	/** @return whether a head first seen at the Watcher has moved without getting farther from him: then it is not his, and is dropped */
	public static boolean notThrown(double firstDistance, double nowDistance, double movedAcross) {
		return firstDistance > THROWN_FROM_BLOCKS || movedAcross >= 2 * AWAY_BLOCKS && nowDistance - firstDistance < AWAY_BLOCKS;
	}


	/**
	 * @param startedAtWatcher whether the head was first seen right at the Watcher: then it is one of his mobs also when its texture is not on
	 *                         the list (Hypixel adds mobs and changes skins; a list alone left most of a run's mobs out)
	 * @return whether a head is a blood mob: on the mob list or thrown out by the Watcher, near him, while he sends mobs, and not in the boss room
	 */
	public static boolean bloodMob(String hash, double distanceToWatcherSq, boolean watcherSending, boolean inBoss, boolean startedAtWatcher) {
		return !inBoss && watcherSending && distanceToWatcherSq <= NEAR_WATCHER_BLOCKS * NEAR_WATCHER_BLOCKS && !watcher(hash)
			&& (mob(hash) || startedAtWatcher);
	}

	/**
	 * Why no mob is followed although the Blood room is open, in words for the debug log.
	 *
	 * @param watcherFound whether an entity wearing one of the Watcher's heads is there
	 * @param stands       the armor stands near him
	 * @param skulls       those wearing a player head
	 * @param notHis       the skulls that are neither on the list nor came from him
	 */
	public static String whyNothing(boolean watcherFound, int stands, int skulls, int notHis) {
		if (!watcherFound) {
			return "the Watcher was not found: no entity in sight wears one of his heads (his texture changed, or he is out of the loaded area)";
		}
		if (stands == 0) {
			return "no armor stand within " + (int) NEAR_WATCHER_BLOCKS + " blocks of the Watcher";
		}
		if (skulls == 0) {
			return stands + " armor stands near the Watcher, none wears a player head";
		}
		if (notHis == skulls) {
			return skulls + " skulls near the Watcher, none on the list and none first seen within " + (int) THROWN_FROM_BLOCKS + " blocks of him";
		}
		return (skulls - notHis) + " of his skulls near the Watcher, none has moved yet";
	}


	private static synchronized void load() {
		if (mobs != null) {
			return;
		}
		Set<String> readWatcher = new HashSet<>();
		Set<String> readMobs = new HashSet<>();
		try (InputStream in = BloodMobs.class.getResourceAsStream(DATA)) {
			if (in != null) {
				JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				root.getAsJsonArray("watcher").forEach(element -> readWatcher.add(element.getAsString()));
				for (JsonElement element : root.getAsJsonArray("mobs")) {
					readMobs.add(element.getAsString());
				}
			}
		} catch (Exception e) {
			Logs.warn("Could not read the blood mob textures", e);
		}
		watcher = Set.copyOf(readWatcher);
		mobs = Set.copyOf(readMobs);
	}
}
