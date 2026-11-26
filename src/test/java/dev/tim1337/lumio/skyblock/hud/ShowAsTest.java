package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** "Show as" of the alerts: what a drop shows as for each choice, where its sound comes from, and the old single choice taken over. */
class ShowAsTest {
	private static final double TITLE_FROM = 1_000_000;

	@Test
	void thePopUpAloneIsAsBefore() {
		ShowAs.Chosen shows = new ShowAs.Chosen(true, false, false).forValue(5_000_000, TITLE_FROM);
		assertEquals(new ShowAs.Chosen(true, false, false), shows);
		assertFalse(shows.soundAtOnce(), "the pop-up plays the sound when its turn comes");
	}

	@Test
	void aTitleOnlyFromItsLeastValue() {
		ShowAs.Chosen title = new ShowAs.Chosen(false, true, false);
		assertEquals(new ShowAs.Chosen(false, true, false), title.forValue(TITLE_FROM, TITLE_FROM));
		assertTrue(title.forValue(TITLE_FROM, TITLE_FROM).soundAtOnce());
		// a small drop with only Title chosen stays a pop-up, so it is not lost
		assertEquals(new ShowAs.Chosen(true, false, false), title.forValue(TITLE_FROM - 1, TITLE_FROM));
	}

	@Test
	void combinations() {
		ShowAs.Chosen all = new ShowAs.Chosen(true, true, true);
		assertEquals(new ShowAs.Chosen(true, true, true), all.forValue(2_000_000, TITLE_FROM));
		assertEquals(new ShowAs.Chosen(true, false, true), all.forValue(500_000, TITLE_FROM));
		ShowAs.Chosen titleAndChat = new ShowAs.Chosen(false, true, true);
		assertEquals(new ShowAs.Chosen(false, true, true), titleAndChat.forValue(2_000_000, TITLE_FROM));
		// below the title's value the chat line alone is left: no pop-up is added, the player chose none
		assertEquals(new ShowAs.Chosen(false, false, true), titleAndChat.forValue(500_000, TITLE_FROM));
		assertTrue(titleAndChat.forValue(500_000, TITLE_FROM).soundAtOnce());
		assertFalse(all.forValue(500_000, TITLE_FROM).soundAtOnce(), "one sound per drop: the pop-up's");
	}

	@Test
	void nothingChosenStillShowsThePopUp() {
		assertEquals(new ShowAs.Chosen(true, false, false), new ShowAs.Chosen(false, false, false).forValue(5_000_000, TITLE_FROM));
	}

	@Test
	void theOldSingleChoiceIsTakenOver() {
		assertEquals(new ShowAs.Chosen(false, true, false), ShowAs.fromOld("TITLE"));
		assertEquals(new ShowAs.Chosen(true, false, false), ShowAs.fromOld("TOAST"));
		assertEquals(new ShowAs.Chosen(false, false, true), ShowAs.fromOld("CHAT"));
		assertNull(ShowAs.fromOld("BANNER"));
	}

	@Test
	void theLineUnderADropsTitle() {
		assertEquals("3.2M · Magic find 437", DropToastFeature.titleDetail("3.2M", "Magic find 437"));
		assertEquals("41M", DropToastFeature.titleDetail("41M", ""));
	}
}
