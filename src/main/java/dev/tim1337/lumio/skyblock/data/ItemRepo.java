package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.HttpLimits;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Every Hypixel SkyBlock item with its recipes, from the NotEnoughUpdates repository, which Firmament, SkySoft and other item list mods read as
 * well. It is downloaded as one zip into the mod folder at most once a day and parsed on a background thread: several thousand items with their
 * ingredients, what they craft into, NPC prices and wiki links.
 */
public final class ItemRepo {
	private static final String URL = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
	private static final long REFRESH_MS = 24 * 3_600_000L;
	private static final Pattern ITEM_MODEL = Pattern.compile("ItemModel:\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
	private static final Pattern TEXTURE = Pattern.compile("Value:\"([A-Za-z0-9+/=]+)\"");
	private static final Pattern COLOR = Pattern.compile("§.");

	/** A SkyBlock item as defined in the repository. */
	public static final class Entry {
		public final String id;
		public final String name;
		/** Lowercase name without colors, plus its lore lines, for searching. */
		final String searchName;
		final String searchLore;
		final String searchId;
		public final List<String> lore;
		public final String wiki;
		/** Whether SkyBlock has a crafting menu for it via /viewrecipe, as indicated by the repository's click command. */
		public final boolean craftable;
		final String itemId;
		final String model;
		final String texture;
		private ItemStack stack;

		Entry(String id, String name, List<String> lore, String wiki, String itemId, String model, String texture, boolean craftable) {
			this.id = id;
			this.craftable = craftable;
			this.name = name;
			this.lore = lore;
			this.wiki = wiki;
			this.itemId = itemId;
			this.model = model;
			this.texture = texture;
			this.searchName = plain(name).toLowerCase(Locale.ROOT);
			this.searchLore = plain(String.join(" ", lore)).toLowerCase(Locale.ROOT);
			this.searchId = id.toLowerCase(Locale.ROOT);
		}

		/** @return the display name without colors */
		public String plainName() {
			return plain(name);
		}

		/** @return the item as rendered and as shown in its tooltip; created lazily on the game thread on first access */
		public ItemStack stack() {
			if (stack == null) {
				stack = makeStack(this);
			}
			return stack;
		}

		/** @return the rarity color from the last lore line ("§6§lLEGENDARY SWORD"), or white */
		public int rarityColor() {
			for (int i = lore.size() - 1; i >= 0; i--) {
				String line = lore.get(i);
				if (line.length() >= 2 && line.charAt(0) == '§') {
					var format = ChatFormatting.getByCode(line.charAt(1));
					var color = format == null ? null : TextColor.fromLegacyFormat(format);
					if (color != null && plain(line).matches("^[A-Z ]+$")) {
						return 0xFF000000 | color.getValue();
					}
				}
			}
			return 0xFFFFFFFF;
		}
	}

	/** An item amount in a recipe, with what the repository says of it: a drop's chance. */
	public record Stack(String id, double count, String note) {
		public Stack(String id, double count) {
			this(id, count, "");
		}

		static Stack parse(String text) {
			if (text == null || text.isBlank()) {
				return null;
			}
			int cut = text.lastIndexOf(':');
			if (cut > 0) {
				try {
					return new Stack(text.substring(0, cut), Double.parseDouble(text.substring(cut + 1)));
				} catch (NumberFormatException ignored) {
				}
			}
			return new Stack(text, 1);
		}
	}

	/**
	 * A way to obtain an item: a 3x3 crafting grid (some cells empty), the forge, an NPC shop, a trade, a Kat pet upgrade, or mob drops. {@code source}
	 * is where it happens (the NPC, the mob), {@code note} an extra line (forge time, what a mob is), and {@code sourceId} the mob or NPC as an entry, or "".
	 */
	public record Recipe(String type, List<Stack> inputs, List<Stack> outputs, String source, String note, String sourceId) {
		public Recipe(String type, List<Stack> inputs, List<Stack> outputs, String source, String note) {
			this(type, inputs, outputs, source, note, "");
		}

		public boolean crafting() {
			return type.equals("crafting");
		}

		public String title() {
			return switch (type) {
				case "crafting" -> "Crafting";
				case "forge" -> "Forge";
				case "npc_shop" -> "Shop";
				case "trade" -> "Trade";
				case "katgrade" -> "Kat";
				case "drops" -> "Drops";
				default -> type;
			};
		}
	}

	private static volatile List<Entry> sources = List.of();
	private static volatile LinkedHashMultimap<String, Recipe> ofSource = LinkedHashMultimap.create();
	private static volatile Map<String, Integer> recipeCounts = Map.of();
	private static volatile Map<String, Integer> unreadKinds = Map.of();
	private static volatile List<Entry> items = List.of();
	private static volatile Map<String, Entry> byId = Map.of();
	/** The ids of the items sacks hold, from the repository's sack list (for the /gfs suggestions). */
	private static volatile List<String> sackItems = List.of();
	private static volatile EnchantTiers enchantTiers = EnchantTiers.EMPTY;
	private static volatile RepoPetSkins petSkins = RepoPetSkins.EMPTY;
	private static volatile List<RepoWarps.Warp> warps = List.of();
	private static volatile LinkedHashMultimap<String, Recipe> recipesFor = LinkedHashMultimap.create();
	private static volatile LinkedHashMultimap<String, Recipe> usagesOf = LinkedHashMultimap.create();
	private static volatile boolean loading;
	private static volatile String state = "Not loaded yet";

	private ItemRepo() {
	}

	public static List<Entry> items() {
		return items;
	}

	public static Entry get(String id) {
		return id == null ? null : byId.get(id);
	}

	/** @return the mobs and NPCs, which a search finds beside the items */
	public static List<Entry> sources() {
		return sources;
	}

	/** @return whether an entry is a mob or an NPC rather than an item */
	public static boolean isSource(String id) {
		Entry entry = byId.get(id);
		return entry != null && sources.contains(entry);
	}

	/** @return what a mob or NPC itself gives: its drops, its shop */
	public static List<Recipe> ofSource(String id) {
		return new ArrayList<>(ofSource.get(id));
	}

	/** @return how many recipes of each kind were read */
	public static Map<String, Integer> recipeCounts() {
		return recipeCounts;
	}

	/** @return how many recipes of each kind could not be read */
	public static Map<String, Integer> unreadKinds() {
		return unreadKinds;
	}

	public static List<Recipe> recipesFor(String id) {
		return new ArrayList<>(recipesFor.get(id));
	}

	public static List<Recipe> usagesOf(String id) {
		return new ArrayList<>(usagesOf.get(id));
	}

	public static boolean ready() {
		return !items.isEmpty();
	}

	/** @return the list state: loading, the item count, or why it could not be loaded */
	public static String state() {
		return state;
	}

	/** Loads the list if needed: from the cached file, which is re-downloaded when older than a day. Usually does nothing. */
	public static void loadIfNeeded() {
		if (loading || ready()) {
			return;
		}
		loading = true;
		Thread thread = new Thread(() -> {
			try {
				Path file = ModFiles.file("item-repo.zip");
				boolean old = !Files.exists(file) || System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > REFRESH_MS;
				if (old) {
					state = "Downloading the items";
					try {
						download(file);
					} catch (Exception e) {
						// a stale file is still usable; without any file there is nothing to load
						if (!Files.exists(file)) {
							throw e;
						}
						Debug.log("Could not download the items again, the old ones are used: {}", e.toString());
					}
				}
				state = "Reading the items";
				read(file);
				state = items.size() + " items";
			} catch (Exception e) {
				state = "Could not load the items: " + (e.getMessage() == null ? e.toString() : e.getMessage());
				Logs.warn("Could not load the item list", e);
			} finally {
				loading = false;
			}
		}, "Lumio item list");
		thread.setDaemon(true);
		thread.start();
	}

	/** Forces a fresh download the next time the list is needed. */
	public static void forceRefresh() {
		try {
			Files.deleteIfExists(ModFiles.file("item-repo.zip"));
		} catch (Exception ignored) {
		}
		items = List.of();
		loadIfNeeded();
	}

	private static void download(Path file) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) URI.create(URL).toURL().openConnection();
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(60_000);
		connection.setInstanceFollowRedirects(true);
		connection.setRequestProperty("User-Agent", "Lumio (Minecraft mod)");
		try {
			if (connection.getResponseCode() != 200) {
				throw new IllegalStateException("the list of the items answered " + connection.getResponseCode());
			}
			Path part = file.resolveSibling(file.getFileName() + ".part");
			try (InputStream in = connection.getInputStream()) {
				Files.copy(HttpLimits.limited(in, HttpLimits.REPO_BYTES), part, StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			connection.disconnect();
		}
	}

	private static void read(Path file) throws Exception {
		Map<String, Integer> books = new java.util.HashMap<>();
		List<Entry> list = new ArrayList<>();
		List<Entry> others = new ArrayList<>();
		LinkedHashMultimap<String, Recipe> fromSources = LinkedHashMultimap.create();
		Map<String, Integer> counts = new java.util.TreeMap<>();
		Map<String, Integer> unread = new java.util.TreeMap<>();
		Map<String, Entry> ids = new HashMap<>();
		LinkedHashMultimap<String, Recipe> recipes = LinkedHashMultimap.create();
		LinkedHashMultimap<String, Recipe> usages = LinkedHashMultimap.create();
		Set<String> sacks = new LinkedHashSet<>();
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (!entry.isDirectory() && name.endsWith(SACKS_FILE)) {
					readSacks(zip, sacks);
					continue;
				}
				if (!entry.isDirectory() && name.endsWith(ENCHANTS_FILE)) {
					readEnchants(zip);
					continue;
				}
				if (!entry.isDirectory() && name.endsWith(ISLANDS_FILE)) {
					readWarps(zip);
					continue;
				}
				if (!entry.isDirectory() && name.endsWith(PET_SKINS_FILE)) {
					readPetSkins(zip);
					continue;
				}
				if (entry.isDirectory() || !name.contains("/items/") || !name.endsWith(".json")) {
					continue;
				}
				JsonObject json;
				try {
					// the zip stream must not be closed by the JSON reader: it continues with the next entry
					json = JsonParser.parseReader(new InputStreamReader(new NonClosing(zip), StandardCharsets.UTF_8)).getAsJsonObject();
				} catch (RuntimeException e) {
					continue;
				}
				String id = string(json, "internalname");
				if (id.isEmpty()) {
					continue;
				}
				noteBook(id, string(json, "itemid"), books);
				List<Recipe> ways = RepoRecipes.of(id, json, kind -> unread.merge(kind, 1, Integer::sum));
				for (Recipe way : ways) {
					counts.merge(way.type(), 1, Integer::sum);
					if (!way.sourceId().isEmpty()) {
						fromSources.put(way.sourceId(), way);
					}
				}
				for (Recipe recipe : ways) {
					for (Stack out : recipe.outputs()) {
						recipes.put(out.id(), recipe);
					}
					for (Stack in : recipe.inputs()) {
						if (in != null) {
							usages.put(in.id(), recipe);
						}
					}
				}
				// NPCs and mobs are stored as items in the repository too (for their shops and drops): they are found by a search, but are not items to list
				boolean source = RepoRecipes.kindOf(id, json.has("recipes")) != RepoRecipes.Kind.ITEM;
				List<String> lore = new ArrayList<>();
				if (json.has("lore") && json.get("lore").isJsonArray()) {
					for (JsonElement line : json.getAsJsonArray("lore")) {
						lore.add(filled(line.getAsString()));
					}
				}
				String wiki = "";
				if (json.has("info") && json.get("info").isJsonArray()) {
					for (JsonElement link : json.getAsJsonArray("info")) {
						String url = link.getAsString();
						if (url.startsWith("https://")) {
							wiki = url;
							break;
						}
					}
				}
				String nbt = string(json, "nbttag");
				Matcher model = ITEM_MODEL.matcher(nbt);
				Matcher texture = TEXTURE.matcher(nbt);
				Entry item = new Entry(id, filled(LEVEL.matcher(string(json, "displayname")).replaceFirst("")), Collections.unmodifiableList(lore), wiki, string(json, "itemid"),
					model.find() ? model.group(1) : "", texture.find() ? texture.group(1) : "", string(json, "clickcommand").equals("viewrecipe"));
				(source ? others : list).add(item);
				ids.put(id, item);
			}
		}
		list.sort((a, b) -> a.plainName().compareToIgnoreCase(b.plainName()));
		others.sort((a, b) -> a.plainName().compareToIgnoreCase(b.plainName()));
		sources = Collections.unmodifiableList(others);
		ofSource = fromSources;
		recipeCounts = Map.copyOf(counts);
		unreadKinds = Map.copyOf(unread);
		Debug.log("Item repository: recipes by kind {}, not read {}, {} items, {} mobs and NPCs", recipeCounts, unreadKinds, list.size(), others.size());
		sackItems = List.copyOf(sacks);
		byId = ids;
		enchantTiers.addBooks(books);
		recipesFor = recipes;
		usagesOf = usages;
		items = Collections.unmodifiableList(list);
	}

	/** Enchanted books are called "SMITE;7" in the repository: the highest of each is how many levels the enchant has. */
	private static void noteBook(String id, String itemId, Map<String, Integer> books) {
		int split = id.indexOf(';');
		if (split <= 0 || !itemId.equals(ENCHANTED_BOOK)) {
			return;
		}
		try {
			books.merge(id.substring(0, split).toLowerCase(java.util.Locale.ROOT), Integer.parseInt(id.substring(split + 1)), Math::max);
		} catch (NumberFormatException e) {
			Debug.log("A book in the item repository without a level: {}", id);
		}
	}

	private static final String ENCHANTED_BOOK = "minecraft:enchanted_book";
	private static final String ENCHANTS_FILE = "/constants/enchants.json";
	private static final String ISLANDS_FILE = "/constants/islands.json";
	private static final String PET_SKINS_FILE = "/constants/animatedskulls.json";

	/** Reads how many levels each enchant has. A file it cannot read adds nothing. */
	private static void readEnchants(ZipInputStream zip) {
		try {
			enchantTiers = EnchantTiers.of(JsonParser.parseReader(new InputStreamReader(new NonClosing(zip), StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (RuntimeException e) {
			Logs.warn("Could not read the enchants of the item repository", e);
		}
	}

	/** Reads the warps with their short names and islands. A file it cannot read adds nothing. */
	private static void readWarps(ZipInputStream zip) {
		try {
			warps = List.copyOf(RepoWarps.of(JsonParser.parseReader(new InputStreamReader(new NonClosing(zip), StandardCharsets.UTF_8)).getAsJsonObject()));
		} catch (RuntimeException e) {
			Logs.warn("Could not read the warps of the item repository", e);
		}
	}

	/** Reads the animated pet skins and the skins with variants. A file it cannot read adds nothing. */
	private static void readPetSkins(ZipInputStream zip) {
		try {
			petSkins = RepoPetSkins.of(JsonParser.parseReader(new InputStreamReader(new NonClosing(zip), StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (RuntimeException e) {
			Logs.warn("Could not read the pet skins of the item repository", e);
		}
	}

	/** @return the animated pet skins and the skins with variants, {@link RepoPetSkins#EMPTY} until the repository is read */
	public static RepoPetSkins petSkins() {
		return petSkins;
	}

	/** @return how good each enchant's levels are, {@link EnchantTiers#EMPTY} until the repository is read */
	public static EnchantTiers enchantTiers() {
		return enchantTiers;
	}

	/** @return the warps of the repository, empty until it is read */
	public static List<RepoWarps.Warp> warps() {
		return warps;
	}

	/** The repository's sack list: every sack with the items it holds. */
	private static final String SACKS_FILE = "/constants/sacks.json";

	/** Reads the sack list: the item ids in every "contents" list, wherever they are in the file. A file it cannot read adds nothing. */
	private static void readSacks(ZipInputStream zip, Set<String> into) {
		try {
			collectContents(JsonParser.parseReader(new InputStreamReader(new NonClosing(zip), StandardCharsets.UTF_8)), into, 0);
		} catch (RuntimeException e) {
			Logs.warn("Could not read the sack list of the item repository", e);
		}
	}

	/** The deepest the sack list is searched for its contents, so a strange file cannot recurse without end. */
	private static final int MOST_SACK_DEPTH = 6;

	private static void collectContents(JsonElement element, Set<String> into, int depth) {
		if (depth > MOST_SACK_DEPTH || !element.isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
			if (child.getKey().equals("contents") && child.getValue().isJsonArray()) {
				for (JsonElement id : child.getValue().getAsJsonArray()) {
					if (id.isJsonPrimitive() && into.size() < MOST_SACK_ITEMS) {
						into.add(id.getAsString());
					}
				}
			} else {
				collectContents(child.getValue(), into, depth + 1);
			}
		}
	}

	/** The most sack items kept; SkyBlock has a few hundred. */
	private static final int MOST_SACK_ITEMS = 2000;

	/** @return the ids of the items sacks can hold, empty until the repository is read */
	public static List<String> sackItems() {
		return sackItems;
	}

	private static String string(JsonObject json, String key) {
		try {
			return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** The pet level placeholder the repository leaves for mods to fill: "[Lvl {LVL}] Ender Dragon" is shown as "Ender Dragon". */
	private static final Pattern LEVEL = Pattern.compile("^((?:§.)*)\\[Lvl \\{LVL}]\\s*");
	/** Other unfilled placeholders ("+{STRENGTH}" in level-dependent pet stats): replaced by a dash rather than the placeholder name. */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Z_0-9]+}");

	private static String filled(String text) {
		return text.indexOf('{') < 0 ? text : PLACEHOLDER.matcher(text).replaceAll("–");
	}

	static String plain(String text) {
		return COLOR.matcher(text).replaceAll("");
	}

	/** @return how the search matches: 2 for the name, 1 if all words appear somewhere in the lore (when lore search is enabled), 0 for none */
	public static int matches(Entry entry, String[] words, boolean inLore) {
		boolean name = true;
		for (String word : words) {
			if (!entry.searchName.contains(word) && !entry.searchId.contains(word)) {
				name = false;
				break;
			}
		}
		if (name) {
			return 2;
		}
		if (!inLore) {
			return 0;
		}
		for (String word : words) {
			if (!entry.searchName.contains(word) && !entry.searchLore.contains(word)) {
				return 0;
			}
		}
		return 1;
	}

	// ------------------------------------------------------------------------------------------------ item stacks for rendering

	private static ItemStack makeStack(Entry entry) {
		Item item = itemFor(entry.itemId);
		if (item == null && entry.model.startsWith("minecraft:")) {
			item = itemFor(entry.model);
		}
		if (item == null) {
			item = entry.texture.isEmpty() ? Items.PAPER : Items.PLAYER_HEAD;
		}
		ItemStack stack = new ItemStack(item);
		try {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.name).withStyle(style -> style.withItalic(false)));
			List<Component> lines = new ArrayList<>();
			for (String line : entry.lore) {
				lines.add(Component.literal(line).withStyle(style -> style.withItalic(false)));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
			// the SkyBlock id as server items carry it: other mods identify the item by it and add their lines (prices) to its tooltip
			CompoundTag data = new CompoundTag();
			data.putString("id", entry.id);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
			// hide what vanilla would add for the base item (sword damage, enchantments), which SkyBlock hides as well
			stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
				.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true).withHidden(DataComponents.ENCHANTMENTS, true)
				.withHidden(DataComponents.UNBREAKABLE, true).withHidden(DataComponents.DYED_COLOR, true));
			// use the Hypixel resource pack model when it is loaded (on the server); otherwise the vanilla item
			if (!entry.model.isEmpty() && !entry.model.startsWith("minecraft:")) {
				Identifier model = Identifier.tryParse(entry.model);
				var client = Minecraft.getInstance();
				if (model != null && client.getModelManager().getItemProperties(model) != null
					&& !(client.getModelManager().getItemModel(model) instanceof MissingItemModel)) {
					stack.set(DataComponents.ITEM_MODEL, model);
				}
			}
			if (item == Items.PLAYER_HEAD && !entry.texture.isEmpty()) {
				SkullTextures.apply(stack, entry.texture);
			}
		} catch (RuntimeException e) {
			Debug.log("Could not make the item {}: {}", entry.id, e.toString());
		}
		return stack;
	}

	private static Item itemFor(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		Identifier key = Identifier.tryParse(id.toLowerCase(Locale.ROOT));
		if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(key);
		return item == Items.AIR ? null : item;
	}

	/** A zip stream that is read entry by entry and must not be closed by the reader of a single entry. */
	private static final class NonClosing extends FilterInputStream {
		NonClosing(InputStream in) {
			super(in);
		}

		@Override
		public void close() {
		}
	}
}
