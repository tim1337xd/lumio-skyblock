package dev.tim1337.lumio.skyblock.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What is known of one SkyBlock profile's wardrobe: every slot as last seen, the player's own names and the favorites, and how it is kept in a file of that profile. Plain data, so it is unit tested. A name and a favorite belong to the slot's number.
 */
public final class WardrobeBook {
	/** The longest own name. */
	public static final int NAME_MOST = 24;
	/** The most slots there can be, and the most pages. */
	public static final int SLOTS_MOST = 81;
	public static final int PAGES_MOST = SLOTS_MOST / WardrobeMenu.SETS;
	/** A kept item longer than this is not read: what a file says is not trusted. */
	private static final int PIECE_MOST_CHARS = 200_000;
	private static final int TEXT_MOST_CHARS = 64;

	/**
	 * A slot as last seen.
	 *
	 * @param pieces its four places, top to bottom, each as written by the game's item format; empty where there is no piece
	 * @param setName the name of its set, from its pieces
	 * @param color the color of that name, as 0xRRGGBB
	 */
	public record Slot(int number, WardrobeMenu.State state, String rank, List<String> pieces, String setName, int color) {
		/** @return whether it holds at least one piece */
		public boolean hasPieces() {
			for (String piece : pieces) {
				if (!piece.isEmpty()) {
					return true;
				}
			}
			return false;
		}
	}

	private final Map<Integer, Slot> slots = new TreeMap<>();
	private final Map<Integer, String> names = new TreeMap<>();
	private final Set<Integer> favorites = new TreeSet<>();
	private int pages = 1;
	private boolean favoritesOnly;

	/** @return the name of a profile's file */
	public static String fileName(String profile) {
		return profile == null ? "wardrobe.json" : "wardrobe-" + profile + ".json";
	}

	/** @return how many pages the game says there are */
	public int pages() {
		return pages;
	}

	/** @return the slot as last seen, or null for one not seen yet */
	public Slot slot(int number) {
		return slots.get(number);
	}

	/**
	 * Takes what an open page shows.
	 *
	 * @param page the open page, from 1
	 * @param pageCount how many pages the game says there are
	 * @param seen the slots of the page
	 * @return whether anything changed
	 */
	public boolean see(int page, int pageCount, List<Slot> seen) {
		boolean changed = pages != Math.min(pageCount, PAGES_MOST);
		pages = Math.min(pageCount, PAGES_MOST);
		boolean wornHere = false;
		for (Slot slot : seen) {
			wornHere |= slot.state() == WardrobeMenu.State.EQUIPPED;
		}
		for (Slot slot : seen) {
			if (slot.number() >= 1 && slot.number() <= SLOTS_MOST) {
				changed |= !slot.equals(slots.put(slot.number(), slot));
			}
		}
		// one set is worn: another page's that was worn is not any more, once a page shows the worn one
		if (wornHere) {
			for (Map.Entry<Integer, Slot> entry : slots.entrySet()) {
				Slot slot = entry.getValue();
				if (slot.state() == WardrobeMenu.State.EQUIPPED && pageOf(slot.number()) != page) {
					entry.setValue(new Slot(slot.number(), WardrobeMenu.State.READY, slot.rank(), slot.pieces(), slot.setName(), slot.color()));
					changed = true;
				}
			}
		}
		return changed;
	}

	/** @return the page of a slot, from 1 */
	public static int pageOf(int number) {
		return (number - 1) / WardrobeMenu.SETS + 1;
	}

	/** @return the worn slot's number, or 0 */
	public int worn() {
		for (Slot slot : slots.values()) {
			if (slot.state() == WardrobeMenu.State.EQUIPPED) {
				return slot.number();
			}
		}
		return 0;
	}

	/** @return the header's second line */
	public String summary() {
		int[] used = new int[pages];
		int[] locked = new int[pages];
		java.util.Arrays.fill(used, -1);
		for (Slot slot : slots.values()) {
			int page = pageOf(slot.number()) - 1;
			if (page >= pages) {
				continue;
			}
			used[page] = Math.max(0, used[page]);
			if (slot.state() == WardrobeMenu.State.READY || slot.state() == WardrobeMenu.State.EQUIPPED) {
				used[page]++;
			} else if (slot.state() == WardrobeMenu.State.LOCKED) {
				locked[page]++;
			}
		}
		return WardrobeMenu.summary(used, locked, worn());
	}

	/** @return the player's name for a slot, or an empty text */
	public String name(int number) {
		return names.getOrDefault(number, "");
	}

	/**
	 * Saves a slot's name: an empty one removes it, and a long one is kept to its first letters.
	 *
	 * @return whether it changed
	 */
	public boolean rename(int number, String name) {
		String kept = name.strip();
		if (kept.length() > NAME_MOST) {
			kept = kept.substring(0, NAME_MOST).strip();
		}
		return kept.isEmpty() ? names.remove(number) != null : !kept.equals(names.put(number, kept));
	}

	/** @return whether a slot is a favorite */
	public boolean favorite(int number) {
		return favorites.contains(number);
	}

	/** Makes a slot a favorite, or no longer one. */
	public void toggleFavorite(int number) {
		if (!favorites.remove(number)) {
			favorites.add(number);
		}
	}

	/** @return whether only the favorites are shown */
	public boolean favoritesOnly() {
		return favoritesOnly;
	}

	public void favoritesOnly(boolean only) {
		favoritesOnly = only;
	}

	/** @return the numbers of the cards to show, in order: every slot of every page, or the favorites */
	public List<Integer> shown() {
		List<Integer> numbers = new ArrayList<>(pages * WardrobeMenu.SETS);
		for (int number = 1; number <= pages * WardrobeMenu.SETS; number++) {
			if (!favoritesOnly || favorites.contains(number)) {
				numbers.add(number);
			}
		}
		return numbers;
	}

	/** Takes what a file kept, for all that this session has not seen itself. */
	public void adopt(WardrobeBook kept) {
		kept.slots.forEach(slots::putIfAbsent);
		kept.names.forEach(names::putIfAbsent);
		favorites.addAll(kept.favorites);
		pages = Math.max(pages, kept.pages);
		favoritesOnly |= kept.favoritesOnly;
	}

	/** @return all of it as the file keeps it */
	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("pages", pages);
		root.addProperty("favorites_only", favoritesOnly);
		JsonArray list = new JsonArray();
		for (Slot slot : slots.values()) {
			JsonObject one = new JsonObject();
			one.addProperty("number", slot.number());
			one.addProperty("state", slot.state().name());
			if (slot.rank() != null) {
				one.addProperty("rank", slot.rank());
			}
			one.addProperty("set", slot.setName());
			one.addProperty("color", slot.color());
			JsonArray pieces = new JsonArray();
			slot.pieces().forEach(pieces::add);
			one.add("pieces", pieces);
			list.add(one);
		}
		root.add("slots", list);
		JsonObject own = new JsonObject();
		names.forEach((number, name) -> own.addProperty(String.valueOf(number), name));
		root.add("names", own);
		JsonArray stars = new JsonArray();
		favorites.forEach(stars::add);
		root.add("favorites", stars);
		return root;
	}

	/** @return what a file says, leaving out all that is not as it should be: a file can be edited or broken */
	public static WardrobeBook fromJson(JsonObject root) {
		WardrobeBook book = new WardrobeBook();
		book.pages = Math.clamp(integer(root, "pages", 1), 1, PAGES_MOST);
		book.favoritesOnly = root.has("favorites_only") && root.get("favorites_only").isJsonPrimitive() && root.get("favorites_only").getAsBoolean();
		if (root.has("slots") && root.get("slots").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("slots")) {
				Slot slot = element.isJsonObject() ? slotOf(element.getAsJsonObject()) : null;
				if (slot != null) {
					book.slots.put(slot.number(), slot);
				}
			}
		}
		if (root.has("names") && root.get("names").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("names").entrySet()) {
				int number = parsed(entry.getKey());
				if (number >= 1 && number <= SLOTS_MOST && entry.getValue().isJsonPrimitive()) {
					book.rename(number, entry.getValue().getAsString());
				}
			}
		}
		if (root.has("favorites") && root.get("favorites").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("favorites")) {
				int number = element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : 0;
				if (number >= 1 && number <= SLOTS_MOST) {
					book.favorites.add(number);
				}
			}
		}
		return book;
	}

	private static Slot slotOf(JsonObject one) {
		int number = integer(one, "number", 0);
		WardrobeMenu.State state = stateOf(text(one, "state"));
		if (number < 1 || number > SLOTS_MOST || state == null || !one.has("pieces") || !one.get("pieces").isJsonArray()) {
			return null;
		}
		List<String> pieces = new ArrayList<>(WardrobeMenu.PIECE_ROWS);
		for (JsonElement element : one.getAsJsonArray("pieces")) {
			if (pieces.size() < WardrobeMenu.PIECE_ROWS) {
				String piece = element.isJsonPrimitive() ? element.getAsString() : "";
				pieces.add(piece.length() > PIECE_MOST_CHARS ? "" : piece);
			}
		}
		while (pieces.size() < WardrobeMenu.PIECE_ROWS) {
			pieces.add("");
		}
		String rank = text(one, "rank");
		return new Slot(number, state, rank.isEmpty() ? null : rank, List.copyOf(pieces), text(one, "set"), integer(one, "color", 0xFFFFFF) & 0xFFFFFF);
	}

	private static WardrobeMenu.State stateOf(String name) {
		for (WardrobeMenu.State state : WardrobeMenu.State.values()) {
			if (state.name().equals(name)) {
				return state;
			}
		}
		return null;
	}

	private static String text(JsonObject one, String key) {
		if (!one.has(key) || !one.get(key).isJsonPrimitive()) {
			return "";
		}
		String value = one.get(key).getAsString();
		return value.length() > TEXT_MOST_CHARS ? value.substring(0, TEXT_MOST_CHARS) : value;
	}

	private static int integer(JsonObject one, String key, int otherwise) {
		return one.has(key) && one.get(key).isJsonPrimitive() && one.get(key).getAsJsonPrimitive().isNumber() ? one.get(key).getAsInt() : otherwise;
	}

	private static int parsed(String text) {
		try {
			return text.length() > 3 ? 0 : Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
