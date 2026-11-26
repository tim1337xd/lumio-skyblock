package dev.tim1337.lumio.skyblock.fishing;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.ServerParticles;
import dev.tim1337.lumio.api.experimental.waypoint.AddonWaypoints;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;


/**
 * Hotspot helper (Fishing). The highlight draws each hotspot the game shows as a calm circle flat on its water or lava (a thin bright ring, a
 * soft fill that breathes slowly), sized to the area its own particles cover, depth tested like every highlight (never through blocks); its
 * particles can be hidden under it. The radar finder reads the trail of flame particles the game sends after the player's own right-click with the
 * Hotspot Radar: the trail is a curve that comes to rest on the hotspot, so a waypoint goes where it ends ({@link RadarTrail}), from the third or
 * fourth flame on, refined with each further one, in its own color and with or without its label, and removed once the player is there
 * ({@link RadarArrival}: inside the hotspot or within 8 blocks of its middle, or the player's own bobber landed in it). One right-click is one prediction; nothing is clicked,
 * moved or sent. It only extends the trail the game shows after the player's own radar use and reveals nothing hidden.
 */
public class HotspotHelperFeature extends AddonFeature {
	/** The radar's item. */
	static final String RADAR_ID = "HOTSPOT_RADAR";
	/** The waypoint's key and label. */
	private static final String WAYPOINT_KEY = SkyBlockAddon.ID + ":hotspot";
	private static final String WAYPOINT_NAME = "Hotspot";
	/** A trail with no new flame for this long has ended (the recorded gaps are below 0.4 s); the first flame may take this long too. */
	private static final long TRAIL_GAP_MILLIS = 1_500L;
	/** The waypoint goes after this long at the latest. */
	private static final long WAYPOINT_MILLIS = 4 * 60_000L;
	private static final long SCAN_TICKS = 20L;
	private static final float RING_PIXELS = 3.5f;
	/** The ring is its color brightened this far toward white, so it stands out on water of any color, also in blue. */
	private static final float RING_LIGHT = 0.45f;
	/** The circle: segments, the glow outside the ring (blocks), how far above the surface (against flicker), and the period and depth of the fill's slow breath. */
	private static final int SEGMENTS = 64;
	private static final float GLOW_WIDTH = 0.6f;
	private static final float LIFT = 0.03f;
	private static final long BREATH_MILLIS = 3_200L;
	private static final float PULSE = 0.15f;
	private static final float RING_OPACITY = 0.95f;
	private static final float REFERENCE_LINES = 1080f;

	private final Toggle highlight = add(make().toggle("highlight", Component.literal("Hotspot highlight"), true), "Highlight");
	private final ColorChoice color = add(make().color("color", Component.literal("Color"), SystemColors.BLUE_RGB).followsMenu(), "Highlight");
	private final Slider fill = add(make().slider("fill", Component.literal("Fill strength"), 35f, 0f, 100f, 0).unit(" %"), "Highlight");
	private final Toggle hideParticles = add(make().toggle("hide_particles", Component.literal("Hide the hotspot particles"), false), "Highlight");
	private final Toggle radar = add(make().toggle("radar", Component.literal("Radar finder"), true), "Radar");
	/** Kept under the keys of the former beam color and waypoint label, so their stored values stay. */
	private final ColorChoice waypointColor = add(make().color("beam_color", Component.literal("Waypoint color"), SystemColors.ORANGE_RGB).followsMenu(), "Radar");
	private final Toggle label = add(make().toggle("label", Component.literal("Show label"), true), "Radar");

	private final Hotspots hotspots = new Hotspots();
	private final SubmitNodeCollector.CustomGeometryRenderer drawAll = this::drawAll;
	/** The trail being read, null between uses; when its last flame (or the use) came. */
	private RadarTrail trail;
	private long trailLast;
	/** The waypoint shown, as given to it: whether there is one, where, and since when. */
	private boolean shown;
	private int shownX;
	private int shownY;
	private int shownZ;
	private long shownSince;
	/** Where the trail ends, unrounded, and the color and label the waypoint was given. */
	private double shownMiddleX;
	private double shownMiddleZ;
	private int shownRgb;
	private boolean shownLabel;
	private ClientLevel lastLevel;
	private long ticks;
	private Vec3 camera = Vec3.ZERO;
	private float pixelAngle;

	public HotspotHelperFeature() {
		super("hotspot_helper", "Hotspot helper", "Hotspots as circles, and the radar's guess.", false);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("fish");
		summary("Hotspots as circles, the radar's guess");
		color.under(highlight);
		fill.under(highlight);
		hideParticles.under(highlight);
		waypointColor.under(radar);
		label.under(radar);
		radar.setTooltip("Right-click the Hotspot Radar: a waypoint marks where its trail leads, sharper with each flame, and goes once you are there.");
	}

	public void register() {
		ServerParticles.listen(this::particle);
		ServerParticles.hide(this::hides);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::draw);
	}

	private boolean active() {
		return isEnabled() && SkyBlock.allowed();
	}

	/** The player used an item: a right-click with the radar starts one prediction, in place of the last. Only looked at: the use goes on as it is. */
	public void onUse(Player player, ItemStack held) {
		if (!active() || !radar.get() || !RADAR_ID.equals(ItemGains.idOf(held))) {
			return;
		}
		Vec3 eyes = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		trail = new RadarTrail(eyes.x, eyes.y, eyes.z, look.x, look.y, look.z);
		trailLast = System.currentTimeMillis();
		removeWaypoint();
	}

	private void particle(ParticleOptions particle, double x, double y, double z, int count) {
		if (!active()) {
			return;
		}
		hotspots.particle(particle.getType(), x, y, z, System.currentTimeMillis());
		if (trail == null || particle.getType() != ParticleTypes.FLAME || !trail.add(x, y, z)) {
			return;
		}
		trailLast = System.currentTimeMillis();
		RadarTrail.Estimate estimate = trail.estimate();
		if (estimate != null) {
			showWaypoint(estimate);
		}
	}

	/** Shows the waypoint where the trail ends, or moves it there; only when that is another block. */
	private void showWaypoint(RadarTrail.Estimate estimate) {
		int x = (int) Math.floor(estimate.x());
		int y = (int) Math.floor(estimate.y());
		int z = (int) Math.floor(estimate.z());
		shownMiddleX = estimate.x();
		shownMiddleZ = estimate.z();
		if (!shown) {
			shownSince = System.currentTimeMillis();
		} else if (x == shownX && y == shownY && z == shownZ) {
			return;
		}
		shown = true;
		shownX = x;
		shownY = y;
		shownZ = z;
		sendWaypoint();
	}

	/** Gives the waypoint its place, color and label as they are now. */
	private void sendWaypoint() {
		shownRgb = waypointColor.get() & 0xFFFFFF;
		shownLabel = label.get();
		AddonWaypoints.show(WAYPOINT_KEY, WAYPOINT_NAME, shownX, shownY, shownZ, shownRgb, shownLabel);
	}

	private void removeWaypoint() {
		if (shown) {
			shown = false;
			AddonWaypoints.remove(WAYPOINT_KEY);
		}
	}

	private boolean hides(ParticleOptions particle, double x, double y, double z, int count) {
		return hideParticles.get() && highlight.get() && active() && hotspots.marks(particle.getType(), x, y, z);
	}

	private void tick(Minecraft client) {
		if (client.level != lastLevel) {
			lastLevel = client.level;
			hotspots.clear();
			trail = null;
			// Lumio lets the add-on's waypoints go on a world change itself
			shown = false;
		}
		if (!active() || !radar.get() || client.level == null || client.player == null) {
			trail = null;
			removeWaypoint();
		}
		if (!active() || client.level == null || client.player == null) {
			return;
		}
		if (++ticks % SCAN_TICKS == 0) {
			hotspots.scan(client.level);
		}
		long now = System.currentTimeMillis();
		if (trail != null && now - trailLast > TRAIL_GAP_MILLIS) {
			trail = null;
		}
		if (shown && (now - shownSince > WAYPOINT_MILLIS || arrived(client.player))) {
			removeWaypoint();
		} else if (shown && ((waypointColor.get() & 0xFFFFFF) != shownRgb || label.get() != shownLabel)) {
			sendWaypoint();
		}
	}

	/** @return whether the player is at the waypoint's hotspot, or the player's own bobber landed in it */
	private boolean arrived(Player player) {
		double middleX = shownMiddleX;
		double middleZ = shownMiddleZ;
		double radius = Double.NaN;
		// the hotspot the game shows there, if any, gives the true middle and size
		for (Hotspots.Spot spot : hotspots.spots()) {
			if (RadarArrival.matches(spot.x, spot.z, spot.size, shownMiddleX, shownMiddleZ)) {
				middleX = spot.x;
				middleZ = spot.z;
				radius = spot.size;
				break;
			}
		}
		FishingHook hook = player.fishing;
		boolean landed = hook != null && (hook.isInWater() || hook.isInLava());
		return RadarArrival.reached(middleX, middleZ, radius, player.getX(), player.getZ(), landed, landed ? hook.getX() : 0, landed ? hook.getZ() : 0);
	}

	private void draw(LevelRenderContext context) {
		if (!active() || hotspots.spots().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		camera = client.gameRenderer.mainCamera().position();
		pixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), drawAll);
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 cam = camera;
		long now = System.currentTimeMillis();
		float breath = 1f - PULSE + PULSE * (float) Math.sin((now % BREATH_MILLIS) / (double) BREATH_MILLIS * Math.PI * 2);
		hotspots.ease(now);
		if (highlight.get()) {
			int rgb = color.get() & 0xFFFFFF;
			float strength = fill.get() / 100f;
			for (Hotspots.Spot spot : hotspots.spots()) {
				circle(pose, consumer, (float) (spot.x - cam.x), (float) (spot.surface + LIFT - cam.y), (float) (spot.z - cam.z), spot.radius, rgb,
					strength * breath);
			}
		}
	}

	/** A circle flat at a height: a soft fill, brighter toward its edge, a thin ring and a glow fading outside it. */
	private void circle(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float radius, int rgb, float strength) {
		float distance = (float) Math.sqrt(x * x + y * y + z * z);
		// the ring as wide on the screen at any distance, at least a little in the world
		float ring = Math.max(0.05f, pixelAngle * RING_PIXELS * distance);
		band(pose, consumer, x, y, z, 0f, radius * 0.6f, rgb, strength * 0.35f, strength * 0.5f);
		band(pose, consumer, x, y, z, radius * 0.6f, radius - ring, rgb, strength * 0.5f, strength);
		int bright = lighter(rgb);
		band(pose, consumer, x, y, z, radius - ring, radius + ring, bright, RING_OPACITY, RING_OPACITY);
		band(pose, consumer, x, y, z, radius + ring, radius + ring + GLOW_WIDTH, rgb, strength, 0f);
	}

	/** A flat band between two radii, its opacity going from the inner to the outer edge; both faces, so it shows from above and below. */
	private static void band(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float inner, float outer, int rgb, float innerOpacity,
		float outerOpacity) {
		if (outer <= inner || innerOpacity <= 0.003f && outerOpacity <= 0.003f) {
			return;
		}
		int in = argb(rgb, innerOpacity);
		int out = argb(rgb, outerOpacity);
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = Math.PI * 2 * i / SEGMENTS;
			double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			consumer.addVertex(pose, x + c0 * inner, y, z + s0 * inner).setColor(in);
			consumer.addVertex(pose, x + c0 * outer, y, z + s0 * outer).setColor(out);
			consumer.addVertex(pose, x + c1 * outer, y, z + s1 * outer).setColor(out);
			consumer.addVertex(pose, x + c1 * inner, y, z + s1 * inner).setColor(in);
			consumer.addVertex(pose, x + c1 * inner, y, z + s1 * inner).setColor(in);
			consumer.addVertex(pose, x + c1 * outer, y, z + s1 * outer).setColor(out);
			consumer.addVertex(pose, x + c0 * outer, y, z + s0 * outer).setColor(out);
			consumer.addVertex(pose, x + c0 * inner, y, z + s0 * inner).setColor(in);
		}
	}

	/** @return a color brightened toward white by {@link #RING_LIGHT} */
	private static int lighter(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return Math.round(r + (255 - r) * RING_LIGHT) << 16 | Math.round(g + (255 - g) * RING_LIGHT) << 8 | Math.round(b + (255 - b) * RING_LIGHT);
	}

	/** @return a color with an opacity from 0 to 1 */
	private static int argb(int rgb, float opacity) {
		return Math.round(Math.clamp(opacity, 0f, 1f) * 255f) << 24 | rgb & 0xFFFFFF;
	}
}
