package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.Feature;

import com.google.gson.JsonElement;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * No setting of the add-on gets lost: every saved key of the list taken before the settings were tidied up is still there, or is named here with where its value went. The list itself never changes;
 * {@code ./gradlew test -PupdateSettingKeys} only writes it when it does not exist yet.
 */
class SkyBlockSettingKeysTest {
	private static final Path BASELINE = Path.of("src/test/resources/settings-keys.txt");
	/** What left, on purpose, and where its value went. */
	private static final Map<String, String> GONE = new TreeMap<>(Map.of("item_tooltip_lines.stack_price", "the row with a fixed stack size is gone: Shift prices the real stack"));

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	private static TreeSet<String> keysNow() {
		TreeSet<String> keys = new TreeSet<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			for (Map.Entry<String, JsonElement> entry : feature.save().entrySet()) {
				keys.add(feature.id() + "." + entry.getKey());
			}
		}
		return keys;
	}

	@Test
	void everyKeyOfTheListIsStillThereOrNamedAsGone() throws IOException {
		TreeSet<String> now = keysNow();
		if (Boolean.getBoolean("lumio.updateSettingKeys") && !Files.exists(BASELINE)) {
			Files.createDirectories(BASELINE.getParent());
			Files.write(BASELINE, now, StandardCharsets.UTF_8);
		}
		List<String> lost = new ArrayList<>();
		for (String key : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
			if (!now.contains(key) && !GONE.containsKey(key)) {
				lost.add(key);
			}
		}
		assertEquals(List.of(), lost, "settings lost without a word");
	}
}
