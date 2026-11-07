package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Holds the wardrobe of the profile being played, a file for each profile, and reads and writes those files away from the game's thread. */
final class WardrobeShelf {
	private static final ExecutorService FILES = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio wardrobe");
		thread.setDaemon(true);
		return thread;
	});

	private static WardrobeBook book;
	private static String bookProfile;
	private static CompletableFuture<WardrobeBook> reading;

	private WardrobeShelf() {
	}

	/** @return the wardrobe of a profile: at once, and what its file kept joins it a moment later */
	static WardrobeBook book(String profile) {
		if (book == null || !java.util.Objects.equals(profile, bookProfile)) {
			book = new WardrobeBook();
			bookProfile = profile;
			Path file = ModFiles.file(WardrobeBook.fileName(profile));
			reading = CompletableFuture.supplyAsync(() -> read(file), FILES);
		}
		return book;
	}

	/** @return whether what a file kept has joined the wardrobe just now */
	static boolean arrived() {
		if (reading == null || !reading.isDone()) {
			return false;
		}
		WardrobeBook kept = reading.join();
		reading = null;
		if (kept == null) {
			return false;
		}
		book.adopt(kept);
		return true;
	}

	private static WardrobeBook read(Path file) {
		try {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return WardrobeBook.fromJson(root);
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the kept wardrobe", e);
			return null;
		}
	}

	/** Writes the wardrobe to its file, away from the game's thread. */
	static void save() {
		if (book == null) {
			return;
		}
		JsonObject root = book.toJson();
		Path file = ModFiles.file(WardrobeBook.fileName(bookProfile));
		FILES.execute(() -> {
			try {
				ModFiles.writeAtomically(file, new Gson().toJson(root));
			} catch (IOException e) {
				Logs.warn("Could not keep the wardrobe", e);
			}
		});
	}
}
