package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Entries in the game's own command suggestions (see {@link SkyBlockSuggestions}): subcommands, warps, players by context, item ids with a
 * recipe and sack items, each kind on its own chip. Nothing is ever sent.
 */
public class CommandSuggestionsFeature extends AddonFeature {
	private final Toggle suggestCommands = make().toggle("suggest_commands", Component.literal("Commands"), true, Toggle.Style.CHECKBOX);
	private final Toggle suggestWarps = make().toggle("suggest_warps", Component.literal("Warps"), true, Toggle.Style.CHECKBOX);
	private final Toggle suggestPlayers = make().toggle("suggest_players", Component.literal("Players"), true, Toggle.Style.CHECKBOX);
	private final Toggle suggestParty = make().toggle("suggest_party", Component.literal("Party"), true, Toggle.Style.CHECKBOX);
	private final Toggle suggestRecipes = make().toggle("suggest_recipes", Component.literal("Recipes"), true, Toggle.Style.CHECKBOX);
	private final Toggle suggestSacks = make().toggle("suggest_sacks", Component.literal("Sacks"), true, Toggle.Style.CHECKBOX);
	private final MultiToggle suggestions = add(make().multi("command_suggestions", Component.literal("Suggest"),
		List.of(suggestCommands, suggestWarps, suggestPlayers, suggestParty, suggestRecipes, suggestSacks)).asChips(), "Suggestions");

	public CommandSuggestionsFeature() {
		super("command_suggestions", "Command suggestions", "More entries in the game's command suggestions: subcommands, warps, players by context, "
			+ "recipes and sacks. It learns from what the game shows you and sends nothing.", true);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("terminal");
		summary("Subcommands, warps, players and more as you type");
	}

	/** @return whether the SkyBlock suggestions of a kind are added to the command suggestions */
	public boolean suggests(CommandCompletion.Source source) {
		if (!isEnabled()) {
			return false;
		}
		return switch (source) {
			case WARPS -> suggestWarps.get();
			case PLAYERS -> suggestPlayers.get() || suggestParty.get();
			case PARTY -> suggestParty.get();
			case GUILD, FRIENDS -> suggestPlayers.get();
			case RECIPES -> suggestRecipes.get();
			case SACKS -> suggestSacks.get();
		};
	}

	/** @return whether subcommands are suggested */
	public boolean suggestsCommands() {
		return isEnabled() && suggestCommands.get();
	}

	/** @return whether the players on the island and in the tab list are suggested */
	public boolean suggestsOtherPlayers() {
		return isEnabled() && suggestPlayers.get();
	}

	/** @return whether the party members come first among the suggested players */
	public boolean suggestsParty() {
		return isEnabled() && suggestParty.get();
	}
}
