package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Guard;
import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Protects the chat trackers from what other players write. On Hypixel, player chat arrives as system messages like the server's own, so a player
 * typing "SLAYER QUEST STARTED!" would otherwise trigger the trackers, and a crafted message must never break the packet handling.
 */
public final class ChatGuard {
	/** A chat channel or a private message: only players write in these ("Party > ", "Guild > ", "Officer > ", "Co-op > ", "From ", "To "). */
	private static final Pattern CHANNEL = Pattern.compile("^(?:(?:Party|Guild|Officer|Co-op) >|(?:From|To) (?:\\[[^\\]]{1,20}\\] )?[A-Za-z0-9_]{1,16}:)");
	/**
	 * A player's line in public chat: a SkyBlock level (with an optional emblem) or a rank in brackets, the name, an optional guild rank, then a
	 * colon. NPC and boss lines have the same shape ("[NPC] Phillip: ...") and are not player chat.
	 */
	private static final Pattern PUBLIC = Pattern.compile(
		"^(?:\\[\\d{1,3}\\] (?:[^\\w\\s\\[]{1,2} )?(?:\\[[^\\]]{1,20}\\] ?)*|(?:\\[(?!NPC\\]|BOSS\\])[^\\]]{1,20}\\] ?)+)[A-Za-z0-9_]{1,16}(?: \\[[^\\]]{1,10}\\])?: ");

	private ChatGuard() {
	}

	/** @return whether a chat message was written by a player rather than by the server */
	public static boolean fromPlayer(Component message) {
		String text = TextColors.noColors(message.getString()).stripLeading();
		return CHANNEL.matcher(text).find() || PUBLIC.matcher(text).find();
	}

	/** @return whether a chat message is a party member's message */
	public static boolean fromParty(String text) {
		return TextColors.noColors(text).stripLeading().startsWith("Party > ");
	}

	/** Runs a chat reader: an error in it is logged once and skipped, so it can never interrupt the game's handling of the message. */
	public static void read(String reader, Runnable action) {
		Guard.run(reader, action);
	}
}
