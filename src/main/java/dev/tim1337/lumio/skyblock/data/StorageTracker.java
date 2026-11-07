package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contents of the Hypixel SkyBlock ender chest and backpacks as of the last time each page was open: the server only sends a page while it is open, so
 * every page is cached while open and written to a file, for the storage overlay to show all of them at once.
 */
public final class StorageTracker {
	/** Ender chest page title: "Ender Chest (2/9)". */
	private static final Pattern ENDER_PAGE = Pattern.compile("^Ender Chest \\((\\d+)/(\\d+)\\)");
	/** Backpack title: "Jumbo Backpack (Slot #3)". */
	private static final Pattern BACKPACK = Pattern.compile("^(.*Backpack)\\s*\\(Slot #(\\d+)\\)");
	/** Names of the storage menu items that open a page: "Ender Chest Page 3", "Backpack Slot 12" (also "Empty Backpack Slot 4"). */
	private static final Pattern MENU_ENDER = Pattern.compile("Ender Chest Page (\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern MENU_BACKPACK = Pattern.compile("Backpack Slot (\\d+)", Pattern.CASE_INSENSITIVE);
	/** The page number or slot at the end of a page title: "(2/9)", "(Slot #3)". */
	private static final Pattern PAGE_NUMBER = Pattern.compile("\\s*\\(.*\\)$");

	/** A page: its key ("e3" or "b12"), title, row count and items (serialized, as stored in the file). */
	public static final class Page {
		public final String key;
		public String title;
		public int rows;
		public List<ItemStack> items = new ArrayList<>();
		List<String> encoded = new ArrayList<>();
		public long seen;

		Page(String key) {
			this.key = key;
		}

		/** @return the items, deserialized from the file on first access, since that requires a world */
		public List<ItemStack> items() {
			if (items.isEmpty() && !encoded.isEmpty()) {
				List<ItemStack> read = new ArrayList<>();
				for (String text : encoded) {
					read.add(PetTracker.decodeIcon(text));
				}
				items = read;
			}
			return items;
		}
	}

	private static final Map<String, Page> PAGES = new LinkedHashMap<>();
	/** The storage menu items that open pages, by page key, as of the last time the menu was open: for the menu panel while a page is open and the
	 *  menu itself is not. */
	private static final Map<String, ItemStack> MENU_ITEMS = new LinkedHashMap<>();
	/** The same items as they are kept in the file: what was read and not yet turned back into items, which needs a world, and what is written. */
	private static final Map<String, String> MENU_ENCODED = new LinkedHashMap<>();
	private static boolean menuChanged;
	private static boolean menuRead;

	/** Caches the storage menu items that open pages. */
	public static void rememberMenu(AbstractContainerMenu menu) {
		Map<String, ItemStack> found = new LinkedHashMap<>();
		for (int i = 0; i < menu.slots.size() && i < 54; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String key = StorageStore.keyOfMenuSlot(i, TextColors.noColors(stack.getHoverName().getString()).strip());
			if (key != null) {
				found.put(key, stack.copy());
			}
		}
		if (!found.isEmpty()) {
			load();
			MENU_ITEMS.clear();
			MENU_ENCODED.clear();
			MENU_ITEMS.putAll(found);
			menuChanged = true;
			if (dirtySince == 0) {
				dirtySince = System.currentTimeMillis();
			}
		}
	}

	/** @return the storage menu items that open pages, as of the last time the menu was open */
	public static Map<String, ItemStack> menuItems() {
		load();
		// once, with a world to read them with: what cannot be read stays out, and is not parsed again every time
		if (!menuRead && Minecraft.getInstance().level != null) {
			menuRead = true;
			MENU_ENCODED.forEach((key, text) -> {
				ItemStack item = PetTracker.decodeIcon(text);
				if (!item.isEmpty()) {
					MENU_ITEMS.putIfAbsent(key, item);
				}
			});
		}
		return MENU_ITEMS;
	}

	/** @return the keys of the known pages: ender chest first, then backpacks, each in order */
	public static List<String> knownKeys() {
		load();
		Set<String> keys = new TreeSet<>((a, b) -> {
			if (a.charAt(0) != b.charAt(0)) {
				return a.charAt(0) == 'e' ? -1 : 1;
			}
			return Integer.compare(Integer.parseInt(a.substring(1)), Integer.parseInt(b.substring(1)));
		});
		keys.addAll(PAGES.keySet());
		keys.addAll(menuItems().keySet());
		return new ArrayList<>(keys);
	}
	/** Whether the file was read and taken in, and whether its reading has begun (on the writer thread, as it can be several megabytes). */
	private static boolean loaded;
	private static boolean loading;
	private static long dirtySince;
	private static int tick;

	private StorageTracker() {
	}

	private static Path file() {
		return ModFiles.file(StorageStore.fileName(profile));
	}

	/** Which SkyBlock profile the pages are of, by what the server says when it starts; null until it said. */
	private static String profile;

	/** @return the SkyBlock profile being played, by what the server said when it started; null until it said */
	public static String profile() {
		return profile;
	}

	/** Listens for the server naming the profile: another profile has other pages, each kept in a file of its own. */
	public static void onMessage(Component message) {
		String named = StorageStore.profileOf(message.getString());
		if (named == null || named.equals(profile)) {
			return;
		}
		if (dirtySince != 0 && loaded) {
			save();
		}
		Path before = file();
		boolean first = profile == null;
		profile = named;
		PAGES.clear();
		MENU_ITEMS.clear();
		MENU_ENCODED.clear();
		menuChanged = false;
		menuRead = false;
		loaded = false;
		loading = false;
		dirtySince = 0;
		// what was kept before the profile was known, or by an older version, belongs to the first profile named
		if (first) {
			Path target = file();
			WRITER.execute(() -> {
				try {
					if (Files.exists(before) && !Files.exists(target)) {
						Files.move(before, target);
					}
				} catch (IOException e) {
					Logs.warn("Could not move the storage that was kept", e);
				}
			});
		}
	}

	/** @return the key of an ender chest or backpack page from its screen title, or null if it is neither */
	public static String keyOf(String title) {
		Matcher ender = ENDER_PAGE.matcher(title);
		if (ender.find()) {
			return "e" + ender.group(1);
		}
		Matcher backpack = BACKPACK.matcher(title);
		if (backpack.find()) {
			return "b" + backpack.group(2);
		}
		return null;
	}

	/** @return the key of the page a storage menu item opens, from its name, or null */
	public static String keyOfMenuItem(String name) {
		Matcher ender = MENU_ENDER.matcher(name);
		if (ender.find()) {
			return "e" + ender.group(1);
		}
		Matcher backpack = MENU_BACKPACK.matcher(name);
		if (backpack.find()) {
			return "b" + backpack.group(1);
		}
		return null;
	}

	/** @return whether a title is the storage menu's, which the overlay covers */
	public static boolean isStorageMenu(String title) {
		return title.strip().equals("Storage");
	}

	public static Page page(String key) {
		load();
		return PAGES.get(key);
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(StorageTracker::tick);
	}

	/** Every few ticks, caches the open page, since its items arrive after the screen opens and change while it is open. */
	private static void tick(Minecraft client) {
		if (dirtySince != 0 && loaded && System.currentTimeMillis() - dirtySince > 3000) {
			save();
		}
		if (++tick % 5 != 0 || !(client.gui.screen() instanceof ContainerScreen screen) || client.player == null) {
			return;
		}
		if (SkyBlockFeatures.STORAGE == null || !SkyBlockFeatures.STORAGE.isEnabled()) {
			return;
		}
		String title = screen.getTitle().getString().strip();
		String key = keyOf(title);
		if (key == null) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		int rows = screen.getMenu().getRowCount();
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < rows * 9 && i < menu.slots.size(); i++) {
			items.add(menu.slots.get(i).getItem().copy());
		}
		load();
		Page page = PAGES.computeIfAbsent(key, Page::new);
		// a SkyBlock page's first row is its buttons (pages, back, close): kept as is and shown like the page shows them
		page.title = PAGE_NUMBER.matcher(title).replaceAll("");
		page.rows = rows;
		page.items = items;
		page.encoded = List.of();
		page.seen = System.currentTimeMillis();
		if (dirtySince == 0) {
			dirtySince = System.currentTimeMillis();
		}
	}

	/** What the file held: the menu's items and the pages, as kept. */
	private record Kept(Map<String, String> menu, Map<String, StorageStore.Saved> pages) {
	}

	/**
	 * Starts reading the file of the profile, once: on the writer thread, after any write still queued, and taken in on the game's thread. Until then
	 * the pages known are only those seen since; what was seen meanwhile stays, the file fills in the rest.
	 */
	private static void load() {
		if (loaded || loading) {
			return;
		}
		loading = true;
		String forProfile = profile;
		Path file = file();
		CompletableFuture.supplyAsync(() -> read(file), WRITER).thenAcceptAsync(kept -> take(kept, forProfile), Minecraft.getInstance());
	}

	/** @return what the file holds, or null without one; on the writer thread */
	private static Kept read(Path file) {
		if (!Files.exists(file)) {
			return null;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return new Kept(StorageStore.menuFromJson(root), StorageStore.fromJson(root));
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the storage that was kept", e);
			return null;
		}
	}

	/** Takes in what the file held, on the game's thread; dropped when the profile changed meanwhile (its own reading starts then). */
	private static void take(Kept kept, String forProfile) {
		if (!Objects.equals(forProfile, profile) || loaded) {
			return;
		}
		loaded = true;
		loading = false;
		if (kept == null) {
			return;
		}
		kept.menu().forEach(MENU_ENCODED::putIfAbsent);
		// the menu's items from the file are turned into items at the next look
		menuRead = false;
		kept.pages().forEach((key, saved) -> {
			if (PAGES.containsKey(key)) {
				return;
			}
			Page page = new Page(key);
			page.title = saved.title();
			page.rows = saved.rows();
			page.seen = saved.seen();
			page.encoded = saved.items();
			PAGES.put(key, page);
		});
	}

	/** Writes the file in the background: with every page known it is several megabytes, too much to write between two frames. */
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio storage");
		thread.setDaemon(true);
		return thread;
	});

	private static void save() {
		dirtySince = 0;
		Map<String, StorageStore.Saved> pages = new LinkedHashMap<>();
		for (Page page : PAGES.values()) {
			// only pages refreshed since the last save are serialized again (the tick clears their encoded items); the others keep theirs
			if (!page.items.isEmpty() && page.encoded.isEmpty()) {
				List<String> encoded = new ArrayList<>(page.items.size());
				for (ItemStack stack : page.items) {
					encoded.add(PetTracker.encodeIcon(stack));
				}
				page.encoded = encoded;
			}
			pages.put(page.key, new StorageStore.Saved(page.title, page.rows, page.seen, page.encoded));
		}
		if (menuChanged) {
			menuChanged = false;
			MENU_ENCODED.clear();
			MENU_ITEMS.forEach((key, item) -> MENU_ENCODED.put(key, PetTracker.encodeIcon(item)));
		}
		JsonObject root = StorageStore.toJson(pages, MENU_ENCODED);
		// the profile can change before the file is written
		Path target = file();
		WRITER.execute(() -> {
			try {
				// through a temporary file, so quitting the game during a write cannot leave half a file
				ModFiles.writeAtomically(target, new Gson().toJson(root));
			} catch (IOException e) {
				Logs.warn("Could not keep the storage", e);
			}
		});
	}
}
