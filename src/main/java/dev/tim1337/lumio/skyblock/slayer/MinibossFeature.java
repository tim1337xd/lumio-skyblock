package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The highlights of the slayer page, for the player's own quest (or the carried player's): its minibosses, with a short alert and a sound when one spawns, its boss,
 * and the boss's drops, each with a soft outline only while it can be seen (never through walls). New entities are checked when they load and for a few seconds after (their name tag arrives a moment later),
 * never every frame; the outlined ones are checked for sight a few times a second.
 */
public class MinibossFeature extends AddonFeature {
	/** The game sounds the alert can play. */
	public enum Sound {
		PLING("Pling"),
		BELL("Bell"),
		CHIME("Chime"),
		ORB("Experience"),
		ANVIL("Anvil");

		private final String title;

		Sound(String title) {
			this.title = title;
		}

		public Component displayName() {
			return Component.literal(title);
		}

		SoundEvent event() {
			return switch (this) {
				case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
				case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
				case CHIME -> SoundEvents.AMETHYST_BLOCK_CHIME;
				case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
				case ANVIL -> SoundEvents.ANVIL_LAND;
			};
		}
	}

	/** A strong orange, the outline's default color. */
	private static final int STRONG_ORANGE = 0xFF7A00;
	/** How long after an entity loads its name tag is still checked, in ticks: the server sends the name a moment after the entity. */
	private static final int NAME_WAIT_TICKS = 60;
	/** Ticks between the name checks of new entities, and between the sight checks of the outlined minibosses. */
	private static final int NAME_CHECK_TICKS = 2;
	private static final int SIGHT_CHECK_TICKS = 5;
	/** How far from its name tag the miniboss itself stands, in blocks. */
	private static final double BODY_RANGE = 3.0;
	/** How far from the miniboss a "Spawned by" tag can stand, in blocks. */
	private static final double OWNER_TAG_RANGE = 3.0;
	/** How long the alert stays, in seconds. */
	private static final float ALERT_SECONDS = 4f;
	/** The most minibosses outlined and entities remembered as alerted; a fight has a few. */
	private static final int MOST_TRACKED = 32;
	private static final int MOST_ALERTED = 256;
	/** No entity id. */
	private static final int NONE = Integer.MIN_VALUE;

	private final Toggle highlight = add(make().toggle("highlight", Component.literal("Minibosses"), true), "Highlight",
		"A soft outline while you can see them");
	private final Toggle bossHighlight = add(make().toggle("boss_highlight", Component.literal("Your boss"), false), "Highlight",
		"Also the boss you carry");
	private final Toggle drops = add(make().toggle("drops", Component.literal("Drops"), true), "Highlight",
		"What your boss dropped glows in its rarity color");
	private final Toggle dropDebug = add(make().toggle("drop_debug", Component.literal("Log every dropped item"), false), "Highlight",
		"For finding out why a drop has no highlight: a line per item in the log");
	private final ColorChoice color = add(make().color("color", Component.literal("Color"), STRONG_ORANGE), "Highlight");
	private final Toggle alert = add(make().toggle("alert", Component.literal("Miniboss alert"), true), "Alert",
		"A short note at the top when one spawns");
	private final Choice<Sound> sound = add(make().choice("sound", Component.literal("Sound"), Sound.PLING, Sound::displayName,
		Choice.Style.DROPDOWN), "Alert");
	private final Slider volume = add(make().slider("volume", Component.literal("Volume"), 0.7f, 0.05f, 1f, 2).percent(), "Alert");
	private final Toggle bossAlert = add(make().toggle("boss_alert", Component.literal("Also for the slayer boss"), false), "Alert");

	{
		sound.under(alert);
		volume.under(alert);
		bossAlert.under(alert);
		Action preview = add(make().action("preview", Component.literal("Hear the sound"), Component.literal("Play"), false, this::playSound),
			"Alert");
		preview.under(alert);
	}

	/** New entities by id, with the tick they loaded, whose name tags are still checked. */
	private final Map<Integer, Integer> pending = new HashMap<>();
	private final Map<Integer, Entity> pendingEntities = new HashMap<>();
	/** The name each new entity had when last read, so an unchanged name is not read again. */
	private final Map<Integer, Object> namesSeen = new HashMap<>();
	/** The watched minibosses (the mob itself) by id with their name tag's entity, and the ones in sight now. */
	private final Map<Integer, Entity> tracked = new HashMap<>();
	private final Map<Integer, Entity> trackedTags = new HashMap<>();
	private final Set<Integer> inSight = new HashSet<>();
	/** Name tags that alerted already: one alert per spawn, never repeated. */
	private final Set<Integer> alerted = new HashSet<>();
	private boolean bossBefore;
	/** The boss's mob while the player sees it, for its outline. */
	private int bossInSight = NONE;
	private final BossDropGlow dropGlow = new BossDropGlow(this);
	private Object level;
	private int tick;

	public MinibossFeature() {
		super("minibosses", "Highlight", "Outlines for your slayer quest's minibosses, its boss and what the boss drops, while you can see them, and an alert when a miniboss spawns.", false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("crown");
		summary("Minibosses, your boss and its drops");
	}

	public void register() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			// only name tag carriers can name a miniboss; the check of their name waits for the tick
			if (isEnabled() && questOrCarry() && (entity instanceof ArmorStand || entity instanceof Display.TextDisplay || isMob(entity))
				&& pending.size() < MOST_ALERTED) {
				pending.put(entity.getId(), tick);
				pendingEntities.put(entity.getId(), entity);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		dropGlow.register();
	}

	private void tick(Minecraft client) {
		tick++;
		if (client.level != level) {
			level = client.level;
			forget();
			alerted.clear();
		}
		if (!isEnabled() || !SkyBlock.allowed() || client.player == null || client.level == null) {
			forget();
			return;
		}
		if (tick % NAME_CHECK_TICKS == 0) {
			checkNew(client);
		}
		if (tick % SIGHT_CHECK_TICKS == 0) {
			checkSight(client);
			checkBoss(client);
		}
		bossTick();
	}

	/** Reads the name tags of the entities that loaded in the last few seconds; one that names a watched miniboss alerts once. */
	private void checkNew(Minecraft client) {
		if (pending.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<Integer, Integer>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			Entity entity = pendingEntities.get(entry.getKey());
			boolean old = tick - entry.getValue() > NAME_WAIT_TICKS;
			if (entity == null || entity.isRemoved() || old) {
				pendingEntities.remove(entry.getKey());
				namesSeen.remove(entry.getKey());
				it.remove();
				continue;
			}
			// only a name that changed is read again
			Object raw = entity instanceof Display.TextDisplay display ? display.getText() : entity.getCustomName();
			if (raw == null || raw == namesSeen.get(entry.getKey())) {
				continue;
			}
			namesSeen.put(entry.getKey(), raw);
			String name = nameOf(entity);
			if (name == null) {
				continue;
			}
			String miniboss = Minibosses.named(name);
			if (miniboss == null) {
				continue;
			}
			// named: decided now, whatever the outcome
			pendingEntities.remove(entry.getKey());
			namesSeen.remove(entry.getKey());
			it.remove();
			found(client, entity, miniboss);
		}
	}

	/** A miniboss's name tag appeared: if it belongs to a watched quest, it alerts and gets outlined. */
	private void found(Minecraft client, Entity tag, String miniboss) {
		List<Entity> near = client.level.getEntities(tag, tag.getBoundingBox().inflate(BODY_RANGE, BODY_RANGE + 1, BODY_RANGE), entity -> true);
		String spawnedBy = null;
		// the name tag is on the mob itself, or on a stand above it: then the nearest mob is the miniboss
		boolean onMob = isMob(tag);
		LivingEntity body = onMob ? (LivingEntity) tag : null;
		double bodyDistance = Double.MAX_VALUE;
		for (Entity entity : near) {
			String line = nameOf(entity);
			if (line != null && SlayerOwners.isOwnerLine(line) && entity.distanceTo(tag) <= OWNER_TAG_RANGE) {
				spawnedBy = SlayerOwners.ownerOf(line);
			}
			if (!onMob && isMob(entity) && entity.distanceTo(tag) < bodyDistance) {
				bodyDistance = entity.distanceTo(tag);
				body = (LivingEntity) entity;
			}
		}
		List<Minibosses.Player> players = new ArrayList<>();
		for (Player player : client.level.players()) {
			players.add(new Minibosses.Player(player.getGameProfile().name(), player.getX(), player.getY(), player.getZ()));
		}
		Entity at = body != null ? body : tag;
		String owner = Minibosses.ownerOf(spawnedBy, at.getX(), at.getY(), at.getZ(), players);
		String me = client.player.getGameProfile().name();
		String carried = SkyBlockFeatures.SLAYER_PHASE == null ? "" : SkyBlockFeatures.SLAYER_PHASE.carriedName();
		boolean watched = Minibosses.watched(owner, me, SkyBlockArea.slayerQuest(), carried);
		Debug.log("Miniboss {} spawned, belongs to {} ({})", miniboss, owner, watched ? "watched" : "ignored");
		if (!watched) {
			return;
		}
		if (alerted.add(tag.getId())) {
			if (alerted.size() > MOST_ALERTED) {
				alerted.clear();
			}
			alert("Miniboss: " + miniboss, owner.equalsIgnoreCase(me) ? "Your slayer quest" : "Carrying " + owner);
		}
		if (body != null && tracked.size() < MOST_TRACKED) {
			tracked.put(body.getId(), body);
			trackedTags.put(body.getId(), tag);
		}
	}

	/** Keeps the outline only on minibosses in sight: one ray from the eyes, a few times a second; gone ones are dropped. */
	private void checkSight(Minecraft client) {
		inSight.clear();
		if (tracked.isEmpty()) {
			return;
		}
		tracked.values().removeIf(entity -> entity.isRemoved() || !entity.isAlive());
		trackedTags.keySet().retainAll(tracked.keySet());
		if (!SlayerBosses.showsHere()) {
			return;
		}
		for (Entity entity : tracked.values()) {
			if (client.player.hasLineOfSight(entity)) {
				inSight.add(entity.getId());
			}
		}
	}

	/** Keeps the outline on the watched boss only while the player sees it. */
	private void checkBoss(Minecraft client) {
		Entity body = bossHighlight.get() ? SlayerBosses.watchedBody() : null;
		// a boss that turned invisible pauses its outline
		bossInSight = body != null && !body.isRemoved() && !body.isInvisible() && Sight.visible(client, body) ? body.getId() : NONE;
	}

	/** The optional alert when the player's own boss spawns. */
	private void bossTick() {
		SlayerBoss own = SlayerBosses.own();
		boolean bossNow = own != null && !SlayerBosses.ownCocooned();
		if (bossNow && !bossBefore && alert.get() && bossAlert.get()) {
			alert("Slayer boss: " + own.name(), "Your slayer quest");
		}
		bossBefore = bossNow;
	}

	private void alert(String title, String text) {
		if (!alert.get()) {
			return;
		}
		Toasts.brief("crown", title, text, ALERT_SECONDS);
		playSound();
	}

	private void playSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get().event(), 1f, volume.get()));
	}

	/** @return whether an entity is a mob that can be a miniboss: alive, not a stand or a player */
	private static boolean isMob(Entity entity) {
		return entity instanceof LivingEntity && !(entity instanceof ArmorStand) && !(entity instanceof Player);
	}

	/** @return whether a miniboss can be watched at all: the own quest runs, or carry mode is on */
	private static boolean questOrCarry() {
		return SkyBlockArea.slayerQuest() || SkyBlockFeatures.SLAYER_PHASE != null && !SkyBlockFeatures.SLAYER_PHASE.carriedName().isEmpty();
	}

	/** @return the name tag text of an entity without color codes, or null when it has none */
	private static String nameOf(Entity entity) {
		Component name = entity instanceof Display.TextDisplay display ? display.getText() : entity.hasCustomName() ? entity.getCustomName() : null;
		if (name == null) {
			return null;
		}
		String text = SkyBlockArea.plainLine(name.getString());
		return text.isBlank() ? null : text;
	}

	private void forget() {
		pending.clear();
		pendingEntities.clear();
		namesSeen.clear();
		tracked.clear();
		trackedTags.clear();
		inSight.clear();
		bossBefore = false;
		bossInSight = NONE;
	}

	/** @return whether the slayer bosses must be read for the boss alert */
	public boolean wantsBosses() {
		return isEnabled() && (alert.get() && bossAlert.get() || bossHighlight.get() || drops.get());
	}

	/** @return whether the boss's drops are wanted */
	boolean dropsOn() {
		return isEnabled() && drops.get();
	}

	/** @return whether each dropped item writes why it is or is not highlighted to the log */
	boolean dropDebug() {
		return dropDebug.get();
	}

	/** Draws what stands above the drops the player sees. */
	public void drawDropLabels(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		dropGlow.draw(graphics);
	}

	/** Remembers a drop the server announced in the chat, so its item is known when it appears. */
	public void onMessage(Component message) {
		dropGlow.onMessage(message);
	}

	/** @return whether an entity is an outlined miniboss right now; called per entity and frame, so it is a set lookup after cheap checks */
	public boolean glows(Entity entity) {
		int id = entity.getId();
		return !inSight.isEmpty() && highlight.get() && inSight.contains(id) || id == bossInSight || dropGlow.glows(id);
	}

	/** @return the outline color of an outlined entity as 0xRRGGBB: its rarity's for a drop, otherwise the one set */
	public int color(Entity entity) {
		int drop = dropGlow.color(entity.getId());
		return drop >= 0 ? drop : color.get() & 0xFFFFFF;
	}
}
