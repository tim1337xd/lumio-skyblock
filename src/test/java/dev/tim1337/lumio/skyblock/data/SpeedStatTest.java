package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

class SpeedStatTest {
	@Test
	void theAttributeTimesAThousand() {
		assertEquals(100, SpeedStat.fromAttribute(0.1, false));
		assertEquals(400, SpeedStat.fromAttribute(0.4, false));
		assertEquals(273, SpeedStat.fromAttribute(0.273, false));
	}

	@Test
	void theSprintBonusDoesNotCount() {
		assertEquals(100, SpeedStat.fromAttribute(0.13, true));
		assertEquals(400, SpeedStat.fromAttribute(0.52, true));
		assertEquals(SpeedStat.fromAttribute(0.2, false), SpeedStat.fromAttribute(0.2 * 1.3, true));
	}

	@Test
	void numbersFromTheServerStayBounded() {
		assertEquals(0, SpeedStat.fromAttribute(0.0, false));
		assertEquals(0, SpeedStat.fromAttribute(-1.0, false));
		assertEquals(0, SpeedStat.fromAttribute(Double.NaN, false));
		assertEquals(0, SpeedStat.fromAttribute(Double.POSITIVE_INFINITY, true));
		assertEquals(0, SpeedStat.fromAttribute(1000.0, false));
	}

	@Test
	void theCap() {
		assertEquals(400, SpeedStat.cap(100));
		assertEquals(400, SpeedStat.cap(400));
		assertEquals(500, SpeedStat.cap(401));
		assertEquals(500, SpeedStat.cap(500));
		assertEquals(500, SpeedStat.cap(620), "above the raised cap the bar is simply full");
	}

	@Test
	void thePlayerList() {
		assertEquals(273, SpeedStat.fromTab(List.of("Stats:", " Strength: ❁120", " Speed: ✦273", " Crit Chance: ☣ 30")));
		assertEquals(400, SpeedStat.fromTab(List.of("Speed: ✦ 400")));
		assertEquals(-1, SpeedStat.fromTab(List.of("Mining Speed: 900", "Pet: Bee")), "another stat with the word in it");
		assertEquals(-1, SpeedStat.fromTab(List.of()));
	}
}
