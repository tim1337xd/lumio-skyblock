package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.util.BreakingProgress;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.mixin.accessor.BreakingBlockAccess;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tick gliding in the SkyBlock mines: how many ticks the mined block has taken out of the ticks it needs, below the crosshair ("8/19"). The server
 * breaks a block once it received enough mining ticks, and it learns about the mining one ping later, so the next block can be started as many
 * ticks before the end as the ping is long; the counter turns green from then on. The required ticks follow from the block strength and the mining
 * speed, which is read from the stats widget in the player list (/widgets).
 */
public class TickGlideFeature extends AddonFeature {
	private final Toggle showCount = add(make().toggle("show_count", Component.literal("Tick counter"), true), "Counter");
	private final Slider countSize = add(make().slider("count_size", Component.literal("Counter size"), 1.15f, 0.6f, 2.5f, 2), "Counter");
	private final Toggle tint = add(make().toggle("tint", Component.literal("Block tint"), true), "Block tint");
	private final Slider tintStrength = add(make().slider("tint_strength", Component.literal("Tint strength"), 0.31f, 0.05f, 0.8f, 2), "Block tint");
	// the colors also color the counter, so they stay visible while the tint is off: a row of their own in the group, not under the tint
	private final ColorChoice miningColor = add(make().color("mining_color", Component.literal("Mining color"), SystemColors.RED_RGB).inRow("Colors"),
		"Block tint");
	private final ColorChoice glideColor = add(make().color("glide_color", Component.literal("Ready color"), SystemColors.GREEN_RGB).inRow("Colors"),
		"Block tint");
	private final Toggle usePing = add(make().toggle("use_ping", Component.literal("Ping compensation"), true), "Timing",
		"Turns green early by your ping");
	// Mining speed the player list does not show because it only applies to some blocks (Heart of the Mountain perks): added to the list's speed
	// while such a block is mined, as Ping Offset Miner does, with its defaults
	private final Slider gemstoneSpeed = add(make().slider("gemstone_speed", Component.literal("Extra speed on gemstones"),
		855f, 0f, 3000f, 0), "Mining speed", "Speed the tab does not show, such as from perks");
	private final Slider metalSpeed = add(make().slider("metal_speed", Component.literal("Extra speed on Dwarven metals"),
		0f, 0f, 3000f, 0), "Mining speed");
	{
		countSize.under(showCount);
		tintStrength.under(tint);
	}

	@Override
	public boolean ownWindow() {
		return true;
	}

	/** "Mining Speed: ⸕1,234" in the stats widget. */
	private static final Pattern MINING_SPEED = Pattern.compile("Mining Speed\\s*:?\\s*[^\\d]{0,4}([\\d,]+)", Pattern.CASE_INSENSITIVE);

	/** Strength of the SkyBlock gemstones, by glass color. */
	private static final Map<DyeColor, Integer> GEMSTONES = Map.ofEntries(
		Map.entry(DyeColor.RED, 2300), // Ruby
		Map.entry(DyeColor.ORANGE, 3000), // Amber
		Map.entry(DyeColor.LIGHT_BLUE, 3000), // Sapphire
		Map.entry(DyeColor.LIME, 3000), // Jade
		Map.entry(DyeColor.PURPLE, 3000), // Amethyst
		Map.entry(DyeColor.WHITE, 3000), // Opal
		Map.entry(DyeColor.YELLOW, 3800), // Topaz
		Map.entry(DyeColor.MAGENTA, 4800), // Jasper
		Map.entry(DyeColor.BLACK, 5200), // Onyx
		Map.entry(DyeColor.BLUE, 5200), // Aquamarine
		Map.entry(DyeColor.BROWN, 5200), // Citrine
		Map.entry(DyeColor.GREEN, 5200)); // Peridot
	private static final Map<Block, DyeColor> GLASS_COLORS = new HashMap<>();

	static {
		ColorCollection.zipApply(Blocks.STAINED_GLASS, ColorCollection.VALUES, GLASS_COLORS::put);
		ColorCollection.zipApply(Blocks.STAINED_GLASS_PANE, ColorCollection.VALUES, GLASS_COLORS::put);
	}

	/** Block types with bonus mining speed the player list does not show. */
	private enum Kind {
		GEMSTONE, METAL, OTHER
	}

	/** The block being mined: position, strength, type, and ticks taken. */
	private BlockPos minedPos;
	private int strength;
	private Kind kind = Kind.OTHER;
	private int ticks;
	/** The mining speed as last read, and when; the list is only read once per second. */
	private int miningSpeed;
	/** When the block being mined turned to the glide color, for its one pulse. */
	private long turnedAt;
	private BlockPos turnedFor;
	private static final long TURN_PULSE_MILLIS = 220L;
	private static final float TURN_PULSE_LIGHT = 0.35f;
	private long speedReadAt;

	public TickGlideFeature() {
		super("tick_glide", "Tick glide", "The ticks of the block you mine under the crosshair, green when you can move on to the next one.", false);
		iconRgb(SystemColors.RED_RGB);
		iconName("timer");
		summary("The ticks of the block under your crosshair");
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::drawTint);
		BreakingProgress.set(this::progressAt);
	}

	private boolean wanted() {
		return isEnabled() && SkyBlock.inSkyBlock() && SkyBlockArea.inMines();
	}

	/** The ticks are counted for the block overlay's progress as well, so also with the counter off. */
	private static boolean counting() {
		return SkyBlock.inSkyBlock() && SkyBlockArea.inMines();
	}

	/**
	 * @return how far the block being mined is along, or -1 for any other block, or when the calculation does not know it: while Tick glide is on, 1
	 *         is the tick from which the next block can be started (when the counter turns green, ping included); otherwise 1 is the break
	 */
	private float progressAt(BlockPos pos) {
		if (minedPos == null || !minedPos.equals(pos) || !counting()) {
			return -1f;
		}
		Minecraft client = Minecraft.getInstance();
		int needed = ticksNeeded();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return progress(ticks, partial, wanted() ? glideTicks(client, needed) : needed);
	}

	/** @return the ticks after which the next block can be started: the needed ones, less the ping's with ping compensation, at least one; 0 if unknown */
	private int glideTicks(Minecraft client, int needed) {
		if (needed <= 0) {
			return 0;
		}
		return Math.max(1, needed - (usePing.get() ? pingTicks(client) : 0));
	}

	/** @return the share of the needed ticks taken, between ticks as well, or -1 if they are not known */
	static float progress(int ticks, float partial, int needed) {
		if (needed <= 0) {
			return -1f;
		}
		return Mth.clamp((ticks + partial) / needed, 0f, 1f);
	}

	private void tick(Minecraft client) {
		if (!counting() || client.player == null || client.level == null || client.gameMode == null || !client.gameMode.isDestroying()) {
			minedPos = null;
			ticks = 0;
			return;
		}
		BlockPos pos = ((BreakingBlockAccess) client.gameMode).evskyblock$destroyPos();
		if (pos == null) {
			minedPos = null;
			return;
		}
		if (!pos.equals(minedPos)) {
			minedPos = pos.immutable();
			BlockState state = client.level.getBlockState(pos);
			strength = strengthOf(state);
			kind = kindOf(state);
			ticks = 0;
		}
		ticks++;
	}

	/** @return the strength of a SkyBlock mining block, or 0 if unknown */
	private static int strengthOf(BlockState state) {
		Block block = state.getBlock();
		DyeColor glass = GLASS_COLORS.get(block);
		if (glass != null) {
			return GEMSTONES.getOrDefault(glass, 0);
		}
		if (block == Blocks.WOOL.pick(DyeColor.GRAY) || block == Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN)) {
			return 500; // gray mithril
		}
		if (block == Blocks.PRISMARINE || block == Blocks.PRISMARINE_BRICKS || block == Blocks.DARK_PRISMARINE) {
			return 800; // green mithril
		}
		if (block == Blocks.WOOL.pick(DyeColor.LIGHT_BLUE)) {
			return 1500; // blue mithril
		}
		if (block == Blocks.POLISHED_DIORITE) {
			return 2000; // titanium
		}
		if (block == Blocks.PACKED_ICE) {
			return 6000; // glacite
		}
		if (block == Blocks.TERRACOTTA || block == Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN) || block == Blocks.SMOOTH_RED_SANDSTONE) {
			return 5600; // umber
		}
		if (block == Blocks.CLAY || block == Blocks.INFESTED_COBBLESTONE) {
			return 5600; // tungsten
		}
		if (block == Blocks.STONE || block == Blocks.INFESTED_STONE) {
			// hard stone in the Crystal Hollows, tunnels and mineshafts, plain stone elsewhere
			readPlace();
			return hardStone ? 50 : 15;
		}
		if (block == Blocks.COBBLESTONE) {
			// in the tunnels and mineshafts, cobblestone is tungsten
			readPlace();
			return tungstenCobble ? 5600 : 20;
		}
		return 0;
	}

	/** @return whether a block is a gemstone, a Dwarven metal (mithril, titanium, tungsten, umber, glacite), or something else */
	private static Kind kindOf(BlockState state) {
		Block block = state.getBlock();
		if (GLASS_COLORS.containsKey(block)) {
			return Kind.GEMSTONE;
		}
		if (block == Blocks.COBBLESTONE) {
			readPlace();
			return tungstenCobble ? Kind.METAL : Kind.OTHER;
		}
		if (block == Blocks.STONE || block == Blocks.INFESTED_STONE) {
			return Kind.OTHER;
		}
		return strengthOf(state) > 0 ? Kind.METAL : Kind.OTHER;
	}

	/** The island and zone {@link #readPlace} last evaluated; identical texts mean the same location. */
	private static String placeIsland;
	private static String placeZone;
	private static boolean hardStone;
	private static boolean tungstenCobble;

	/** Determines what stone and cobblestone are at the current location: computed when the location changes, not on every block query (every frame). */
	private static void readPlace() {
		String island = SkyBlockArea.island();
		String zone = SkyBlockArea.zone();
		if (island == placeIsland && zone == placeZone) {
			return;
		}
		placeIsland = island;
		placeZone = zone;
		String place = (island + " " + zone).toLowerCase(Locale.ROOT);
		boolean tunnels = place.contains("glacite") || place.contains("mineshaft") || place.contains("tunnels");
		hardStone = tunnels || place.contains("crystal");
		tungstenCobble = tunnels;
	}

	private int miningSpeed() {
		long now = System.currentTimeMillis();
		if (now - speedReadAt > 1000L) {
			speedReadAt = now;
			boolean found = false;
			for (String line : SkyBlockArea.tab()) {
				Matcher matcher = MINING_SPEED.matcher(SkyBlockArea.plainLine(line));
				if (matcher.find()) {
					try {
						miningSpeed = Integer.parseInt(matcher.group(1).replace(",", ""));
						found = true;
					} catch (NumberFormatException ignored) {
					}
					break;
				}
			}
			if (!found) {
				miningSpeed = 0;
			}
		}
		return miningSpeed;
	}

	/** @return the ticks the block needs: its strength over the mining speed, at least four, or one when the speed instamines it; 0 if unknown */
	private int ticksNeeded() {
		int speed = miningSpeed();
		if (strength <= 0 || speed <= 0) {
			return 0;
		}
		// bonus speed only applying to gemstones or Dwarven metals, which the list does not show
		speed += Math.round(switch (kind) {
			case GEMSTONE -> gemstoneSpeed.get();
			case METAL -> metalSpeed.get();
			case OTHER -> 0f;
		});
		if (speed >= strength * 60) {
			return 1;
		}
		return Math.max(4, Math.round(30f * strength / speed));
	}

	/** @return the ping in ticks (round trip, as the player list reports it), by which the next block can be started before the end */
	private static int pingTicks(Minecraft client) {
		if (client.getConnection() == null || client.player == null) {
			return 0;
		}
		var info = client.getConnection().getPlayerInfo(client.player.getUUID());
		return info == null ? 0 : (int) Math.ceil(info.getLatency() / 50.0);
	}

	/** @return whether the next block can be started now */
	private boolean canGlide(Minecraft client, int needed) {
		// the same moment the block overlay's progress is full at (see glideTicks)
		return needed > 0 && ticks >= glideTicks(client, needed);
	}

	/**
	 * @return the color of the block being mined as 0xRRGGBB: the mining color until the tick from which the next block can be started, then the glide
	 *         color at once, with one short soft brightening as it turns. Without the needed ticks it never turns.
	 */
	private int minedColor(Minecraft client, int needed) {
		int mining = miningColor.get() & 0xFFFFFF;
		int glide = glideColor.get() & 0xFFFFFF;
		if (needed <= 0 || !canGlide(client, needed)) {
			turnedAt = 0L;
			return mining;
		}
		long now = System.currentTimeMillis();
		if (turnedAt == 0L || !java.util.Objects.equals(turnedFor, minedPos)) {
			turnedAt = now;
			turnedFor = minedPos;
		}
		float pulse = 1f - Math.min(1f, (now - turnedAt) / (float) TURN_PULSE_MILLIS);
		return Draw.mix(glide, 0xFFFFFF, TURN_PULSE_LIGHT * pulse * pulse);
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (minedPos == null || !wanted() || !showCount.get()) {
			return;
		}
		int needed = ticksNeeded();
		String text = needed > 0 ? Math.min(ticks, needed) + "/" + needed : String.valueOf(ticks);
		int color = 0xFF000000 | minedColor(client, needed);
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, color, 0f, false);
		float scale = countSize.get();
		float width = Draw.measureScaled(text, scale);
		ui.textAtScaledBold(text, graphics.guiWidth() / 2f - width / 2f, graphics.guiHeight() / 2f + 9f, scale, color);
		// without the mining speed the required ticks are unknown: show what is missing below the counter
		if (needed == 0) {
			String hint = miningSpeed == 0 ? "Turn on Stats > Mining Speed in /widgets" : "Block not known";
			float hintWidth = Draw.measureScaled(hint, TextScale.SMALL);
			ui.textAtScaled(hint, graphics.guiWidth() / 2f - hintWidth / 2f, graphics.guiHeight() / 2f + 9f + 11f * scale, TextScale.SMALL, 0xB0FFFFFF);
		}
	}

	/**
	 * @return the color the block overlay uses for the block at a position as 0xRRGGBB: the counter color for the block being mined, or -1 for the
	 *         overlay's own color
	 */
	public int overlayColor(BlockPos pos) {
		if (!tint.get() || !wanted() || pos == null) {
			return -1;
		}
		if (pos.equals(minedPos)) {
			int needed = ticksNeeded();
			return minedColor(Minecraft.getInstance(), needed);
		}
		// while mining, the next block is already red when aimed at, before it is hit
		return pos.equals(aimedOre(Minecraft.getInstance())) ? miningColor.get() & 0xFFFFFF : -1;
	}

	/** @return the block aimed at while mining (the mining panels are up), if it is a mining block, otherwise null */
	private static BlockPos aimedOre(Minecraft client) {
		if (!ProfitActivity.MINING.engaged() || client.level == null
			|| !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		return strengthOf(client.level.getBlockState(pos)) > 0 ? pos : null;
	}

	/** Tints the block being mined in the counter color: red while mining, green once the next one can be started. */
	private void drawTint(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!tint.get() || !wanted()) {
			return;
		}
		// with the block overlay enabled, it draws the block in its configured style, in the counter color
		if (WorldDraw.blockOverlayOn()) {
			return;
		}
		// the block being mined, or while mining the next aimed block, red before it is hit
		BlockPos at = minedPos != null ? minedPos : aimedOre(client);
		if (at == null) {
			return;
		}
		int rgb = overlayColor(at);
		if (rgb < 0) {
			return;
		}
		int alpha = Math.round(tintStrength.get() * 255f) << 24;
		var cam = client.gameRenderer.mainCamera().position();
		AABB box = new AABB(at.getX() - cam.x, at.getY() - cam.y, at.getZ() - cam.z, at.getX() + 1 - cam.x, at.getY() + 1 - cam.y, at.getZ() + 1 - cam.z).inflate(0.003);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (last, consumer) ->
			WorldDraw.quads(last, consumer, box, alpha | rgb, null));
	}
}
