package dev.tim1337.lumio.skyblock.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Typing only filters; a double click on the search bar lights up the slots until it is turned off. */
class SearchLightTest {
	@Test
	void aSingleClickDoesNothing() {
		SearchLight light = new SearchLight();
		assertFalse(light.clicked(1000));
		assertFalse(light.on());
		assertFalse(light.clicked(1000 + SearchLight.DOUBLE_CLICK_MILLIS + 1));
		assertFalse(light.on());
	}

	@Test
	void aDoubleClickTurnsItOnAndAnotherOff() {
		SearchLight light = new SearchLight();
		light.clicked(1000);
		assertTrue(light.clicked(1200));
		assertTrue(light.on());
		light.clicked(2000);
		assertTrue(light.clicked(2100));
		assertFalse(light.on());
	}

	@Test
	void aThirdClickIsNotAnotherDoubleClick() {
		SearchLight light = new SearchLight();
		light.clicked(1000);
		light.clicked(1100);
		assertFalse(light.clicked(1200));
		assertTrue(light.on());
	}

	@Test
	void anEmptySearchNeverLightsUp() {
		SearchLight light = new SearchLight();
		light.clicked(1000);
		light.clicked(1100);
		assertFalse(light.lights(""));
		assertFalse(light.lights("  "));
		assertTrue(light.lights("diamond"));
	}

	@Test
	void escTurnsItOff() {
		SearchLight light = new SearchLight();
		assertFalse(light.escape());
		light.clicked(1000);
		light.clicked(1100);
		assertTrue(light.escape());
		assertFalse(light.on());
	}

	@Test
	void itStaysOnWhileContainersChangeAndGoesWhenAllAreClosed() {
		SearchLight light = new SearchLight();
		light.clicked(1000);
		light.clicked(1100);
		light.tick(false);
		light.tick(false);
		light.tick(true);
		assertTrue(light.on());
		for (int tick = 0; tick < SearchLight.CLOSED_TICKS; tick++) {
			light.tick(false);
		}
		assertFalse(light.on());
	}
}
