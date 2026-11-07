package dev.tim1337.lumio.skyblock.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Warps match by their start or any word, unlocked ones first, then by use; the Fast Travel menu's items are read. */
class WarpPickerTest {
	private static final WarpPicker.Warp HUB = new WarpPicker.Warp("hub", "Hub", true);
	private static final WarpPicker.Warp DRAG = new WarpPicker.Warp("drag", "The End", true);
	private static final WarpPicker.Warp END = new WarpPicker.Warp("end", "The End", true);
	private static final WarpPicker.Warp VOID = new WarpPicker.Warp("void", "The End", false);
	private static final WarpPicker.Warp SMOLD = new WarpPicker.Warp("smoldering_tomb", "Crimson Isle", true);

	private static List<String> names(List<WarpPicker.Warp> warps) {
		return warps.stream().map(WarpPicker.Warp::name).toList();
	}

	@Test
	void theStartThenAnyWord() {
		assertEquals(List.of("end", "drag", "void"), names(WarpPicker.order("end", List.of(HUB, DRAG, END, VOID), Map.of())));
		assertEquals(List.of("smoldering_tomb"), names(WarpPicker.order("tomb", List.of(HUB, SMOLD), Map.of())));
		assertEquals(List.of("smoldering_tomb"), names(WarpPicker.order("crim", List.of(HUB, SMOLD), Map.of())));
		assertEquals(List.of(), names(WarpPicker.order("zzz", List.of(HUB, SMOLD), Map.of())));
	}

	@Test
	void unlockedThenByUse() {
		assertEquals(List.of("drag", "hub", "void"), names(WarpPicker.order("", List.of(VOID, HUB, DRAG), Map.of("void", 50, "drag", 3, "hub", 1))));
	}

	@Test
	void theMenuShowsWarps() {
		WarpPicker.Warp hub = WarpPicker.ofMenuItem("Hub", List.of("/warp hub", "", "Click to warp!"));
		assertEquals("hub", hub.name());
		assertEquals("Hub", hub.island());
		assertEquals(true, hub.unlocked());
		assertFalse(WarpPicker.ofMenuItem("The End", List.of("/warp end", "Warp not unlocked!")).unlocked());
		assertNull(WarpPicker.ofMenuItem("Close", List.of("Closes the menu")));
	}
}
