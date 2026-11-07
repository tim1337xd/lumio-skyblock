package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.api.SuggestionProvider;
import dev.tim1337.lumio.api.experimental.util.CommandHints;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.PartyList;
import dev.tim1337.lumio.skyblock.data.RecentChatNames;
import dev.tim1337.lumio.skyblock.data.RepoWarps;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The entries of the game's own command suggestions (the list above the chat input) for the commands the mod knows: subcommands with what they take, warps with their islands,
 * players by context (party members, friends and guild first), item ids with a recipe for /viewrecipe and sack items for /gfs. Where the mod knows what a word takes, only its own entries
 * that match the word are shown, in place of the server's; elsewhere the server's stay. Nothing is ever sent: the entries come from the tab list, the world, the chat and the item repository.
 */
public final class SkyBlockSuggestions {
	/** The most entries the mod adds to one list. */
	private static final int MOST = 100;
	/** The bundled warp points. */
	private static final String WARPS_FILE = "/assets/lumio-skyblock/skyblock/warps.txt";
	/** The commands: their subcommands and what their arguments take. */
	private static final String COMMANDS_FILE = "/assets/lumio-skyblock/skyblock/commands.json";
	/** A real player's name; the tab list also holds made-up entries for its columns ("!A-a"). */
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

	private static List<String> warps;
	private static CommandTree tree;
	/** The ids of the items with a recipe, made again only when the item list changes. */
	private static List<ItemRepo.Entry> recipesOf;
	private static List<String> recipeIds = List.of();
	/** The sack items in alphabetical order, made again only when the repository's list changes. */
	private static List<String> sacksOf;
	private static List<String> sortedSacks = List.of();
	/** The suggestions merged last, whose order the list keeps instead of sorting them again. */
	private static volatile Suggestions merged;

	private SkyBlockSuggestions() {
	}

	/** The party is forgotten on every server join: it was learned on another one. */
	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			PartyList.INSTANCE.clear();
			RecentChatNames.INSTANCE.clear();
		});
		CommandHints.setUsage(SkyBlockSuggestions::usage);
		LearnedCommands.register();
	}

	/**
	 * Adds the SkyBlock entries for the argument being typed to the server's suggestions, if it is one of the SkyBlock commands; otherwise the
	 * server's suggestions are returned untouched.
	 */
	public static CompletableFuture<Suggestions> add(CompletableFuture<Suggestions> server, String input, int cursor) {
		CommandSuggestionsFeature chat = SkyBlockFeatures.COMMAND_SUGGESTIONS;
		if (chat == null || !chat.isEnabled() || cursor < 0 || cursor > input.length() || !SkyBlock.allowed()) {
			return server;
		}
		CommandTree.Completion found = tree().find(input.substring(0, cursor));
		if (found == null) {
			return server;
		}
		if (!knows(found, chat)) {
			return server;
		}
		Map<String, String> hints = new HashMap<>();
		List<String> first = first(found, chat, hints);
		List<String> names = found.names() == null || found.names() == CommandCompletion.Source.WARPS || !chat.suggests(found.names())
			? List.of() : CommandCompletion.order(found.typed(), groups(found.names(), chat), MOST);
		// where the list knows what the word takes, only its own entries that match the word are shown: the server suggests all of a command's subcommands at
		// any word of Hypixel's commands; with nothing that matches, the list is empty and the game shows none
		return server.thenApply(suggested -> ours(input, cursor, found.start(), first, names, hints));
	}

	/** @return whether the list knows what the word at the end takes, in a kind the player wants suggested */
	private static boolean knows(CommandTree.Completion found, CommandSuggestionsFeature chat) {
		return chat.suggestsCommands() && !found.fixed().isEmpty() || found.names() != null && chat.suggests(found.names());
	}

	/** @return the mod's entries for the word being typed: the subcommands and fixed values first, then the names, each once */
	private static Suggestions ours(String input, int cursor, int start, List<String> first, List<String> names, Map<String, String> hints) {
		StringRange range = StringRange.between(start, cursor);
		List<Suggestion> list = new ArrayList<>();
		for (String text : CommandCompletion.merge(input.substring(start, cursor), first, List.of(), names)) {
			String hint = hints.get(text);
			list.add(hint != null ? new Suggestion(range, text, new CommandHints.Quiet(hint)) : new Suggestion(range, text));
		}
		Suggestions result = new Suggestions(range, list);
		merged = result;
		return result;
	}

	/** @return the subcommands and fixed values that match, and the warps, with their quiet texts */
	private static List<String> first(CommandTree.Completion found, CommandSuggestionsFeature chat, Map<String, String> hints) {
		List<String> first = new ArrayList<>();
		if (chat.suggestsCommands()) {
			List<String> fixed = new ArrayList<>();
			for (CommandTree.Entry entry : found.fixed()) {
				fixed.add(entry.text());
				if (entry.quiet() != null) {
					hints.put(entry.text(), entry.quiet());
				}
			}
			first.addAll(CommandCompletion.order(found.typed(), List.of(fixed), MOST));
		}
		if (found.names() == CommandCompletion.Source.WARPS && chat.suggests(CommandCompletion.Source.WARPS)) {
			for (WarpPicker.Warp warp : WarpPicker.order(found.typed(), allWarps(), LearnedCommands.warpUses())) {
				first.add(warp.name());
				if (!warp.island().isEmpty()) {
					hints.put(warp.name(), warp.island());
				}
			}
		}
		return first;
	}

	/** @return the commands of the data file, with what the game has shown on top; read once */
	static CommandTree tree() {
		if (tree == null) {
			JsonObject root = new JsonObject();
			try (InputStream in = SkyBlockSuggestions.class.getResourceAsStream(COMMANDS_FILE)) {
				if (in != null) {
					root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				}
			} catch (IOException | RuntimeException e) {
				Logs.warn("Could not read the bundled command list", e);
			}
			tree = CommandTree.of(root);
		}
		return tree;
	}

	/** @return what the input before the cursor takes next, as a quiet hint, while the suggestions are on */
	private static String usage(String beforeCursor) {
		CommandSuggestionsFeature chat = SkyBlockFeatures.COMMAND_SUGGESTIONS;
		return chat != null && chat.suggestsCommands() && SkyBlock.allowed() ? tree().usage(beforeCursor) : null;
	}

	/** @return whether these are suggestions the mod merged, in the order it chose */
	public static boolean ours(Suggestions suggestions) {
		return suggestions != null && suggestions == merged;
	}

	/** @return these suggestions as a provider of the add-on API */
	public static SuggestionProvider provider() {
		return new SuggestionProvider() {
			@Override
			public CompletableFuture<Suggestions> add(CompletableFuture<Suggestions> server, String input, int cursor) {
				return SkyBlockSuggestions.add(server, input, cursor);
			}

			@Override
			public boolean merged(Suggestions suggestions) {
				return ours(suggestions);
			}
		};
	}

	/** @return the entries for an argument, in groups of priority */
	private static List<List<String>> groups(CommandCompletion.Source source, CommandSuggestionsFeature chat) {
		return switch (source) {
			case WARPS -> List.of(warps());
			case PLAYERS -> players(chat);
			case PARTY -> List.of(partyMembers());
			case GUILD -> List.of(List.copyOf(LearnedCommands.NAMES.guild()));
			case FRIENDS -> List.of(List.copyOf(LearnedCommands.NAMES.friends()));
			case RECIPES -> List.of(recipeIds());
			case SACKS -> List.of(sackItems());
		};
	}

	/** @return the party members, never the player */
	private static List<String> partyMembers() {
		Minecraft client = Minecraft.getInstance();
		String me = client.player == null ? "" : client.player.getGameProfile().name();
		List<String> party = new ArrayList<>();
		for (String member : PartyList.INSTANCE.members()) {
			if (!member.equalsIgnoreCase(me)) {
				party.add(member);
			}
		}
		return party;
	}

	/** @return every warp known: the repository's with their short names, the bundled ones until it is read, and what the game's menu showed */
	private static List<WarpPicker.Warp> allWarps() {
		ItemRepo.loadIfNeeded();
		Map<String, WarpPicker.Warp> all = new LinkedHashMap<>();
		for (String warp : warps()) {
			all.put(warp, new WarpPicker.Warp(warp, "", true));
		}
		for (RepoWarps.Warp warp : ItemRepo.warps()) {
			all.put(warp.name(), new WarpPicker.Warp(warp.name(), warp.island(), true));
		}
		for (WarpPicker.Warp warp : LearnedCommands.warps().values()) {
			// the repository's island is the name to show; the menu knows whether it is unlocked
			WarpPicker.Warp known = all.get(warp.name());
			all.put(warp.name(), new WarpPicker.Warp(warp.name(), known == null || known.island().isEmpty() ? warp.island() : known.island(), warp.unlocked()));
		}
		return List.copyOf(all.values());
	}

	/** @return the party members (when chosen), the friends and guild members seen, the players on the island and the rest of the tab list, then who wrote in chat lately; never the player. Nothing is sent to look a name up */
	private static List<List<String>> players(CommandSuggestionsFeature chat) {
		Minecraft client = Minecraft.getInstance();
		String me = client.player == null ? "" : client.player.getGameProfile().name();
		List<String> party = chat.suggestsParty() ? partyMembers() : List.of();
		List<String> known = new ArrayList<>();
		if (chat.suggestsOtherPlayers()) {
			known.addAll(LearnedCommands.NAMES.friends());
			known.addAll(LearnedCommands.NAMES.guild());
			known.removeIf(name -> name.equalsIgnoreCase(me));
		}
		List<String> island = new ArrayList<>();
		List<String> tab = new ArrayList<>();
		if (chat.suggestsOtherPlayers()) {
			if (client.level != null) {
				for (Player player : client.level.players()) {
					String name = player.getGameProfile().name();
					if (!name.equalsIgnoreCase(me) && PLAYER_NAME.matcher(name).matches()) {
						island.add(name);
					}
				}
			}
			if (client.getConnection() != null) {
				for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
					String name = info.getProfile().name();
					if (!name.equalsIgnoreCase(me) && PLAYER_NAME.matcher(name).matches()) {
						tab.add(name);
					}
				}
				tab.sort(String.CASE_INSENSITIVE_ORDER);
			}
		}
		List<String> chatted = chat.suggestsOtherPlayers() ? RecentChatNames.INSTANCE.newestFirst(me) : List.of();
		return List.of(party, known, island, tab, chatted);
	}

	/** @return the bundled warp points, read once */
	private static List<String> warps() {
		if (warps == null) {
			List<String> read = new ArrayList<>();
			try (InputStream in = SkyBlockSuggestions.class.getResourceAsStream(WARPS_FILE)) {
				if (in != null) {
					for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
						String warp = line.strip();
						if (!warp.isEmpty() && !warp.startsWith("#")) {
							read.add(warp);
						}
					}
				}
			} catch (IOException e) {
				Logs.warn("Could not read the bundled warp list", e);
			}
			warps = List.copyOf(read);
		}
		return warps;
	}

	/** @return the ids of the items with a recipe menu (/viewrecipe), from the item repository; it starts loading if it has not yet */
	private static List<String> recipeIds() {
		ItemRepo.loadIfNeeded();
		List<ItemRepo.Entry> items = ItemRepo.items();
		if (items != recipesOf) {
			recipesOf = items;
			List<String> ids = new ArrayList<>();
			for (ItemRepo.Entry item : items) {
				if (item.craftable) {
					ids.add(item.id);
				}
			}
			ids.sort(String::compareTo);
			recipeIds = List.copyOf(ids);
		}
		return recipeIds;
	}

	private static List<String> sackItems() {
		ItemRepo.loadIfNeeded();
		List<String> items = ItemRepo.sackItems();
		if (items != sacksOf) {
			sacksOf = items;
			List<String> sorted = new ArrayList<>(items);
			sorted.sort(String::compareTo);
			sortedSacks = List.copyOf(sorted);
		}
		return sortedSacks;
	}
}
