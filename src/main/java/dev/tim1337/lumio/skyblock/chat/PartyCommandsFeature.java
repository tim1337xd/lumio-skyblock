package dev.tim1337.lumio.skyblock.chat;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.ServerPing;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.PartyList;
import dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue;
import dev.tim1337.lumio.skyblock.dungeon.Dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party commands: party members write "!warp" and the like, and Lumio runs the one party command the player would type for it, or answers with
 * the info asked for (see {@link PartyCommands}). Off by default. Only commands the player switched on, only from party members (and, if the
 * player wants, the guild and private messages), one command per request with a cooldown per person, the leader's commands only while the player
 * leads; every message goes through the same slow queue as the dungeon announcements. Nothing is ever sent without a request.
 */
public class PartyCommandsFeature extends AddonFeature {
	/** Who may use the commands. */
	public enum Allowed {
		PARTY("Party"), FRIENDS("Friends"), WHITELIST("Whitelist");

		final String label;

		Allowed(String label) {
			this.label = label;
		}
	}

	/** How long one person waits between two requests. */
	public enum Cooldown {
		SHORT(2), MEDIUM(5), LONG(10);

		final int seconds;

		Cooldown(int seconds) {
			this.seconds = seconds;
		}
	}

	private static final String LEADER = "Leader commands";
	private static final String INFO = "Info commands";
	private static final String WHO = "Who may ask";
	private static final Pattern FRIEND_ONLINE = Pattern.compile("^Friend > (\\w{1,16}) (?:joined|left)\\.$");
	private static final Pattern NAMES = Pattern.compile("[\\s,;]+");
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final int MOST_FRIENDS = 300;
	private static final int MOST_DOWNTIMES = 8;
	private static final long MILLIS_PER_SECOND = 1_000;
	/** How the server's tick rate is measured: over ten seconds, sampled once a second. */
	private static final int TPS_SAMPLES = 10;
	private static final int TICKS_PER_SECOND = 20;

	private final Map<PartyCommands.Command, Toggle> switches = new EnumMap<>(PartyCommands.Command.class);
	private final TextInput prefix;
	private final Choice<Allowed> allowed;
	private final TextInput whitelist;
	private final Choice<Cooldown> cooldown;
	private final TextInput blocked;
	private final Toggle guildAndMessages;

	/** The world the waiting replies belong to. */
	private net.minecraft.client.multiplayer.ClientLevel queueLevel;
	private final AnnouncementQueue queue = new AnnouncementQueue(System::currentTimeMillis, span -> ThreadLocalRandom.current().nextLong(span + 1),
		AnnouncementQueue.SHARED_GAP);
	private final PartyCommands.Gate gate = new PartyCommands.Gate();
	/** The gate's key of a command answered once per cooldown whoever asks; no player name starts like this. */
	private static final String COMMAND_KEY = "!";
	/** When "ping" was asked while no ping was known, so the answer waits for the one measurement; how long it waits, and how old a ping may be. */
	private static final long NOT_ASKED = Long.MIN_VALUE;
	private static final long PING_WAIT_MILLIS = 10_000L;
	private static final long PING_FRESH_MILLIS = 30_000L;
	private long pingAskedAt = NOT_ASKED;
	private final Set<String> friends = new HashSet<>();
	/** Who asked for downtime this run, and why. */
	private final Map<String, String> downtimes = new LinkedHashMap<>();
	private long requestNumber;
	private final long[] tickSamples = new long[TPS_SAMPLES];
	private final long[] timeSamples = new long[TPS_SAMPLES];
	private int sampleCount;
	private long lastSampleAt;

	public PartyCommandsFeature() {
		super("party_commands", "Party commands", "Answers to party commands like !warp.", false);
		pageGroup("Chat and party");
		iconRgb(SystemColors.TEAL_RGB);
		iconName("bubble");
		summary("!warp, !pt, !ping and more");
		prefix = add(make().text("prefix", Component.literal("Prefix"), "!", false), WHO);
		allowed = add(make().choice("allowed", Component.literal("Allowed from"), Allowed.PARTY, value -> Component.literal(value.label), Choice.Style.SEGMENTED),
			WHO, "Party members only, in any case");
		whitelist = add(make().text("whitelist", Component.literal("Whitelist"), "", false), WHO);
		cooldown = add(make().choice("cooldown", Component.literal("Cooldown per person"), Cooldown.MEDIUM, value -> Component.literal(value.seconds + " s"),
			Choice.Style.SEGMENTED), WHO);
		blocked = add(make().text("blocked", Component.literal("Blocked players"), "", false), WHO);
		guildAndMessages = add(make().toggle("guild_and_messages", Component.literal("Also in guild and messages"), false), WHO,
			"Info only; leader commands: party chat");
		for (PartyCommands.Command command : PartyCommands.Command.values()) {
			String label = command == PartyCommands.Command.FLOOR ? "!f1 – !m7 (queue a floor)" : "!" + String.join(" / !", command.names());
			Toggle toggle = add(make().toggle("command_" + command.name().toLowerCase(Locale.ROOT), Component.literal(label),
				command != PartyCommands.Command.DICE), command.leaderOnly() ? LEADER : INFO);
			switches.put(command, toggle);
		}
		whitelist.visibleWhen(() -> allowed.get() == Allowed.WHITELIST);
		cardSettings(allowed, cooldown);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void phaseChanged(Dungeon.Phase phase) {
				if (phase == Dungeon.Phase.DONE) {
					remindDowntime();
				}
			}

			@Override
			public void left() {
				downtimes.clear();
			}
		});
	}

	/**
	 * Reads a chat message: requests from party chat (and guild and private messages if allowed), and friends coming online from the server's lines.
	 *
	 * @param fromServer whether the server wrote it rather than a player
	 */
	public void onMessage(Component message, boolean fromServer) {
		if (!isEnabled()) {
			return;
		}
		String line = TextColors.noColors(message.getString()).strip();
		if (fromServer) {
			Matcher friend = FRIEND_ONLINE.matcher(line);
			if (friend.matches() && friends.size() < MOST_FRIENDS) {
				friends.add(friend.group(1).toLowerCase(Locale.ROOT));
			}
			return;
		}
		PartyCommands.Request request = PartyCommands.read(line, prefix.get().strip());
		if (request == null || !mayAsk(request)) {
			return;
		}
		long now = System.currentTimeMillis();
		long cooldownMillis = cooldown.get().seconds * MILLIS_PER_SECOND;
		Set<String> blockedNames = names(blocked.get());
		if (PartyCommands.ownNumbers(request.command())) {
			// the ping and the frame rate: once per cooldown each, whoever asks; both asked close together are both answered
			if (!switchedOn(request.command()) || request.source() != PartyCommands.Source.PARTY || blockedNames.contains(request.sender().toLowerCase(Locale.ROOT))
				|| !gate.take(COMMAND_KEY + request.command().mainName(), now, cooldownMillis, Set.of())) {
				return;
			}
		} else if (!gate.take(request.sender(), now, cooldownMillis, blockedNames)) {
			return;
		}
		String command = PartyCommands.answer(request, context, this::switchedOn);
		if (command == null) {
			if (request.command() == PartyCommands.Command.PING && context.ping() < 0) {
				// no ping measured yet (the info bar does not show it): one measurement, and the answer when it is there
				ServerPing.ask();
				pingAskedAt = now;
			}
			return;
		}
		if (request.command() == PartyCommands.Command.DOWNTIME && downtimes.size() < MOST_DOWNTIMES) {
			downtimes.put(request.sender(), request.argument());
		} else if (request.command() == PartyCommands.Command.UNDOWNTIME) {
			downtimes.remove(request.sender());
		}
		queue.add("request " + requestNumber++, command);
	}

	/** @return the prefix requests start with ("!"), for the features that listen to the party's words too */
	public String prefix() {
		return prefix.get().strip();
	}

	private boolean switchedOn(PartyCommands.Command command) {
		Toggle toggle = switches.get(command);
		return toggle != null && toggle.get();
	}

	/** Party members only (and the guild and private messages when allowed), then the player's choice of who. */
	private boolean mayAsk(PartyCommands.Request request) {
		String sender = request.sender().toLowerCase(Locale.ROOT);
		if (request.source() != PartyCommands.Source.PARTY && !guildAndMessages.get()) {
			return false;
		}
		if (request.source() == PartyCommands.Source.DIRECT && !isPartyMember(sender)) {
			return false;
		}
		return switch (allowed.get()) {
			case PARTY -> true;
			case FRIENDS -> friends.contains(sender);
			case WHITELIST -> names(whitelist.get()).contains(sender);
		};
	}

	private static boolean isPartyMember(String lowerName) {
		for (String member : PartyList.INSTANCE.members()) {
			if (member.equalsIgnoreCase(lowerName)) {
				return true;
			}
		}
		return false;
	}

	/** @return the names in a list the player typed, lower case */
	private static Set<String> names(String text) {
		Set<String> names = new HashSet<>();
		for (String name : NAMES.split(text.strip())) {
			if (!name.isEmpty()) {
				names.add(name.toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	/** At the end of the run: who asked for downtime, once in party chat and as a toast for the player. */
	private void remindDowntime() {
		if (downtimes.isEmpty() || !isEnabled()) {
			return;
		}
		StringBuilder who = new StringBuilder();
		for (Map.Entry<String, String> entry : downtimes.entrySet()) {
			if (!who.isEmpty()) {
				who.append(", ");
			}
			who.append(entry.getKey());
			if (!entry.getValue().isEmpty()) {
				who.append(" (").append(entry.getValue()).append(')');
			}
		}
		downtimes.clear();
		Toasts.brief("bubble", "Downtime", who.toString(), 6f);
		queue.add("downtime " + requestNumber++, "pc Downtime: " + who);
	}

	/** Lets the next waiting message out, and samples the server's tick rate. */
	public void tick(Minecraft client) {
		sampleTicks();
		// a reply waiting for one world is not sent into the next
		if (client.level != queueLevel) {
			queueLevel = client.level;
			queue.clear();
		}
		if (!isEnabled() || client.player == null || client.getConnection() == null) {
			if (queue.waiting() > 0) {
				queue.clear();
			}
			return;
		}
		if (pingAskedAt != NOT_ASKED) {
			int ping = context.ping();
			if (ping >= 0) {
				queue.add("request " + requestNumber++, PartyCommands.pingReply(ping));
				pingAskedAt = NOT_ASKED;
			} else if (System.currentTimeMillis() - pingAskedAt > PING_WAIT_MILLIS) {
				// no answer from the server: nothing is said
				pingAskedAt = NOT_ASKED;
			}
		}
		AnnouncementQueue.Waiting next = queue.poll();
		if (next != null) {
			client.getConnection().sendCommand(next.text());
		}
		if (queue.waiting() == 0) {
			queue.forgetEvents();
		}
	}

	private void sampleTicks() {
		long now = System.currentTimeMillis();
		if (now - lastSampleAt < MILLIS_PER_SECOND) {
			return;
		}
		lastSampleAt = now;
		int slot = sampleCount++ % TPS_SAMPLES;
		tickSamples[slot] = ServerTicks.count();
		timeSamples[slot] = now;
	}

	/** @return the server's ticks per second over the last ten seconds, "19.8", or "?" before there are enough */
	private String tps() {
		if (sampleCount < 2) {
			return "?";
		}
		int newest = (sampleCount - 1) % TPS_SAMPLES;
		int oldest = sampleCount < TPS_SAMPLES ? 0 : sampleCount % TPS_SAMPLES;
		long ticks = tickSamples[newest] - tickSamples[oldest];
		long millis = timeSamples[newest] - timeSamples[oldest];
		if (millis <= 0) {
			return "?";
		}
		double rate = Math.min(TICKS_PER_SECOND, ticks * (double) MILLIS_PER_SECOND / millis);
		return String.valueOf(Math.round(rate * 10) / 10.0);
	}

	private final PartyCommands.Context context = new PartyCommands.Context() {
		@Override
		public String self() {
			Minecraft client = Minecraft.getInstance();
			return client.player == null ? "" : client.player.getName().getString();
		}

		@Override
		public boolean selfLeads() {
			return PartyList.INSTANCE.selfLeads();
		}

		@Override
		public String lastLeft() {
			return PartyList.INSTANCE.lastLeft();
		}

		@Override
		public String coords() {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) {
				return "?";
			}
			BlockPos pos = client.player.blockPosition();
			return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
		}

		@Override
		public int ping() {
			// the ping Lumio measures, the info bar's value: the latency in the player list is a fixed number on Hypixel
			return ServerPing.millis(System.currentTimeMillis(), PING_FRESH_MILLIS);
		}

		@Override
		public String tps() {
			return PartyCommandsFeature.this.tps();
		}

		@Override
		public int fps() {
			return Minecraft.getInstance().getFps();
		}

		@Override
		public String time() {
			return LocalTime.now().format(CLOCK);
		}

		@Override
		public int roll(int most) {
			return ThreadLocalRandom.current().nextInt(most) + 1;
		}
	};
}
