package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.chat.PartyCommands;
import dev.tim1337.lumio.skyblock.data.PartyList;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto Requeue: at the end of a dungeon run it sends the one command that starts the next ("/instancerequeue"), after a delay the player can
 * cancel. Off by default. The one command Lumio sends by itself here, approved as its own exception: exactly one per finished run, through the
 * shared send queue, only while every condition holds ({@link AutoRequeue}), never repeated or in a loop, and nothing else is sent or clicked.
 * Party members can ask for downtime ("!dt"), take it back ("!undt"), stop it ("!stop") or ask the leader to requeue now ("!rq"); what Lumio
 * answers to those shows only to the player, unless the Party commands module replies for them.
 */
public class DungeonAutoRequeueFeature extends AddonPanel {
	/** The command of a requeue, as the player would type it, without its slash. */
	static final String COMMAND = "instancerequeue";
	private static final String EVENT = "requeue";
	private static final String CONDITIONS = "When";
	private static final String SHOWS = "Shows";
	private static final int WIDTH = 150;
	private static final int PAD = PanelKit.PAD;
	private static final long MILLIS_PER_SECOND = 1000;
	private static final long SAMPLE_MILLIS = 4200;
	/** A teammate who lost the connection in the run, as the dungeon says it. */
	private static final Pattern DISCONNECTED = Pattern.compile("^☠ (\\w{1,16}) disconnected from the Dungeon.*$");

	private final Slider delay = add(make().slider("delay", Component.literal("Delay"), AutoRequeue.Settings.DEFAULT.delaySeconds(), 1f, 30f, 0).unit(" s"), CONDITIONS,
		"You can cancel until then");
	private final Slider minParty = add(make().slider("min_party", Component.literal("Party size at least"), AutoRequeue.Settings.DEFAULT.minParty(), 2f, 5f, 0),
		CONDITIONS);
	private final Toggle notLeader = add(make().toggle("not_leader", Component.literal("Also when I'm not the leader"), false), CONDITIONS);
	private final Toggle solo = add(make().toggle("solo", Component.literal("Also solo"), false), CONDITIONS);
	private final Toggle stopOnLeave = add(make().toggle("stop_on_leave", Component.literal("Stop when someone leaves"), true), CONDITIONS,
		"A leave, kick or lost connection");
	private final Toggle afterFailed = add(make().toggle("after_failed", Component.literal("Also after a failed run"), false), CONDITIONS);
	private final Toggle partyWords = add(make().toggle("party_words", Component.literal("Listen to the party"), true), CONDITIONS,
		"dt, undt, stop and rq after the prefix");
	private final Toggle chatLine = add(make().toggle("chat_line", Component.literal("Chat line with Cancel"), true), SHOWS);
	private final Toggle sayWhy = add(make().toggle("say_why", Component.literal("Say why it does not requeue"), true), SHOWS);

	private final AutoRequeue requeue = new AutoRequeue();
	private final AnnouncementQueue queue = new AnnouncementQueue(System::currentTimeMillis, most -> ThreadLocalRandom.current().nextLong(most + 1),
		AnnouncementQueue.SHARED_GAP);
	/** How the command goes out, and what the game says about the party and the run; fields so both can be stood in for. */
	private Consumer<String> send = DungeonAutoRequeueFeature::sendCommand;
	private Supplier<AutoRequeue.Facts> facts = DungeonAutoRequeueFeature::factsNow;
	private KeyMapping cancelKey;
	/** Whether the command going out now is Lumio's own, so it is not taken for the player's. */
	private boolean sendingOwn;
	private AutoRequeue.State shownState = AutoRequeue.State.IDLE;
	private AutoRequeue.Reason shownReason = AutoRequeue.Reason.NONE;
	private long shownSeconds = -1;
	private String shownText = "";

	public DungeonAutoRequeueFeature() {
		super("dungeon_auto_requeue", "Auto Requeue", "One requeue after a run, cancellable.", false,
			Anchor.CENTER, false, 0f, 60f);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("reset");
		summary("One requeue per finished run");
		offerNoBackground();
	}

	/** Starts with the add-on: the cancel key and command, the run's events and the party's. */
	public void register() {
		cancelKey = DungeonKeys.register("dungeon_requeue_cancel");
		addKey(cancelKey, KeyPlace.WORLD);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void runStarted(DungeonFloor floor) {
				requeue.runStarted();
				queue.clear();
			}

			@Override
			public void runEnded() {
				if (isEnabled()) {
					requeue.ended(System.currentTimeMillis());
				}
			}

			@Override
			public void serverLine(String line) {
				Matcher gone = DISCONNECTED.matcher(line);
				if (gone.matches()) {
					requeue.memberLeft(gone.group(1));
				}
			}

			@Override
			public void left() {
				// another world: whatever waited is not sent into it
				requeue.left();
				queue.clear();
			}
		});
		PartyList.INSTANCE.onLeft(requeue::memberLeft);
		ClientSendMessageEvents.COMMAND.register(command -> {
			if (!sendingOwn && command.strip().toLowerCase(java.util.Locale.ROOT).startsWith(COMMAND)) {
				requeue.selfRequeued();
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(ClientCommands.literal("lumio")
			.then(ClientCommands.literal("requeue").then(ClientCommands.literal("cancel").executes(context -> {
				cancel();
				return 1;
			})))));
	}

	/** The player cancels the requeue of this run: the key, or Cancel in the chat line. */
	private void cancel() {
		boolean counting = requeue.state() == AutoRequeue.State.COUNTING || requeue.state() == AutoRequeue.State.SETTLING;
		requeue.cancel();
		if (counting) {
			LumioChat.info(Component.literal("Requeue cancelled."));
		}
	}

	/**
	 * A line of party chat: the words the party can steer the requeue with. Only party chat counts, and "rq" never from the player's own line.
	 *
	 * @param line the plain chat line
	 */
	public void onPartyLine(String line) {
		if (!isEnabled() || !partyWords.get()) {
			return;
		}
		PartyCommands.Said said = PartyCommands.said(line, SkyBlockFeatures.PARTY_COMMANDS.prefix());
		if (said == null || said.source() != PartyCommands.Source.PARTY) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		boolean own = client.player != null && said.sender().equalsIgnoreCase(client.player.getName().getString());
		switch (said.word()) {
			case "dt", "downtime" -> {
				requeue.downtime(said.sender(), said.argument());
				LumioChat.info(Component.literal(said.sender() + " asked for downtime" + (said.argument().isEmpty() ? "" : " (" + said.argument() + ")")
					+ ": no requeue after this run."));
			}
			case "undt" -> {
				requeue.undowntime(said.sender());
				LumioChat.info(Component.literal(said.sender() + " took the downtime back."));
			}
			case "stop" -> {
				requeue.stop();
				LumioChat.info(Component.literal(said.sender() + " stopped the requeue of this run."));
			}
			case "rq", "requeue" -> {
				if (!own && requeue.requestNow(facts.get())) {
					LumioChat.info(Component.literal(said.sender() + " asked to requeue: requeuing now."));
					queue.add(EVENT, COMMAND);
				}
			}
			default -> {
			}
		}
	}

	private void tick(Minecraft client) {
		if (cancelKey != null && DungeonKeys.presses(cancelKey) > 0) {
			cancel();
		}
		if (!isEnabled() || client.player == null || client.getConnection() == null) {
			// off: nothing counts down and nothing waits to go out
			if (queue.waiting() > 0) {
				queue.clear();
			}
			requeue.cancel();
			return;
		}
		AutoRequeue.State state = requeue.state();
		if (state == AutoRequeue.State.IDLE || state == AutoRequeue.State.SENT) {
			// no run just ended, or its one command is out: only what still waits in the queue goes on
			tell(0);
			sendWaiting();
			return;
		}
		long now = System.currentTimeMillis();
		AutoRequeue.Settings settings = new AutoRequeue.Settings(Math.round(delay.get()), notLeader.get(), solo.get(), Math.round(minParty.get()),
			stopOnLeave.get(), afterFailed.get());
		if (requeue.tick(now, settings, facts.get())) {
			queue.add(EVENT, COMMAND);
		}
		tell(now);
		sendWaiting();
	}

	/** Lets the requeue out of the send queue once the gap since the last message Lumio sent has passed. */
	private void sendWaiting() {
		AnnouncementQueue.Waiting next = queue.poll();
		if (next != null) {
			sendingOwn = true;
			try {
				send.accept(next.text());
			} finally {
				sendingOwn = false;
			}
		}
	}

	/** Says once when the countdown starts (with Cancel), and once why no requeue goes out. */
	private void tell(long now) {
		AutoRequeue.State state = requeue.state();
		AutoRequeue.Reason reason = requeue.reason();
		if (state == shownState && reason == shownReason) {
			return;
		}
		shownState = state;
		shownReason = reason;
		if (state == AutoRequeue.State.COUNTING && chatLine.get()) {
			long seconds = (requeue.millisLeft(now) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
			LumioChat.line(Component.literal("Requeue in " + seconds + " s · ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("[Cancel]").withStyle(style -> style.withColor(ChatFormatting.RED)
					.withClickEvent(new ClickEvent.RunCommand("/lumio requeue cancel"))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("No requeue after this run"))))));
		} else if (state == AutoRequeue.State.BLOCKED && sayWhy.get() && reason != AutoRequeue.Reason.CANCELLED) {
			LumioChat.info(Component.literal("No requeue: " + requeue.reasonText() + "."));
		}
	}

	private static void sendCommand(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() != null) {
			client.getConnection().sendCommand(command);
		}
	}

	/** @return what the game says now: whether the boss fell, and the party as far as the server told of it */
	private static AutoRequeue.Facts factsNow() {
		Minecraft client = Minecraft.getInstance();
		String self = client.player == null ? "" : client.player.getName().getString();
		int size = 0;
		boolean selfListed = false;
		for (String member : PartyList.INSTANCE.members()) {
			size++;
			selfListed |= member.equalsIgnoreCase(self);
		}
		if (size > 0 && !selfListed) {
			size++;
		}
		// a party the chat never told of (joined before the game started) still shows in the run's players; who leads it is not known then
		return new AutoRequeue.Facts(Dungeon.phase() == Dungeon.Phase.DONE, Math.max(1, Math.max(size, Dungeon.party().size())), PartyList.INSTANCE.selfLeads());
	}

	// ------------------------------------------------------------------------------------------------------ the panel

	@Override
	public boolean shown(Minecraft client) {
		return isEnabled() && requeue.state() == AutoRequeue.State.COUNTING || simulated();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return 2 * PAD + 12;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, panelHeight(), PanelKit.RADIUS);
		boolean counting = requeue.state() == AutoRequeue.State.COUNTING;
		long left = counting ? requeue.millisLeft(System.currentTimeMillis()) : SAMPLE_MILLIS;
		long seconds = (left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
		if (seconds != shownSeconds) {
			shownSeconds = seconds;
			shownText = seconds + " s";
		}
		ui.textAtScaled("Requeue in", PAD, PAD + 2, TextScale.BODY, HudTheme.secondary());
		TabularText.drawRight(ui, shownText, WIDTH - PAD, PAD + 2, TextScale.BODY, true, SystemColors.GREEN);
	}
}
