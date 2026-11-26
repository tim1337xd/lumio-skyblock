package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.InfoLine;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The dungeon announcements in party chat, asked for by the user: the Mimic, the Prince and bats in Odin's exact words (so other players' mods
 * count them), and the 270 and 300 score and the leap in words of the player's own. These are the only messages Lumio sends by itself: only to the
 * party (/pc), only in a dungeon, each event once per run, right away, or 1 to 2 seconds after the last one when several come at once, never twice
 * the same (see {@link AnnouncementQueue}).
 */
public class DungeonAnnouncementsFeature extends AddonFeature {
	private final Toggle mimic = add(make().toggle("mimic", Component.literal("Send Mimic message"), true), "Fixed texts", "\"Mimic Killed!\", as Odin sends it");
	private final Toggle prince = add(make().toggle("prince", Component.literal("Send Prince message"), true), "Fixed texts", "\"Prince Killed!\"");
	private final Toggle bat = add(make().toggle("bat", Component.literal("Send Bat message"), true), "Fixed texts", "\"Bat Killed!\"");
	private final Toggle score270 = add(make().toggle("score_270", Component.literal("270 score"), true), "Your texts");
	private final TextInput text270 = add(make().text("text_270", Component.literal("270 text"), "", false), "Your texts",
		"{score} {time} {floor}");
	private final InfoLine preview270 = add(make().info("preview_270", Component.literal("Sends"), () -> fill270(271, "6m 42s", "F7")), "Your texts");
	private final Toggle score300 = add(make().toggle("score_300", Component.literal("300 score"), true), "Your texts");
	private final TextInput text300 = add(make().text("text_300", Component.literal("300 text"), "", false), "Your texts",
		"{score} {time} {floor}");
	private final InfoLine preview300 = add(make().info("preview_300", Component.literal("Sends"), () -> fill300(301, "7m 05s", "F7")), "Your texts");
	private final Toggle leap = add(make().toggle("leap", Component.literal("Leap"), true), "Your texts");
	private final TextInput textLeap = add(make().text("text_leap", Component.literal("Leap text"), "", false), "Your texts",
		"{name} {class} {room}");
	private final InfoLine previewLeap = add(make().info("preview_leap", Component.literal("Sends"), () -> fillLeap("Kiwi", "Mage", "Bear room")),
		"Your texts");
	private final InfoLine timing = add(make().info("timing", Component.literal("Timing"), () -> "Sent right away. If several come at once, they go out 1-2 s apart."),
		"Sending");

	private final AnnouncementQueue queue = new AnnouncementQueue(System::currentTimeMillis,
		span -> ThreadLocalRandom.current().nextLong(span + 1), AnnouncementQueue.SHARED_GAP);

	public DungeonAnnouncementsFeature() {
		super("dungeon_announcements", "Announcements", "Mimic, Prince, bats, score, leaps in party chat.",
			true);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("bubble");
		// what an empty text sends, on hover of its name
		text270.setTooltip("Empty is \"" + AnnouncementQueue.DEFAULT_270 + "\".");
		text300.setTooltip("Empty is \"" + AnnouncementQueue.DEFAULT_300 + "\".");
		textLeap.setTooltip("Empty is \"" + AnnouncementQueue.DEFAULT_LEAP + "\".");
		summary("Party chat, once per run");
		text270.visibleWhen(score270::get);
		preview270.visibleWhen(score270::get);
		text300.visibleWhen(score300::get);
		preview300.visibleWhen(score300::get);
		textLeap.visibleWhen(leap::get);
		previewLeap.visibleWhen(leap::get);
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void mimicKilled() {
				if (mimic.get()) {
					queue.add("mimic", AnnouncementQueue.MIMIC);
				}
			}

			@Override
			public void princeKilled() {
				if (prince.get()) {
					queue.add("prince", AnnouncementQueue.PRINCE);
				}
			}

			@Override
			public void batKilled() {
				if (bat.get()) {
					queue.add("bat", AnnouncementQueue.BAT);
				}
			}

			@Override
			public void scoreReached(int mark, int score) {
				String time = Dungeon.stats().time.isEmpty() ? "?" : Dungeon.stats().time;
				String floor = Dungeon.floor() == null ? "?" : Dungeon.floor().name();
				if (mark >= DungeonScore.S_PLUS_SCORE && score300.get()) {
					queue.add("300", fill300(score, time, floor));
				} else if (mark < DungeonScore.S_PLUS_SCORE && score270.get()) {
					queue.add("270", fill270(score, time, floor));
				}
			}

			@Override
			public void leaped(String name) {
				if (leap.get()) {
					DungeonPlayer player = Dungeon.player(name);
					String room = CurrentRoom.name();
					// a leap is announced each time, so its event is the moment, not the run
					queue.add("leap " + System.nanoTime(), fillLeap(name, player == null ? "?" : player.dungeonClass().label(), room.isEmpty() ? "?" : room));
				}
			}

			@Override
			public void left() {
				queue.clear();
			}
		});
	}

	private String fill270(int score, String time, String floor) {
		return AnnouncementQueue.fill(text270.get(), AnnouncementQueue.DEFAULT_270, "score", String.valueOf(score), "time", time, "floor", floor);
	}

	private String fill300(int score, String time, String floor) {
		return AnnouncementQueue.fill(text300.get(), AnnouncementQueue.DEFAULT_300, "score", String.valueOf(score), "time", time, "floor", floor);
	}

	private String fillLeap(String name, String dungeonClass, String room) {
		return AnnouncementQueue.fill(textLeap.get(), AnnouncementQueue.DEFAULT_LEAP, "name", name, "class", dungeonClass, "room", room);
	}

	private void tick(Minecraft client) {
		if (!isEnabled() || !Dungeon.in() || client.player == null) {
			if (queue.waiting() > 0 && !Dungeon.in()) {
				queue.clear();
			}
			return;
		}
		AnnouncementQueue.Waiting next = queue.poll();
		if (next != null) {
			partyChat(client, next.text());
		}
	}

	/** Sends one message to the party, as the player would type /pc. */
	private static void partyChat(Minecraft client, String text) {
		if (client.player != null && client.getConnection() != null && Dungeon.in()) {
			client.getConnection().sendCommand("pc " + text);
		}
	}

	/** @return whether a leap is announced in party chat (the leap overlay's option row shows and switches it) */
	public boolean leapMessage() {
		return leap.get();
	}

	public void leapMessage(boolean on) {
		leap.set(on);
		dev.tim1337.lumio.api.LumioApi.saveSoon();
	}
}
