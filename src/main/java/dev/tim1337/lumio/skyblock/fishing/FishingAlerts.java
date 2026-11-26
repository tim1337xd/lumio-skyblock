package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.experimental.util.ChatCoords;
import dev.tim1337.lumio.skyblock.fishing.SeaCreatures.Creature;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The logic of the fishing alerts, without the game: the party message of a rare catch, a rare catch someone else shared in chat, a rare sea
 * creature told by the name tag of an entity nearby, that one creature is told of once (not by the chat and again by its name tag, and not when
 * it is the player's own catch), and the short queue that shows several alerts one after the other.
 */
public final class FishingAlerts {
	/** The party message of a catch as shipped, and the longest it gets. */
	public static final String DEFAULT_MESSAGE = "{doublehook}{creature} caught! {coords}";
	static final int MOST_MESSAGE = 100;
	private static final String DOUBLE_HOOK_WORDS = "Double Hook! ";

	/** The chats a share can come from. */
	public enum Chat {
		PARTY, COOP, GUILD, PUBLIC
	}

	/** What a player wrote: where, who, and the text. */
	public record Said(Chat chat, String player, String text) {
	}

	/** A rare catch someone shared: where, who, the creature, whether it was a double hook, and the place when the message has coordinates (or null). */
	public record Share(Chat chat, String player, Creature creature, boolean doubleHook, ChatCoords.Found at) {
	}

	private static final String NAME = "(?:\\[[^]]{1,20}] )?(\\w{1,16})";
	private static final Pattern PARTY = Pattern.compile("^Party > " + NAME + "(?: [^:]{0,8})?: (.+)$");
	private static final Pattern COOP = Pattern.compile("^Co-op > " + NAME + ": (.+)$");
	private static final Pattern GUILD = Pattern.compile("^Guild > " + NAME + "(?: \\[[^]]{1,10}])?: (.+)$");
	/** Public chat on SkyBlock: an optional level, an optional rank, the name. */
	private static final Pattern PUBLIC = Pattern.compile("^(?:\\[\\d{1,4}] )?" + NAME + ": (.+)$");
	/** A word that makes a creature's name in a message a catch rather than talk about it; coordinates do the same. */
	private static final Pattern CATCH_WORD = Pattern.compile("\\b(?:caught|spawned|fished|hooked|found)\\b|double hook", Pattern.CASE_INSENSITIVE);
	/** A sea creature's name tag: its level, then its name among other things ("[Lv400] Thunder 35M/35M"). */
	private static final Pattern LEVEL_TAG = Pattern.compile("^\\W{0,4}\\[Lv\\d{1,4}] ");

	private FishingAlerts() {
	}

	/**
	 * @param template the player's text with {creature}, {coords} and {doublehook}
	 * @param coords   the place as the chat waypoints read it, or "" to leave it out
	 * @return the party message of a catch, without the command before it; never longer than a chat line of the queue
	 */
	public static String message(String template, String creature, String coords, boolean doubleHook) {
		String text = (template == null || template.isBlank() ? DEFAULT_MESSAGE : template).replace("{creature}", creature)
			.replace("{coords}", coords).replace("{doublehook}", doubleHook ? DOUBLE_HOOK_WORDS : "");
		text = text.replace('\n', ' ').replaceAll(" {2,}", " ").strip();
		return text.length() > MOST_MESSAGE ? text.substring(0, MOST_MESSAGE).strip() : text;
	}

	/** @return a place as the chat waypoints read it: "x: 12, y: 70, z: -3" */
	public static String coords(int x, int y, int z) {
		return "x: " + x + ", y: " + y + ", z: " + z;
	}

	/** @return what a player wrote in a plain chat line (without colors), or null when the line is no player's message */
	public static Said said(String line) {
		Matcher matcher;
		Chat chat;
		if ((matcher = PARTY.matcher(line)).matches()) {
			chat = Chat.PARTY;
		} else if ((matcher = COOP.matcher(line)).matches()) {
			chat = Chat.COOP;
		} else if ((matcher = GUILD.matcher(line)).matches()) {
			chat = Chat.GUILD;
		} else if ((matcher = PUBLIC.matcher(line)).matches()) {
			chat = Chat.PUBLIC;
		} else {
			return null;
		}
		return new Said(chat, matcher.group(1), matcher.group(2).strip());
	}

	/**
	 * @param line   a plain chat line a player wrote
	 * @param listed whether a creature is on the player's alert list
	 * @return the rare catch the line shares, or null: a listed creature's name as whole words, with a word of catching or coordinates, so talk
	 *         about a creature is no alert. This takes our own share and the usual forms of other mods ("Thunder caught!", "Caught a Thunder at
	 *         x: 1, y: 2, z: 3", "x: 1, y: 2, z: 3 | Thunder").
	 */
	public static Share share(String line, Predicate<Creature> listed) {
		Said said = said(line);
		if (said == null) {
			return null;
		}
		Creature creature = named(said.text(), listed);
		if (creature == null) {
			return null;
		}
		ChatCoords.Found at = ChatCoords.inChat(said.text());
		if (at == null && !CATCH_WORD.matcher(said.text()).find()) {
			return null;
		}
		return new Share(said.chat(), said.player(), creature, said.text().toLowerCase(Locale.ROOT).contains("double hook"), at);
	}

	/** @return the listed creature a name tag (without colors) is of, or null: only a tag that starts with a level, as a sea creature's does */
	public static Creature inTag(String tag, Predicate<Creature> listed) {
		Matcher level = LEVEL_TAG.matcher(tag);
		return level.find() ? named(tag.substring(level.end()), listed) : null;
	}

	/** @return the listed creature whose name stands in a text as whole words, the longest when several do ("Poisoned Water Worm" before "Water Worm") */
	private static Creature named(String text, Predicate<Creature> listed) {
		String lower = text.toLowerCase(Locale.ROOT);
		Creature best = null;
		for (Creature creature : SeaCreatures.all()) {
			if ((best == null || creature.name().length() > best.name().length()) && listed.test(creature) && wholeWords(lower, creature.name().toLowerCase(Locale.ROOT))) {
				best = creature;
			}
		}
		return best;
	}

	private static boolean wholeWords(String text, String name) {
		for (int at = text.indexOf(name); at >= 0; at = text.indexOf(name, at + 1)) {
			boolean before = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
			int end = at + name.length();
			boolean after = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
			if (before && after) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a creature at these distances from the player along the axes is within so many blocks */
	public static boolean within(double dx, double dy, double dz, double blocks) {
		return dx * dx + dy * dy + dz * dz <= blocks * blocks;
	}

	/**
	 * What was told of already, so a creature is told of once: not by its name tag when the chat shared it or the player caught it a moment
	 * before, and each entity once.
	 */
	public static final class Seen {
		/** How long after the player's own catch, and after a share in chat, a creature of that name nearby is the same one. */
		static final long OWN_MILLIS = 20_000;
		static final long CHAT_MILLIS = 60_000;
		private static final int MOST_ENTITIES = 512;
		private final Map<String, Long> own = new HashMap<>();
		private final Map<String, Long> chat = new HashMap<>();
		private final Map<String, Long> chatBy = new HashMap<>();
		private final Set<Integer> entities = new HashSet<>();

		/** The player caught a creature. */
		public void ownCatch(Creature creature, long now) {
			own.put(creature.name(), now);
		}

		/** @return whether a share in chat is told of: not the same player's same creature again within a minute */
		public boolean chat(String player, Creature creature, long now) {
			String key = player.toLowerCase(Locale.ROOT) + ' ' + creature.name();
			Long last = chatBy.get(key);
			chat.put(creature.name(), now);
			if (last != null && now - last < CHAT_MILLIS) {
				return false;
			}
			if (chatBy.size() > MOST_ENTITIES) {
				chatBy.clear();
			}
			chatBy.put(key, now);
			return true;
		}

		/** @return whether a creature seen nearby by its name tag is told of: each entity once, and not the one just caught or just shared */
		public boolean nearby(int entity, Creature creature, long now) {
			if (entities.size() > MOST_ENTITIES) {
				entities.clear();
			}
			if (!entities.add(entity)) {
				return false;
			}
			Long caught = own.get(creature.name());
			Long shared = chat.get(creature.name());
			return (caught == null || now - caught > OWN_MILLIS) && (shared == null || now - shared > CHAT_MILLIS);
		}

		/**
		 * @param visible whether the player sees the creature now: drawn by the game, in view, nothing solid between ({@link
		 *                dev.tim1337.lumio.api.experimental.util.Sight})
		 * @return whether a creature found by its name tag is told of: only once it is seen, then as {@link #nearby}; one hidden now is not
		 *         counted, so it is told of when it comes into sight, once
		 */
		public boolean sighted(int entity, Creature creature, boolean visible, long now) {
			return visible && nearby(entity, creature, now);
		}

		/** Another world: nothing seen there counts here. */
		public void clear() {
			own.clear();
			chat.clear();
			chatBy.clear();
			entities.clear();
		}
	}

	/** An alert to show: its title, a smaller line under it, and whether it is the player's own catch. */
	public record Alert(String title, String detail, boolean own) {
	}

	/** Several alerts at once are shown one after the other, each briefly, instead of on top of each other. */
	public static final class Queue {
		/** How long one alert has the screen before the next, and the most that wait. */
		static final long EACH_MILLIS = 1_500;
		static final int MOST_WAITING = 8;
		private final ArrayDeque<Alert> waiting = new ArrayDeque<>();
		private long shownAt = Long.MIN_VALUE / 2;

		public void add(Alert alert) {
			if (waiting.size() < MOST_WAITING) {
				waiting.add(alert);
			}
		}

		/** @return the alert to show now, or null: the next one once the one before had its time */
		public Alert poll(long now) {
			if (waiting.isEmpty() || now - shownAt < EACH_MILLIS) {
				return null;
			}
			shownAt = now;
			return waiting.poll();
		}

		public void clear() {
			waiting.clear();
		}
	}
}
