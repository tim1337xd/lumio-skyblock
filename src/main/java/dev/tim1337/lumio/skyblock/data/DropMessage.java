package dev.tim1337.lumio.skyblock.data;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rare drop as the server announces it, split into its parts: "VERY RARE DROP! (Mana Steal I) (+437% ✯ Magic Find)" is the item ("Mana
 * Steal I", an enchanted book), its amount and the magic find; every kind of drop (rare, very rare, pet, ...) counts the same. Color codes
 * anywhere in the message are removed first; the magic find's symbol may be any character, so a symbol the font cannot show never breaks the
 * reading. Plain strings, so it is unit tested with recorded messages.
 *
 * @param name      the item's name: "Mana Steal I", "Shard of the Shredded"
 * @param amount    how many dropped, 1 unless the message says more
 * @param magicFind the magic find, or -1 when the message has none
 * @param bookId    the item id of an enchanted book ("ENCHANTMENT_MANA_STEAL_1"), or null when it is no book
 * @param pet       whether it is a pet ("PET DROP!", or a name with its level in front: "[Lvl 1] Phoenix"); its name is then the pet's type
 */
public record DropMessage(String name, int amount, int magicFind, String bookId, boolean pet) {
	private static final Pattern DROP = Pattern.compile(
		"^(?:(PET) DROP|[A-Z][A-Z ]{0,30} DROP)!\\s*(.{1,120})$");
	/** The magic find at the end: "(+437% ✯ Magic Find)", whatever the symbol. */
	private static final Pattern MAGIC_FIND = Pattern.compile("\\s*\\(\\+(\\d{1,5})%\\s*\\S{0,2}\\s*Magic Find!?\\)\\s*$");
	/** A sign in front of a name, such as a rune's "◆ ". */
	private static final Pattern SIGN_IN_FRONT = Pattern.compile("^[^\\p{L}\\p{N}\\[(]{1,3}\\s*");
	/** An amount in front ("2x Enchanted Bone") or behind ("Enchanted Bone x2"). */
	private static final Pattern AMOUNT_BEFORE = Pattern.compile("^(\\d{1,5})x\\s+(.+)$");
	private static final Pattern AMOUNT_AFTER = Pattern.compile("^(.+?)\\s+x(\\d{1,5})$");
	/** An enchanted book: its enchantment and level in parentheses, "(Mana Steal I)", maybe after "Enchanted Book". */
	private static final Pattern BOOK = Pattern.compile("^(?:Enchanted Book\\s*)?\\(([A-Za-z' -]{1,40}?)\\s+([IVXLC]{1,7})\\)$");
	/** A pet's level in front of its name: "[Lvl 1] ". */
	private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl\\s*\\d{1,3}\\]\\s*");
	/** The longest an amount can be, so a strange message cannot multiply a value without end. */
	private static final int MOST_AMOUNT = 99_999;

	/** @return the drop a server message announces, or null when it is no rare drop */
	public static DropMessage parse(String message) {
		String text = noColors(message).strip();
		Matcher drop = DROP.matcher(text);
		if (!drop.matches()) {
			return null;
		}
		boolean pet = drop.group(1) != null;
		String rest = drop.group(2).strip();
		int magicFind = -1;
		Matcher found = MAGIC_FIND.matcher(rest);
		if (found.find()) {
			magicFind = Integer.parseInt(found.group(1));
			rest = rest.substring(0, found.start()).strip();
		}
		// the server puts most drops in parentheses, also with an amount or a rune's sign: "(63x Twilight Arrow Poison)", "(Null Atom)", "(◆ End Rune I)"; a book is told by its level below
		boolean isBook = BOOK.matcher(rest).matches();
		if (!isBook && rest.length() > 2 && rest.charAt(0) == '(' && rest.endsWith(")")) {
			rest = rest.substring(1, rest.length() - 1).strip();
		}
		rest = SIGN_IN_FRONT.matcher(rest).replaceFirst("");
		int amount = 1;
		Matcher before = AMOUNT_BEFORE.matcher(rest);
		Matcher after = AMOUNT_AFTER.matcher(rest);
		if (before.matches()) {
			amount = Math.min(MOST_AMOUNT, Integer.parseInt(before.group(1)));
			rest = before.group(2).strip();
		} else if (after.matches()) {
			amount = Math.min(MOST_AMOUNT, Integer.parseInt(after.group(2)));
			rest = after.group(1).strip();
		}
		String bookId = null;
		Matcher book = BOOK.matcher(rest);
		if (book.matches()) {
			int level = roman(book.group(2));
			if (level > 0) {
				String enchantment = book.group(1).strip();
				rest = enchantment + " " + book.group(2);
				bookId = "ENCHANTMENT_" + enchantment.toUpperCase(Locale.ROOT).replace("'", "").replace(' ', '_').replace('-', '_') + "_" + level;
			}
		}
		// a pet's level in front of its type: "[Lvl 1] Phoenix"
		Matcher level = PET_LEVEL.matcher(rest);
		if (level.find()) {
			pet = true;
			rest = rest.substring(level.end()).strip();
		}
		if (rest.isEmpty()) {
			return null;
		}
		return new DropMessage(rest, Math.max(1, amount), magicFind, bookId, pet);
	}

	/** @return whether the drop is an enchanted book */
	public boolean book() {
		return bookId != null;
	}

	/** @return a roman number's value (I to C), or 0 when it is none */
	static int roman(String numeral) {
		int total = 0;
		int previous = 0;
		for (int i = numeral.length() - 1; i >= 0; i--) {
			int value = switch (numeral.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> 0;
			};
			if (value == 0) {
				return 0;
			}
			total += value < previous ? -value : value;
			previous = Math.max(previous, value);
		}
		return total;
	}

	/** @return the text without color and format codes ("§d"), wherever they are */
	private static String noColors(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i++;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
