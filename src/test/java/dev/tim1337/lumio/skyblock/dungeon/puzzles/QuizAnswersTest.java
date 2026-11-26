package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The Quiz: the SkyBlock year from the clock, and the questions and options as the server writes them. */
class QuizAnswersTest {
	private static final Map<String, List<String>> KNOWN = Map.of("What is the status of Bonzo?", List.of("New Necromancer"),
		"Which of these enemies does not spawn in the Spiders Den?", List.of("Zombie Spider", "Cave Spider", "Wither Skeleton"));

	@Test
	void theSkyBlockYear() {
		assertEquals(1, QuizAnswers.skyblockYear(1_560_276_000L), "the calendar's first second");
		assertEquals(1, QuizAnswers.skyblockYear(1_560_276_000L + 446_399L), "the last second of year 1");
		assertEquals(2, QuizAnswers.skyblockYear(1_560_276_000L + 446_400L));
		// 2024-01-01 00:00 UTC
		assertEquals(323, QuizAnswers.skyblockYear(1_704_067_200L));
		// 2026-11-08 12:00 UTC
		assertEquals(Math.floorDiv(1_794_052_800L - 1_560_276_000L, 446_400L) + 1, QuizAnswers.skyblockYear(1_794_052_800L));
	}

	@Test
	void theYearQuestionIsAnsweredFromTheClock() {
		List<String> answers = QuizAnswers.answersFor("                         What SkyBlock year is it?", KNOWN, 1_704_067_200L);
		assertEquals(List.of("Year 323"), answers);
		assertEquals(1, QuizAnswers.rightOption("     ⓑ Year 323", answers));
		assertEquals(-1, QuizAnswers.rightOption("     ⓐ Year 322", answers));
	}

	@Test
	void recordedLines() {
		List<String> answers = QuizAnswers.answersFor("                      What is the status of Bonzo?", KNOWN, 0);
		assertEquals(List.of("New Necromancer"), answers);
		assertEquals(-1, QuizAnswers.rightOption("     ⓐ Apprentice Necromancer", answers));
		assertEquals(1, QuizAnswers.rightOption("     ⓑ New Necromancer", answers));
		assertEquals(-1, QuizAnswers.rightOption("     ⓒ Shaman Necromancer", answers));
		List<String> spiders = QuizAnswers.answersFor(" Which of these enemies does not spawn in the Spiders Den?", KNOWN, 0);
		assertEquals(2, QuizAnswers.rightOption(" ⓒ Wither Skeleton", spiders));
		assertNull(QuizAnswers.answersFor("[STATUE] Oruo the Omniscient: Kiwi answered Question #1 correctly!", KNOWN, 0));
	}

}
