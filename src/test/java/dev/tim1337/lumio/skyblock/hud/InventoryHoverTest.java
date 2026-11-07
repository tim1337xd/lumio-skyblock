package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The dim rule: while a screen is open a panel is dimmed like the others, and only the one under the mouse comes up. */
class InventoryHoverTest {
	/** How a panel draws itself again over an open screen. */
	private static final String DRAWS_OVER_A_SCREEN = "void renderInInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY)";
	/** The equipment's slots belong to the inventory window itself: they are no HUD panel. */
	private static final String PART_OF_THE_INVENTORY = "EquipmentHudFeature.java";
	private static final Path SOURCES = Path.of("src/main/java/dev/tim1337/lumio/skyblock");

	@Test
	void onlyThePanelUnderTheMouse() {
		int[] box = {10, 20, 100, 50};
		assertTrue(InventoryHover.over(box, 10, 20));
		assertTrue(InventoryHover.over(box, 109, 69));
		assertFalse(InventoryHover.over(box, 110, 20));
		assertFalse(InventoryHover.over(box, 9, 20));
		assertFalse(InventoryHover.over(box, 10, 70));
	}

	@Test
	void aPanelAtRestIsNotDrawnOverTheScreen() {
		assertFalse(InventoryHover.drawn(0f));
		assertTrue(InventoryHover.drawn(1f));
	}

	@Test
	void everyPanelDrawnOverAScreenFollowsTheRule() throws IOException {
		List<String> panels = new ArrayList<>();
		try (var files = Files.walk(SOURCES)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				if (source.contains(DRAWS_OVER_A_SCREEN) && !file.endsWith(PART_OF_THE_INVENTORY)) {
					panels.add(file.getFileName().toString());
					assertTrue(source.contains("InventoryHover"), file.getFileName() + " draws over a screen without the dim rule");
				}
			}
		}
		assertEquals(List.of("ProfitHudFeature.java", "SeaCreatureHudFeature.java"), panels.stream().sorted().toList());
	}
}
