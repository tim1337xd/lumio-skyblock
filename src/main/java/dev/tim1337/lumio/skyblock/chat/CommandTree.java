package dev.tim1337.lumio.skyblock.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The commands the chat suggests, from one data file: each with its aliases, its subcommands and what its arguments take. Subcommands the game showed in chat are added on top. Plain
 * data, so it is unit tested.
 */
public final class CommandTree {
	/** The most subcommands one command can hold, so that a strange file or chat message cannot grow it without end. */
	private static final int MOST_SUBS = 64;
	private static final String QUIET_SEPARATOR = " · ";
	/** What arguments end with that go on, such as "<players...>". */
	private static final String GOES_ON = "...>";

	/** A suggestion, with what it takes and what it does, or null for none of them. */
	public record Entry(String text, String hint, String description) {
		/** @return the quiet text beside it: what it takes and what it does, or null */
		public String quiet() {
			if (hint == null || description == null) {
				return hint == null ? description : hint;
			}
			return hint + QUIET_SEPARATOR + description;
		}
	}

	/**
	 * What can be typed at the end of a command.
	 *
	 * @param start where the word being typed starts in the input
	 * @param typed what is typed of it so far
	 * @param fixed its subcommands and fixed values, in the file's order
	 * @param names which names it takes, or null
	 * @param usage what it takes, such as "&lt;player&gt;", or null
	 */
	public record Completion(int start, String typed, List<Entry> fixed, CommandCompletion.Source names, String usage) {
	}

	private static final class Node {
		final Map<String, Node> subs = new LinkedHashMap<>();
		final List<String> values = new ArrayList<>();
		String name;
		String args;
		String description;
		CommandCompletion.Source names;

		/** @return whether its argument takes any number of words, each like the first */
		boolean goesOn() {
			return args != null && names != null && args.endsWith(GOES_ON);
		}
	}

	private final Map<String, Node> roots = new HashMap<>();

	private CommandTree() {
	}

	/** @return the tree of a data file; what it cannot read is left out */
	public static CommandTree of(JsonObject root) {
		CommandTree tree = new CommandTree();
		JsonElement commands = root.get("commands");
		if (commands == null || !commands.isJsonArray()) {
			return tree;
		}
		for (JsonElement element : commands.getAsJsonArray()) {
			if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
				JsonObject json = element.getAsJsonObject();
				Node node = node(json);
				tree.roots.put(node.name, node);
				for (String alias : strings(json.get("aliases"))) {
					tree.roots.put(alias.toLowerCase(Locale.ROOT), node);
				}
			}
		}
		return tree;
	}

	private static Node node(JsonObject json) {
		Node node = new Node();
		node.name = json.get("name").getAsString().toLowerCase(Locale.ROOT);
		node.args = json.has("args") ? json.get("args").getAsString() : null;
		node.description = json.has("desc") ? json.get("desc").getAsString() : null;
		node.names = source(json.has("names") ? json.get("names").getAsString() : "");
		node.values.addAll(strings(json.get("values")));
		JsonElement subs = json.get("subs");
		if (subs != null && subs.isJsonArray()) {
			for (JsonElement sub : subs.getAsJsonArray()) {
				if (sub.isJsonObject() && sub.getAsJsonObject().has("name") && node.subs.size() < MOST_SUBS) {
					Node child = node(sub.getAsJsonObject());
					node.subs.put(child.name, child);
				}
			}
		}
		return node;
	}

	private static List<String> strings(JsonElement element) {
		List<String> out = new ArrayList<>();
		if (element != null && element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (JsonElement value : array) {
				if (value.isJsonPrimitive()) {
					out.add(value.getAsString());
				}
			}
		}
		return out;
	}

	private static CommandCompletion.Source source(String name) {
		return switch (name) {
			case "players" -> CommandCompletion.Source.PLAYERS;
			case "party" -> CommandCompletion.Source.PARTY;
			case "guild" -> CommandCompletion.Source.GUILD;
			case "friends" -> CommandCompletion.Source.FRIENDS;
			case "warps" -> CommandCompletion.Source.WARPS;
			case "recipes" -> CommandCompletion.Source.RECIPES;
			case "sacks" -> CommandCompletion.Source.SACKS;
			default -> null;
		};
	}

	/** Adds a subcommand the game showed, if it is missing. @return whether it was new */
	public boolean learn(String command, String sub, String args) {
		Node root = roots.get(command.toLowerCase(Locale.ROOT));
		String name = sub.toLowerCase(Locale.ROOT);
		if (root == null || root.subs.containsKey(name) || root.subs.size() >= MOST_SUBS) {
			return false;
		}
		Node child = new Node();
		child.name = name;
		child.args = args == null || args.isEmpty() ? null : args;
		child.names = args != null && args.startsWith("<player") ? CommandCompletion.Source.PLAYERS : null;
		root.subs.put(name, child);
		return true;
	}

	/** @return the command's full name when it or an alias is known ("p" gives "party"), or null */
	public String rootOf(String command) {
		Node root = roots.get(command.toLowerCase(Locale.ROOT));
		return root == null ? null : root.name;
	}

	/** @return what can be typed at the end of the input before the cursor, or null when it is no known command or past what is known */
	public Completion find(String beforeCursor) {
		if (!beforeCursor.startsWith("/")) {
			return null;
		}
		String[] words = beforeCursor.substring(1).split(" ", -1);
		if (words.length < 2) {
			// still the command's name
			return null;
		}
		Node node = roots.get(words[0].toLowerCase(Locale.ROOT));
		for (int i = 1; node != null && i < words.length - 1; i++) {
			Node next = node.subs.get(words[i].toLowerCase(Locale.ROOT));
			node = next == null && node.goesOn() ? node : next;
		}
		if (node == null) {
			return null;
		}
		String typed = words[words.length - 1];
		List<Entry> fixed = new ArrayList<>();
		for (Node sub : node.subs.values()) {
			fixed.add(new Entry(sub.name, sub.args, sub.description));
		}
		for (String value : node.values) {
			fixed.add(new Entry(value, null, null));
		}
		return new Completion(beforeCursor.length() - typed.length(), typed, fixed, node.names, node.args);
	}

	/** @return what the word at the end of the input takes, while none of it is typed, for a quiet hint; or null */
	public String usage(String beforeCursor) {
		Completion found = find(beforeCursor);
		return found == null || !found.typed().isEmpty() ? null : found.usage();
	}
}
