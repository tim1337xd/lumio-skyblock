package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Higher or Lower and the Quiz: the blazes' name tags and their order in both rooms, and the Quiz read from chat lines as the server sends them. */
class BlazeAndQuizTest {
	// ------------------------------------------------------------------------------------------------ Higher or Lower

	@Test
	void theBlazesNameTags() {
		assertEquals(1_500, BlazeOrder.health("[Lv15] Blaze 1,234/1,500❤"));
		assertEquals(600, BlazeOrder.health("[Lv15] Blaze 600/600❤"));
		assertEquals(1_500, BlazeOrder.health("[Lv15] ♨ Blaze 1,234/1,500❤"), "with the symbol of its kind before the name");
		assertEquals(12_000, BlazeOrder.health("§8[§7Lv15§8] §c♨ §cBlaze §a11,000§f/§a12,000§c❤"), "with the game's color codes");
		assertEquals(1_500, BlazeOrder.health("[Lv15] Blaze 1.2k/1.5k❤"), "shortened numbers");
		assertEquals(2_000_000, BlazeOrder.health("[Lv150] Blaze 1.5M/2M❤"));
		assertEquals(600, BlazeOrder.health("  [Lv15] Blaze 0/600❤  "));
		assertEquals(-1, BlazeOrder.health("[Lv15] Skeleton Soldier 600/600❤"));
		assertEquals(-1, BlazeOrder.health("[Lv15] Blaze"));
		assertEquals(-1, BlazeOrder.health("Blaze 600/600❤"), "no level: not a mob's tag");
		assertEquals(-1, BlazeOrder.health("[Lv15] ♨ Wither Blaze King 600/600❤"), "another mob with Blaze in its name");
		assertEquals(-1, BlazeOrder.health(null));
		assertEquals(-1, BlazeOrder.health(""));
	}

	private static List<BlazeOrder.Blaze> blazes(double y) {
		return List.of(new BlazeOrder.Blaze(1, 900, y), new BlazeOrder.Blaze(2, 300, y), new BlazeOrder.Blaze(3, 1_500, y), new BlazeOrder.Blaze(4, 600, y));
	}

	private static List<Long> healths(BlazeOrder.Order order) {
		List<Long> healths = new ArrayList<>();
		order.blazes().forEach(blaze -> healths.add(blaze.health()));
		return healths;
	}

	@Test
	void theOrderInBothRooms() {
		BlazeOrder.Order lower = BlazeOrder.order(blazes(60), "Lower Blaze", 70);
		assertEquals(List.of(1_500L, 900L, 600L, 300L), healths(lower), "the lower room: from the highest health down");
		assertEquals(BlazeOrder.By.ROOM, lower.by());
		BlazeOrder.Order higher = BlazeOrder.order(blazes(110), "Higher Blaze", 70);
		assertEquals(List.of(300L, 600L, 900L, 1_500L), healths(higher), "the higher room: from the lowest health up");
		assertNull(higher.other());
		// a blaze died: its tag is gone, and the next one is first
		List<BlazeOrder.Blaze> left = new ArrayList<>(blazes(60));
		left.removeIf(blaze -> blaze.health() == 1_500);
		assertEquals(900, BlazeOrder.order(left, "Lower Blaze", 70).blazes().getFirst().health());
		assertNull(BlazeOrder.order(List.of(), "Lower Blaze", 70), "no blazes: nothing to show");
		assertTrue(BlazeOrder.puzzleRoom("Lower Blaze"));
		assertTrue(BlazeOrder.puzzleRoom("Higher Blaze"));
		assertFalse(BlazeOrder.puzzleRoom("Quiz"));
		assertFalse(BlazeOrder.puzzleRoom(null));
	}

	@Test
	void withoutTheRoomsNameTheHeightTells() {
		// the room is not known yet: blazes well below the player are the lower room's, well above the higher room's
		BlazeOrder.Order below = BlazeOrder.order(blazes(55), null, 70);
		assertEquals(BlazeOrder.By.HEIGHT, below.by());
		assertEquals(1_500, below.blazes().getFirst().health());
		BlazeOrder.Order above = BlazeOrder.order(blazes(95), "", 70);
		assertEquals(BlazeOrder.By.HEIGHT, above.by());
		assertEquals(300, above.blazes().getFirst().health());
		// about level with the player it cannot be told: one order is shown and the other order's first blaze is named too
		BlazeOrder.Order level = BlazeOrder.order(blazes(72), null, 70);
		assertEquals(BlazeOrder.By.UNKNOWN, level.by());
		assertNotNull(level.other());
		assertEquals(300L + 1_500L, level.blazes().getFirst().health() + level.other().health(), "the first of both orders");
	}

	// ------------------------------------------------------------------------------------------------ the Quiz

	private static final Map<String, List<String>> KNOWN = Map.of("What is the status of The Watcher?", List.of("Stalker"),
		"What is the name of the vendor in the Hub who sells stained", List.of("Wool Weaver"),
		"How many total Fairy Souls are there?", List.of("289 Fairy Souls"));
	private static final long NOW = 1_800_000_000L;

	private static QuizReader.Result ask(QuizReader reader, String... lines) {
		QuizReader.Result last = null;
		for (String line : lines) {
			QuizReader.Result result = reader.read(line, NOW);
			if (result != null) {
				assertNull(last, "one line per question");
				last = result;
			}
		}
		return last;
	}

	@Test
	void theRightOptionOfAQuestion() {
		QuizReader reader = new QuizReader(KNOWN);
		QuizReader.Result result = ask(reader, "[STATUE] Oruo the Omniscient: I am Oruo the Omniscient. I have lived many lives.",
			"                                Question #1", "                 What is the status of The Watcher?", "", "       ⓐ Watcher", "       ⓑ Stalker",
			"       ⓒ Overseer");
		assertEquals(QuizReader.Kind.ANSWER, result.kind());
		assertEquals(1, result.option());
		assertEquals("Stalker", result.text());
		// the next question starts afresh
		QuizReader.Result next = ask(reader, "[STATUE] Oruo the Omniscient: Kiwi answered Question #1 correctly!", "Question #2",
			"How many total Fairy Souls are there?", "ⓐ 289 Fairy Souls", "ⓑ 266 Fairy Souls", "ⓒ 247 Fairy Souls");
		assertEquals(0, next.option());
		assertEquals("289 Fairy Souls", next.text());
	}

	@Test
	void aQuestionBrokenOverTwoLines() {
		QuizReader.Result result = ask(new QuizReader(KNOWN), "Question #3", "   What is the name of the vendor in the Hub who sells stained", "   glass?",
			"ⓐ Wool Weaver", "ⓑ Builder", "ⓒ Librarian");
		assertEquals(QuizReader.Kind.ANSWER, result.kind());
		assertEquals(0, result.option());
		assertEquals("What is the name of the vendor in the Hub who sells stained glass?", result.question());
	}

	@Test
	void theYearIsWorkedOut() {
		long year = QuizAnswers.skyblockYear(NOW);
		QuizReader.Result result = ask(new QuizReader(KNOWN), "Question #1", "What SkyBlock year is it?", "ⓐ Year " + (year - 1), "ⓑ Year " + (year + 1),
			"ⓒ Year " + year);
		assertEquals(2, result.option());
		assertEquals("Year " + year, result.text());
	}

	@Test
	void noAnswerKnown() {
		// a question the list does not have
		QuizReader.Result unknown = ask(new QuizReader(KNOWN), "Question #2", "Who is the mayor of the moon?", "ⓐ Nobody", "ⓑ Diana", "ⓒ Paul");
		assertEquals(QuizReader.Kind.NO_ANSWER, unknown.kind());
		assertFalse(unknown.known());
		assertEquals("Who is the mayor of the moon?", unknown.question());
		// a question on the list whose answer is not among the options (the list is out of date)
		QuizReader.Result outdated = ask(new QuizReader(KNOWN), "Question #2", "How many total Fairy Souls are there?", "ⓐ 301 Fairy Souls", "ⓑ 302 Fairy Souls",
			"ⓒ 303 Fairy Souls");
		assertEquals(QuizReader.Kind.NO_ANSWER, outdated.kind());
		assertTrue(outdated.known());
	}

	@Test
	void aQuestionWithoutItsHeaderAndOtherChat() {
		// the "Question #n" line was missed: the list still knows the question
		QuizReader.Result result = ask(new QuizReader(KNOWN), "What is the status of The Watcher?", "ⓐ Stalker", "ⓑ Watcher", "ⓒ Overseer");
		assertEquals(0, result.option());
		// options without any question, and other chat, say nothing
		QuizReader reader = new QuizReader(KNOWN);
		assertNull(ask(reader, "ⓐ Stalker", "ⓑ Watcher", "ⓒ Overseer", "Is this a question?", "[NPC] Mort: Good luck."));
		// answers of the question before never count for the next one
		QuizReader two = new QuizReader(KNOWN);
		ask(two, "Question #1", "What is the status of The Watcher?", "ⓐ Stalker", "ⓑ Watcher", "ⓒ Overseer");
		QuizReader.Result next = ask(two, "Question #2", "Who is the mayor of the moon?", "ⓐ Stalker", "ⓑ Diana", "ⓒ Paul");
		assertEquals(QuizReader.Kind.NO_ANSWER, next.kind());
	}
}
