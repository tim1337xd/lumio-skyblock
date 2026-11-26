package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.filter.HiddenEntities;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Livid (F5, M5): the real Livid is the one the color of the wool at the arena's known spot names; nothing is shown before that wool shows one of
 * the nine colors. Its entity is kept from tick to tick and only looked for again when it is gone, so the outline never blinks; while the player
 * is blind (the start of the fight) nothing shows. The fakes can be left undrawn (visual only). Before the wool names one, there is no real Livid:
 * nothing is outlined or hidden (no default). It came out
 * of the old "Boss helpers" module, whose Livid switch and color it takes over once ({@link #takeOver}).
 */
public class DungeonLividFeature extends AddonFeature {
	private static final BlockPos LIVID_WOOL = new BlockPos(5, 108, 43);
	private static final DyeColor[] WOOLS = {DyeColor.WHITE, DyeColor.MAGENTA, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GRAY, DyeColor.PURPLE,
		DyeColor.BLUE, DyeColor.GREEN, DyeColor.RED};
	/** How often the wool and the fakes are looked at, in client ticks; the real Livid's entity is kept in between. */
	private static final int CHECK_TICKS = 5;
	private static final int FLOOR = 5;
	/** A fake's own name tag (an armor stand over it) stands this close to it, in blocks. */
	private static final double TAG_REACH = 3.0;
	/** The setting of the label that is gone; dropped from configs from before. */
	static final String SHOW_NAME_KEY = "show_name";
	private static final int MOST_HIDDEN = 32;
	private static final String TRACER = "Tracer";
	/** The tracer: how opaque it is, how far in front of the eye it starts (so it leaves from the crosshair), and the sizes its width is given for. */
	private static final float TRACER_OPACITY = 0.9f;
	private static final float TRACER_START_BLOCKS = 1f;
	private static final float REFERENCE_LINES = 1080f;
	private static final float REFERENCE_GUI_SCALE = 2f;

	/** Where the tracer's color comes from. */
	public enum TracerColor {
		LIVID("Livid's color"), OWN("Own color");

		final String label;

		TracerColor(String label) {
			this.label = label;
		}
	}

	private final ColorChoice lividColor = add(make().color("livid_color", Component.literal("Livid color"), 0xFF6EC7), "General");
	private final Toggle hideOthers = add(make().toggle("hide_others", Component.literal("Hide other Livids"), true), "General",
		"Only the drawing changes");
	private final Toggle tracer = add(make().toggle("tracer", Component.literal("Tracer to the real Livid"), false), TRACER,
		"Hidden behind walls, like the highlight");
	private final Choice<TracerColor> tracerColorFrom = add(make().choice("tracer_color_from", Component.literal("Color"), TracerColor.LIVID,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), TRACER);
	private final ColorChoice tracerColor = add(make().color("tracer_color", Component.literal("Own color"), 0xFF6EC7), TRACER);
	private final Slider tracerWidth = add(make().slider("tracer_width", Component.literal("Line width"), 2f, 1f, 6f, 1), TRACER);

	private final LividState state = new LividState();
	private Player real;
	private boolean blind;
	private int ticks;
	private final int[] hidden = new int[MOST_HIDDEN];
	private boolean hiding;
	/** The tracer of this frame: where it starts and ends relative to the camera, its color and its width. */
	private final float[] tracerLine = new float[6];
	private int tracerRgb;
	private float tracerPixelAngle;
	private float tracerPixels;
	private final SubmitNodeCollector.CustomGeometryRenderer drawTracer = this::drawTracer;
	/** Whether this module's own settings were loaded; until then the old module's may stand in for them. */
	private boolean ownLoaded;
	private JsonObject legacy;
	private boolean legacyOn;

	public DungeonLividFeature() {
		super("dungeon_livid", "Livid", "F5: the real Livid outlined.", false);
		iconRgb(SystemColors.PINK_RGB);
		iconName("person");
		summary("The real Livid");
		badge("F5");
		tracerColorFrom.visibleWhen(tracer::get);
		tracerColor.visibleWhen(() -> tracer.get() && tracerColorFrom.get() == TracerColor.OWN);
		tracerWidth.visibleWhen(tracer::get);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void left() {
				reset();
			}
		});
	}

	private void reset() {
		state.reset();
		real = null;
		if (hiding) {
			HiddenEntities.clear();
			hiding = false;
		}
	}

	@Override
	public void load(JsonObject json) {
		// the label and its "Show name" are gone: their keys go with the next save
		json.remove(SHOW_NAME_KEY);
		super.load(json);
		ownLoaded = json.has("enabled");
		if (!ownLoaded) {
			applyLegacy();
		}
	}

	/** The old "Boss helpers" settings: on while it was on with its Livid switch, and its Livid color. Only where this module has none of its own. */
	public void takeOver(JsonObject old, boolean oldOn) {
		legacy = old;
		legacyOn = oldOn;
		if (!ownLoaded) {
			applyLegacy();
		}
	}

	private void applyLegacy() {
		if (legacy == null) {
			return;
		}
		boolean lividSwitch = !(legacy.get("livid") instanceof JsonPrimitive livid && livid.isBoolean()) || legacy.get("livid").getAsBoolean();
		setEnabled(legacyOn && lividSwitch);
		JsonObject colors = new JsonObject();
		if (legacy.has("livid_color")) {
			colors.add("livid_color", legacy.get("livid_color"));
			super.load(colors);
		}
		legacy = null;
	}

	/** @return whether the player is in the fight of F5 or M5 (the boss room, the run not over) */
	private static boolean inFight() {
		return Dungeon.inBoss() && Dungeon.phase() != Dungeon.Phase.DONE && Dungeon.floor() != null && Dungeon.floor().number() == FLOOR;
	}

	/** Every tick in the fight: the real Livid's entity kept; the wool and the fakes a few times a second. */
	public void tick(Minecraft client) {
		if (!isEnabled() || client.level == null || client.player == null || !inFight()) {
			if (state.known() || hiding) {
				reset();
			}
			return;
		}
		blind = client.player.hasEffect(MobEffects.BLINDNESS);
		boolean check = ++ticks % CHECK_TICKS == 0;
		if (check) {
			state.wool(woolAt(client));
		}
		if (!state.known()) {
			return;
		}
		// the kept entity stays the real Livid through every data update; only when it is gone (or a respawn made a new one) is it looked for
		if (real == null || !state.keeps(!real.isRemoved(), real.getName().getString())) {
			state.lost();
			real = null;
			String wanted = state.entityName();
			for (Player player : client.level.players()) {
				if (player.getName().getString().equals(wanted)) {
					real = player;
					state.found(player.getId());
					break;
				}
			}
		}
		if (check) {
			updateHidden(client);
		}
	}

	private static DyeColor woolAt(Minecraft client) {
		Block wool = client.level.getBlockState(LIVID_WOOL).getBlock();
		for (DyeColor color : WOOLS) {
			if (wool == Blocks.WOOL.pick(color)) {
				return color;
			}
		}
		return null;
	}

	/** The fakes (and their name tags) not drawn, while the setting is on; the real Livid never. */
	private void updateHidden(Minecraft client) {
		if (!hideOthers.get()) {
			if (hiding) {
				HiddenEntities.clear();
				hiding = false;
			}
			return;
		}
		int count = 0;
		for (Player player : client.level.players()) {
			if (count < MOST_HIDDEN && state.fake(player.getId(), player.getName().getString())) {
				hidden[count++] = player.getId();
				for (Entity tag : client.level.getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(TAG_REACH))) {
					if (count < MOST_HIDDEN && tag.hasCustomName() && tag.getCustomName().getString().contains("Livid")) {
						hidden[count++] = tag.getId();
					}
				}
			}
		}
		HiddenEntities.set(java.util.Arrays.copyOf(hidden, count));
		hiding = true;
	}

	/**
	 * The world pass: the tracer from the crosshair to the real Livid, depth tested like the highlight (nothing through walls), following him every
	 * frame. Only while he is known and alive in the fight; it points at the real one whatever is hidden.
	 */
	public void collect(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		Player livid = real;
		if (client.player == null || !state.tracerActive(isEnabled() && tracer.get(), inFight(), livid != null && livid.isAlive() && !livid.isRemoved(), blind)) {
			return;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = client.gameRenderer.mainCamera().position();
		Vec3 start = client.player.getViewVector(partial).scale(TRACER_START_BLOCKS);
		Vec3 at = livid.getPosition(partial);
		tracerLine[0] = (float) start.x;
		tracerLine[1] = (float) start.y;
		tracerLine[2] = (float) start.z;
		tracerLine[3] = (float) (at.x - camera.x);
		tracerLine[4] = (float) (at.y + livid.getBbHeight() / 2.0 - camera.y);
		tracerLine[5] = (float) (at.z - camera.z);
		tracerRgb = state.tracerRgb(tracerColor.get(), tracerColorFrom.get() == TracerColor.LIVID);
		tracerPixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		tracerPixels = tracerWidth.get() * client.getWindow().getGuiScale() / REFERENCE_GUI_SCALE;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), drawTracer);
	}

	private void drawTracer(PoseStack.Pose pose, VertexConsumer consumer) {
		WaypointLook.line(pose, consumer, tracerLine[0], tracerLine[1], tracerLine[2], tracerLine[3], tracerLine[4], tracerLine[5], tracerRgb, TRACER_OPACITY,
			tracerPixelAngle, tracerPixels);
	}

	/** @return whether the entity is outlined now: the real Livid, once known and not while blind */
	public boolean glows(Entity entity) {
		return real != null && entity == real && !blind && isEnabled();
	}

	public int color(Entity entity) {
		return lividColor.get() & 0xFFFFFF;
	}

}
