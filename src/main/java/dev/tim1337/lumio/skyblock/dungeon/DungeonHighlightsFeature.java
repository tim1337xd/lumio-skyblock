package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import dev.tim1337.lumio.api.experimental.draw.WorldLabel;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Outlines in the Catacombs, only on what the player sees (in view and in line of sight, checked a few times a second), never through walls:
 * starred mobs (the mob under a nametag with ✯), bats, and the mini bosses (Shadow Assassin, the Adventurers, the Angry Archaeologist, King Midas).
 * The mobs are looked for a few times a second and only while in a dungeon.
 */
public class DungeonHighlightsFeature extends AddonFeature {
	/** How far the glow reaches. */
	public enum GlowSize {
		SMALL("Small", 4f), MEDIUM("Medium", 8f), LARGE("Large", 14f);

		final String label;
		final float pixels;

		GlowSize(String label, float pixels) {
			this.label = label;
			this.pixels = pixels;
		}
	}

	/** How the starred mobs are marked. */
	public enum Look {
		OUTLINE("Outline"), GLOW("Glow");

		final String label;

		Look(String label) {
			this.label = label;
		}
	}

	private static final int SCAN_TICKS = 4;
	private static final String STAR = "✯";
	/** How far under its nametag a starred mob's body may be, in blocks. */
	private static final double TAG_BELOW_BLOCKS = 3.5;
	private static final double TAG_ACROSS_BLOCKS = 1.0;
	private static final List<String> MINIBOSSES = List.of("Shadow Assassin", "Lost Adventurer", "Frozen Adventurer", "Angry Archaeologist", "King Midas");
	private static final int MOST_MARKED = 256;
	/** The GUI scale the widths are chosen at. */
	private static final float REFERENCE_GUI_SCALE = 2f;
	/** How far past the room's walls a mob still counts, in blocks: its doorways. */
	private static final double DOORWAY_BLOCKS = 4.0;
	/** How far above a teammate's head its name stands, in blocks. */
	private static final double NAME_LIFT_BLOCKS = 0.6;

	private final Toggle starred = add(make().toggle("starred", Component.literal("Starred mobs"), true), "Mobs");
	private final Choice<Look> starredLook = add(make().choice("starred_look", Component.literal("Style"), Look.OUTLINE,
		value -> Component.literal(value.label)), "Look");
	private final Slider lineWidth = add(make().slider("line_width", Component.literal("Line width"), 2f, 1f, 5f, 0), "Look",
		"In pixels; also for door outlines");
	private final Choice<GlowSize> glowSize = add(make().choice("glow_size", Component.literal("Glow size"), GlowSize.MEDIUM,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "Look");
	private final ColorChoice starredColor = add(make().color("starred_color", Component.literal("Starred color"), 0xFFD60A), "Mobs");
	private final Toggle bats = add(make().toggle("bats", Component.literal("Bats"), false), "Mobs");
	private final ColorChoice batColor = add(make().color("bat_color", Component.literal("Bat color"), 0xFF6EC7), "Mobs");
	private final Toggle minibosses = add(make().toggle("minibosses", Component.literal("Mini bosses"), false), "Mobs");
	private final ColorChoice minibossColor = add(make().color("miniboss_color", Component.literal("Mini boss color"), SystemColors.ORANGE_RGB), "Mobs");
	private final Toggle hideUnstarred = add(make().toggle("hide_unstarred_tags", Component.literal("Hide plain nametags"), false), "Mobs",
		"Mobs without ✯; the rest keep theirs");
	private final Toggle teammates = add(make().toggle("teammates", Component.literal("Teammates"), false), "Teammates", "In class color, only while seen");
	private final Toggle teammateNames = add(make().toggle("teammate_names", Component.literal("Names in class color"), false), "Teammates");
	private final Toggle hideGameNames = add(make().toggle("hide_game_names", Component.literal("Hide the game's name tags"), false), "Teammates",
		"Only your teammates' tags, in a dungeon");

	/** The marked entities in sight, by id, with their colors (-1 for none); the teammates in sight, for their names. */
	private final Int2IntOpenHashMap marked = new Int2IntOpenHashMap();
	/** Every teammate near, seen or not, for hiding the game's name tags (which the game draws through walls). */
	private final IntOpenHashSet teammateIds = new IntOpenHashSet();
	/** The name tags of mobs without ✯ in the room, hidden while the player chose so. */
	private final IntOpenHashSet hiddenTags = new IntOpenHashSet();
	private final List<Player> teammatesSeen = new ArrayList<>();
	private final List<DungeonPlayer> teammateNamesSeen = new ArrayList<>();
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	private final IntOpenHashSet bodies = new IntOpenHashSet();
	private int ticks;

	public DungeonHighlightsFeature() {
		super("dungeon_highlights", "Highlights", "Mobs and teammates outlined while seen.",
			true);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("eye");
		summary("Only what you can see");
		marked.defaultReturnValue(-1);
		starredColor.visibleWhen(starred::get);
		batColor.visibleWhen(bats::get);
		minibossColor.visibleWhen(minibosses::get);
	}

	/** Looks for the mobs a few times a second, in a dungeon only; the teammates also in the boss room. */
	public void tick(Minecraft client) {
		if (++ticks % SCAN_TICKS != 0) {
			return;
		}
		marked.clear();
		teammatesSeen.clear();
		teammateIds.clear();
		hiddenTags.clear();
		teammateNamesSeen.clear();
		if (!isEnabled() || !Dungeon.in() || client.level == null || client.player == null) {
			return;
		}
		boolean clearing = Dungeon.inClear();
		bodies.clear();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (marked.size() >= MOST_MARKED) {
				break;
			}
			if (entity instanceof Player player && player != client.player) {
				DungeonPlayer teammate = Dungeon.player(player.getName().getString());
				if (teammate != null) {
					teammateIds.add(player.getId());
					if (teammate.dungeonClass() != null && !player.isInvisible() && (teammates.get() || teammateNames.get()) && sees(client, player)) {
						if (teammates.get()) {
							marked.put(player.getId(), teammate.dungeonClass().rgb());
						}
						teammatesSeen.add(player);
						teammateNamesSeen.add(teammate);
					}
					continue;
				}
			}
			// only the room the player is in and its doorways: a mob anywhere else is left before any outline or name work
			if (!clearing || !CurrentRoom.inRoom(entity.getX(), entity.getZ(), DOORWAY_BLOCKS)) {
				continue;
			}
			if (hideUnstarred.get() && entity instanceof ArmorStand tag && tag.hasCustomName()
				&& MobNameTags.hides(TextColors.noColors(tag.getCustomName().getString()))) {
				hiddenTags.add(tag.getId());
				continue;
			}
			if (starred.get() && entity instanceof ArmorStand stand && stand.hasCustomName() && stand.getCustomName().getString().contains(STAR)) {
				LivingEntity body = bodyUnder(client, stand);
				if (body != null && bodies.add(body.getId()) && sees(client, body)) {
					marked.put(body.getId(), starredColor.get() & 0xFFFFFF);
				}
			} else if (bats.get() && entity instanceof Bat bat && !bat.isInvisible() && sees(client, bat)) {
				marked.put(bat.getId(), batColor.get() & 0xFFFFFF);
			} else if (minibosses.get() && entity instanceof Player player && player != client.player && miniboss(player.getName().getString())
				&& sees(client, player)) {
				marked.put(player.getId(), minibossColor.get() & 0xFFFFFF);
			}
		}
	}

	private static boolean sees(Minecraft client, Entity entity) {
		return dev.tim1337.lumio.api.experimental.util.Sight.visible(client, entity);
	}

	static boolean miniboss(String name) {
		for (String boss : MINIBOSSES) {
			if (name.contains(boss)) {
				return true;
			}
		}
		return false;
	}

	/** @return the mob a nametag belongs to: the nearest living one right under it, not a stand and not a player */
	private static LivingEntity bodyUnder(Minecraft client, ArmorStand tag) {
		AABB below = new AABB(tag.getX() - TAG_ACROSS_BLOCKS, tag.getY() - TAG_BELOW_BLOCKS, tag.getZ() - TAG_ACROSS_BLOCKS, tag.getX() + TAG_ACROSS_BLOCKS,
			tag.getY(), tag.getZ() + TAG_ACROSS_BLOCKS);
		LivingEntity nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (Entity entity : client.level.getEntities(tag, below)) {
			if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && !(entity instanceof Player player && player == client.player)
				&& living.isAlive()) {
				double distanceSq = entity.distanceToSqr(tag);
				if (distanceSq < nearestSq) {
					nearestSq = distanceSq;
					nearest = living;
				}
			}
		}
		return nearest;
	}

	/** @return whether the entity is outlined now; asked for every entity in every frame */
	public boolean glows(Entity entity) {
		return !marked.isEmpty() && marked.containsKey(entity.getId());
	}

	/** @return the outline color, or -1 */
	public int color(Entity entity) {
		return marked.get(entity.getId());
	}

	/** Teammates' names above their heads in their class color, only for those in sight. */
	public void drawNames(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (teammatesSeen.isEmpty() || !teammateNames.get() || !isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float partial = tracker.getGameTimeDeltaPartialTick(false);
		for (int i = 0; i < teammatesSeen.size(); i++) {
			Player player = teammatesSeen.get(i);
			if (player.isRemoved()) {
				continue;
			}
			double x = player.xOld + (player.getX() - player.xOld) * partial;
			double y = player.yOld + (player.getY() - player.yOld) * partial + player.getBbHeight() + NAME_LIFT_BLOCKS;
			double z = player.zOld + (player.getZ() - player.zOld) * partial;
			if (WorldLabel.place(client, x, y, z, place) < WorldLabel.NEAR) {
				continue;
			}
			DungeonPlayer teammate = teammateNamesSeen.get(i);
			String name = teammate.name();
			graphics.text(client.font, name, Math.round(place[WorldLabel.X] - client.font.width(name) / 2f), Math.round(place[WorldLabel.Y]) - 4,
				0xFF000000 | teammate.dungeonClass().rgb(), true);
		}
	}

	/** @return whether the name tag of an armor stand is hidden: a mob's without ✯ in the room, when the player chose so; asked per stand per frame */
	public boolean hidesTag(Entity stand) {
		return !hiddenTags.isEmpty() && hiddenTags.contains(stand.getId());
	}

	/** @return whether the game's name tag above a player is hidden: a teammate's, in a dungeon, when the player chose so; asked per player per frame */
	public boolean hidesGameName(Entity entity) {
		return !teammateIds.isEmpty() && teammateIds.contains(entity.getId()) && hideGameNames.get();
	}

	/** @return the outline width in screen pixels at GUI scale 2, scaled to look the same at every scale */
	public float lineWidth() {
		return lineWidth.get() * Minecraft.getInstance().getWindow().getGuiScale() / REFERENCE_GUI_SCALE;
	}

	/** @return the glow's reach in pixels, scaled like the line */
	public float glowPixels() {
		return glowSize.get().pixels * Minecraft.getInstance().getWindow().getGuiScale() / REFERENCE_GUI_SCALE;
	}

	public Look starredLook() {
		return starredLook.get();
	}
}
