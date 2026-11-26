package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.experimental.util.Threads;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.StorageTracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The player's own Abiphone contacts, for /call: learned while the Abiphone's contacts menu is open, from the names of its items that are contacts the
 * item repository knows (so no button of the menu is taken for one), and kept per SkyBlock profile in a file in the mod's folder, read and written in
 * the background. Only reads what the game shows; nothing is sent.
 */
public final class AbiphoneContacts {
	private static final String FILE = "skyblock-abiphone-contacts.json";
	/** The Abiphone's menus are named after the phone: "Abiphone XIII Pro Giga". */
	private static final String MENU = "Abiphone";
	private static final String NO_PROFILE = "unknown";
	private static final int MOST_PROFILES = 32;
	private static final int MOST_CONTACTS = 300;
	private static final long MOST_FILE_BYTES = 500_000L;
	private static final int SAVE_AFTER_TICKS = 40;

	private static final ExecutorService WORKER = Threads.daemonExecutor("Lumio Abiphone contacts");

	/** The contacts by profile, as the Abiphone lists them. */
	private static final Map<String, Set<String>> BY_PROFILE = new LinkedHashMap<>();
	/** The contacts the repository knows, by their name in lower case, made again only when its list changes. */
	private static java.util.List<ContactCompletion.Contact> knownOf;
	private static Map<String, String> known = Map.of();
	private static boolean loadStarted;
	private static int saveIn;
	private static Object menuFor;
	private static int menuState = -1;

	private AbiphoneContacts() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AbiphoneContacts::tick);
	}

	/** @return the player's own contacts on the profile played now, as the Abiphone lists them; empty until its menu was open once */
	public static Set<String> own() {
		Set<String> contacts = BY_PROFILE.get(profile());
		return contacts == null ? Set.of() : contacts;
	}

	private static String profile() {
		String profile = StorageTracker.profile();
		return profile == null ? NO_PROFILE : profile;
	}

	private static void tick(Minecraft client) {
		if (!loadStarted) {
			loadStarted = true;
			CompletableFuture.supplyAsync(AbiphoneContacts::read, WORKER).thenAcceptAsync(AbiphoneContacts::take, client);
		}
		CommandSuggestionsFeature chat = SkyBlockFeatures.COMMAND_SUGGESTIONS;
		if (chat != null && chat.isEnabled() && client.gui.screen() instanceof ContainerScreen screen) {
			readMenu(screen);
		}
		if (saveIn > 0 && --saveIn == 0) {
			save();
		}
	}

	/** The contacts the menu shows, once per change of it: items whose name is a contact the repository knows. */
	private static void readMenu(ContainerScreen screen) {
		int state = screen.getMenu().getStateId();
		if (screen == menuFor && state == menuState) {
			return;
		}
		menuFor = screen;
		menuState = state;
		if (!screen.getTitle().getString().startsWith(MENU)) {
			return;
		}
		Map<String, String> names = knownNames();
		if (names.isEmpty()) {
			return;
		}
		Set<String> contacts = BY_PROFILE.computeIfAbsent(profile(), key -> new LinkedHashSet<>());
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory || slot.getItem().isEmpty()) {
				continue;
			}
			String name = names.get(TextColors.noColors(slot.getItem().getHoverName().getString()).strip().toLowerCase(Locale.ROOT));
			if (name != null && contacts.size() < MOST_CONTACTS && contacts.add(name)) {
				saveIn = SAVE_AFTER_TICKS;
			}
		}
	}

	/** @return the names of the contacts the repository knows, by their lower case; it starts loading if it has not yet */
	private static Map<String, String> knownNames() {
		ItemRepo.loadIfNeeded();
		java.util.List<ContactCompletion.Contact> contacts = ItemRepo.abiphoneContacts();
		if (contacts != knownOf) {
			knownOf = contacts;
			Map<String, String> names = new HashMap<>();
			for (ContactCompletion.Contact contact : contacts) {
				names.put(contact.name().toLowerCase(Locale.ROOT), contact.name());
			}
			known = names;
		}
		return known;
	}

	private static Path file() {
		return ModFiles.file(FILE);
	}

	private static JsonObject read() {
		try {
			Path file = file();
			if (Files.exists(file) && Files.size(file) < MOST_FILE_BYTES) {
				JsonElement root = JsonParser.parseString(Files.readString(file));
				return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
			}
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the Abiphone contacts", e);
		}
		return new JsonObject();
	}

	/** Takes what was kept, on the game's thread, under what was learned meanwhile. The file can have been edited, so every value is checked. */
	private static void take(JsonObject root) {
		for (Map.Entry<String, JsonElement> profile : root.entrySet()) {
			if (BY_PROFILE.size() >= MOST_PROFILES || !profile.getValue().isJsonArray()) {
				continue;
			}
			Set<String> contacts = BY_PROFILE.computeIfAbsent(profile.getKey(), key -> new LinkedHashSet<>());
			for (JsonElement name : profile.getValue().getAsJsonArray()) {
				if (name.isJsonPrimitive() && contacts.size() < MOST_CONTACTS) {
					contacts.add(name.getAsString());
				}
			}
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Set<String>> profile : BY_PROFILE.entrySet()) {
			JsonArray names = new JsonArray();
			profile.getValue().forEach(names::add);
			root.add(profile.getKey(), names);
		}
		String text = root.toString();
		WORKER.execute(() -> {
			try {
				ModFiles.writeAtomically(file(), text);
			} catch (IOException e) {
				Logs.warn("Could not write the Abiphone contacts", e);
			}
		});
	}
}
