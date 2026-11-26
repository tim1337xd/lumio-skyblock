package dev.tim1337.lumio.skyblock.fishing;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.CommandSender;
import dev.tim1337.lumio.skyblock.data.PartyList;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.dungeon.DungeonAlert;
import dev.tim1337.lumio.skyblock.fishing.SeaCreatures.Creature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fishing alerts: a title, a sound and a chat line when the player catches a sea creature of the alert list (the rare ones at first), and one
 * party message about it while in a party; and alerts for the rare sea creatures of others, from catches shared in chat and from the name
 * tags of creatures that appear nearby. It reads the server's catch lines, players' chat lines and the name tags the client has loaded; the
 * one thing it sends is the party message of the player's own catch, through the shared send queue.
 */
public class FishingAlertsFeature extends AddonFeature {
	/** A sound of an alert: a few of the game's own. */
	public enum Sound {
		LEVEL_UP("Level up", () -> SoundEvents.PLAYER_LEVELUP), PLING("Pling", () -> SoundEvents.NOTE_BLOCK_PLING.value()),
		BELL("Bell", () -> SoundEvents.NOTE_BLOCK_BELL.value()), CHIME("Chime", () -> SoundEvents.NOTE_BLOCK_CHIME.value()),
		ORB("Orb", () -> SoundEvents.EXPERIENCE_ORB_PICKUP);

		private final String label;
		private final Supplier<SoundEvent> event;

		Sound(String label, Supplier<SoundEvent> event) {
			this.label = label;
			this.event = event;
		}

		/** @return the sound to play */
		SoundEvent event() {
			return event.get();
		}
	}

	/** How an alert about someone else's creature shows. */
	public enum ShowAs {
		TITLE("Title"), BANNER("Top banner"), CHAT("Chat line only");

		private final String label;

		ShowAs(String label) {
			this.label = label;
		}
	}

	private static final String LIST = "Alert list";
	private static final String ALERTS = "Alerts";
	private static final String PARTY = "Party";
	private static final String OTHERS = "From others";
	private static final String ON_SCREEN = "From others: on screen";
	private static final String KEY_PREFIX = "alert_";
	/** A double hook is told in the line before the catch; it counts for a catch this soon after. */
	private static final long DOUBLE_HOOK_MILLIS = 3_000;
	private static final int TITLE_FADE_TICKS = 4;
	private static final int TITLE_STAY_TICKS = 40;
	private static final int OTHERS_STAY_TICKS = 25;
	private static final long BANNER_MILLIS = 2_500;
	private static final float PITCH = 1f;
	private static final float OTHERS_PITCH = 1.3f;
	/** The name tags nearby are read this often, in ticks. */
	private static final int SCAN_TICKS = 10;
	private static final int OWN_COLOR = SystemColors.YELLOW;
	private static final int OTHERS_COLOR = SystemColors.BLUE;

	private final Map<String, Toggle> list = new LinkedHashMap<>();
	private final Toggle title;
	private final Toggle sound;
	private final Choice<Sound> which;
	private final Slider volume;
	private final Toggle share;
	private final Toggle includeCoords;
	private final TextInput messageText;
	private final Toggle fromChat;
	private final Map<FishingAlerts.Chat, Toggle> chats = new EnumMap<>(FishingAlerts.Chat.class);
	private final Toggle nearby;
	private final Slider within;
	private final Toggle highlight;
	private final ColorChoice highlightColor;
	private final Choice<ShowAs> showAs;
	private final Toggle othersSound;
	private final Choice<Sound> othersWhich;
	private final Slider othersVolume;

	private final FishingAlerts.Seen seen = new FishingAlerts.Seen();
	private final FishingAlerts.Queue queue = new FishingAlerts.Queue();
	private final CommandSender sender = new CommandSender();
	/** The listed creatures nearby that are someone else's, by their entity: the ones the highlight outlines. */
	private final Map<Integer, Creature> marked = new HashMap<>();
	private long doubleHookAt;
	private ClientLevel level;
	private int ticks;
	private int catches;

	public FishingAlertsFeature() {
		super("fishing_alerts", "Fishing alerts", "Alerts for rare sea creatures.",
			false);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("bell");
		summary("Rare sea creatures, yours and others'");
		title = add(make().toggle("title", Component.literal("Title"), true), ALERTS);
		sound = add(make().toggle("sound", Component.literal("Sound"), true), ALERTS);
		which = add(make().choice("sound_which", Component.literal("Which sound"), Sound.LEVEL_UP, value -> Component.literal(value.label), Choice.Style.DROPDOWN), ALERTS);
		which.under(sound);
		volume = add(make().slider("sound_volume", Component.literal("Volume"), 1f, 0.05f, 1f, 2).percent(), ALERTS);
		volume.under(sound);
		share = add(make().toggle("share", Component.literal("Share to party chat"), true), PARTY, "One message per catch, in a party only");
		includeCoords = add(make().toggle("share_coords", Component.literal("Include my coordinates"), true), PARTY);
		includeCoords.under(share);
		messageText = add(make().text("share_text", Component.literal("Message text"), FishingAlerts.DEFAULT_MESSAGE, false), PARTY,
			"{creature} {coords} {doublehook}");
		messageText.under(share);
		fromChat = add(make().toggle("from_chat", Component.literal("Seen in chat"), true), OTHERS, "A rare catch someone shares in chat");
		chat(FishingAlerts.Chat.PARTY, "Party", true);
		chat(FishingAlerts.Chat.COOP, "Co-op", true);
		chat(FishingAlerts.Chat.GUILD, "Guild", false);
		chat(FishingAlerts.Chat.PUBLIC, "Public", false);
		nearby = add(make().toggle("nearby", Component.literal("Spawned nearby"), true), OTHERS, "Listed creatures near you, not yours");
		within = add(make().slider("nearby_within", Component.literal("Within"), 60f, 10f, 120f, 0).unit(" blocks"), OTHERS);
		within.under(nearby);
		highlight = add(make().toggle("highlight", Component.literal("Highlight it"), false), OTHERS, "Only while seen; never through walls");
		highlight.under(nearby);
		highlightColor = add(make().color("highlight_color", Component.literal("Highlight color"), SystemColors.BLUE_RGB), OTHERS);
		highlightColor.under(highlight);
		showAs = add(make().choice("others_show", Component.literal("Show as"), ShowAs.BANNER, value -> Component.literal(value.label), Choice.Style.DROPDOWN), ON_SCREEN);
		othersSound = add(make().toggle("others_sound", Component.literal("Sound"), true), ON_SCREEN);
		othersWhich = add(make().choice("others_sound_which", Component.literal("Which sound"), Sound.CHIME, value -> Component.literal(value.label),
			Choice.Style.DROPDOWN), ON_SCREEN);
		othersWhich.under(othersSound);
		othersVolume = add(make().slider("others_sound_volume", Component.literal("Volume"), 0.6f, 0.05f, 1f, 2).percent(), ON_SCREEN);
		othersVolume.under(othersSound);
		showAs.visibleWhen(() -> fromChat.get() || nearby.get());
		othersSound.visibleWhen(() -> fromChat.get() || nearby.get());
		add(make().action("list_rare", Component.literal("Rare only"), Component.literal("Set"), false, () -> pick(SeaCreatures::rare)), LIST);
		add(make().action("list_all", Component.literal("All"), Component.literal("Set"), false, () -> pick(creature -> true)), LIST);
		add(make().action("list_none", Component.literal("None"), Component.literal("Set"), false, () -> pick(creature -> false)), LIST);
		for (Creature creature : SeaCreatures.all()) {
			list.put(creature.name(), add(make().toggle(KEY_PREFIX + creature.key(), Component.literal(creature.name()), SeaCreatures.rare(creature)),
				LIST + ": " + creature.place().label()));
		}
	}

	private void chat(FishingAlerts.Chat chat, String label, boolean on) {
		Toggle toggle = add(make().toggle("chat_" + chat.name().toLowerCase(java.util.Locale.ROOT), Component.literal(label), on), OTHERS);
		toggle.under(fromChat);
		chats.put(chat, toggle);
	}

	private void pick(java.util.function.Predicate<Creature> wanted) {
		for (Creature creature : SeaCreatures.all()) {
			list.get(creature.name()).set(wanted.test(creature));
		}
		LumioApi.saveSoon();
	}

	/** @return whether a creature is on the player's alert list */
	public boolean listed(Creature creature) {
		Toggle toggle = list.get(creature.name());
		return toggle != null && toggle.get();
	}

	private boolean active() {
		return isEnabled() && SkyBlock.allowed();
	}

	/** A line of the server: a double hook, or a sea creature surfacing. */
	public void onServerLine(Component message) {
		if (!active()) {
			return;
		}
		String text = TextColors.noColors(message.getString()).strip();
		long now = System.currentTimeMillis();
		if (text.contains(SeaCreatures.DOUBLE_HOOK)) {
			doubleHookAt = now;
			return;
		}
		Creature creature = SeaCreatures.caughtIn(text);
		if (creature == null) {
			return;
		}
		boolean doubled = now - doubleHookAt < DOUBLE_HOOK_MILLIS;
		doubleHookAt = 0;
		// its name tag nearby is this catch, on the list or not
		seen.ownCatch(creature, now);
		if (listed(creature)) {
			caught(creature, doubled);
		}
	}

	private void caught(Creature creature, boolean doubled) {
		Minecraft client = Minecraft.getInstance();
		String detail = doubled ? "Double Hook!" : "";
		if (title.get()) {
			// through the one queue of Lumio's titles: it waits a moment behind another one instead of wiping it
			dev.tim1337.lumio.skyblock.hud.TitleQueue.show(Component.literal(creature.name()).withColor(OWN_COLOR & 0xFFFFFF), Component.literal(detail),
				TITLE_FADE_TICKS, TITLE_STAY_TICKS, TITLE_FADE_TICKS, false);
		}
		if (sound.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(which.get().event.get(), PITCH, volume.get()));
		}
		LumioChat.info(Component.literal(creature.name() + " caught!" + (doubled ? " Double Hook!" : "")).withColor(OWN_COLOR & 0xFFFFFF));
		String message = partyMessage(creature, doubled, share.get(), PartyList.INSTANCE.members().size() > 1, client.player == null ? null : client.player.blockPosition());
		if (message != null) {
			// one party message per catch, like typing it; never without a party, never to another chat
			sender.send("pc " + message);
			catches++;
		}
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.SKYBLOCK, "Fishing alerts: {} caught{}, party message {}", creature.name(), doubled ? " (double hook)" : "",
				message == null ? "not sent" : "queued");
		}
	}

	/**
	 * @param inParty whether the player is in a party with someone
	 * @param at      where the player stands, or null when not known
	 * @return the party message of a catch, or null when none goes out: the switch is off, or the player is in no party
	 */
	String partyMessage(Creature creature, boolean doubled, boolean shareOn, boolean inParty, BlockPos at) {
		if (!shareOn || !inParty) {
			return null;
		}
		String coords = includeCoords.get() && at != null ? FishingAlerts.coords(at.getX(), at.getY(), at.getZ()) : "";
		return FishingAlerts.message(messageText.get(), creature.name(), coords, doubled);
	}

	/** A line a player wrote: a rare catch shared in a chat the player chose. */
	public void onPlayerLine(Component message) {
		if (!active() || !fromChat.get()) {
			return;
		}
		FishingAlerts.Share shared = FishingAlerts.share(TextColors.noColors(message.getString()).strip(), this::listed);
		if (shared == null || !chats.get(shared.chat()).get()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && shared.player().equalsIgnoreCase(client.player.getGameProfile().name())) {
			// the player's own share coming back
			return;
		}
		if (seen.chat(shared.player(), shared.creature(), System.currentTimeMillis())) {
			queue.add(new FishingAlerts.Alert(shared.player() + ": " + shared.creature().name(), shared.doubleHook() ? "Double Hook!" : "", false));
		}
	}

	/** Every tick: the party message waiting goes out, the next alert shows, and the name tags nearby are read a few times a second. */
	public void tick(Minecraft client) {
		if (client.level != level) {
			// nothing of one world counts in the next, and a message waiting for one is not sent into the other
			level = client.level;
			seen.clear();
			queue.clear();
			marked.clear();
			sender.clear();
		}
		if (!active() || client.player == null || client.getConnection() == null) {
			sender.clear();
			marked.clear();
			return;
		}
		sender.poll(command -> client.getConnection().sendCommand(command));
		FishingAlerts.Alert next = queue.poll(System.currentTimeMillis());
		if (next != null) {
			showOthers(client, next);
		}
		if (++ticks % SCAN_TICKS == 0) {
			scan(client);
		}
	}

	/** Reads the name tags of the entities loaded nearby: a listed sea creature that is not the player's own catch is told of once, when the player sees it. */
	private void scan(Minecraft client) {
		marked.keySet().removeIf(id -> client.level.getEntity(id) == null);
		if (!nearby.get()) {
			marked.clear();
			return;
		}
		double reach = within.get();
		long now = System.currentTimeMillis();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == client.player || !entity.hasCustomName() || marked.containsKey(entity.getId())) {
				continue;
			}
			double dx = entity.getX() - client.player.getX();
			double dy = entity.getY() - client.player.getY();
			double dz = entity.getZ() - client.player.getZ();
			if (!FishingAlerts.within(dx, dy, dz, reach)) {
				continue;
			}
			Creature creature = FishingAlerts.inTag(TextColors.noColors(entity.getCustomName().getString()).strip(), this::listed);
			// only what the player can see: drawn by the game at this distance, in view and with nothing solid between; one behind a wall or
			// outside the view is told of when it comes into sight
			if (creature != null && seen.sighted(entity.getId(), creature, seesNow(client, entity), now)) {
				marked.put(entity.getId(), creature);
				queue.add(new FishingAlerts.Alert(creature.name() + " nearby", Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + " blocks", false));
			}
		}
	}

	/** @return whether the player sees an entity now: drawn by the game from here, in view and in line of sight */
	private static boolean seesNow(Minecraft client, Entity entity) {
		net.minecraft.world.phys.Vec3 camera = client.player.getEyePosition();
		return entity.shouldRender(camera.x, camera.y, camera.z) && Sight.visible(client, entity);
	}

	private void showOthers(Minecraft client, FishingAlerts.Alert alert) {
		String line = alert.detail().isEmpty() ? alert.title() : alert.title() + " · " + alert.detail();
		switch (showAs.get()) {
			case TITLE -> {
				// smaller than the player's own catch: the game's subtitle line, under an empty title
				dev.tim1337.lumio.skyblock.hud.TitleQueue.show(Component.empty(), Component.literal(line).withColor(OTHERS_COLOR & 0xFFFFFF), TITLE_FADE_TICKS,
					OTHERS_STAY_TICKS, TITLE_FADE_TICKS, false);
			}
			case BANNER -> DungeonAlert.toast(alert.title(), alert.detail(), OTHERS_COLOR, BANNER_MILLIS);
			case CHAT -> {
			}
		}
		LumioChat.info(Component.literal(line).withColor(OTHERS_COLOR & 0xFFFFFF));
		if (othersSound.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(othersWhich.get().event.get(), OTHERS_PITCH, othersVolume.get()));
		}
	}

	/** @return whether an entity is a listed sea creature of someone else nearby that the player chose to have outlined (the game's glow, only while seen) */
	public boolean glows(Entity entity) {
		return highlight.get() && !marked.isEmpty() && isEnabled() && marked.containsKey(entity.getId());
	}

	/** @return the outline's color as 0xRRGGBB */
	public int color() {
		return highlightColor.argb() & 0xFFFFFF;
	}

	/** @return how many party messages were queued, for the tests */
	int catchesShared() {
		return catches;
	}
}
