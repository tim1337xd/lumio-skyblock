package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.SocialNames;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the command suggestions learn from what the game shows: subcommands from help and usage messages right after the player ran that command, warps from the Fast Travel menu, how often
 * each warp is used, and the guild members and friends seen. Read and written in the background, in a file in the mod's folder that is not part of shared configs; nothing of it is ever sent
 * anywhere. Only reads: it never sends a command or clicks.
 */
public final class LearnedCommands {
	private static final String FILE = "skyblock-learned-commands.json";
	private static final long HELP_WAIT_MILLIS = 15_000L;
	private static final int SAVE_EVERY_TICKS = 200;
	private static final int MOST_WARPS = 200;
	private static final int MOST_LEARNED = 256;
	private static final String FAST_TRAVEL = "Fast Travel";

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio learned commands");
		thread.setDaemon(true);
		return thread;
	});

	private static final Map<String, WarpPicker.Warp> WARPS = new LinkedHashMap<>();
	private static final Map<String, Integer> WARP_USES = new HashMap<>();
	private static final List<CommandLearner.Learned> SUBS = new ArrayList<>();
	/** The guild members and friends seen, for the suggestions. */
	public static final SocialNames NAMES = new SocialNames();
	/** The friends and guild members are kept per account, by its id (never anything of its session): the other accounts' as read, to be written again. */
	private static final String ACCOUNTS = "accounts";
	private static final int MOST_ACCOUNTS = 32;
	private static final JsonObject OTHER_ACCOUNTS = new JsonObject();
	private static String askedRoot;
	private static long askedAt;
	private static boolean loadStarted;
	private static boolean dirty;
	private static int tick;
	private static Object menuFor;
	private static int menuState = -1;

	private LearnedCommands() {
	}

	public static void register() {
		ClientSendMessageEvents.COMMAND.register(LearnedCommands::onCommand);
		ClientTickEvents.END_CLIENT_TICK.register(LearnedCommands::tick);
	}

	/** The player ran a command: its help may follow, and a warp counts as used. */
	private static void onCommand(String command) {
		String[] words = command.strip().split(" ");
		if (words.length == 0 || !enabled()) {
			return;
		}
		CommandTree tree = SkyBlockSuggestions.tree();
		askedRoot = tree.rootOf(words[0]);
		askedAt = System.currentTimeMillis();
		if ("warp".equals(askedRoot) && words.length == 2) {
			WARP_USES.merge(words[1].toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
			dirty = true;
		}
	}

	/** Reads a server message: never one a player typed. */
	public static void onMessage(Component message) {
		if (!enabled()) {
			return;
		}
		boolean asked = askedRoot != null && System.currentTimeMillis() - askedAt <= HELP_WAIT_MILLIS;
		CommandTree tree = SkyBlockSuggestions.tree();
		for (String raw : message.getString().split("\n")) {
			String line = TextColors.noColors(raw).strip();
			NAMES.read(line);
			if (asked) {
				CommandLearner.Learned learned = CommandLearner.read(line);
				if (learned != null && askedRoot.equals(tree.rootOf(learned.command())) && SUBS.size() < MOST_LEARNED && tree.learn(learned.command(), learned.sub(), learned.args())) {
					SUBS.add(learned);
					dirty = true;
				}
			}
		}
	}

	private static boolean enabled() {
		return SkyBlockFeatures.COMMAND_SUGGESTIONS != null && SkyBlockFeatures.COMMAND_SUGGESTIONS.isEnabled();
	}

	private static void tick(Minecraft client) {
		if (!loadStarted) {
			loadStarted = true;
			CompletableFuture.supplyAsync(LearnedCommands::read, WORKER).thenAcceptAsync(LearnedCommands::take, client);
		}
		if (enabled() && client.gui.screen() instanceof ContainerScreen screen) {
			readFastTravel(screen);
		}
		if (dirty && ++tick % SAVE_EVERY_TICKS == 0) {
			dirty = false;
			String text = new Gson().toJson(snapshot());
			WORKER.execute(() -> {
				try {
					ModFiles.writeAtomically(file(), text);
				} catch (IOException e) {
					Logs.warn("Could not write the learned commands", e);
				}
			});
		}
	}

	/** The warps the game's Fast Travel menu shows, once per change of the menu. */
	private static void readFastTravel(ContainerScreen screen) {
		int state = screen.getMenu().getStateId();
		if (screen == menuFor && state == menuState) {
			return;
		}
		menuFor = screen;
		menuState = state;
		if (!screen.getTitle().getString().contains(FAST_TRAVEL)) {
			return;
		}
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			ItemLore lore = stack.get(DataComponents.LORE);
			if (slot.container instanceof Inventory || lore == null) {
				continue;
			}
			List<String> lines = new ArrayList<>();
			for (Component line : lore.lines()) {
				lines.add(TextColors.noColors(line.getString()));
			}
			WarpPicker.Warp warp = WarpPicker.ofMenuItem(TextColors.noColors(stack.getHoverName().getString()), lines);
			if (warp != null && (WARPS.size() < MOST_WARPS || WARPS.containsKey(warp.name())) && !warp.equals(WARPS.put(warp.name(), warp))) {
				dirty = true;
			}
		}
	}

	static Map<String, WarpPicker.Warp> warps() {
		return WARPS;
	}

	static Map<String, Integer> warpUses() {
		return WARP_USES;
	}

	private static Path file() {
		return ModFiles.file(FILE);
	}

	private static JsonObject read() {
		try {
			Path file = file();
			if (Files.exists(file) && Files.size(file) < MOST_FILE_BYTES) {
				return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			}
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the learned commands", e);
		}
		return new JsonObject();
	}

	private static final long MOST_FILE_BYTES = 1_000_000L;

	/** Takes what was kept from before, on the game's thread. The file can have been edited, so every value is checked by what uses it. */
	private static void take(JsonObject root) {
		try {
			CommandTree tree = SkyBlockSuggestions.tree();
			if (root.has("subs")) {
				for (JsonElement element : root.getAsJsonArray("subs")) {
					JsonObject sub = element.getAsJsonObject();
					CommandLearner.Learned learned = CommandLearner.read("/" + sub.get("command").getAsString() + " " + sub.get("sub").getAsString() + " " + sub.get("args").getAsString());
					if (learned != null && SUBS.size() < MOST_LEARNED && tree.learn(learned.command(), learned.sub(), learned.args())) {
						SUBS.add(learned);
					}
				}
			}
			if (root.has("warps")) {
				for (JsonElement element : root.getAsJsonArray("warps")) {
					JsonObject warp = element.getAsJsonObject();
					if (WARPS.size() < MOST_WARPS) {
						WARPS.put(warp.get("name").getAsString(), new WarpPicker.Warp(warp.get("name").getAsString(), warp.get("island").getAsString(), warp.get("unlocked").getAsBoolean()));
					}
				}
			}
			if (root.has("uses")) {
				for (Map.Entry<String, JsonElement> use : root.getAsJsonObject("uses").entrySet()) {
					if (WARP_USES.size() < MOST_WARPS) {
						WARP_USES.put(use.getKey(), use.getValue().getAsInt());
					}
				}
			}
			String account = account();
			JsonObject accounts = root.has(ACCOUNTS) && root.get(ACCOUNTS).isJsonObject() ? root.getAsJsonObject(ACCOUNTS) : new JsonObject();
			for (Map.Entry<String, JsonElement> entry : accounts.entrySet()) {
				if (!entry.getKey().equals(account) && entry.getValue().isJsonObject() && OTHER_ACCOUNTS.size() < MOST_ACCOUNTS) {
					OTHER_ACCOUNTS.add(entry.getKey(), entry.getValue());
				}
			}
			// names kept before they were kept per account belong to the account playing now
			JsonObject own = accounts.has(account) && accounts.get(account).isJsonObject() ? accounts.getAsJsonObject(account) : root;
			NAMES.keep(names(own, "guild"), names(own, "friends"));
		} catch (RuntimeException e) {
			Logs.warn("Could not read the learned commands", e);
		}
	}

	private static String[] names(JsonObject root, String key) {
		if (!root.has(key)) {
			return new String[0];
		}
		JsonArray array = root.getAsJsonArray(key);
		List<String> out = new ArrayList<>();
		for (JsonElement name : array) {
			String text = name.getAsString();
			if (text.matches("[A-Za-z0-9_]{1,16}")) {
				out.add(text);
			}
		}
		return out.toArray(new String[0]);
	}

	/** @return the id of the account playing, by which its friends and guild members are kept */
	private static String account() {
		return Minecraft.getInstance().getUser().getProfileId().toString();
	}

	private static JsonObject snapshot() {
		JsonObject root = new JsonObject();
		JsonArray subs = new JsonArray();
		for (CommandLearner.Learned learned : SUBS) {
			JsonObject sub = new JsonObject();
			sub.addProperty("command", learned.command());
			sub.addProperty("sub", learned.sub());
			sub.addProperty("args", learned.args());
			subs.add(sub);
		}
		root.add("subs", subs);
		JsonArray warps = new JsonArray();
		for (WarpPicker.Warp warp : WARPS.values()) {
			JsonObject json = new JsonObject();
			json.addProperty("name", warp.name());
			json.addProperty("island", warp.island());
			json.addProperty("unlocked", warp.unlocked());
			warps.add(json);
		}
		root.add("warps", warps);
		JsonObject uses = new JsonObject();
		WARP_USES.forEach(uses::addProperty);
		root.add("uses", uses);
		JsonObject own = new JsonObject();
		JsonArray guild = new JsonArray();
		NAMES.guild().forEach(guild::add);
		own.add("guild", guild);
		JsonArray friends = new JsonArray();
		NAMES.friends().forEach(friends::add);
		own.add("friends", friends);
		JsonObject accounts = OTHER_ACCOUNTS.deepCopy();
		accounts.add(account(), own);
		root.add(ACCOUNTS, accounts);
		return root;
	}
}
