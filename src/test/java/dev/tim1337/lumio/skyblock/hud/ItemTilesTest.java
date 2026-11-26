package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemTilesTest {
	@Test
	void anItemKeepsItsOwnSizeInTheUsualSlots() {
		for (int size = 18; size <= 22; size++) {
			assertEquals(ItemTiles.ITEM, ItemTiles.drawnSize(size, 2), "slot " + size);
		}
	}

	@Test
	void anItemIsInTheMiddleOnWholeScreenPixels() {
		for (int scale = 1; scale <= 4; scale++) {
			for (int size = 8; size <= 40; size++) {
				float drawn = ItemTiles.drawnSize(size, scale);
				float offset = ItemTiles.centered(size, drawn, scale);
				float left = offset * scale;
				float right = (size - offset - drawn) * scale;
				assertEquals(Math.round(left), left, 1e-4, "starts on a screen pixel: slot " + size + " at scale " + scale);
				assertEquals(Math.round(drawn * scale), drawn * scale, 1e-4, "ends on a screen pixel");
				assertTrue(Math.abs(left - right) <= 1f + 1e-4, "the room on both sides differs by a screen pixel at most: slot " + size + " at scale " + scale);
			}
		}
	}
}
