package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.Changelog;
import dev.tim1337.lumio.api.Changelog.Kind;
import dev.tim1337.lumio.api.Changelog.Release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The add-on's changelog is readable in Lumio's format, so its tab shows in What is new, and its newest section fits the release notes. */
class ChangelogFileTest {
	@Test
	void theChangelogIsReadable() throws IOException {
		try (InputStream in = ChangelogFileTest.class.getResourceAsStream("/" + Changelog.path(SkyBlockAddon.ID))) {
			assertNotNull(in, "no changelog.txt");
			List<Release> releases = Changelog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			assertFalse(releases.isEmpty());
			Release newest = releases.getFirst();
			for (Kind kind : Kind.values()) {
				assertTrue(newest.of(kind).size() <= 9, newest.version() + " has more than 9 " + kind + " entries");
			}
		}
	}

	@Test
	void theNewestSectionHasFourHighlightsAtMostEachBeforeAnEntry() throws IOException {
		try (InputStream in = ChangelogFileTest.class.getResourceAsStream("/" + Changelog.path(SkyBlockAddon.ID))) {
			String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
			int sections = 0;
			int highlights = 0;
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i].strip();
				if (line.startsWith("##") && ++sections > 1) {
					break;
				}
				if (line.equalsIgnoreCase("# highlight")) {
					highlights++;
					String next = i + 1 < lines.length ? lines[i + 1].strip() : "";
					assertTrue(!next.isEmpty() && "+*!".indexOf(next.charAt(0)) >= 0, "a highlight mark not right before an entry: " + next);
				}
			}
			assertTrue(highlights <= 4, highlights + " highlights in the newest section");
		}
	}
}
