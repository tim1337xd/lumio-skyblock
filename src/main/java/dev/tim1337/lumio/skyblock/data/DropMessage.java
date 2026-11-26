package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.Optional;
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
	/** The magic find at the end: "(+437% ✯ Magic Find)", whatever the symbol, also without the percent sign: "(+191✯ Magic Find)", "(+507 ✯ Magic Find)". */
	private static final Pattern MAGIC_FIND = Pattern.compile("\\s*\\(\\+(\\d{1,5})%?\\s*\\S{0,2}\\s*Magic Find!?\\)\\s*$");
	/** The farming fortune at the end of a Garden drop: "(+144.4)". */
	private static final Pattern FORTUNE = Pattern.compile("\\s*\\(\\+[\\d.,]{1,12}\\)\\s*$");
	/** The pest a Garden drop came from, after its name: "Not Just a Pest Vinyl (Cocoaleech)". */
	private static final Pattern FROM_PEST = Pattern.compile("^(.+?\\S)\\s+\\([A-Z][a-z]{2,20}\\)$");
	/** A drop told as a sentence: "You dropped 24x Enchanted Moonflower!", "You dug out a Griffin Feather!". */
	private static final Pattern SENTENCE = Pattern.compile("^You (?:dropped|dug out|found)\\s+(?:an?\\s+)?(.+?)!?$");
	/** A pet's rarity in front of its name: "EPIC Slug". */
	private static final Pattern RARITY_IN_FRONT = Pattern.compile("^(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)\\s+");
	/** "LOOT SHARE! You received 3 Thunder Shards for assisting …!", or "… received loot for …", which names nothing. */
	private static final Pattern LOOT_SHARE = Pattern.compile("^LOOT SHARE!?\\s+You received (?:(\\d{1,5})\\s+)?(.{1,60}?) for assisting .{1,40}!$");
	private static final String ANY_LOOT = "loot";
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
		String text = TextColors.noColors(message).strip();
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
		rest = FORTUNE.matcher(rest).replaceFirst("").strip();
		Matcher sentence = SENTENCE.matcher(rest);
		if (sentence.matches()) {
			rest = sentence.group(1).strip();
		}
		if (pet) {
			rest = RARITY_IN_FRONT.matcher(rest).replaceFirst("");
		}
		// the server puts most drops in parentheses, also with an amount or a rune's sign: "(63x Twilight Arrow Poison)", "(Null Atom)", "(◆ End Rune I)"; a book is told by its level below
		boolean isBook = BOOK.matcher(rest).matches();
		if (!isBook && rest.length() > 2 && rest.charAt(0) == '(' && rest.endsWith(")")) {
			rest = rest.substring(1, rest.length() - 1).strip();
		}
		rest = SIGN_IN_FRONT.matcher(rest).replaceFirst("");
		Matcher fromPest = isBook ? null : FROM_PEST.matcher(rest);
		if (fromPest != null && fromPest.matches()) {
			rest = fromPest.group(1);
		}
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
			int level = Roman.strict(book.group(2), 0);
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

	/**
	 * A loot share as the server announces it: "LOOT SHARE! You received 3 Thunder Shards for assisting …!" names the item, "LOOT SHARE You received loot
	 * for assisting …!" does not (its items then simply arrive).
	 *
	 * @param name   the item's name as written, maybe in the plural ("Thunder Shards"); null when the message names none
	 * @param amount how many
	 */
	public record LootShare(String name, int amount) {
		/** @return whether the message names the item */
		public boolean named() {
			return name != null;
		}
	}

	/** @return the loot share a server message announces, or null when it is none */
	public static LootShare lootShare(String message) {
		Matcher share = LOOT_SHARE.matcher(TextColors.noColors(message).strip());
		if (!share.matches()) {
			return null;
		}
		String name = share.group(2).strip();
		if (name.equalsIgnoreCase(ANY_LOOT)) {
			return new LootShare(null, 1);
		}
		int amount = share.group(1) == null ? 1 : Math.min(MOST_AMOUNT, Integer.parseInt(share.group(1)));
		return new LootShare(name, Math.max(1, amount));
	}

	/**
	 * Shards the player got, as the server says it: they go into the Hunting Box, never into the inventory, so this line is all there is of them.
	 *
	 * @param name   the shard's name without "Shard": "Flaming Spider", "Murkbat"
	 * @param amount how many
	 */
	public record ShardGain(String name, int amount) {
	}

	/** "CHARM! You charmed the Flaming Spider and received 1 Flaming Spider Shard!" (the shard may be named apart from the mob: an Empyrean Sphinx gives a Sphinx Shard). */
	private static final Pattern CHARMED = Pattern.compile("^CHARM! You charmed the .{1,60}? and received (\\d{1,5}) (.{1,40}?) Shards?!$");
	/** "You caught x2 Murkbat Shards!" */
	private static final Pattern CAUGHT_SHARDS = Pattern.compile("^You caught x(\\d{1,5}) (.{1,40}?) Shards?!$");
	/** A loot share of shards: "Thunder Shards". */
	private static final Pattern SHARD_NAME = Pattern.compile("^(.{1,40}?) Shards?$");

	/** @return the shards a server message says the player got (charmed, caught, or a loot share of shards), or null when it says none */
	public static ShardGain shards(String message) {
		String text = TextColors.noColors(message).strip();
		Matcher gained = CHARMED.matcher(text);
		if (!gained.matches()) {
			gained = CAUGHT_SHARDS.matcher(text);
		}
		if (gained.matches()) {
			return new ShardGain(gained.group(2).strip(), Math.max(1, Math.min(MOST_AMOUNT, Integer.parseInt(gained.group(1)))));
		}
		LootShare share = lootShare(text);
		Matcher name = share == null || !share.named() ? null : SHARD_NAME.matcher(share.name());
		return name != null && name.matches() ? new ShardGain(name.group(1).strip(), share.amount()) : null;
	}

	/** @return the color of an item's name in a drop message: its rarity color, or {@code fallback} when it has none */
	public static int nameColor(Component message, String item, int fallback) {
		String start = item.length() > 3 ? item.substring(0, 3) : item;
		Optional<TextColor> found = message.visit((Style style, String part) -> style.getColor() != null && part.contains(start)
			? Optional.of(style.getColor()) : Optional.empty(), Style.EMPTY);
		return found.map(color -> 0xFF000000 | color.getValue()).orElse(fallback);
	}

	/** @return whether the drop is an enchanted book */
	public boolean book() {
		return bookId != null;
	}

}
