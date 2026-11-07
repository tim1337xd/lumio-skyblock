package dev.tim1337.lumio.skyblock.slayer;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which items on the ground dropped from the player's own or carried slayer boss: those that appeared close to where it died, shortly around its death, and whose
 * name is a slayer drop. So other players' drops and random items nearby stay plain. Plain text and numbers, so it is unit tested.
 */
final class BossDrops {
	/** How far from where the boss died a drop lies, in blocks. */
	static final double NEAR_BLOCKS = 12.0;
	/** How long after the death a drop appears; it can also appear a moment before the death's chat message arrives. */
	static final long AFTER_MILLIS = 15_000L;
	static final long BEFORE_MILLIS = 8_000L;
	/** The rarity from which a drop gets its name above it: rare, in the order common, uncommon, rare, epic, legendary, mythic. */
	static final int LABEL_FROM_RARITY = 2;
	/** Rarities no name color tells, such as divine and special, are above all others. */
	static final int UNKNOWN_RARITY = -1;

	private static final Pattern AMOUNT_AT_END = Pattern.compile("\\s+x?\\d{1,5}$");
	private static final Pattern AMOUNT_IN_FRONT = Pattern.compile("^\\d{1,5}x?\\s+");
	/** Color codes, and signs in front of a name or behind it, such as a rune's "◆" or a star. */
	private static final Pattern COLOR_CODES = Pattern.compile("§.");
	private static final Pattern SIGNS_AROUND = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}']+$");
	private static final Pattern LEVEL_AT_END = Pattern.compile("\\s+[ivxlc]{1,7}$");
	private static final List<String> BOOK_PREFIXES = List.of("enchanted book");
	/** The drops of the slayer bosses, in lower case. */
	private static final Set<String> NAMES = Set.of(
		// Revenant Horror
		"revenant flesh", "foul flesh", "pestilence rune", "undead catalyst", "smite vi", "beheaded horror", "revenant catalyst", "revenant viscera", "scythe blade",
		"snake rune", "severed hand", "shard of the shredded", "warden heart",
		// Tarantula Broodfather
		"tarantula web", "tarantula silk", "toxic arrow poison", "bite rune", "spider catalyst", "bane of arthropods vi", "fly swatter", "tarantula talisman",
		"digested mosquito", "shriveled wasp", "ensnared snail", "primordial eye", "vial of venom",
		// Sven Packmaster
		"wolf tooth", "hamster wheel", "spirit rune", "furball", "critical vi", "red claw egg", "couture rune", "overflux capacitor", "grizzly bait", "golden tooth",
		// Voidgloom Seraph
		"null sphere", "null ovoid", "null atom", "twilight arrow poison", "mana steal i", "sinful dice", "exceedingly rare ender artifact upgrader", "etherwarp merger",
		"judgement core", "enchant rune", "transmission tuner", "hazmat enderman", "pocket espresso machine", "smarty pants i", "end rune", "handy blood chalice",
		"summoning eye", "void conqueror enderman skin", "soulflow", "raw soulflow", "enderman cortex rewriter",
		// Inferno Demonlord
		"derelict ashe", "wilson's engineering plans", "subzero inverter", "flawed opal gemstone", "kelvin inverter", "inferno vertex", "molten powder", "blaze rod distillate",
		"glowstone distillate", "magma cream distillate", "nether wart distillate", "gabagool distillate", "scorched power crystal", "fire aspect iii", "bundle of magma arrows",
		"mana disintegrator", "scorched books", "archfiend dice", "high class archfiend dice", "duplex i", "inferno apex", "fiery burst rune", "enchanted blaze powder",
		"enchanted blaze rod");

	private BossDrops() {
	}

	/** @return an item's name to compare, in whatever format it comes: in lower case, without color codes, signs around it or an amount */
	static String nameKey(String name) {
		String plain = SIGNS_AROUND.matcher(COLOR_CODES.matcher(name).replaceAll("").strip()).replaceAll("");
		return AMOUNT_IN_FRONT.matcher(AMOUNT_AT_END.matcher(plain).replaceAll("")).replaceAll("").toLowerCase(Locale.ROOT);
	}

	/** @return a name to compare without its level: the chat's "End Rune I" is the ground's "End Rune" */
	static String withoutLevel(String key) {
		return LEVEL_AT_END.matcher(key).replaceAll("");
	}

	/** @return whether a name is a slayer drop */
	static boolean onList(String name) {
		String key = nameKey(name);
		if (NAMES.contains(key) || NAMES.contains(withoutLevel(key))) {
			return true;
		}
		for (String prefix : BOOK_PREFIXES) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param blocks     how far from where the boss died the item appeared
	 * @param afterDeath how long after the death it appeared, in milliseconds; below zero when it appeared before the death was known
	 * @param named      whether its name is a slayer drop or was just announced as one
	 * @return whether the item counts as a drop of the boss
	 */
	static boolean counts(double blocks, long afterDeath, boolean named) {
		return named && blocks <= NEAR_BLOCKS && afterDeath >= -BEFORE_MILLIS && afterDeath <= AFTER_MILLIS;
	}

	/** @return whether a drop of this rarity gets its name above it */
	static boolean labeled(int rarity) {
		return rarity == UNKNOWN_RARITY || rarity >= LABEL_FROM_RARITY;
	}
}
