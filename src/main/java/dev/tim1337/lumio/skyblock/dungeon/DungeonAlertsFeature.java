package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.hud.ShowAs;
import dev.tim1337.lumio.skyblock.hud.TitleQueue;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;
import java.util.regex.Matcher;

/**
 * Local alerts in a dungeon run, nothing sent: "Room cleared" when the room the player is in gets its check on the game's map (once per room, the
 * entrance, fairy and blood rooms only when wanted; "All secrets" when it turns green, if wanted), and "Wither Key" / "Blood Key" with who picked it
 * up, from Hypixel's own line (the same line the door colors follow, so both agree). Each alert as a title, a pop-up or a chat line (or several of them), with a sound.
 */
public final class DungeonAlertsFeature extends AddonFeature {
	/** The sound an alert plays: a few of the game's own. */
	public enum Sound {
		PLING("Pling", () -> SoundEvents.NOTE_BLOCK_PLING.value()), BELL("Bell", () -> SoundEvents.NOTE_BLOCK_BELL.value()),
		CHIME("Chime", () -> SoundEvents.NOTE_BLOCK_CHIME.value()), LEVEL_UP("Level up", () -> SoundEvents.PLAYER_LEVELUP),
		ORB("Orb", () -> SoundEvents.EXPERIENCE_ORB_PICKUP);

		final String label;
		final Supplier<SoundEvent> event;

		Sound(String label, Supplier<SoundEvent> event) {
			this.label = label;
			this.event = event;
		}
	}

	/** One alert's own settings. */
	private final class Style {
		final Toggle on;
		final ShowAs look;
		final Toggle sound;
		final Choice<Sound> which;
		final Slider volume;
		final Slider seconds;
		final String oldLookKey;

		Style(String key, String label, String section) {
			on = add(make().toggle(key, Component.literal(label), true), section);
			// the same chips as the Drop alerts: any of them together; the one choice older versions saved is taken over on load
			look = new ShowAs(key + "_show_as", true, false, false);
			add(look.setting(), section);
			oldLookKey = key + "_look";
			sound = add(make().toggle(key + "_sound", Component.literal("Sound"), true), section);
			which = add(make().choice(key + "_sound_kind", Component.literal("Which sound"), Sound.PLING, value -> Component.literal(value.label)), section);
			volume = add(make().slider(key + "_volume", Component.literal("Volume"), 0.7f, 0.05f, 1f, 2).percent(), section);
			seconds = add(make().slider(key + "_seconds", Component.literal("Duration"), 1.5f, 1f, 5f, 1).unit(" s"), section);
			look.setting().visibleWhen(on::get);
			sound.visibleWhen(on::get);
			which.visibleWhen(() -> on.get() && sound.get());
			volume.visibleWhen(() -> on.get() && sound.get());
			// a chat line has no duration: only the title and the pop-up use it
			seconds.visibleWhen(() -> on.get() && (look.chosen().title() || look.chosen().popup()));
		}
	}

	static final String WITHER = "Wither";
	private static final int WITHER_KEY_COLOR = 0xFF8E8E93;
	private static final int TICKS_PER_SECOND = 20;
	private static final int TITLE_FADE_TICKS = 3;
	private static final float PITCH = 1.2f;
	private static final String ROOMS = "Room cleared";
	private static final String KEYS = "Key picked up";

	private final Style roomStyle = new Style("room_cleared", "Room cleared", ROOMS);
	private final Toggle allSecrets = add(make().toggle("all_secrets", Component.literal("All secrets found"), false), ROOMS);
	private final Toggle specialRooms = add(make().toggle("special_rooms", Component.literal("Special rooms too"), false), ROOMS, "Entrance, fairy and blood rooms");
	private final Style keyStyle = new Style("key_picked", "Key picked up", KEYS);
	private final RoomAlerts rooms = new RoomAlerts();

	public DungeonAlertsFeature() {
		super("dungeon_alerts", "Alerts", "Room and key alerts, just for you.",
			false);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("bell");
		summary("Room cleared, keys");
		allSecrets.visibleWhen(roomStyle.on::get);
		specialRooms.visibleWhen(roomStyle.on::get);
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		roomStyle.look.takeOver(json, roomStyle.oldLookKey);
		keyStyle.look.takeOver(json, keyStyle.oldLookKey);
	}

	/** Watches the map and the chat; once, while the add-on starts. */
	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String line) {
				keyLine(line);
			}

			@Override
			public void left() {
				rooms.reset();
			}
		});
	}

	private void tick(Minecraft client) {
		if (!isEnabled() || !roomStyle.on.get() || !Dungeon.inClear() || client.player == null) {
			return;
		}
		DungeonMapFeature map = SkyBlockFeatures.DUNGEON_MAP;
		int room = map.roomTileAt(client.player.getX(), client.player.getZ());
		if (room < 0) {
			return;
		}
		int alerts = rooms.update(room, map.kindOf(room), map.checkOf(room), specialRooms.get(), allSecrets.get());
		String name = map.roomAt(client.player.getX(), client.player.getZ());
		if ((alerts & RoomAlerts.CLEARED) != 0) {
			alert(roomStyle, "Room cleared", name, SystemColors.GREEN);
		}
		if ((alerts & RoomAlerts.ALL_SECRETS) != 0) {
			alert(roomStyle, "All secrets", name, SystemColors.GREEN);
		}
	}

	private void keyLine(String line) {
		if (!isEnabled() || !keyStyle.on.get()) {
			return;
		}
		String[] key = keyOf(line);
		if (key != null) {
			alert(keyStyle, key[0] + " Key", key[1], WITHER.equals(key[0]) ? WITHER_KEY_COLOR : SystemColors.RED);
		}
	}

	/** @return the key a line of the server says was picked up and by whom ("Wither", "Kiwi"; "" when it names nobody), or null */
	static String[] keyOf(String line) {
		Matcher matcher = DungeonParse.KEY_PICKED.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		return matcher.group(2) != null ? new String[] {matcher.group(2), matcher.group(1)} : new String[] {matcher.group(3), ""};
	}

	/** Shows an alert as its style says: "Room cleared · Redstone Crypt", only here. */
	private void alert(Style style, String title, String detail, int color) {
		Minecraft client = Minecraft.getInstance();
		long millis = Math.round(style.seconds.get() * 1000f);
		ShowAs.Chosen shows = style.look.chosen();
		if (shows.title()) {
			int stay = Math.round(style.seconds.get() * TICKS_PER_SECOND);
			TitleQueue.show(Component.literal(title).withColor(color & 0xFFFFFF), Component.literal(detail), TITLE_FADE_TICKS, stay, TITLE_FADE_TICKS, true);
		}
		if (shows.popup()) {
			DungeonAlert.toast(title, detail, color, millis);
		}
		if (shows.chat()) {
			LumioChat.info(Component.literal(detail.isEmpty() ? title : title + " · " + detail).withColor(color & 0xFFFFFF));
		}
		if (style.sound.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(style.which.get().event.get(), PITCH, style.volume.get()));
		}
	}
}
