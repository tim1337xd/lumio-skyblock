package dev.tim1337.lumio.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every probe that builds text ({@code DebugHooks.log}, {@code DebugHooks.event}) is guarded, so it costs nothing while no debug tool listens:
 * inside a block whose condition reads {@code DebugHooks.ACTIVE} or {@code DebugHooks.wants(...)}, or after a return in its method when they
 * do not hold. Lumio has the same test for its own probes.
 */
public class ProbeGuardTest {
	private static final List<String> ROOTS = List.of("src/main/java");
	/** The hooks themselves. */
	private static final String HOOKS_PACKAGE = "api/experimental/debug/";

	@Test
	void everyProbeIsGuarded() throws IOException {
		List<String> unguarded = new ArrayList<>();
		for (String root : ROOTS) {
			try (Stream<Path> files = Files.walk(Path.of(root))) {
				for (Path file : files.filter(path -> path.toString().endsWith(".java") && !path.toString().replace('\\', '/').contains(HOOKS_PACKAGE)).toList()) {
					unguarded.addAll(unguarded(file));
				}
			}
		}
		assertEquals(List.of(), unguarded, "probes that build text while nothing listens");
	}

	/** @return the probes of a file that no guard encloses, as "file:line" */
	public static List<String> unguarded(Path file) throws IOException {
		List<String> lines = Files.readAllLines(file);
		List<String> found = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if ((line.startsWith("DebugHooks.log(") || line.startsWith("DebugHooks.event(")) && !guarded(lines, i)) {
				found.add(file.getFileName() + ":" + (i + 1));
			}
		}
		return found;
	}

	/** @return whether a guard encloses the line: a block around it reads the guard, or an earlier line of its method returns without it */
	static boolean guarded(List<String> lines, int at) {
		int level = indent(lines.get(at));
		for (int i = at - 1; i >= 0; i--) {
			String line = lines.get(i);
			if (line.isBlank()) {
				continue;
			}
			String text = line.strip();
			boolean guard = text.contains("DebugHooks.ACTIVE") || text.contains("DebugHooks.wants(");
			if (indent(line) < level) {
				level = indent(line);
				if (guard && (text.startsWith("if (") || text.startsWith("} else if (") || text.startsWith("while ("))) {
					return true;
				}
				if (isMethodStart(text)) {
					return false;
				}
			} else if (guard && text.startsWith("if (!") || text.startsWith("if (!on()")) {
				// an early return of the method when nothing listens
				return true;
			}
		}
		return false;
	}

	private static boolean isMethodStart(String text) {
		if (text.matches("^(if|for|while|switch|else|try|catch|finally|synchronized|do|return|\\}).*")) {
			return false;
		}
		return text.endsWith("{") && (text.startsWith("public ") || text.startsWith("private ") || text.startsWith("protected ")
			|| text.startsWith("static ") || text.matches("^[A-Za-z<>\\[\\], .]+\\(.*\\)\\s*(throws [A-Za-z., ]+)?\\{$"));
	}

	private static int indent(String line) {
		int tabs = 0;
		while (tabs < line.length() && line.charAt(tabs) == '\t') {
			tabs++;
		}
		return tabs;
	}
}
