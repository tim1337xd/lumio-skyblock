package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every key the add-on registers is a key field in its feature's own settings: the class that makes a key hands it to {@code addKey} or
 * {@code addMenuKey} (itself, or through the features named here). Lumio lists every such field on its Keybinds page too, and both edit the
 * game's own key mapping (the main mod's KeyFieldsTest). No feature sheet sends the player to the Keybinds page to set a key.
 */
class KeyFieldsTest {
	private static final String ROOT = "src/main/java";
	/** Classes that make keys for features: the classes that list them in the features' settings. */
	private static final Map<String, List<String>> LISTED_BY = Map.of(
		"DungeonKeys.java", List.of(),
		"WrongClickKey.java", List.of("TerminalSolverFeature.java", "F7DevicesFeature.java"));

	private static boolean lists(String source) {
		return source.contains("addKey(") || source.contains("addMenuKey(");
	}

	@Test
	void everyKeyIsAFieldInItsFeaturesSettings() throws IOException {
		List<Path> all;
		try (Stream<Path> files = Files.walk(Path.of(ROOT))) {
			all = files.filter(path -> path.toString().endsWith(".java")).toList();
		}
		List<String> wrong = new ArrayList<>();
		for (Path file : all) {
			String source = Files.readString(file);
			String name = file.getFileName().toString();
			boolean makesKey = source.contains("new KeyMapping(") || source.contains("DungeonKeys.register(");
			if (makesKey) {
				for (String holder : LISTED_BY.getOrDefault(name, List.of(name))) {
					Path holderFile = all.stream().filter(path -> path.getFileName().toString().equals(holder)).findFirst().orElse(null);
					if (holderFile == null || !lists(Files.readString(holderFile))) {
						wrong.add(name + ": its key is no field in " + holder);
					}
				}
			}
			if (source.contains("Menus.openKeybinds") || source.contains("Menus::openKeybinds")) {
				wrong.add(name + ": sends the player to the Keybinds page instead of offering the key field");
			}
		}
		assertEquals(List.of(), wrong);
	}
}
