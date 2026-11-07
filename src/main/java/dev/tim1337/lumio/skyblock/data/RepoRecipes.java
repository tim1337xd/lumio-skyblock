package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads the ways to get an item from a file of the item repository: "recipe" (a crafting grid), and "recipes" of the kinds crafting, forge, npc_shop, trade, katgrade and drops. A file can also be a mob or an NPC, with its drops or its
 * shop. A kind it does not know is counted, so that it shows up. Plain data, so it is unit tested.
 */
public final class RepoRecipes {
	/** What a file of the repository stands for. */
	public enum Kind {
		ITEM, MOB, NPC
	}

	private static final Pattern COLOR = Pattern.compile("§.");
	private static final Pattern WHAT_IT_IS = Pattern.compile("\\s*\\((?:NPC|Monster|Boss|Miniboss|Animal|Sea Creature)\\)$");
	private static final String CRAFTING_TABLE = "Crafting table";
	private static final String THE_FORGE = "The Forge";
	private static final String KAT = "Kat";
	private static final String DOT = " · ";

	private RepoRecipes() {
	}

	/** @return what a file stands for: NPCs and mobs are kept as items in the repository too, for their shops and drops */
	public static Kind kindOf(String id, boolean hasRecipes) {
		if (id.endsWith("_NPC")) {
			return Kind.NPC;
		}
		boolean mob = id.endsWith("_MONSTER") || id.endsWith("_ANIMAL") || id.endsWith("_MINIBOSS") || id.endsWith("_SC") || id.endsWith("_WITHER");
		return mob || id.endsWith("_BOSS") && hasRecipes ? Kind.MOB : Kind.ITEM;
	}

	/**
	 * @param unknown takes the kind of each recipe that is not read, or null
	 * @return the ways a file gives
	 */
	public static List<ItemRepo.Recipe> of(String id, JsonObject json, java.util.function.Consumer<String> unknown) {
		List<ItemRepo.Recipe> out = new ArrayList<>();
		String needs = plain(string(json, "crafttext"));
		if (json.has("recipe") && json.get("recipe").isJsonObject()) {
			ItemRepo.Recipe grid = crafting(id, json.getAsJsonObject("recipe"), needs);
			if (grid != null) {
				out.add(grid);
			}
		}
		if (!json.has("recipes") || !json.get("recipes").isJsonArray()) {
			return out;
		}
		String source = shownName(string(json, "displayname"));
		for (JsonElement element : json.getAsJsonArray("recipes")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject recipe = element.getAsJsonObject();
			String type = string(recipe, "type");
			try {
				ItemRepo.Recipe made = read(type, id, recipe, source, needs);
				if (made == null) {
					if (unknown != null) {
						unknown.accept(type);
					}
					continue;
				}
				// crafting grids keep their empty cells; other recipe types only list their contents
				List<ItemRepo.Stack> inputs = made.crafting() ? made.inputs() : made.inputs().stream().filter(Objects::nonNull).toList();
				List<ItemRepo.Stack> outputs = made.outputs().stream().filter(Objects::nonNull).toList();
				if (!outputs.isEmpty()) {
					out.add(new ItemRepo.Recipe(made.type(), inputs, outputs, made.source(), made.note(), made.sourceId()));
				}
			} catch (RuntimeException e) {
				if (unknown != null) {
					unknown.accept(type + " (not readable)");
				}
			}
		}
		return out;
	}

	private static ItemRepo.Recipe read(String type, String id, JsonObject recipe, String source, String needs) {
		return switch (type) {
			case "crafting" -> crafting(id, recipe, needs);
			case "forge" -> forge(id, recipe);
			case "npc_shop" -> new ItemRepo.Recipe("npc_shop", stacks(array(recipe, "cost")), Arrays.asList(ItemRepo.Stack.parse(string(recipe, "result"))), source, "", id);
			case "trade" -> new ItemRepo.Recipe("trade", Arrays.asList(ItemRepo.Stack.parse(string(recipe, "cost"))), Arrays.asList(ItemRepo.Stack.parse(string(recipe, "result"))), source, "", "");
			case "katgrade" -> kat(recipe);
			case "drops" -> drops(id, recipe, source);
			default -> null;
		};
	}

	private static ItemRepo.Recipe forge(String id, JsonObject recipe) {
		String output = recipe.has("overrideOutputId") ? string(recipe, "overrideOutputId") : id;
		long seconds = recipe.has("duration") ? recipe.get("duration").getAsLong() : 0;
		return new ItemRepo.Recipe("forge", stacks(array(recipe, "inputs")), List.of(new ItemRepo.Stack(output, count(recipe))), THE_FORGE, seconds > 0 ? duration(seconds) : "", "");
	}

	private static ItemRepo.Recipe kat(JsonObject recipe) {
		List<ItemRepo.Stack> inputs = new ArrayList<>();
		inputs.add(ItemRepo.Stack.parse(string(recipe, "input")));
		inputs.addAll(stacks(array(recipe, "items")));
		long coins = recipe.has("coins") ? recipe.get("coins").getAsLong() : 0;
		long seconds = recipe.has("time") ? recipe.get("time").getAsLong() : 0;
		String note = String.format(Locale.ROOT, "%,d coins", coins) + (seconds > 0 ? ", " + duration(seconds) : "");
		return new ItemRepo.Recipe("katgrade", inputs, Arrays.asList(ItemRepo.Stack.parse(string(recipe, "output"))), KAT, note, "");
	}

	private static ItemRepo.Recipe drops(String id, JsonObject recipe, String source) {
		List<ItemRepo.Stack> drops = new ArrayList<>();
		for (JsonElement drop : array(recipe, "drops")) {
			if (drop.isJsonObject()) {
				JsonObject one = drop.getAsJsonObject();
				ItemRepo.Stack stack = ItemRepo.Stack.parse(string(one, "id"));
				if (stack != null) {
					drops.add(new ItemRepo.Stack(stack.id(), stack.count(), dropNote(one)));
				}
			}
		}
		String mob = recipe.has("name") ? shownName(string(recipe, "name")) : source;
		return new ItemRepo.Recipe("drops", List.of(), drops, mob, mobNote(recipe), id);
	}

	/** @return how a mob's drop comes: its chance, then what the repository says besides, as "4% · x2-4" */
	private static String dropNote(JsonObject drop) {
		StringBuilder note = new StringBuilder(plain(string(drop, "chance")));
		for (JsonElement extra : array(drop, "extra")) {
			if (extra.isJsonPrimitive()) {
				note.append(note.isEmpty() ? "" : DOT).append(plain(extra.getAsString()));
			}
		}
		return note.toString();
	}

	/** @return the facts of a mob's drops: its level, coins and experience */
	private static String mobNote(JsonObject recipe) {
		StringBuilder note = new StringBuilder();
		part(note, recipe, "level", "Level ", "");
		part(note, recipe, "coins", "", " coins");
		part(note, recipe, "xp", "", " XP");
		part(note, recipe, "combat_xp", "", " Combat XP");
		return note.toString();
	}

	private static void part(StringBuilder note, JsonObject recipe, String key, String before, String after) {
		long value = recipe.has(key) && recipe.get(key).isJsonPrimitive() ? recipe.get(key).getAsLong() : 0;
		if (value > 0) {
			note.append(note.isEmpty() ? "" : DOT).append(before).append(String.format(Locale.ROOT, "%,d", value)).append(after);
		}
	}

	/** @return a 3x3 crafting recipe, A1 to C3 row by row; empty cells stay in the list as null so the grid keeps its shape */
	private static ItemRepo.Recipe crafting(String id, JsonObject recipe, String needs) {
		List<ItemRepo.Stack> grid = new ArrayList<>();
		boolean any = false;
		for (char row : new char[]{'A', 'B', 'C'}) {
			for (int column = 1; column <= 3; column++) {
				ItemRepo.Stack stack = ItemRepo.Stack.parse(string(recipe, "" + row + column));
				grid.add(stack);
				any |= stack != null;
			}
		}
		if (!any) {
			return null;
		}
		String output = recipe.has("overrideOutputId") ? string(recipe, "overrideOutputId") : id;
		return new ItemRepo.Recipe("crafting", grid, List.of(new ItemRepo.Stack(output, count(recipe))), CRAFTING_TABLE, needs, "");
	}

	private static List<ItemRepo.Stack> stacks(JsonArray array) {
		List<ItemRepo.Stack> out = new ArrayList<>();
		for (JsonElement element : array) {
			out.add(ItemRepo.Stack.parse(element.getAsString()));
		}
		return out;
	}

	private static double count(JsonObject recipe) {
		try {
			return recipe.has("count") ? recipe.get("count").getAsDouble() : 1;
		} catch (RuntimeException e) {
			return 1;
		}
	}

	static String duration(long seconds) {
		if (seconds >= 3600) {
			long minutes = seconds % 3600 / 60;
			return seconds / 3600 + "h" + (minutes > 0 ? " " + minutes + "m" : "");
		}
		return seconds >= 60 ? seconds / 60 + "m" : seconds + "s";
	}

	private static JsonArray array(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray();
	}

	private static String string(JsonObject json, String key) {
		try {
			return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static String plain(String text) {
		return COLOR.matcher(text).replaceAll("").strip();
	}

	/** @return a mob's or NPC's name without colors and without the word that says what it is */
	static String shownName(String name) {
		return WHAT_IT_IS.matcher(plain(name)).replaceFirst("");
	}
}
