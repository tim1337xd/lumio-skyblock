package dev.tim1337.lumio.skyblock.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Typing lights up the matches; a double click on the search bar keeps the light on from one container to the next until it is turned off. */
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
	void typingAloneLightsNothingUp() {
		// the bug: typing lit the matches and dimmed the rest always. Typing only filters the items; highlight mode is a choice
		SearchLight light = new SearchLight();
		assertFalse(light.lights("diamond"));
		assertFalse(light.lights(""));
	}

	@Test
	void inHighlightModeASearchLightsUpAnEmptyOneNever() {
		SearchLight light = new SearchLight();
		light.clicked(1000);
		light.clicked(1100);
		assertTrue(light.on());
		assertTrue(light.lights("diamond"));
		assertFalse(light.lights(""));
		assertFalse(light.lights("  "));
		// switched off again: the same search lights nothing
		light.clicked(5000);
		light.clicked(5100);
		assertFalse(light.lights("diamond"));
	}

	@Test
	void afterARestartOrWithAllScreensClosedItIsOff() {
		// a new game starts with it off (it is not saved), also with a remembered search
		assertFalse(new SearchLight().lights("diamond"));
		// reopening the inventory after it was closed for a moment: off again, the search still typed
		SearchLight light = new SearchLight();
		light.clicked(1000);
		light.clicked(1100);
		for (int tick = 0; tick < SearchLight.CLOSED_TICKS; tick++) {
			light.tick(false);
		}
		light.tick(true);
		assertFalse(light.lights("diamond"));
	}

	@Test
	void fromOneContainerToTheNextItStaysOn() {
		SearchLight light = new SearchLight();
		light.clicked(1000);
		light.clicked(1100);
		// a server changing containers closes one a little before it opens the next
		light.tick(false);
		light.tick(false);
		light.tick(true);
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
