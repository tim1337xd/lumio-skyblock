package dev.tim1337.lumio.skyblock.dungeon.f7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/** Arrow Align's clicks per frame. */
class DevicesTest {
	@Test
	void arrowAlign() {
		int[] layout = new int[Devices.FRAMES];
		Arrays.fill(layout, -1);
		layout[0] = 7;
		layout[5] = 1;
		layout[10] = 3;
		int[] rotations = new int[Devices.FRAMES];
		Arrays.fill(rotations, -1);
		rotations[0] = 7;
		rotations[5] = 0;
		rotations[10] = 5;
		int[] clicks = Devices.arrowClicks(rotations, List.of(layout));
		assertEquals(0, clicks[0]);
		assertEquals(1, clicks[5]);
		// from 5 to 3 is six clicks: frames only turn one way
		assertEquals(6, clicks[10]);
		rotations[11] = 2;
		assertNull(Devices.arrowClicks(rotations, List.of(layout)));
	}
}
