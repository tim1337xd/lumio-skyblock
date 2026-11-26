package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects items the player gains in Hypixel SkyBlock: items landing in the inventory (by diffing the inventory every few ticks), and items going into
 * sacks, which the server reports in chat ("[Sacks] +12 items", with the items in the message's hover text). Nothing is counted while a server menu
 * is open or shortly after it closed, since items moved out of a chest or bought are not gains. What is only moved, crafted or compacted among the player's
 * own items, or picked up again after being dropped, is no gain either: see {@link ItemFlow}.
 */
public final class ItemGains {
	/** Receives gained items. */
	@FunctionalInterface
	public interface Listener {
		/**
		 * @param id    the SkyBlock item id, such as ENCHANTED_CARROT
		 * @param name  the item name, without colors
		 * @param count the gained amount
		 * @param sacks whether the items went into the sacks rather than the inventory
		 */
		void gained(String id, String name, long count, boolean sacks);
	}

	/** Who wants every item of a sack summary, in and out (the pickup log). */
	private static final List<Consumer<List<SackMessage.Entry>>> SACK_LISTENERS = new ArrayList<>();

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static final List<Listener> TAKEN_LISTENERS = new ArrayList<>();
	private static Map<String, Long> before = new HashMap<>();
	private static final Map<String, String> NAMES = new HashMap<>();
	/** Items seen in the inventory, by id, for their icons: a sack item that was never in the inventory has none. */
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	/** @return the icon of an item seen in the inventory, or null */
	public static ItemStack iconOf(String id) {
		return id == null ? null : ICONS.get(id);
	}
	private static boolean screenWasOpen;
	private static long screenClosedAt;
	private static long levelSince;
	private static Object lastLevel;
	private static int tick;

	private ItemGains() {
	}

	/** Listens to every sack summary in chat: each item that went in or was taken out. */
	public static void listenSacks(Consumer<List<SackMessage.Entry>> listener) {
		SACK_LISTENERS.add(listener);
	}

	/** Listens to gains taken back: something counted turned out to come from the player's own items. */
	public static void listenTaken(Listener listener) {
		TAKEN_LISTENERS.add(listener);
	}

	public static void listen(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ItemGains::tick);
	}

	/** @return the lore of an item, line by line, without colors */
	public static List<String> loreLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>(lore.lines().size());
		for (Component line : lore.lines()) {
			lines.add(TextColors.noColors(line.getString()));
		}
		return lines;
	}

	/** @return the SkyBlock id the server stores in an item's custom data, or null if it has none */
	public static String idOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		// Custom data can only be read as a full copy, which for a SkyBlock item (enchantments, gems, stats) is large; this is queried for every
		// inventory item several times per second, by many features. An item's data never changes (a modified item gets new data), so the id is
		// read once and cached per data instance.
		String id = ID_CACHE.get(data);
		if (id == null) {
			if (ID_CACHE.size() > CACHED_MOST) {
				ID_CACHE.clear();
			}
			id = data.copyTag().getString("id").filter(s -> !s.isBlank()).orElse("");
			ID_CACHE.put(data, id);
		}
		return id.isEmpty() ? null : id;
	}

	/** @return the SkyBlock UUID the server stores in a single item's custom data (stacks have none), or "" if it has none; cached per data instance as {@link #idOf} */
	public static String uuidOf(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		String uuid = UUID_CACHE.get(data);
		if (uuid == null) {
			if (UUID_CACHE.size() > CACHED_MOST) {
				UUID_CACHE.clear();
			}
			uuid = data.copyTag().getStringOr("uuid", "");
			UUID_CACHE.put(data, uuid);
		}
		return uuid;
	}

	/** Cached id per custom data instance (identity, not equality); "" for data without an id. */
	private static final Map<CustomData, String> ID_CACHE = new IdentityHashMap<>();
	/** The same for the UUID. */
	private static final Map<CustomData, String> UUID_CACHE = new IdentityHashMap<>();
	private static final int CACHED_MOST = 4096;

	/** Whether anything needs the gains right now; while nothing does (trackers disabled), the inventory is not scanned at all. */
	private static BooleanSupplier wanted = () -> true;

	public static void wantedWhen(BooleanSupplier condition) {
		wanted = condition;
	}

	private static void tick(Minecraft client) {
		if (client.level == null || client.player == null || !wanted.getAsBoolean()) {
			// forgotten once, not made anew every tick while nothing wants it
			if (!before.isEmpty()) {
				before = new HashMap<>();
			}
			lastLevel = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			levelSince = now;
			before = new HashMap<>();
		}
		boolean screenOpen = client.gui.screen() != null && !(client.gui.screen() instanceof ChatScreen);
		if (screenOpen) {
			screenWasOpen = true;
			return;
		}
		if (screenWasOpen) {
			screenWasOpen = false;
			screenClosedAt = now;
		}
		// crops, immediately, from the farming tool's counter
		CropCounter.tick(client);
		if (++tick % 2 != 0) {
			return;
		}
		Map<String, Long> counts = new HashMap<>();
		for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
			String id = countedKey(stack);
			if (id == null) {
				continue;
			}
			counts.merge(id, (long) stack.getCount(), Long::sum);
			if (!ICONS.containsKey(id) && ICONS.size() < 400) {
				ICONS.put(id, stack.copyWithCount(1));
			}
			if (!NAMES.containsKey(id)) {
				String shown = COUNTED_NAMES.get(id);
				String name = shown != null ? shown : SkyBlockPrices.clean(stack.getHoverName().getString());
				NAMES.put(id, name);
				SkyBlockPrices.learnName(id, name);
			}
		}
		// nothing is counted right after closing a menu or joining a world: the inventory is only being populated then
		boolean settled = now - screenClosedAt > 1500 && now - levelSince > 4000 && !before.isEmpty();
		// what left is noted also across a menu: put into the sacks or crafted there, it comes back in a sack summary later. It goes on after the
		// items that landed before it, which wait a moment for a drop line: in this order a compaction is seen as it happened
		for (Map.Entry<String, Long> entry : before.entrySet()) {
			long lost = entry.getValue() - counts.getOrDefault(entry.getKey(), 0L);
			if (lost > 0) {
				String key = entry.getKey();
				DROPS.inOrder(() -> FLOW.left(key, lost, now));
			}
		}
		if (settled) {
			for (Map.Entry<String, Long> entry : counts.entrySet()) {
				long gained = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
				if (gained > 0) {
					DROPS.landed(entry.getKey(), gained, now);
				}
			}
		}
		before = counts;
		DROPS.tick(now);
	}

	/** Items whose value hangs on what is inside them: counted by their price key, not by their id. */
	private static final Set<String> COUNTED_BY_CONTENT = Set.of("ENCHANTED_BOOK", "PET", "RUNE", "UNIQUE_RUNE", "POTION", "ATTRIBUTE_SHARD");
	/** The key an item is counted by, per custom data instance, as {@link #idOf}. */
	private static final Map<CustomData, String> COUNTED_CACHE = new IdentityHashMap<>();
	/** The name of what is counted by its content ("Flash I"), by its key. */
	private static final Map<String, String> COUNTED_NAMES = new HashMap<>();

	/**
	 * @return the key a gained item is counted by: an enchanted book by its enchantment's product, a pet by its kind and rarity, a rune, an attribute
	 *         shard or a potion by its price key (see {@link PriceKeys#counted}); anything else by its id
	 */
	static String countedKey(ItemStack stack) {
		String id = idOf(stack);
		if (id == null || !COUNTED_BY_CONTENT.contains(id) && !id.startsWith("ATTRIBUTE_SHARD")) {
			return id;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		String key = COUNTED_CACHE.get(data);
		if (key == null) {
			if (COUNTED_CACHE.size() > CACHED_MOST) {
				COUNTED_CACHE.clear();
			}
			JsonObject json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data.copyTag()) instanceof JsonObject object ? object : new JsonObject();
			PriceKeys.Counted counted = PriceKeys.counted(id, json, ItemRepo::shardProduct, ItemRepo::bookName);
			key = counted.key() == null ? id : counted.key();
			if (counted.name() != null && COUNTED_NAMES.size() < CACHED_MOST) {
				COUNTED_NAMES.put(key, counted.name());
			}
			COUNTED_CACHE.put(data, key);
		}
		return key;
	}

	/** Receives drops the server announced, matched with their item or counted from the line alone. */
	@FunctionalInterface
	public interface DropListener {
		/**
		 * @param id       the key the item is counted by
		 * @param name     its name
		 * @param count    how many
		 * @param fromChat whether its item never showed (a full inventory, the stash) and it counts from the chat line alone
		 */
		void dropped(String id, String name, long count, boolean fromChat);
	}

	private static final List<DropListener> DROP_LISTENERS = new ArrayList<>();

	private static final List<DropListener> SHARD_LISTENERS = new ArrayList<>();

	/** Listens to shards the player got (charmed, caught, a loot share of shards), from the server's line: they never reach the inventory. */
	public static void listenShards(DropListener listener) {
		SHARD_LISTENERS.add(listener);
	}

	/** Listens to announced drops: rare drops, RNG meter drops and loot shares, each once, also when the kill was not the player's. */
	public static void listenDrops(DropListener listener) {
		DROP_LISTENERS.add(listener);
	}

	/** Announced drops matched with what arrives; what no line announced goes on through {@link #FLOW}. */
	private static final AnnouncedDrops DROPS = new AnnouncedDrops(new AnnouncedDrops.Out() {
		@Override
		public void arrived(String key, long count) {
			FLOW.landed(key, count, System.currentTimeMillis());
		}

		@Override
		public void dropped(String key, String name, long count, boolean fromChat) {
			String shown = name != null ? name : NAMES.getOrDefault(key, SkyBlockPrices.nameOf(key));
			NAMES.putIfAbsent(key, shown);
			// crops already counted from the farming tool are not counted again, as with any gain
			long fresh = withoutCorpseLoot(key, CropCounter.settle(key, shown, count));
			if (fresh <= 0) {
				return;
			}
			for (DropListener listener : DROP_LISTENERS) {
				listener.dropped(CropCounter.canonical(key), shown, fresh, fromChat);
			}
		}
	});

	/** A rare drop or a loot share in chat is announced to {@link #DROPS}, when the item it names is known. */
	private static void dropLine(Component message, String text) {
		long now = System.currentTimeMillis();
		// shards go into the Hunting Box, never into the inventory: their line is all there is of them, so they are counted from it alone
		DropMessage.ShardGain shards = DropMessage.shards(text);
		if (shards != null) {
			String id = ItemRepo.shardByName(shards.name());
			String name = shards.name() + " Shard";
			NAMES.putIfAbsent(id, name);
			for (DropListener listener : SHARD_LISTENERS) {
				listener.dropped(id, name, shards.amount(), false);
			}
			return;
		}
		DropMessage.LootShare share = DropMessage.lootShare(text);
		if (share != null) {
			if (!share.named()) {
				DROPS.lootShare(now);
				return;
			}
			String id = idForDropName(share.name(), share.amount());
			if (id != null) {
				DROPS.announce(id, NAMES.getOrDefault(id, singular(share.name(), share.amount())), share.amount(), now);
			}
			return;
		}
		DropMessage drop = DropMessage.parse(text);
		if (drop == null) {
			return;
		}
		String id;
		if (drop.book()) {
			String product = ItemRepo.bookProduct(drop.name());
			id = product != null ? product : drop.bookId();
		} else if (drop.pet()) {
			id = DropPriceIds.petId(drop.name(), DropMessage.nameColor(message, drop.name(), 0));
		} else {
			id = idForDropName(drop.name(), drop.amount());
		}
		// an item whose id is not known is left to its arrival: announcing it could count it twice
		if (id != null) {
			DROPS.announce(id, drop.name(), drop.amount(), now);
		}
	}

	/** @return the id of an item by the name a drop line gives, also in the plural ("3 Thunder Shards"); null when it is not known */
	private static String idForDropName(String name, int amount) {
		String id = SkyBlockPrices.idForName(SkyBlockPrices.clean(name));
		return id != null || amount <= 1 ? id : SkyBlockPrices.idForName(SkyBlockPrices.clean(singular(name, amount)));
	}

	private static String singular(String name, int amount) {
		return amount > 1 && name.endsWith("s") ? name.substring(0, name.length() - 1) : name;
	}

	/** What really came in, from everything that changed: see {@link ItemFlow}. */
	private static final ItemFlow FLOW = new ItemFlow(ItemGains::craftingWays, new ItemFlow.Out() {
		@Override
		public void gained(String id, long count, boolean sacks) {
			String name = NAMES.getOrDefault(id, SkyBlockPrices.nameOf(id));
			// crops already counted from the tool are not counted again
			long fresh = CropCounter.settle(id, name, count);
			if (fresh > 0) {
				emit(CropCounter.canonical(id), name, fresh, sacks);
			}
		}

		@Override
		public void taken(String id, long count) {
			String name = NAMES.getOrDefault(id, SkyBlockPrices.nameOf(id));
			for (Listener listener : TAKEN_LISTENERS) {
				listener.gained(CropCounter.canonical(id), name, count, false);
			}
		}
	});

	private static List<ItemFlow.Way> craftingWays(String id) {
		List<ItemFlow.Way> ways = new ArrayList<>();
		for (ItemRepo.Recipe recipe : ItemRepo.recipesFor(id)) {
			if (!recipe.crafting()) {
				continue;
			}
			long makes = 1;
			for (ItemRepo.Stack made : recipe.outputs()) {
				if (made != null && id.equals(made.id())) {
					makes = Math.max(1L, Math.round(made.count()));
				}
			}
			Map<String, Long> needs = new HashMap<>();
			for (ItemRepo.Stack input : recipe.inputs()) {
				if (input != null && input.id() != null && !input.id().isBlank()) {
					needs.merge(input.id(), Math.round(input.count()), Long::sum);
				}
			}
			List<ItemFlow.Part> parts = new ArrayList<>(needs.size());
			needs.forEach((item, count) -> parts.add(new ItemFlow.Part(item, count)));
			ways.add(new ItemFlow.Way(makes, parts));
		}
		return ways;
	}

	/** Every chat message passes here; sack messages are parsed. */
	public static void onMessage(Component message) {
		String text = message.getString().strip();
		if (!SackMessage.is(text)) {
			for (String line : text.split("\\n")) {
				corpseLine(TextColors.noColors(line).strip());
			}
			dropLine(message, text);
			return;
		}
		SkyBlock.evidence(120_000);
		List<String> hover = new ArrayList<>();
		// The item list is the message's hover text, which is attached to several message parts (the server splits it into differently colored
		// parts sharing the same hover text): read every text only once, or every item is counted twice.
		Set<String> seenTexts = new HashSet<>();
		message.visit((style, part) -> {
			if (style.getHoverEvent() instanceof HoverEvent.ShowText show) {
				String over = show.value().getString();
				if (seenTexts.add(over)) {
					for (String line : over.split("\\n")) {
						hover.add(TextColors.noColors(line).strip());
					}
				}
			}
			return Optional.<Object>empty();
		}, Style.EMPTY);
		List<SackMessage.Entry> entries = SackMessage.entries(hover);
		for (Consumer<List<SackMessage.Entry>> listener : SACK_LISTENERS) {
			listener.accept(entries);
		}
		Map<String, Long> changes = new HashMap<>();
		for (SackMessage.Entry entry : entries) {
			String name = SkyBlockPrices.clean(entry.name());
			String id = SkyBlockPrices.idForName(name);
			if (id == null) {
				id = name.toUpperCase(Locale.ROOT).replace(' ', '_');
			}
			NAMES.putIfAbsent(id, name);
			// an announced drop that went into the sacks counts as that drop, once
			long count = entry.count() > 0 ? DROPS.intoSacks(id, entry.count()) : entry.count();
			if (count != 0) {
				changes.merge(id, count, Long::sum);
			}
		}
		long now = System.currentTimeMillis();
		DROPS.inOrder(() -> FLOW.sacks(changes, now));
	}

	/** "TUNGSTEN CORPSE LOOT!", the heading above a mineshaft corpse's loot list, with its kind. */
	private static final Pattern CORPSE_HEADER = Pattern.compile("(\\w+)\\s+CORPSE LOOT!$");
	/** A loot list line: "Tungsten x12", or just a name for one item. */
	private static final Pattern CORPSE_ITEM = Pattern.compile("^(.+?)(?:\\s+x([\\d,]+))?$");
	/** "Enchanted Book (Ice Cold I)": a book is priced by the enchantment in brackets. */
	private static final Pattern CORPSE_BOOK = Pattern.compile("^Enchanted Book \\((.+?) ([IVX]+)\\)$");
	/** The Heart of the Mountain perk message that occasionally saves a corpse's key. */
	private static final String KEY_SAVED = "LUCKY! Your Resourceful perk saved your key from being consumed!";
	/** Until when chat lines belong to a corpse loot list. */
	private static long corpseListUntil;
	/** Corpse loot by id: amount and expiry. It arrives later (sacks report every 30 seconds) and must not count as gains of the current activity then. */
	private static final Map<String, long[]> corpseLoot = new HashMap<>();

	/**
	 * An opened corpse: its kind as named in chat ("TUNGSTEN"), its loot, whether its key was consumed, and whether every line of its loot list could be
	 * read (a list with a line that could not, or with none, is not complete: what it is worth is not known for sure).
	 */
	public record Corpse(String kind, List<Drop> drops, boolean keyUsed, boolean complete) {
	}

	/** An item from a corpse: its id, name and amount. */
	public record Drop(String id, String name, long count) {
	}

	private static final List<Consumer<Corpse>> CORPSE_LISTENERS = new ArrayList<>();
	/** The corpse whose loot list is being read, and its loot so far. */
	private static String corpseKind;
	private static final List<Drop> corpseDrops = new ArrayList<>();
	/** Whether a line of the loot list being read could not be read. */
	private static boolean corpseBroken;
	/** Whether the next corpse kept its key; the server announces it before the list. */
	private static boolean keySaved;

	/** Registers a listener for every opened corpse, called once its whole loot list has been read. */
	public static void listenCorpses(Consumer<Corpse> listener) {
		CORPSE_LISTENERS.add(listener);
	}

	/** When the last line of a corpse's loot list came, for the case its closing bar never does. */
	private static long corpseLineAt;
	/** How long a loot list may wait for its closing bar before it counts as complete without it. */
	private static final long CLOSING_BAR_MILLIS = 300L;

	/** Reports the corpse whose list is being read, with all its loot. */
	private static void finishCorpse() {
		corpseListUntil = 0;
		if (corpseKind != null) {
			Corpse corpse = new Corpse(corpseKind, List.copyOf(corpseDrops), !keySaved, !corpseBroken && !corpseDrops.isEmpty());
			corpseKind = null;
			keySaved = false;
			for (var listener : CORPSE_LISTENERS) {
				listener.accept(corpse);
			}
		}
	}

	/** Every tick: a loot list whose closing bar did not come within a few ticks of its last line is complete without it. */
	public static void tickCorpse() {
		if (corpseKind != null && !corpseDrops.isEmpty() && System.currentTimeMillis() - corpseLineAt > CLOSING_BAR_MILLIS) {
			finishCorpse();
		}
	}

	private static void corpseLine(String line) {
		long now = System.currentTimeMillis();
		if (line.equals(KEY_SAVED)) {
			keySaved = true;
			return;
		}
		Matcher header = CORPSE_HEADER.matcher(line);
		if (header.find()) {
			corpseListUntil = now + 2000L;
			corpseKind = header.group(1).toUpperCase(Locale.ROOT);
			corpseDrops.clear();
			corpseBroken = false;
			return;
		}
		if (now > corpseListUntil || line.isEmpty() || line.equals("REWARDS")) {
			return;
		}
		corpseLineAt = now;
		// the list ends with the separator bar: report the corpse with all its loot
		if (line.contains("▬")) {
			finishCorpse();
			return;
		}
		Matcher matcher = CORPSE_ITEM.matcher(line);
		if (!matcher.matches()) {
			corpseBroken = true;
			return;
		}
		long count = 1;
		if (matcher.group(2) != null) {
			try {
				count = Long.parseLong(matcher.group(2).replace(",", ""));
			} catch (NumberFormatException e) {
				corpseBroken = true;
				return;
			}
		}
		String name = SkyBlockPrices.clean(matcher.group(1));
		String id = SkyBlockPrices.idForName(name);
		Matcher book = CORPSE_BOOK.matcher(name);
		if (book.matches()) {
			// "Enchanted Book (Ice Cold I)" is sold as the enchantment: ENCHANTMENT_ICE_COLD_1
			id = "ENCHANTMENT_" + book.group(1).toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "") + "_" + Roman.ofIvx(book.group(2));
		} else if (id == null) {
			id = name.toUpperCase(Locale.ROOT).replace(' ', '_');
		}
		long[] loot = corpseLoot.computeIfAbsent(id, key -> new long[2]);
		loot[0] += count;
		loot[1] = now + 60_000L;
		if (corpseKind != null) {
			corpseDrops.add(new Drop(id, name, count));
		}
	}

	/** @return the remaining gain after subtracting corpse loot of the item */
	private static long withoutCorpseLoot(String id, long count) {
		long[] loot = corpseLoot.get(id);
		if (loot == null) {
			return count;
		}
		if (System.currentTimeMillis() > loot[1]) {
			corpseLoot.remove(id);
			return count;
		}
		long taken = Math.min(count, loot[0]);
		loot[0] -= taken;
		if (loot[0] <= 0) {
			corpseLoot.remove(id);
		}
		return count - taken;
	}

	/** Notifies all listeners of gained items. */
	static void emit(String id, String name, long count, boolean sacks) {
		count = withoutCorpseLoot(id, count);
		if (count <= 0) {
			return;
		}
		for (Listener listener : LISTENERS) {
			listener.gained(id, name, count, sacks);
		}
	}
}
