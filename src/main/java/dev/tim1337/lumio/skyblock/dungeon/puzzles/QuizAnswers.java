package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import java.util.List;
import java.util.Map;

/**
 * The Quiz's answers: a question's right answers from the bundled list (Odin's data, BSD-3-Clause, see CREDITS.md), the SkyBlock year computed
 * from the clock, and which of the three options (ⓐ ⓑ ⓒ) is a right one. Plain text, so it is tested without the game.
 */
public final class QuizAnswers {
	/** The question with no fixed answer, and how the SkyBlock calendar counts: its start, and the seconds of one SkyBlock year. */
	static final String YEAR_QUESTION = "What SkyBlock year is it?";
	static final long CALENDAR_START_SECONDS = 1_560_276_000L;
	static final long YEAR_SECONDS = 446_400L;
	/** The letters of the three options, in their order. */
	public static final char[] LETTERS = {'ⓐ', 'ⓑ', 'ⓒ'};

	private QuizAnswers() {
	}

	/** @return the SkyBlock year at a time, in seconds since 1970 */
	public static long skyblockYear(long nowSeconds) {
		return Math.floorDiv(nowSeconds - CALENDAR_START_SECONDS, YEAR_SECONDS) + 1;
	}

	/**
	 * @param line       a line of the server's chat, as sent
	 * @param nowSeconds the time now, for the year question
	 * @return the right answers if the line is a question the list knows (or the year question), else null
	 */
	public static List<String> answersFor(String line, Map<String, List<String>> known, long nowSeconds) {
		String trimmed = line.strip();
		if (trimmed.equals(YEAR_QUESTION)) {
			return List.of("Year " + skyblockYear(nowSeconds));
		}
		for (Map.Entry<String, List<String>> entry : known.entrySet()) {
			if (trimmed.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** @return which option a line is (0 to 2) when it is one of the right answers, else -1 */
	public static int rightOption(String line, List<String> answers) {
		String trimmed = line.strip();
		if (answers == null || trimmed.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < LETTERS.length; i++) {
			if (trimmed.charAt(0) == LETTERS[i]) {
				for (String answer : answers) {
					if (trimmed.endsWith(answer)) {
						return i;
					}
				}
			}
		}
		return -1;
	}
}
