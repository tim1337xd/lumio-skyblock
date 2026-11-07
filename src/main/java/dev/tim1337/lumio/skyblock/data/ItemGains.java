package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
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
 * is open or shortly after it closed, since items moved out of a chest or bought are not gains; neither are items picked up again right after
 * being dropped.
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

	private static final Pattern SACK_MESSAGE = Pattern.compile("^\\[Sacks]\\s+[+-]");
	private static final Pattern SACK_LINE = Pattern.compile("^([+-][\\d,]+)\\s+(.+?)\\s+\\((.+?)\\)\\.?$");

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static Map<String, Long> before = new HashMap<>();
	private static final Map<String, String> NAMES = new HashMap<>();
	/** Items seen in the inventory, by id, for their icons: a sack item that was never in the inventory has none. */
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	/** @return the icon of an item seen in the inventory, or null */
	public static ItemStack iconOf(String id) {
		return id == null ? null : ICONS.get(id);
	}
	/** Items that left the inventory recently, by id: picking them up again is not a gain. */
	private static final Map<String, long[]> dropped = new HashMap<>();
	private static boolean screenWasOpen;
	private static long screenClosedAt;
	private static long levelSince;
	private static Object lastLevel;
	private static int tick;

	private ItemGains() {
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
			String id = idOf(stack);
			if (id == null) {
				continue;
			}
			counts.merge(id, (long) stack.getCount(), Long::sum);
			if (!ICONS.containsKey(id) && ICONS.size() < 400) {
				ICONS.put(id, stack.copyWithCount(1));
			}
			if (!NAMES.containsKey(id)) {
				String name = SkyBlockPrices.clean(stack.getHoverName().getString());
				NAMES.put(id, name);
				SkyBlockPrices.learnName(id, name);
			}
		}
		// nothing is counted right after closing a menu or joining a world: the inventory is only being populated then
		boolean settled = now - screenClosedAt > 1500 && now - levelSince > 4000 && !before.isEmpty();
		if (settled) {
			for (Map.Entry<String, Long> entry : counts.entrySet()) {
				long gained = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
				if (gained <= 0) {
					continue;
				}
				long[] back = dropped.get(entry.getKey());
				if (back != null && now - back[1] < 30_000L) {
					long taken = Math.min(gained, back[0]);
					back[0] -= taken;
					gained -= taken;
					if (back[0] <= 0) {
						dropped.remove(entry.getKey());
					}
				}
				String name = NAMES.getOrDefault(entry.getKey(), SkyBlockPrices.nameOf(entry.getKey()));
				// crops already counted from the tool are not counted again
				gained = gained > 0 ? CropCounter.settle(entry.getKey(), name, gained) : 0;
				if (gained > 0) {
					emit(CropCounter.canonical(entry.getKey()), name, gained, false);
				}
			}
			for (Map.Entry<String, Long> entry : before.entrySet()) {
				long lost = entry.getValue() - counts.getOrDefault(entry.getKey(), 0L);
				if (lost > 0) {
					long[] back = dropped.computeIfAbsent(entry.getKey(), key -> new long[2]);
					back[0] += lost;
					back[1] = now;
				}
			}
		}
		before = counts;
		if (dropped.size() > 200) {
			dropped.clear();
		}
	}

	/** Every chat message passes here; sack messages are parsed. */
	public static void onMessage(Component message) {
		String text = message.getString().strip();
		if (!SACK_MESSAGE.matcher(text).find()) {
			for (String line : text.split("\\n")) {
				corpseLine(TextColors.noColors(line).strip());
			}
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
		for (String line : hover) {
			Matcher matcher = SACK_LINE.matcher(line);
			if (!matcher.matches() || !matcher.group(1).startsWith("+")) {
				continue;
			}
			long count;
			try {
				count = Long.parseLong(matcher.group(1).substring(1).replace(",", ""));
			} catch (NumberFormatException e) {
				continue;
			}
			String name = SkyBlockPrices.clean(matcher.group(2));
			String id = SkyBlockPrices.idForName(name);
			if (id == null) {
				id = name.toUpperCase(Locale.ROOT).replace(' ', '_');
			}
		// crops already counted from the tool are subtracted from what the sacks report
			count = CropCounter.settle(id, name, count);
			if (count > 0) {
				emit(CropCounter.canonical(id), name, count, true);
			}
		}
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

	/** An opened corpse: its kind as named in chat ("TUNGSTEN"), its loot, and whether its key was consumed. */
	public record Corpse(String kind, List<Drop> drops, boolean keyUsed) {
	}

	/** An item from a corpse: its id, name and amount. */
	public record Drop(String id, String name, long count) {
	}

	private static final List<Consumer<Corpse>> CORPSE_LISTENERS = new ArrayList<>();
	/** The corpse whose loot list is being read, and its loot so far. */
	private static String corpseKind;
	private static final List<Drop> corpseDrops = new ArrayList<>();
	/** Whether the next corpse kept its key; the server announces it before the list. */
	private static boolean keySaved;

	/** Registers a listener for every opened corpse, called once its whole loot list has been read. */
	public static void listenCorpses(Consumer<Corpse> listener) {
		CORPSE_LISTENERS.add(listener);
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
			return;
		}
		if (now > corpseListUntil || line.isEmpty() || line.equals("REWARDS")) {
			return;
		}
		// the list ends with the separator bar: report the corpse with all its loot
		if (line.contains("▬")) {
			corpseListUntil = 0;
			if (corpseKind != null) {
				Corpse corpse = new Corpse(corpseKind, List.copyOf(corpseDrops), !keySaved);
				corpseKind = null;
				keySaved = false;
				for (var listener : CORPSE_LISTENERS) {
					listener.accept(corpse);
				}
			}
			return;
		}
		Matcher matcher = CORPSE_ITEM.matcher(line);
		if (!matcher.matches()) {
			return;
		}
		long count = 1;
		if (matcher.group(2) != null) {
			try {
				count = Long.parseLong(matcher.group(2).replace(",", ""));
			} catch (NumberFormatException e) {
				return;
			}
		}
		String name = SkyBlockPrices.clean(matcher.group(1));
		String id = SkyBlockPrices.idForName(name);
		Matcher book = CORPSE_BOOK.matcher(name);
		if (book.matches()) {
			// "Enchanted Book (Ice Cold I)" is sold as the enchantment: ENCHANTMENT_ICE_COLD_1
			id = "ENCHANTMENT_" + book.group(1).toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "") + "_" + roman(book.group(2));
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

	private static int roman(String numeral) {
		int value = 0;
		int last = 0;
		for (int i = numeral.length() - 1; i >= 0; i--) {
			int digit = switch (numeral.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				default -> 0;
			};
			value += digit < last ? -digit : digit;
			last = Math.max(last, digit);
		}
		return value;
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
