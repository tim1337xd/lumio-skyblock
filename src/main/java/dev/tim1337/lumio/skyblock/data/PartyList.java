package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The members of the player's party, learned passively from the party messages the server sends anyway: someone joining or leaving, the party
 * being disbanded, and the list the server prints when the player runs /p list themselves. Nothing is ever sent to learn it. Only server messages
 * reach it (see {@link ChatGuard}); plain strings, so it is unit tested with recorded messages.
 */
public final class PartyList {
	/** The party list for the command suggestions. */
	public static final PartyList INSTANCE = new PartyList();
	/** The most members remembered; a Hypixel party has far fewer. */
	private static final int MOST_MEMBERS = 50;
	/** A name with its optional rank in front: "[MVP+] Steve123" or "Steve123". */
	private static final String NAME = "(?:\\[[^\\]]{1,20}\\] )?([A-Za-z0-9_]{1,16})";
	private static final Pattern JOINED = Pattern.compile("^" + NAME + " joined the party\\.$");
	private static final Pattern JOINED_THEIRS = Pattern.compile("^You have joined " + NAME + "'s? party!$");
	private static final Pattern PARTYING_WITH = Pattern.compile("^You'll be partying with: (.+)$");
	private static final Pattern LEFT = Pattern.compile("^" + NAME + " (?:has left the party\\.|has been removed from the party\\.|was removed from your party"
		+ " because they disconnected\\.?)$");
	private static final Pattern KICKED_OFFLINE = Pattern.compile("^Kicked " + NAME + " because they were offline\\.$");
	private static final Pattern TRANSFERRED = Pattern.compile("^The party was transferred to " + NAME + " by " + NAME + "$");
	private static final Pattern DISBANDED = Pattern.compile("^(?:" + NAME + " has disbanded the party!|The party was disbanded.*|You left the party\\.|"
		+ "You have been kicked from the party.*|You are not currently in a party\\.)$");
	/** The list from /p list: its header starts a fresh list, then the leader, moderators and members lines. */
	private static final Pattern LIST_HEADER = Pattern.compile("^Party Members \\(\\d{1,3}\\)$");
	private static final Pattern LIST_LINE = Pattern.compile("^Party (?:Leader|Moderators|Members): (.+)$");
	private static final Pattern ANY_NAME = Pattern.compile(NAME);
	private static final Pattern PROMOTED_LEADER = Pattern.compile("^" + NAME + " has promoted " + NAME + " to Party Leader$");
	private static final Pattern LEADER_LINE = Pattern.compile("^Party Leader: " + NAME);

	private final Set<String> members = new LinkedHashSet<>();
	/** Who leads the party, or "" while not known; the one who left last, for a re-invite. */
	private String leader = "";
	private String lastLeft = "";
	/** Told who left the party, was removed or kicked for being offline. */
	private java.util.function.Consumer<String> onLeft;

	/** Sets who hears of a member leaving the party; one listener, set while the add-on starts. */
	public void onLeft(java.util.function.Consumer<String> listener) {
		onLeft = listener;
	}

	/** Reads a server message, without its colors. */
	public void onMessage(Component message) {
		read(TextColors.noColors(message.getString()).strip());
	}

	/** Reads one plain line of a server message. */
	public void read(String line) {
		if (line.isEmpty() || !line.contains("arty") && !line.startsWith("Kicked ")) {
			return;
		}
		Matcher matcher;
		if ((matcher = JOINED_THEIRS.matcher(line)).matches()) {
			add(matcher.group(1));
			leader = matcher.group(1);
		} else if ((matcher = JOINED.matcher(line)).matches()) {
			add(matcher.group(1));
			// someone joined a party nobody else was known to lead: the player's own
			if (leader.isEmpty()) {
				leader = self();
			}
		} else if ((matcher = PARTYING_WITH.matcher(line)).matches()) {
			addAll(matcher.group(1));
		} else if ((matcher = LEFT.matcher(line)).matches() || (matcher = KICKED_OFFLINE.matcher(line)).matches()) {
			String gone = matcher.group(1);
			members.removeIf(member -> member.equalsIgnoreCase(gone));
			lastLeft = gone;
			if (onLeft != null) {
				onLeft.accept(gone);
			}
		} else if ((matcher = TRANSFERRED.matcher(line)).matches()) {
			add(matcher.group(1));
			add(matcher.group(2));
			leader = matcher.group(1);
		} else if ((matcher = PROMOTED_LEADER.matcher(line)).matches()) {
			leader = matcher.group(2);
		} else if (DISBANDED.matcher(line).matches()) {
			members.clear();
			leader = "";
		} else if (LIST_HEADER.matcher(line).matches()) {
			members.clear();
		} else if ((matcher = LIST_LINE.matcher(line)).matches()) {
			addAll(matcher.group(1));
			Matcher lead = LEADER_LINE.matcher(line);
			if (lead.find()) {
				leader = lead.group(1);
			}
		}
	}

	/** Adds every name of a list like "[VIP] A ● [MVP+] B ●" or "[VIP] A, B". */
	private void addAll(String names) {
		Matcher each = ANY_NAME.matcher(names);
		while (each.find()) {
			add(each.group(1));
		}
	}

	private void add(String name) {
		if (members.size() < MOST_MEMBERS && members.stream().noneMatch(member -> member.equalsIgnoreCase(name))) {
			members.add(name);
		}
	}

	/** @return the members known now, in the order they were learned */
	public List<String> members() {
		return new ArrayList<>(members);
	}

	/** @return who leads the party, or "" while not known */
	public String leader() {
		return leader;
	}

	/** @return the one who left the party last, or "" */
	public String lastLeft() {
		return lastLeft;
	}

	/** @return whether the player leads the party, as far as the server told */
	public boolean selfLeads() {
		return !leader.isEmpty() && leader.equalsIgnoreCase(self());
	}

	/** @return the player's own name, or "" outside a world */
	private static String self() {
		var client = net.minecraft.client.Minecraft.getInstance();
		return client == null || client.player == null ? "" : client.player.getName().getString();
	}

	/** Forgets the party, for a new server. */
	public void clear() {
		members.clear();
		leader = "";
	}
}
