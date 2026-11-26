package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import it.unimi.dsi.fastutil.longs.LongIterator;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * F6 terracotta timers, kept simple: in Sadan's boss (F6, M6) a block that turns into a flower pot where no timer runs starts one, 15 seconds (12 in
 * Master Mode) in server ticks ({@link TerracottaTimers}). The seconds stand in the world at the pot, large and outlined, green, orange under five
 * seconds and red under two, each in the first free space above its pot, so no block around it cuts it (texts closer than a block are pushed apart
 * sideways); walls hide them as they hide anything. Up to 64 blocks, and growing with the distance so it stays readable. A timer ends when its pot
 * turns back. With Debug on, each timer logs its start, the predicted end and the real one, in server ticks and the world's game time.
 */
public class DungeonTerracottaFeature extends AddonFeature {
	/** Sadan's line that ends the terracotta phase. */
	static final String ENOUGH = "[BOSS] Sadan: ENOUGH!";
	private static final int FLOOR = 6;
	/** Settings that are gone; dropped from configs from before: the dash over living terracottas and the old white and yellow. */
	static final String[] GONE_KEYS = {"alive_dash", "normal_color", "under_4_color"};
	/** The text is as bright as in full light, and its outline dark. */
	private static final int FULL_LIGHT = 0xF000F0;
	private static final int OUTLINE = 0xFF0C0C0E;
	private static final float TEXT_MIDDLE = 4f;
	/** The texts, by tenths of a second up to the longest timer, made once each. */
	private static final FormattedCharSequence[] TEXTS = new FormattedCharSequence[TerracottaTimers.tenths(TerracottaTimers.RESPAWN_TICKS) + 1];

	private final Toggle timer = add(make().toggle("timer", Component.literal("Timer over each pot"), true), "Timer");
	private final Slider textSize = add(make().slider("text_size", Component.literal("Text size"), 1f, 0.6f, 2f, 1), "Timer");
	private final ColorChoice greenColor = add(make().color("above_5_color", Component.literal("Above 5 s"), SystemColors.GREEN_RGB).ownColorOnly(), "Colors");
	private final ColorChoice orangeColor = add(make().color("above_2_color", Component.literal("Above 2 s"), SystemColors.ORANGE_RGB).ownColorOnly(), "Colors");
	private final ColorChoice redColor = add(make().color("under_2_color", Component.literal("Under 2 s"), SystemColors.RED_RGB).ownColorOnly(), "Colors");

	private final TerracottaTimers timers = new TerracottaTimers();
	private final TerracottaTimers.Log log = place -> DebugHooks.log(SkyBlockDebug.DUNGEON, "Terracotta: timer at {} {} {} removed", BlockPos.getX(place), BlockPos.getY(place),
		BlockPos.getZ(place));
	/** Debug: when each timer started, in server ticks and game time, by place. */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<long[]> startedAt = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
	/** The texts' places this frame, reused: across, height, along, and the ticks left. */
	private final double[] textX = new double[TerracottaTimers.MOST_TEXTS];
	private final double[] textY = new double[TerracottaTimers.MOST_TEXTS];
	private final double[] textZ = new double[TerracottaTimers.MOST_TEXTS];
	private final long[] textLeft = new long[TerracottaTimers.MOST_TEXTS];
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	/** The height of each pot's text (its first air block), by place, and when they were last looked up. */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap anchors = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	private long lookedAt = -1;
	private static final long LOOK_AGAIN_TICKS = 20;

	/** Whether block changes are watched now: on, in the F6 or M6 boss. Read for every block the server changes. */
	private boolean watching;

	public DungeonTerracottaFeature() {
		super("dungeon_terracotta", "Terracotta", "F6: when each terracotta comes back.", false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("timer");
		summary("At the flower pots");
		badge("F6");
		textSize.visibleWhen(timer::get);
		greenColor.visibleWhen(timer::get);
		orangeColor.visibleWhen(timer::get);
		redColor.visibleWhen(timer::get);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String line) {
				if (watching && line.equals(ENOUGH)) {
					timers.enough(ServerTicks.count());
				}
			}

			@Override
			public void left() {
				timers.clear();
			}
		});
	}

	@Override
	public void load(JsonObject json) {
		for (String key : GONE_KEYS) {
			json.remove(key);
		}
		super.load(json);
	}

	/** A block the server changed: a flower pot where no timer runs starts one. Called for every change, so it leaves at once while not watching. */
	public void blockChanged(BlockPos pos, BlockState before, BlockState after) {
		if (!watching) {
			return;
		}
		if (before.getBlock() instanceof FlowerPotBlock && !(after.getBlock() instanceof FlowerPotBlock)) {
			// the pot turned back: the terracotta is there again, and its timer ends now
			long tick = ServerTicks.count();
			long left = timers.back(pos.asLong(), tick);
			if (left != -1 && DebugHooks.ACTIVE) {
				logBack(pos, tick, left);
			}
			return;
		}
		if (!(after.getBlock() instanceof FlowerPotBlock)) {
			return;
		}
		DungeonFloor floor = Dungeon.floor();
		boolean master = floor != null && floor.master();
		long tick = ServerTicks.count();
		if (timers.potAppeared(pos.asLong(), tick, master)) {
			if (DebugHooks.ACTIVE) {
				int length = master ? TerracottaTimers.MASTER_RESPAWN_TICKS : TerracottaTimers.RESPAWN_TICKS;
				startedAt.put(pos.asLong(), new long[]{tick, ServerTicks.serverGameTime()});
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Terracotta: timer at {} {} {} started at tick {} (game time {}), {} {} ticks, predicted end at tick {}",
					pos.getX(), pos.getY(), pos.getZ(), tick, ServerTicks.serverGameTime(), master ? "M6" : "F6", length, tick + length);
			}
		}
	}

	/**
	 * Debug: a terracotta came back. Logs when its timer started, when it was predicted to end and when it really did, in server ticks and in the
	 * world's game time, with the differences: the measure for the timer's length and the moment it starts.
	 */
	private void logBack(BlockPos pos, long tick, long ticksLeft) {
		long[] start = startedAt.remove(pos.asLong());
		long gameNow = ServerTicks.serverGameTime();
		String started = start == null ? "?" : start[0] + " (game time " + start[1] + ")";
		long ticksTaken = start == null ? -1 : tick - start[0];
		long gameTaken = start == null || start[1] < 0 || gameNow < 0 ? -1 : gameNow - start[1];
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Terracotta: back at {} {} {} at tick {} (game time {}); started {}; took {} ticks, {} by game time; "
				+ "predicted end off by {} ticks ({} = came back earlier than predicted)", pos.getX(), pos.getY(), pos.getZ(), tick, gameNow, started, ticksTaken,
				gameTaken, ticksLeft, ticksLeft > 0 ? "positive" : "negative or 0");
		}
	}

	/** Once a client tick: whether the boss is on, and the timers counted down, cleared on a world change or after "ENOUGH!". */
	public void tick(Minecraft client) {
		DungeonFloor floor = Dungeon.floor();
		watching = isEnabled() && Dungeon.inBoss() && floor != null && floor.number() == FLOOR;
		if (!watching) {
			timers.clear();
		}
		if (timers.isEmpty() && !anchors.isEmpty()) {
			anchors.clear();
			startedAt.clear();
		}
		timers.tick(ServerTicks.count(), client.level, log);
	}

	/** The seconds at each pot, with the world. */
	public void draw(LevelRenderContext context) {
		if (!watching || !timer.get() || timers.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Camera camera = client.gameRenderer.mainCamera();
		Vec3 cam = camera.position();
		long tick = ServerTicks.count();
		PoseStack pose = context.poseStack();
		// where each text stands is looked up when its timer starts and again once a second, not every frame
		boolean lookAgain = tick / LOOK_AGAIN_TICKS != lookedAt;
		lookedAt = tick / LOOK_AGAIN_TICKS;
		int count = 0;
		for (LongIterator places = timers.places(); places.hasNext() && count < TerracottaTimers.MOST_TEXTS; ) {
			long key = places.nextLong();
			long left = timers.ticksLeft(key, tick);
			if (left <= 0) {
				continue;
			}
			int x = BlockPos.getX(key);
			int z = BlockPos.getZ(key);
			// in the first free space above the pot's column, so no block around it cuts the text
			int airY = anchors.getOrDefault(key, Integer.MIN_VALUE);
			if (airY == Integer.MIN_VALUE || lookAgain) {
				airY = TerracottaTimers.firstAirAbove(BlockPos.getY(key), y -> client.level.getBlockState(probe.set(x, y, z)).isAir());
				anchors.put(key, airY);
			}
			textX[count] = x + 0.5;
			textY[count] = airY + 0.5;
			textZ[count] = z + 0.5;
			textLeft[count] = left;
			count++;
		}
		// the camera's right along the ground, for texts that would stand on each other
		float yaw = (float) Math.toRadians(camera.yRot());
		TerracottaTimers.spread(textX, textY, textZ, count, -Math.cos(yaw), -Math.sin(yaw));
		for (int i = 0; i < count; i++) {
			double dx = textX[i] - cam.x;
			double dy = textY[i] - cam.y;
			double dz = textZ[i] - cam.z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (!TerracottaTimers.inReach(distance)) {
				continue;
			}
			long left = textLeft[i];
			float scale = TerracottaTimers.worldScale(distance) * textSize.get();
			FormattedCharSequence text = textOf(left);
			pose.pushPose();
			pose.translate(dx, dy, dz);
			pose.mulPose(camera.rotation());
			pose.scale(scale, -scale, scale);
			context.submitNodeCollector().submitText(pose, -client.font.width(text) / 2f, -TEXT_MIDDLE, text, false, Font.DisplayMode.NORMAL, FULL_LIGHT,
				0xFF000000 | colorOf(left), 0, OUTLINE);
			pose.popPose();
		}
	}

	private int colorOf(long ticksLeft) {
		return switch (TerracottaTimers.stage(ticksLeft)) {
			case GREEN -> greenColor.get();
			case ORANGE -> orangeColor.get();
			case RED -> redColor.get();
		};
	}

	/** @return the text for some ticks left, made once per value (the same few dozen texts come back every run) */
	private static FormattedCharSequence textOf(long ticks) {
		int tenths = Math.min(TerracottaTimers.tenths(ticks), TEXTS.length - 1);
		FormattedCharSequence text = TEXTS[tenths];
		if (text == null) {
			text = FormattedCharSequence.forward(TerracottaTimers.text(tenths), Style.EMPTY);
			TEXTS[tenths] = text;
		}
		return text;
	}
}
