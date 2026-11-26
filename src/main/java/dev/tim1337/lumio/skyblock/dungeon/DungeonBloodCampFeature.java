package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.InfoLine;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.SkullTextures;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blood camp helper. Between the Watcher's first line and his last, the heads near him that are on the blood mob list ({@link BloodMobs}) are
 * followed by the positions the server sends ({@link BloodCamp}): where each lands (a box, a line from the mob if wanted) and when (seconds over its
 * landing, green, yellow, red). Each mob has its own clock, started on the server tick its skull first moves. Apart from that the panel counts down to the Watcher's move:
 * an estimate per floor, learned from the player's own runs ({@link WatcherTimes}), named as one ("Watcher moves in ~"). The "Kill mobs" title, if
 * wanted, comes from the first mob's own countdown, never from that estimate. Hints only; any head not on the list (the player's Soulweaver skulls, other heads) is ignored.
 */
public class DungeonBloodCampFeature extends AddonPanel {
	private static final String WATCHER_LINE = "[BOSS] The Watcher: ";
	private static final double BODY_LIFT = 1.5;
	private static final long KILL_WARNING_TICKS = 100;
	/** The "Kill mobs" title shows once the first mob's own countdown is this short. */
	private static final long KILL_TITLE_MILLIS = 1500;
	private static final int COUNTDOWN_ORANGE = 0xFFFF9F0A;
	private static final long SAMPLE_TICKS = 240;
	private static final float REFERENCE_LINES = 1080f;
	private static final int WIDTH = 150;
	private static final int PAD = PanelKit.PAD;
	private static final int TICKS_PER_SECOND = 20;
	private static final int TITLE_FADE_IN_TICKS = 3;
	private static final int TITLE_STAY_TICKS = 30;
	private static final int TITLE_FADE_OUT_TICKS = 10;
	private static final int MOST_LOGGED = 64;

	private final ColorChoice landingColor = add(make().color("landing_color", Component.literal("Landing color"), SystemColors.RED_RGB), "General");
	private final ColorChoice flyingColor = add(make().color("flying_color", Component.literal("Mob color"), SystemColors.GREEN_RGB), "General");
	private final Slider boxSize = add(make().slider("box_size", Component.literal("Box size"), 0.8f, 0.3f, 1f, 1), "General");
	private final Toggle showCountdown = add(make().toggle("show_countdown", Component.literal("Show the countdown"), true), "General",
		"Each mob's time, over its box");
	private final Toggle line = add(make().toggle("line", Component.literal("Show the line"), true), "General", "A thin line from the skull to its spawn box");
	private final Toggle pingAhead = add(make().toggle("ping_ahead", Component.literal("Mob box ahead by your ping"), true), "General",
		"Where the server has the mob now");
	private final Toggle killTitle = add(make().toggle("kill_title", Component.literal("\"Kill mobs\" title"), true), "Watcher",
		"When the first mob is about to spawn");
	private final InfoLine learned = add(make().info("learned", Component.literal("Watcher time"), this::learnedText), "Watcher");
	private final Slider spawnTick = add(make().slider("spawn_tick", Component.literal("Spawn tick"), BloodCamp.SPAWN_TICK, BloodCamp.LEAST_SPAWN_TICK,
		BloodCamp.MOST_SPAWN_TICK, 0), "Advanced", "Ticks from first move to landing");
	private final Slider offsetMillis = add(make().slider("offset_millis", Component.literal("Timer offset"),
		BloodCamp.Settings.DEFAULT.offsetMillis(), -100f, 100f, 0).unit(" ms"), "Advanced");
	private final Toggle pingOffset = add(make().toggle("ping_offset", Component.literal("Ping offset"), false), "Advanced",
		"Shifts the countdown by your ping");

	private final BloodCamp camp = new BloodCamp();
	private final WatcherTimes watcherTimes = new WatcherTimes();
	private final IntOpenHashSet seenMobs = new IntOpenHashSet();
	/** Each head's texture hash, by entity id, worked out once per entity. */
	private final Int2ObjectOpenHashMap<String> hashes = new Int2ObjectOpenHashMap<>();
	/** Skulls near the Watcher not on the list, logged once each (Debug). */
	private final Set<String> logged = new HashSet<>();
	private int watcherId = -1;
	private boolean titleShown;
	private BloodCamp.Settings settings = BloodCamp.Settings.DEFAULT;
	private List<BloodCamp.Landing> landings = List.of();
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	private Vec3 camera = Vec3.ZERO;
	private float pixelAngle;
	private double pingTicks;
	private final SubmitNodeCollector.CustomGeometryRenderer drawCamp = this::drawLandings;

	public DungeonBloodCampFeature() {
		super("dungeon_blood_camp", "Blood camp", "Where and when the Watcher's mobs land.", false,
			Anchor.CENTER, false, 0f, 90f);
		iconRgb(SystemColors.RED_RGB);
		iconName("timer");
		summary("Where the mobs land");
		offerNoBackground();
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String text) {
				long now = ServerTicks.count();
				if (text.startsWith(WATCHER_LINE)) {
					camp.opened(now);
				}
				if (DungeonParse.WATCHER_MOVES.matcher(text).matches()) {
					if (camp.openedAt() >= 0 && !camp.laterWaves()) {
						watcherTimes.measured(Dungeon.floor(), (now - camp.openedAt()) / (double) TICKS_PER_SECOND);
						dev.tim1337.lumio.api.LumioApi.saveSoon();
					}
					camp.wavesStarted();
				} else if (DungeonParse.WATCHER_ENOUGH.matcher(text).matches() || DungeonParse.WATCHER_DONE.matcher(text).matches()) {
					camp.ended();
					landings = List.of();
				}
			}

			@Override
			public void left() {
				resetRun();
			}
		});
	}

	private void resetRun() {
		camp.reset();
		landings = List.of();
		hashes.clear();
		firstSeen.clear();
		nothingSince = -1;
		watcherId = -1;
		titleShown = false;
	}

	private String learnedText() {
		DungeonFloor floor = Dungeon.floor() != null ? Dungeon.floor() : DungeonFloor.F7;
		int runs = watcherTimes.learnedFrom(floor);
		String seconds = String.format(java.util.Locale.ROOT, "%.1f s on %s", watcherTimes.seconds(floor), floor.name());
		return runs == 0 ? seconds + ", an estimate" : seconds + ", learned from " + runs + " runs";
	}

	/** Follows the blood mobs every tick, only while the Watcher sends them and not in the boss room. */
	public void tick(Minecraft client) {
		if (!isEnabled() || !Dungeon.in() || client.level == null || client.player == null) {
			landings = List.of();
			return;
		}
		long now = ServerTicks.count();
		PlayerInfo own = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(client.player.getUUID());
		int ping = own == null ? 0 : Math.max(0, own.getLatency());
		settings = new BloodCamp.Settings(Math.round(spawnTick.get()), Math.round(offsetMillis.get()), pingOffset.get() ? ping : 0);
		if (!camp.active() || Dungeon.inBoss()) {
			landings = List.of();
			return;
		}
		Entity watcher = watcher(client);
		if (watcher == null) {
			landings = List.of();
			noteNothing(now, false, 0, 0, 0);
			return;
		}
		seenMobs.clear();
		int stands = 0;
		int skulls = 0;
		int notHis = 0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || stand.distanceToSqr(watcher) > BloodMobs.NEAR_WATCHER_BLOCKS * BloodMobs.NEAR_WATCHER_BLOCKS) {
				continue;
			}
			stands++;
			String hash = hashOf(stand);
			if (hash.isEmpty()) {
				continue;
			}
			skulls++;
			if (!stand.isAlive()) {
				// killed: its landing box goes, and it is not followed again
				camp.died(stand.getId());
				continue;
			}
			// the server's own place of the mob, not the place the client smooths it to
			Vec3 server = stand.getPositionCodec().getBase();
			double across = Math.hypot(server.x - watcher.getX(), server.z - watcher.getZ());
			if (BloodMobs.watcher(hash)) {
				continue;
			}
			if (BloodMobs.mob(hash)) {
				if (BloodMobs.bloodMob(hash, stand.distanceToSqr(watcher), camp.active(), Dungeon.inBoss(), false)) {
					camp.seen(stand.getId(), server.x, server.y, server.z, now);
					seenMobs.add(stand.getId());
				}
				continue;
			}
			// not on the list: one of his only when it was first seen right at him and then flies away from him. Until that is seen it is
			// followed without a box, so its way is whole; a head that moves without getting farther from him (a Soulweaver skull) is dropped
			double[] first = firstSeen.get(stand.getId());
			if (first == null) {
				first = new double[] {across, server.x, server.z};
				if (firstSeen.size() < MOST_FOLLOWED) {
					firstSeen.put(stand.getId(), first);
				}
			}
			double moved = Math.hypot(server.x - first[1], server.z - first[2]);
			if (BloodMobs.notThrown(first[0], across, moved)) {
				camp.notHis(stand.getId());
				notHis++;
				if (DebugHooks.ACTIVE && logged.size() < MOST_LOGGED && logged.add(hash)) {
					DebugHooks.log(SkyBlockDebug.DUNGEON, "Blood camp: a skull near the Watcher not on the list and not thrown out by him: {} at {} {} {}", hash,
						Math.round(server.x), Math.round(server.y), Math.round(server.z));
				}
				continue;
			}
			boolean thrown = BloodMobs.thrownOut(first[0], across);
			if (BloodMobs.bloodMob(hash, stand.distanceToSqr(watcher), camp.active(), Dungeon.inBoss(), true)) {
				camp.seen(stand.getId(), server.x, server.y, server.z, now, thrown);
				seenMobs.add(stand.getId());
				if (thrown && DebugHooks.ACTIVE && logged.size() < MOST_LOGGED && logged.add(hash)) {
					DebugHooks.log(SkyBlockDebug.DUNGEON, "Blood camp: a skull not on the list is followed, the Watcher threw it out: {}", hash);
				}
			}
		}
		noteNothing(now, true, stands, skulls, notHis);
		camp.keepOnly(seenMobs);
		// a mob that reached its spawn tick has landed: it is let go, so only mobs in flight have boxes
		camp.settle(now, settings);
		landings = camp.landings(now, settings);
		pingTicks = ping / 50.0;
		warnToKill(client);
		logReports();
	}

	/** Where each head near the Watcher that is not on the list was first seen (its distance from him across, its x and z); forgotten with the run. */
	private final Int2ObjectOpenHashMap<double[]> firstSeen = new Int2ObjectOpenHashMap<>();
	private static final int MOST_FOLLOWED = 512;
	/** While the room is open and no mob is followed for this long, the reason goes to the debug log, and again this much later. */
	private static final long NOTHING_TICKS = 200;
	private long nothingSince = -1;

	/** Fails visibly: the room is open, the Watcher sends mobs, and nothing has been followed for a while: why, for the debug log. */
	private void noteNothing(long now, boolean watcherFound, int stands, int skulls, int notHis) {
		if (!seenMobs.isEmpty() && watcherFound || camp.tracked() > 0) {
			nothingSince = -1;
			return;
		}
		if (nothingSince < 0) {
			nothingSince = now;
			return;
		}
		if (now - nothingSince >= NOTHING_TICKS) {
			nothingSince = now;
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Blood camp: no mob followed for {} s while the room is open: {}", NOTHING_TICKS / TICKS_PER_SECOND,
					BloodMobs.whyNothing(watcherFound, stands, skulls, notHis));
			}
		}
	}

	/**
	 * The "Kill mobs" title once per run, from the first mob's own countdown: when its spawn is a moment away. Nothing is said from an estimate
	 * made when the room opened any more.
	 */
	private void warnToKill(Minecraft client) {
		if (titleShown || !killTitle.get() || landings.isEmpty()) {
			return;
		}
		if (BloodCamp.soonest(landings) <= KILL_TITLE_MILLIS) {
			titleShown = true;
			dev.tim1337.lumio.skyblock.hud.TitleQueue.show(Component.literal("§cKill mobs"), Component.empty(), TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS,
				TITLE_FADE_OUT_TICKS, true);
		}
	}

	/** With the debug probe: per mob, the tick its skull started, the predicted spawn tick and point, and the tick and place its skull was last seen. */
	private void logReports() {
		for (BloodCamp.Report report : camp.drainReports()) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Blood camp: mob {} ({} wave) skull started at tick {}, predicted spawn at tick {} at {} {} {}; {} at tick {} at {} {} {}: "
					+ "{} ticks and {} blocks off", report.id(), report.firstWave() ? "first" : "later", report.startTick(), report.predictedTick(),
					tenths(report.predictedX()), tenths(report.predictedY()), tenths(report.predictedZ()), report.how(), report.endTick(), tenths(report.endX()),
					tenths(report.endY()), tenths(report.endZ()), report.ticksOff(), tenths(report.blocksOff()));
			}
		}
	}

	private static String tenths(double value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	/** @return server ticks until the Watcher should start sending his mobs, 0 once it is time, -1 when not counting */
	private long watcherMovesIn(long now) {
		if (camp.openedAt() < 0 || camp.laterWaves() || !camp.active()) {
			return -1;
		}
		long due = camp.openedAt() + Math.round(watcherTimes.seconds(Dungeon.floor()) * TICKS_PER_SECOND);
		return Math.max(0, due - now);
	}

	/** @return the Watcher: the one wearing one of his own heads, found again only when the last one is gone */
	private Entity watcher(Minecraft client) {
		Entity known = watcherId < 0 ? null : client.level.getEntity(watcherId);
		if (known != null && known.isAlive()) {
			return known;
		}
		watcherId = -1;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && BloodMobs.watcher(hashOf(living))) {
				watcherId = entity.getId();
				return entity;
			}
		}
		return null;
	}

	private String hashOf(LivingEntity entity) {
		String known = hashes.get(entity.getId());
		if (known != null) {
			return known;
		}
		ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
		String hash = head.is(Items.PLAYER_HEAD) ? SkullTextures.of(head) : "";
		if (!hash.isEmpty() || !head.isEmpty()) {
			hashes.put(entity.getId(), hash);
		}
		return hash;
	}

	/** Draws where the blood mobs land, where they are, and a line between, with the world and hidden by walls. */
	public void draw(LevelRenderContext context) {
		if (landings.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		camera = client.gameRenderer.mainCamera().position();
		pixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), drawCamp);
	}

	private void drawLandings(PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 cam = camera;
		float half = boxSize.get() / 2f;
		int landing = landingColor.get() & 0xFFFFFF;
		int flying = flyingColor.get() & 0xFFFFFF;
		double ahead = pingAhead.get() ? pingTicks : 0;
		for (BloodCamp.Landing each : landings) {
			// the boxes stand where a mob's body is: a little above the stand's place
			float lx = (float) (each.x() - cam.x);
			float ly = (float) (each.y() + BODY_LIFT - cam.y);
			float lz = (float) (each.z() - cam.z);
			float nx = (float) (each.nowX() + each.stepX() * ahead - cam.x);
			float ny = (float) (each.nowY() + each.stepY() * ahead + BODY_LIFT - cam.y);
			float nz = (float) (each.nowZ() + each.stepZ() * ahead - cam.z);
			WaypointLook.boxAt(pose, consumer, lx - half, ly - half, lz - half, lx + half, ly + half, lz + half, landing, 0.3f);
			WaypointLook.outlineAt(pose, consumer, lx - half, ly - half, lz - half, lx + half, ly + half, lz + half, landing, 0.9f, pixelAngle, 2f);
			WaypointLook.outlineAt(pose, consumer, nx - half, ny - half, nz - half, nx + half, ny + half, nz + half, flying, 0.9f, pixelAngle, 2f);
			if (line.get()) {
				WaypointLook.line(pose, consumer, nx, ny, nz, lx, ly, lz, flying, 0.7f, pixelAngle, 1.5f);
			}
		}
	}

	/** The seconds until each mob lands, over its landing, only for mobs the player sees. */
	public void drawLabels(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (landings.isEmpty() || !showCountdown.get()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		for (BloodCamp.Landing each : landings) {
			Entity mob = client.level.getEntity(each.id());
			if (mob == null || !Sight.visible(client, mob) || WorldLabel.place(client, each.x(), each.y() + BODY_LIFT + 1.0, each.z(), place) < WorldLabel.NEAR) {
				continue;
			}
			long millis = Math.max(0, each.millisLeft());
			int color = countdownColor(millis);
			String text = BloodCamp.countdownText(millis);
			graphics.centeredText(client.font, text, Math.round(place[WorldLabel.X]), Math.round(place[WorldLabel.Y]) - 4, color);
		}
	}

	/** @return a countdown's color: green with time left, orange when soon, red at the spawn */
	private static int countdownColor(long millis) {
		return switch (BloodCamp.urgency(millis)) {
			case CALM -> SystemColors.GREEN;
			case SOON -> COUNTDOWN_ORANGE;
			case NOW -> SystemColors.RED;
		};
	}

	/**
	 * The panel shows the estimated time until the Watcher moves, and only that: once it is over the panel is gone. The mobs' own countdowns are
	 * over their boxes; nothing about killing them is said from this estimate.
	 */
	private boolean shownNow() {
		return isEnabled() && Dungeon.in() && camp.active() && !Dungeon.inBoss() && watcherMovesIn(ServerTicks.count()) > 0;
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownNow() || simulated();
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
		long moves = shownNow() ? watcherMovesIn(ServerTicks.count()) : SAMPLE_TICKS;
		// an estimate (per floor, learned from the player's runs), named as one so it is not taken for the mobs' timing
		String label = "Watcher moves in";
		String value = "~" + DungeonTime.seconds(Math.max(0, moves));
		int color = moves < KILL_WARNING_TICKS ? SystemColors.RED : SystemColors.YELLOW;
		ui.textAtScaled(label, PAD, PAD + 2, TextScale.BODY, HudTheme.secondary());
		TabularText.drawRight(ui, value, WIDTH - PAD, PAD + 2, TextScale.BODY, true, color);
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		JsonObject times = new JsonObject();
		for (Map.Entry<DungeonFloor, List<Double>> floor : watcherTimes.runs().entrySet()) {
			JsonArray seconds = new JsonArray();
			floor.getValue().forEach(seconds::add);
			times.add(floor.getKey().name(), seconds);
		}
		json.add("watcher_times", times);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (!(json.get("watcher_times") instanceof JsonObject times)) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : times.entrySet()) {
			DungeonFloor floor = DungeonFloor.of(entry.getKey());
			if (floor == null || !(entry.getValue() instanceof JsonArray array) || array.size() > WatcherTimes.KEPT) {
				continue;
			}
			List<Double> seconds = new java.util.ArrayList<>();
			for (JsonElement each : array) {
				try {
					seconds.add(each.getAsDouble());
				} catch (RuntimeException e) {
					// not a number: left out
				}
			}
			watcherTimes.load(floor, seconds);
		}
	}
}
