package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.Setting;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Builds a settings page's rows as Lumio's menu does (a copy of the main project's test helper, which the add-on cannot see). */
final class PageRows {
	private PageRows() {
	}


	/** @return the settings a page shows as rows, as the menu picks them: shown, and not the feature's own switch */
	static List<Setting<?>> rows(Feature page) {
		List<Setting<?>> rows = new ArrayList<>();
		for (Setting<?> setting : page.shownSettings()) {
			if (setting.isVisible() && !setting.key().equals("enabled")) {
				rows.add(setting);
			}
		}
		for (Feature child : page.children()) {
			for (Setting<?> setting : child.settings()) {
				if (setting.isVisible()) {
					rows.add(setting);
				}
			}
		}
		return rows;
	}

	static void buildsWithRows(Feature page) {
		List<Setting<?>> rows = assertDoesNotThrow(() -> rows(page), page.id());
		assertFalse(rows.isEmpty(), page.id() + " has no rows");
		for (Setting<?> setting : rows) {
			assertDoesNotThrow(() -> setting.createControl(() -> {
			}), page.id() + " / " + setting.key());
		}
	}
}
