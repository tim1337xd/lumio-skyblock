package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.ui.SlotLayer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Layer order: what lies on the slots is drawn in the slot layer, which the game's tooltip and the cursor's item lie on top of; drawing after the screen would lie over them. */
class SlotLayerUseTest {
	private static final Path SOURCES = Path.of("src/main/java/dev/tim1337/lumio/skyblock");
	private static final String AFTER_THE_SCREEN = "ScreenEvents.afterExtract";

	@Test
	void whatDrawsOnSlotsUsesTheSlotLayer() throws IOException {
		for (String file : List.of("experiments/ExperimentHelperFeature.java", "hud/PetCandyFeature.java", "hud/WardrobeOverlay.java", "SkyBlockRegistration.java")) {
			String source = Files.readString(SOURCES.resolve(file));
			assertFalse(source.contains(AFTER_THE_SCREEN), file + " draws over the tooltip");
			assertTrue(source.contains("SlotLayer.add"), file);
		}
	}

	@Test
	void onlyTheItemSearchPanelIsDrawnAfterTheScreen() throws IOException {
		List<String> after = new ArrayList<>();
		try (var files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				if (Files.readString(file).contains(AFTER_THE_SCREEN)) {
					after.add(file.getFileName().toString());
				}
			}
		}
		assertEquals(List.of("ItemListFeature.java"), after);
	}

	@Test
	void paintersRunInTheOrderTheyWereAdded() {
		List<Integer> order = new ArrayList<>();
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> order.add(1));
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> order.add(2));
		SlotLayer.draw(null, null, 0, 0);
		assertEquals(List.of(1, 2), order);
	}
}
