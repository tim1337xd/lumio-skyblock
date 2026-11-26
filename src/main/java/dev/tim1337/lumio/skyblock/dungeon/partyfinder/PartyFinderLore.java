package dev.tim1337.lumio.skyblock.dungeon.partyfinder;

import dev.tim1337.lumio.skyblock.dungeon.DungeonClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a party of Hypixel's dungeon "Party Finder" menu from its item: the head's name ("Ash's Party") and its lore lines without colors, each
 * with whether the game wrote it in red. Plain parsing, tested without the game. The wordings are Hypixel's as other mods describe them; a line
 * that is none of them is left alone, and a head without a dungeon line and members is no party.
 */
public final class PartyFinderLore {
	/** The most players of a dungeon party. */
	public static final int PARTY_SIZE = 5;
	/** The longest note, name and reason kept: lore is text from the server and from other players. */
	private static final int MOST_TEXT = 120;
	private static final int MOST_LEVEL = 999;

	/** A lore line without its colors, and whether the game wrote it (its first colored part) in red. */
	public record Line(String text, boolean red) {
	}

	/** A member of a party: the name, the class and its level; the level is -1 when it could not be read. */
	public record Member(String name, DungeonClass dungeonClass, int level) {
	}

	/**
	 * A party as its head tells it.
	 *
	 * @param leader           the leader's name, from the head's name; "" when it has none
	 * @param master           whether it is Master Mode
	 * @param floor            the floor, 1 to 7; 0 for the Entrance; -1 when not read
	 * @param note             the leader's note; "" for none
	 * @param dungeonLevel     the Catacombs level a player needs; 0 for none
	 * @param classLevel       the class level a player needs; 0 for none
	 * @param members          the members listed
	 * @param refusal          the game's own red line saying the player cannot join; "" for none
	 */
	public record Party(String leader, boolean master, int floor, String note, int dungeonLevel, int classLevel, List<Member> members, String refusal) {
		/** @return "F7", "M5", "E" for the Entrance, "" when the floor was not read */
		public String floorLabel() {
			return floor < 0 ? "" : floor == 0 ? "E" : (master ? "M" : "F") + floor;
		}

		public boolean full() {
			return members.size() >= PARTY_SIZE;
		}

		/** @return the member who plays a class, or null when nobody does */
		public Member playing(DungeonClass dungeonClass) {
			for (Member member : members) {
				if (member.dungeonClass() == dungeonClass) {
					return member;
				}
			}
			return null;
		}

		/** @return the classes nobody in the party plays, in the game's order */
		public List<DungeonClass> missing() {
			List<DungeonClass> missing = new ArrayList<>();
			for (DungeonClass each : DungeonClass.values()) {
				if (each != DungeonClass.NONE && playing(each) == null) {
					missing.add(each);
				}
			}
			return missing;
		}

		public boolean has(String player) {
			if (player == null || player.isEmpty()) {
				return false;
			}
			if (leader.equalsIgnoreCase(player)) {
				return true;
			}
			for (Member member : members) {
				if (member.name().equalsIgnoreCase(player)) {
					return true;
				}
			}
			return false;
		}
	}

	private static final Pattern PARTY_NAME = Pattern.compile("^(?:\\[[^]]{1,12}] )?(\\w{1,16})'s? Party$");
	private static final Pattern DUNGEON = Pattern.compile("^Dungeon: (.+)$");
	private static final Pattern FLOOR = Pattern.compile("^Floor: (?:Floor )?(Entrance|[IV]{1,3}|[0-7])$");
	private static final Pattern NOTE = Pattern.compile("^Note: (.*)$");
	private static final Pattern DUNGEON_LEVEL = Pattern.compile("^Dungeon Level Required: (\\d{1,3})$");
	private static final Pattern CLASS_LEVEL = Pattern.compile("^Class Level Required: (\\d{1,3})$");
	/** "Ash: Mage (35)", also with a rank before the name. */
	private static final Pattern MEMBER = Pattern.compile("^(?:\\[[^]]{1,12}] )?(\\w{1,16}): (\\w{1,12}) \\((\\d{1,3})\\)$");
	private static final Pattern SELECTED = Pattern.compile("^Currently Selected: (\\w{1,12})$");
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII"};

	private PartyFinderLore() {
	}

	/**
	 * @param name the head's name without colors
	 * @param lore its lore lines without colors
	 * @return the party, or null when the item is none (no dungeon line, or no member read)
	 */
	public static Party read(String name, List<Line> lore) {
		Matcher leaderName = PARTY_NAME.matcher(name.strip());
		String leader = leaderName.matches() ? leaderName.group(1) : "";
		boolean dungeonSeen = false;
		boolean master = false;
		int floor = -1;
		String note = "";
		int dungeonLevel = 0;
		int classLevel = 0;
		String refusal = "";
		List<Member> members = new ArrayList<>();
		for (Line line : lore) {
			String text = line.text().strip();
			Matcher matcher;
			if ((matcher = DUNGEON.matcher(text)).matches()) {
				dungeonSeen = true;
				master = matcher.group(1).toLowerCase(Locale.ROOT).contains("master");
			} else if ((matcher = FLOOR.matcher(text)).matches()) {
				floor = floorOf(matcher.group(1));
			} else if ((matcher = NOTE.matcher(text)).matches()) {
				note = cut(matcher.group(1).strip());
			} else if ((matcher = DUNGEON_LEVEL.matcher(text)).matches()) {
				dungeonLevel = Math.min(MOST_LEVEL, Integer.parseInt(matcher.group(1)));
			} else if ((matcher = CLASS_LEVEL.matcher(text)).matches()) {
				classLevel = Math.min(MOST_LEVEL, Integer.parseInt(matcher.group(1)));
			} else if ((matcher = MEMBER.matcher(text)).matches()) {
				DungeonClass dungeonClass = DungeonClass.of(matcher.group(2));
				if (dungeonClass != DungeonClass.NONE && members.size() < PARTY_SIZE) {
					members.add(new Member(matcher.group(1), dungeonClass, Math.min(MOST_LEVEL, Integer.parseInt(matcher.group(3)))));
				}
			} else if (line.red() && !text.isEmpty() && refusal.isEmpty()) {
				// a red line that is none of the above: the game's own word that the player cannot join, whatever it says
				refusal = cut(text);
			}
		}
		return dungeonSeen && !members.isEmpty() ? new Party(leader, master, floor, note, dungeonLevel, classLevel, List.copyOf(members), refusal) : null;
	}

	/** @return the class the menu's class item says is selected ("Currently Selected: Mage"), or NONE when no line says so */
	public static DungeonClass selectedClass(List<Line> lore) {
		for (Line line : lore) {
			Matcher matcher = SELECTED.matcher(line.text().strip());
			if (matcher.matches()) {
				return DungeonClass.of(matcher.group(1));
			}
		}
		return DungeonClass.NONE;
	}

	/**
	 * @param words the words the player typed, separated by commas
	 * @return whether a party's note contains one of them, whatever the case; never for an empty note or list
	 */
	public static boolean noteHas(String note, String words) {
		if (note.isEmpty() || words.isBlank()) {
			return false;
		}
		String lower = note.toLowerCase(Locale.ROOT);
		for (String word : words.split(",")) {
			String wanted = word.strip().toLowerCase(Locale.ROOT);
			if (!wanted.isEmpty() && lower.contains(wanted)) {
				return true;
			}
		}
		return false;
	}

	private static int floorOf(String written) {
		if (written.equals("Entrance") || written.equals("0")) {
			return 0;
		}
		for (int i = 0; i < ROMAN.length; i++) {
			if (ROMAN[i].equals(written)) {
				return i + 1;
			}
		}
		return written.length() == 1 && Character.isDigit(written.charAt(0)) ? written.charAt(0) - '0' : -1;
	}

	private static String cut(String text) {
		return text.length() > MOST_TEXT ? text.substring(0, MOST_TEXT) : text;
	}
}
