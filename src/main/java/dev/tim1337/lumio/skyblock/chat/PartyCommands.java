package dev.tim1337.lumio.skyblock.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party commands: a member writes "!warp" in party chat and Lumio does the one party command that stands for it, or answers with the info asked
 * for. Which line is a request, from whom and where, what it asks for and what it leads to is worked out here, so it is tested without the game.
 * Requests are only taken from lines the server shows as party (or, if the player wants, guild and private) messages; every request leads to at
 * most one command, and the leader's commands only while the player leads the party.
 */
public final class PartyCommands {
	/** Where a request was written. */
	public enum Source {
		PARTY, GUILD, DIRECT
	}

	/** The commands, each with its names; the leader's commands only work while the player leads the party. */
	public enum Command {
		WARP(true, "warp", "w"), TRANSFER(true, "pt", "ptme", "transfer"), ALL_INVITE(true, "allinvite", "ai"), KICK(true, "kick", "k"),
		KICK_OFFLINE(true, "kickoffline", "ko"), PROMOTE(true, "promote"), DEMOTE(true, "demote"), REINVITE(true, "reinvite", "reinv"),
		FLOOR(true, "f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7"),
		DOWNTIME(false, "dt", "downtime"), UNDOWNTIME(false, "undt"), COORDS(false, "coords", "co"), PING(false, "ping"), TPS(false, "tps"), FPS(false, "fps"), TIME(false, "time"),
		HELP(false, "help", "h"), DICE(false, "dice", "cf");

		final boolean leaderOnly;
		final List<String> names;

		Command(boolean leaderOnly, String... names) {
			this.leaderOnly = leaderOnly;
			this.names = List.of(names);
		}

		public boolean leaderOnly() {
			return leaderOnly;
		}

		/** @return how the command is written first ("warp") */
		public String mainName() {
			return names.getFirst();
		}

		public List<String> names() {
			return names;
		}
	}

	/** A request: from where and whom, what it asks for, by which of its names, and what follows the name. */
	public record Request(Source source, String sender, Command command, String name, String argument) {
	}

	/** What the game tells the commands: who the player is, where, how the game runs, and the party. */
	public interface Context {
		String self();

		boolean selfLeads();

		String lastLeft();

		/** @return "-12, 70, 340" */
		String coords();

		/** @return the measured ping in milliseconds, or a negative number while none is known */
		int ping();

		String tps();

		int fps();

		/** @return the local time, "21:04" */
		String time();

		/** @return a whole number from 1 to the given most */
		int roll(int most);
	}

	private static final String NAME = "(?:\\[[^]]{1,20}] )?(\\w{1,16})";
	private static final Pattern PARTY = Pattern.compile("^Party > " + NAME + "(?: [^:]{0,8})?: (.+)$");
	private static final Pattern GUILD = Pattern.compile("^Guild > " + NAME + "(?: \\[[^]]{1,10}])?: (.+)$");
	private static final Pattern DIRECT = Pattern.compile("^From " + NAME + ": (.+)$");
	private static final Pattern PLAYER_NAME = Pattern.compile("^\\w{1,16}$");
	private static final Map<String, Command> BY_NAME = new HashMap<>();
	private static final int DIE_SIDES = 6;
	private static final int MOST_ARGUMENT = 32;

	static {
		for (Command command : Command.values()) {
			for (String name : command.names) {
				BY_NAME.put(name, command);
			}
		}
	}

	private PartyCommands() {
	}

	/** @return the request in a plain chat line, or null when the line is none (not a message, not the prefix, not a command) */
	public static Request read(String line, String prefix) {
		Said said = said(line, prefix);
		Command command = said == null ? null : BY_NAME.get(said.word());
		return command == null ? null : new Request(said.source(), said.sender(), command, said.word(), said.argument());
	}

	/** A word said after the prefix: from where and whom, the word in lower case, and what follows it. */
	public record Said(Source source, String sender, String word, String argument) {
	}

	/** @return the word a plain chat line says after the prefix, a command or not, or null when the line is no message or lacks the prefix */
	public static Said said(String line, String prefix) {
		Source source;
		Matcher matcher;
		if ((matcher = PARTY.matcher(line)).matches()) {
			source = Source.PARTY;
		} else if ((matcher = GUILD.matcher(line)).matches()) {
			source = Source.GUILD;
		} else if ((matcher = DIRECT.matcher(line)).matches()) {
			source = Source.DIRECT;
		} else {
			return null;
		}
		String text = matcher.group(2).strip();
		if (prefix.isEmpty() || !text.startsWith(prefix)) {
			return null;
		}
		String rest = text.substring(prefix.length()).strip();
		int space = rest.indexOf(' ');
		String word = (space < 0 ? rest : rest.substring(0, space)).toLowerCase(Locale.ROOT);
		if (word.isEmpty()) {
			return null;
		}
		String argument = space < 0 ? "" : rest.substring(space + 1).strip();
		if (argument.length() > MOST_ARGUMENT) {
			argument = argument.substring(0, MOST_ARGUMENT);
		}
		return new Said(source, matcher.group(1), word, argument);
	}

	/**
	 * @param active which commands the player switched on
	 * @return the one game command the request leads to (without its slash: "p warp", "pc Ping: 42 ms"), or null for none
	 */
	public static String answer(Request request, Context context, Predicate<Command> active) {
		// the player's own lines never count as requests, but for the two numbers of the player's own game, which the player may ask for too
		boolean own = request.sender().equalsIgnoreCase(context.self());
		if (!active.test(request.command()) || own && !ownNumbers(request.command())) {
			return null;
		}
		if (ownNumbers(request.command()) && request.source() != Source.PARTY) {
			// the ping and the frame rate are told in party chat only
			return null;
		}
		if (request.command().leaderOnly) {
			// the leader's commands only from party chat, and only while the player leads
			return request.source() == Source.PARTY && context.selfLeads() ? leaderCommand(request, context) : null;
		}
		String reply = switch (request.command()) {
			case DOWNTIME -> "Downtime noted for " + request.sender() + (request.argument().isEmpty() ? "" : ": " + request.argument()) + ".";
			case UNDOWNTIME -> "Downtime cleared for " + request.sender() + ".";
			case COORDS -> "Coords: " + context.coords();
			case PING -> context.ping() < 0 ? null : pingReply(context.ping());
			case TPS -> "TPS: " + context.tps();
			case FPS -> "FPS: " + context.fps();
			case TIME -> "Time: " + context.time();
			case HELP -> "Commands: " + activeNames(active);
			case DICE -> request.name().equals("cf") ? "Coin: " + (context.roll(2) == 1 ? "heads" : "tails") : "Dice: " + context.roll(DIE_SIDES);
			default -> null;
		};
		return reply == null ? null : reply.startsWith("pc ") && request.command() == Command.PING ? reply : replyPrefix(request) + reply;
	}

	/**
	 * @return whether a command tells a number of the player's own game (the ping, the frame rate): asked in party chat only, also by the player,
	 *         and answered once per cooldown whoever asks, so several people asking do not make the player spam
	 */
	public static boolean ownNumbers(Command command) {
		return command == Command.PING || command == Command.FPS;
	}

	/** @return the answer to "ping" for a measured ping, as it goes to party chat */
	public static String pingReply(int millis) {
		return "pc Ping: " + millis + " ms";
	}

	private static String leaderCommand(Request request, Context context) {
		String target = request.argument().isEmpty() ? null : request.argument().split(" ")[0];
		if (target != null && !PLAYER_NAME.matcher(target).matches()) {
			return null;
		}
		return switch (request.command()) {
			case WARP -> "p warp";
			case TRANSFER -> "p transfer " + (target == null || request.name().equals("ptme") ? request.sender() : target);
			case ALL_INVITE -> "p settings allinvite";
			case KICK -> target == null ? null : "p kick " + target;
			case KICK_OFFLINE -> "p kickoffline";
			case PROMOTE -> "p promote " + (target == null ? request.sender() : target);
			case DEMOTE -> target == null ? null : "p demote " + target;
			case REINVITE -> {
				String who = target != null ? target : context.lastLeft();
				yield who.isEmpty() ? null : "p invite " + who;
			}
			case FLOOR -> "joininstance " + instanceOf(request.name());
			default -> null;
		};
	}

	/** @return the instance of a floor's name as /joininstance takes it: "f7" is CATACOMBS_FLOOR_SEVEN, "m3" MASTER_CATACOMBS_FLOOR_THREE */
	static String instanceOf(String floor) {
		String[] numbers = {"ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"};
		String number = numbers[floor.charAt(1) - '1'];
		return (floor.charAt(0) == 'm' ? "MASTER_" : "") + "CATACOMBS_FLOOR_" + number;
	}

	private static String replyPrefix(Request request) {
		return switch (request.source()) {
			case PARTY -> "pc ";
			case GUILD -> "gc ";
			case DIRECT -> "msg " + request.sender() + " ";
		};
	}

	private static String activeNames(Predicate<Command> active) {
		List<String> names = new ArrayList<>();
		for (Command command : Command.values()) {
			if (active.test(command)) {
				names.add(command == Command.FLOOR ? "f1-m7" : command.mainName());
			}
		}
		return String.join(", ", names);
	}

	/** Who may ask, and how often: blocked players never, everyone else once per their cooldown. */
	public static final class Gate {
		private final Map<String, Long> lastAsked = new HashMap<>();
		private static final int MOST_REMEMBERED = 200;

		/** @return whether a request of this sender is taken now; a taken one starts the sender's cooldown */
		public boolean take(String sender, long nowMillis, long cooldownMillis, Set<String> blocked) {
			String key = sender.toLowerCase(Locale.ROOT);
			if (blocked.contains(key)) {
				return false;
			}
			Long last = lastAsked.get(key);
			if (last != null && nowMillis - last < cooldownMillis) {
				return false;
			}
			if (lastAsked.size() >= MOST_REMEMBERED) {
				lastAsked.clear();
			}
			lastAsked.put(key, nowMillis);
			return true;
		}
	}
}
