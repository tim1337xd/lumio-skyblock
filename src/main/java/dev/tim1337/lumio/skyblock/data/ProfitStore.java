package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Logs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where the profit trackers keep what they counted (today and all time): a data file of their own for each SkyBlock profile,
 * {@code config/lumio/skyblock-profit/<profile>.json}, apart from the settings. So nothing that changes settings can touch it: not "Reset
 * everything", not "Disable everything", not loading another config, not the first setup; only a tracker's own reset clears its values.
 * <p>
 * A file is written through a temporary file that then takes its place, so a crash while saving leaves the old file whole; before that the old
 * file, when it reads well, is kept beside it as {@code <profile>.json.bak}, the last good state, which is read when the file itself cannot be.
 * <p>
 * Before the server has named the profile, what is counted goes under "unknown"; once the profile is known it is added to that profile's values.
 * The values the trackers had in the settings file before this store existed are taken over the same way, once.
 */
public final class ProfitStore {
	/** The folder of the files, in the mod's config folder. */
	public static final String FOLDER = "skyblock-profit";
	/** The profile while the server has not named it. */
	public static final String UNKNOWN = "unknown";
	private static final String BACKUP = ".bak";
	private static final String TEMPORARY = ".tmp";
	/** A marker in the folder: the settings file's old values were moved here. */
	private static final String LEGACY_TAKEN = "moved-from-settings.txt";
	private static final int MOST_NAME = 32;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** A tracker's values in the store, by the tracker's id. */
	public interface Part {
		/** @return the values now */
		JsonObject save();

		/** Takes the values of a profile in place of its own; null for a profile that has none yet. */
		void load(JsonObject json);

		/** Adds values to its own: what was counted before the profile was known. */
		void merge(JsonObject json);
	}

	private final Path folder;
	private final Map<String, Part> parts = new LinkedHashMap<>();
	private String profile = UNKNOWN;
	private boolean opened;

	public ProfitStore(Path folder) {
		this.folder = folder;
	}

	/** Adds a tracker; done once while the add-on starts. */
	public void add(String id, Part part) {
		parts.put(id, part);
	}

	/** @return the profile whose values the trackers hold now, {@link #UNKNOWN} before the server named it */
	public String profile() {
		return profile;
	}

	/** @return whether the trackers' values are those of a named profile, so nothing of them needs to stay in the settings file any more */
	public boolean settled() {
		return opened && !profile.equals(UNKNOWN);
	}

	/**
	 * The profile is known, or another one is played: the values counted so far are written for the profile they belong to, and the trackers
	 * take the values of the profile played now. What was counted before any profile was known is added to it.
	 *
	 * @param named the profile as the server names it; null or blank while it is not known
	 */
	public void play(String named) {
		String next = fileName(named);
		if (opened && next.equals(profile)) {
			return;
		}
		if (!opened) {
			opened = true;
			// what was counted under no profile in an earlier game joins what the trackers hold now (the settings file's old values)
			JsonObject loose = read(file(UNKNOWN));
			if (loose != null) {
				parts.forEach((id, part) -> {
					if (loose.get(id) instanceof JsonObject values) {
						part.merge(values);
					}
				});
			}
			if (next.equals(UNKNOWN)) {
				return;
			}
		}
		JsonObject carried = collect();
		boolean fromUnknown = profile.equals(UNKNOWN);
		if (!fromUnknown) {
			write(file(profile), carried);
		}
		JsonObject theirs = read(file(next));
		for (Map.Entry<String, Part> part : parts.entrySet()) {
			JsonObject values = theirs != null && theirs.get(part.getKey()) instanceof JsonObject found ? found : null;
			if (!fromUnknown) {
				part.getValue().load(values);
			} else if (values != null) {
				part.getValue().load(values);
				if (carried.get(part.getKey()) instanceof JsonObject before) {
					part.getValue().merge(before);
				}
			}
		}
		profile = next;
		if (fromUnknown) {
			// in the profile's file first, then gone from "unknown": never in neither
			if (write(file(next), collect())) {
				markLegacyTaken();
				try {
					Files.deleteIfExists(file(UNKNOWN));
				} catch (IOException e) {
					Logs.warn("Could not remove {}", file(UNKNOWN).getFileName(), e);
				}
			}
		}
	}

	/**
	 * @return whether the values the trackers had in the settings file before this store existed were taken over: from then on they are read from
	 *         here only, so values still left in an old settings file are never counted twice
	 */
	public boolean legacyTaken() {
		return Files.exists(folder.resolve(LEGACY_TAKEN));
	}

	private void markLegacyTaken() {
		try {
			Files.createDirectories(folder);
			Files.writeString(folder.resolve(LEGACY_TAKEN), "The profit trackers' values of the settings file were moved into this folder.\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			Logs.warn("Could not write {}", LEGACY_TAKEN, e);
		}
	}

	/** @return the trackers' values now, as the file holds them */
	public JsonObject collect() {
		JsonObject root = new JsonObject();
		parts.forEach((id, part) -> root.add(id, part.save()));
		return root;
	}

	/** Writes the trackers' values for the profile played. @return whether they are in the file */
	public boolean save() {
		return save(collect());
	}

	/** Writes values collected earlier (on the game's thread) for the profile played; may run on another thread. */
	public boolean save(JsonObject collected) {
		return write(file(profile), collected);
	}

	private Path file(String name) {
		return folder.resolve(name + ".json");
	}

	/** @return a profile's file name: its name in lower case, letters, digits, "-" and "_" only; {@link #UNKNOWN} for none */
	public static String fileName(String profile) {
		if (profile == null) {
			return UNKNOWN;
		}
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < profile.length() && name.length() < MOST_NAME; i++) {
			char c = Character.toLowerCase(profile.charAt(i));
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_') {
				name.append(c);
			}
		}
		return name.isEmpty() ? UNKNOWN : name.toString().toLowerCase(Locale.ROOT);
	}

	/** @return a file's values; those of its backup when the file is missing or broken; null when neither reads */
	static JsonObject read(Path file) {
		JsonObject values = parse(file);
		return values != null ? values : parse(backup(file));
	}

	private static JsonObject parse(Path file) {
		try {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read {}", file.getFileName(), e);
			return null;
		}
	}

	/** Writes a file safely: the old one, if it reads, is kept as the backup; the new one is written beside it and then takes its place. */
	static synchronized boolean write(Path file, JsonObject values) {
		try {
			Files.createDirectories(file.getParent());
			if (parse(file) != null) {
				Files.copy(file, backup(file), StandardCopyOption.REPLACE_EXISTING);
			}
			Path temporary = file.resolveSibling(file.getFileName() + TEMPORARY);
			Files.writeString(temporary, GSON.toJson(values), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not write {}", file.getFileName(), e);
			return false;
		}
	}

	private static Path backup(Path file) {
		return file.resolveSibling(file.getFileName() + BACKUP);
	}
}
