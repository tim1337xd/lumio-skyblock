package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.api.experimental.util.Logs;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads the puzzle data shipped with the add-on (Odin's solutions, BSD-3-Clause, see CREDITS.md). A file that cannot be read gives nothing. */
public final class PuzzleData {
	private static final String FOLDER = "/assets/lumio-skyblock/skyblock/dungeon/";

	private PuzzleData() {
	}

	/** @return the file's JSON, or null */
	public static JsonElement read(String name) {
		try (InputStream in = PuzzleData.class.getResourceAsStream(FOLDER + name)) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (Exception e) {
			Logs.warn("Could not read the puzzle data " + name, e);
			return null;
		}
	}
}
