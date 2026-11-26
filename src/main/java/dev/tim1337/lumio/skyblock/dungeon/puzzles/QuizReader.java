package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the Quiz from the server's chat, line by line: the "Question #n" line, the question after it (one line or broken over two), and the
 * three options ⓐ ⓑ ⓒ. For every question it says once what is known: the letter and text of the right option, or that no answer is known
 * (the question is not on the list, or none of the options is one of its answers). A question that comes without its "Question #n" line is
 * still found by the list. Plain text in, so it is tested without the game.
 */
public final class QuizReader {
	/** What the reader says about a question. */
	public enum Kind {
		/** The right option is known. */
		ANSWER,
		/** The question is asked and no answer is known for it. */
		NO_ANSWER
	}

	/**
	 * @param option   the right option, 0 to 2; -1 without one
	 * @param text     the right option's text, without its letter; "" without one
	 * @param question the question as it was asked, its lines joined
	 * @param known    whether the question is on the list (then its options did not match), or not at all
	 */
	public record Result(Kind kind, int option, String text, String question, boolean known) {
	}

	private static final Pattern HEADER = Pattern.compile("^Question #\\d{1,2}$");
	private static final int MOST_QUESTION = 300;

	private final Map<String, List<String>> answers;
	private boolean asking;
	private final StringBuilder question = new StringBuilder();
	private List<String> current;
	private boolean told;
	private int optionsSeen;

	public QuizReader(Map<String, List<String>> answers) {
		this.answers = answers;
	}

	/** Forgets the question: the room was left or the run is over. */
	public void reset() {
		asking = false;
		question.setLength(0);
		current = null;
		told = false;
		optionsSeen = 0;
	}

	/**
	 * @param line       a line of the server's chat, plain text; a message of several lines is given line by line
	 * @param nowSeconds the time now, for the year question
	 * @return what is known about the question once it is: the answer when its option is read, or "no answer" after the last option; else null
	 */
	public Result read(String line, long nowSeconds) {
		String text = line.strip();
		if (text.isEmpty()) {
			return null;
		}
		if (HEADER.matcher(text).matches()) {
			reset();
			asking = true;
			return null;
		}
		int option = optionOf(text);
		if (option < 0) {
			if (asking && optionsSeen == 0 && !text.startsWith("[") && question.length() + text.length() < MOST_QUESTION) {
				// the question, which may be broken over two lines
				question.append(question.isEmpty() ? "" : " ").append(text);
				current = QuizAnswers.answersFor(question.toString(), answers, nowSeconds);
			} else if (!asking) {
				// without its "Question #n" line: any line the list knows is a question
				List<String> found = QuizAnswers.answersFor(text, answers, nowSeconds);
				if (found != null) {
					reset();
					asking = true;
					question.append(text);
					current = found;
				}
			}
			return null;
		}
		if (!asking) {
			return null;
		}
		optionsSeen++;
		if (told) {
			return null;
		}
		if (QuizAnswers.rightOption(text, current) == option) {
			told = true;
			return new Result(Kind.ANSWER, option, text.substring(1).strip(), question.toString(), true);
		}
		if (option == QuizAnswers.LETTERS.length - 1) {
			// the last option, and none was right
			told = true;
			return new Result(Kind.NO_ANSWER, -1, "", question.toString(), current != null);
		}
		return null;
	}

	/** @return which option a line is (0 to 2) by its letter, or -1 */
	static int optionOf(String text) {
		for (int i = 0; i < QuizAnswers.LETTERS.length; i++) {
			if (text.charAt(0) == QuizAnswers.LETTERS[i]) {
				return i;
			}
		}
		return -1;
	}
}
