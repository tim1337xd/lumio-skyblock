package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.Threads;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;

/**
 * The profit trackers' store in the game: it follows the profile played, and writes what the trackers counted a little after it changed (on a
 * background thread, never on the game's) and when the game closes. See {@link ProfitStore} for where and how.
 */
public final class ProfitData {
	/** A change is written at the earliest this long after the last write. */
	private static final long WRITE_EVERY_MILLIS = 15_000L;
	private static final ProfitStore STORE = new ProfitStore(folder());
	private static final ExecutorService WRITER = Threads.daemonExecutor("Lumio profit store");
	private static boolean dirty;
	private static long writtenAt;
	/** The profile name the store was last told, so it is told again only when the name changes. */
	private static boolean followed;
	private static String followedName;

	private ProfitData() {
	}

	/** @return the store's folder in the mod's config folder; beside the working folder where there is no game (the unit tests, which write nothing) */
	private static java.nio.file.Path folder() {
		try {
			return ModFiles.dir().resolve(ProfitStore.FOLDER);
		} catch (RuntimeException | LinkageError e) {
			return java.nio.file.Path.of("config", "lumio", ProfitStore.FOLDER);
		}
	}

	public static ProfitStore store() {
		return STORE;
	}

	/** A tracker counted something, or was reset: written soon. */
	public static void changed() {
		dirty = true;
	}

	/** Follows the profile and writes what changed; called every tick. */
	public static void tick(Minecraft client) {
		String named = PetTracker.profileName();
		if (!followed || !java.util.Objects.equals(named, followedName)) {
			followed = true;
			followedName = named;
			boolean settledBefore = STORE.settled();
			STORE.play(named);
			if (STORE.settled() && !settledBefore) {
				// the settings file no longer needs the values it kept until now
				LumioApi.saveSoon();
			}
		}
		long now = System.currentTimeMillis();
		if (dirty && now - writtenAt >= WRITE_EVERY_MILLIS) {
			dirty = false;
			writtenAt = now;
			// collected on the game's thread, written on another
			JsonObject collected = STORE.collect();
			WRITER.execute(() -> STORE.save(collected));
		}
	}

	/** The game closes: what was counted is written now, on this thread, as a background write might not run any more. */
	public static void stopping() {
		if (dirty || STORE.settled()) {
			dirty = false;
			STORE.save();
		}
	}
}
