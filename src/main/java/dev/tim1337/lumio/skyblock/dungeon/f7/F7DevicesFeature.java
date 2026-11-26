package dev.tim1337.lumio.skyblock.dungeon.f7;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.dungeon.Dungeon;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.MarksDrawing;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.PuzzleData;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.PuzzleMarks;

import com.google.gson.JsonElement;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hints for the Floor 7 devices in Goldor's phase, in the world and depth tested: the Simon Says buttons in the order to press them (also after a skip at the start),
 * the clicks each Arrow Align frame still needs, the Lights levers still off, and the Sharpshooter's lit target. Hints only: every click is the
 * player's. The places are where the devices stand in every Floor 7 boss room.
 */
public class F7DevicesFeature extends AddonFeature {
	/** Simon Says: the lanterns' column, the buttons' column, and the grid's corner (four by four). */
	private static final int SIMON_LANTERN_X = 111;
	private static final int SIMON_BUTTON_X = 110;
	private static final int SIMON_Y = 120;
	private static final int SIMON_Z = 92;
	private static final int SIMON_SIDE = 4;
	/** Its start button, how many buttons of the order are marked (the next and the two after it), and after how long without buttons it counts as done. */
	private static final BlockPos SIMON_START = new BlockPos(110, 121, 91);
	private static final int SIMON_SHOWN = 3;
	private static final int SIMON_DONE_TICKS = 200;
	/** Arrow Align: the frames' column and the grid's corner (five by five), and how near the player must be. */
	private static final int ARROW_X = -2;
	private static final int ARROW_Y = 120;
	private static final int ARROW_Z = 75;
	private static final int ARROW_SIDE = 5;
	private static final BlockPos ARROW_MIDDLE = new BlockPos(0, 120, 77);
	private static final double NEAR_SQ = 15 * 15;
	private static final BlockPos[] LIGHTS = {new BlockPos(62, 136, 142), new BlockPos(58, 136, 142), new BlockPos(60, 135, 142), new BlockPos(60, 134, 142),
		new BlockPos(62, 133, 142), new BlockPos(58, 133, 142)};
	private static final BlockPos[] TARGETS = {new BlockPos(68, 130, 50), new BlockPos(66, 130, 50), new BlockPos(64, 130, 50), new BlockPos(68, 128, 50),
		new BlockPos(66, 128, 50), new BlockPos(64, 128, 50), new BlockPos(68, 126, 50), new BlockPos(66, 126, 50), new BlockPos(64, 126, 50)};
	private static final int ARROW_SCAN_TICKS = 2;
	private static final float FILL = 0.3f;
	private static final int NEXT = SystemColors.GREEN_RGB;
	private static final int THEN = SystemColors.YELLOW_RGB;
	private static final int LATER = SystemColors.RED_RGB;
	/** Simon Says: the two buttons after the next one, each dimmer than the one before. */
	private static final int SOON = 0x1F8A3A;
	private static final int AFTER = 0x165C29;

	private final Toggle simon = add(make().toggle("simon_says", Component.literal("Simon Says"), true), "Devices");
	private final Toggle arrows = add(make().toggle("arrow_align", Component.literal("Arrow Align"), true), "Devices");
	private final Toggle lights = add(make().toggle("lights", Component.literal("Lights"), true), "Devices");
	private final Toggle sharpshooter = add(make().toggle("sharpshooter", Component.literal("Sharpshooter"), true), "Devices");
	private static final String BLOCK_GROUP = "Block wrong clicks";
	private static final int START_CLICKS_DEFAULT = 4;
	private static final int START_CLICKS_MOST = 10;
	private final Toggle blockSimon = add(make().toggle("block_simon_says", Component.literal("Block Simon Says"), false), BLOCK_GROUP,
		"Clicks on wrong buttons are not sent");
	private final Toggle limitStart = add(make().toggle("limit_start", Component.literal("Also limit the start button"), false), BLOCK_GROUP,
		"Only right after its first press");
	private final Slider startClicks = add(make().slider("start_clicks", Component.literal("Max start clicks"), START_CLICKS_DEFAULT, 1f, START_CLICKS_MOST, 0),
		BLOCK_GROUP);
	private final Toggle blockArrows = add(make().toggle("block_arrow_align", Component.literal("Block Arrow Align"), false), BLOCK_GROUP,
		"Clicks on finished arrows aren't sent");
	private final Toggle blockLights = add(make().toggle("block_lights", Component.literal("Block Lights"), false), BLOCK_GROUP);
	private final Toggle blockedSound = add(make().toggle("blocked_sound", Component.literal("Blocked click sound"), true), BLOCK_GROUP);

	private final SimonSays simonSays = new SimonSays(what -> {
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Simon Says: {}", what);
		}
	});
	private final boolean[] lanternLit = new boolean[SIMON_SIDE * SIMON_SIDE];
	private final boolean[] buttonDown = new boolean[SIMON_SIDE * SIMON_SIDE];
	private boolean startDown;
	private boolean inGoldor;
	/** For how many ticks no button of the grid stood: long enough, and the device is done. */
	private int gridGoneTicks;
	private final int[] rotations = new int[Devices.FRAMES];
	private int[] arrowClicks;
	/** The player's own clicks that went through and the world has not shown yet: Simon Says presses, start presses, and clicks per frame. */
	private int simonAhead;
	private int simonAheadTick;
	private int ownStarts;
	private int ownStartTick;
	private final int[] lastRotations = new int[Devices.FRAMES];
	private final int[] arrowPending = new int[Devices.FRAMES];
	private final int[] arrowPendingTick = new int[Devices.FRAMES];
	/** Where a click was blocked last and when, for the short red mark. */
	private BlockPos blockedAt;
	private int blockedTick;
	private static final int BLOCKED_MARK_TICKS = 6;
	private List<int[]> layouts;
	private final PuzzleMarks marks = new PuzzleMarks();
	private final MarksDrawing drawing = new MarksDrawing(marks);
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
	private int ticks;

	public F7DevicesFeature() {
		super("dungeon_f7_devices", "Device solver", "F7 device hints on the real blocks.", false);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("list_check");
		summary("Simon Says, arrows, lights, targets");
		badge("P3");
		blockSimon.visibleWhen(simon::get);
		limitStart.visibleWhen(() -> simon.get() && blockSimon.get());
		startClicks.visibleWhen(() -> simon.get() && blockSimon.get() && limitStart.get());
		blockArrows.visibleWhen(arrows::get);
		blockLights.visibleWhen(lights::get);
		blockedSound.visibleWhen(() -> simon.get() && blockSimon.get() || arrows.get() && blockArrows.get() || lights.get() && blockLights.get());
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void left() {
				forgetSimonSays();
				marks.clear();
			}
		});
	}

	/** Starts with the add-on: the key that lets every click through while held, and the player's own clicks on the devices. */
	public void register() {
		addKey(WrongClickKey.key(), KeyPlace.WORLD);
		// a client-side FAIL ends the click before anything is sent; PASS leaves it to the game as it was
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> level.isClientSide() && blocksUse(level.getBlockState(hit.getBlockPos()), hit.getBlockPos())
			? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> level.isClientSide() && blocksUse(entity) ? InteractionResult.FAIL
			: InteractionResult.PASS);
	}

	/** A click that was not sent: a short red mark, the sound if wanted, and the debug log counts it. */
	private void blocked(BlockPos pos, String device, String reason) {
		blockedAt = pos.immutable();
		blockedTick = ticks;
		if (blockedSound.get()) {
			WrongClickKey.playBlocked();
		}
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Devices: {}", WrongClicks.note(device, reason));
		}
	}

	/**
	 * The player's own click on a block, before it is sent: a Simon Says button that is not the next one, the start button pressed too often
	 * in the first phase, or a lever of the Lights device that is already on. Only in Goldor's phase, only for a device whose hints are on.
	 *
	 * @return whether the click is known to be wrong and is not sent
	 */
	private boolean blocksUse(BlockState state, BlockPos pos) {
		if (!isEnabled() || !inGoldor) {
			return false;
		}
		boolean holding = WrongClickKey.holding();
		if (simon.get() && pos.equals(SIMON_START)) {
			boolean firstPhase = simonSays.firstPhase() || ownStarts > 0;
			int presses = Math.max(simonSays.startPresses(), ownStarts);
			if (WrongClicks.simonStart(blockSimon.get() && limitStart.get(), holding, firstPhase, presses, Math.round(startClicks.get()))) {
				blocked(pos, "Simon Says", "start button, " + presses + " presses in this first phase");
				return true;
			}
			ownStarts = presses + 1;
			ownStartTick = ticks;
			return false;
		}
		int row = pos.getY() - SIMON_Y;
		int column = pos.getZ() - SIMON_Z;
		if (simon.get() && pos.getX() == SIMON_BUTTON_X && row >= 0 && row < SIMON_SIDE && column >= 0 && column < SIMON_SIDE) {
			int button = row * SIMON_SIDE + column;
			int place = simonSays.place(button);
			if (WrongClicks.simonButton(blockSimon.get(), holding, simonSays.size(), place, simonAhead)) {
				blocked(pos, "Simon Says", "button " + button + " is not the next one (place " + place + ", " + simonAhead + " on their way)");
				return true;
			}
			if (place == simonAhead && place >= 0) {
				simonAhead++;
				simonAheadTick = ticks;
			}
			return false;
		}
		if (lights.get() && state.is(Blocks.LEVER)) {
			for (BlockPos lever : LIGHTS) {
				if (lever.equals(pos)) {
					if (WrongClicks.lever(blockLights.get(), holding, state.getValue(BlockStateProperties.POWERED))) {
						blocked(pos, "Lights", "the lever is already on");
						return true;
					}
					return false;
				}
			}
		}
		return false;
	}

	/** The player's own click on an entity, before it is sent: a frame of Arrow Align whose arrow already points the right way. */
	private boolean blocksUse(Entity entity) {
		if (!isEnabled() || !inGoldor || !arrows.get() || !(entity instanceof ItemFrame frame)) {
			return false;
		}
		BlockPos pos = frame.blockPosition();
		int index = (pos.getY() - ARROW_Y) + (pos.getZ() - ARROW_Z) * ARROW_SIDE;
		if (pos.getX() != ARROW_X || pos.getY() < ARROW_Y || pos.getY() >= ARROW_Y + ARROW_SIDE || index < 0 || index >= Devices.FRAMES) {
			return false;
		}
		if (WrongClicks.arrow(blockArrows.get(), WrongClickKey.holding(), arrowClicks, index, arrowPending[index])) {
			blocked(pos, "Arrow Align", "frame " + index + " already points the right way");
			return true;
		}
		arrowPending[index]++;
		arrowPendingTick[index] = ticks;
		return false;
	}

	private void forgetSimonSays() {
		simonAhead = 0;
		ownStarts = 0;
		simonSays.reset();
		Arrays.fill(lanternLit, false);
		Arrays.fill(buttonDown, false);
		startDown = false;
		gridGoneTicks = 0;
	}

	/** Reads the devices every tick, only in Goldor's phase. */
	public void tick(Minecraft client, F7Progress.Phase phase) {
		marks.clear();
		if (!isEnabled() || !Dungeon.inBoss() || phase != F7Progress.Phase.GOLDOR || client.level == null || client.player == null) {
			inGoldor = false;
			return;
		}
		if (!inGoldor) {
			// Goldor's phase begins: nothing of an earlier run or phase is left
			inGoldor = true;
			forgetSimonSays();
		}
		ticks++;
		if (simon.get()) {
			simonSays(client);
		}
		// clicks on their way that the world never showed are forgotten, so nothing stays blocked for them
		if (simonAhead > 0 && ticks - simonAheadTick > WrongClicks.AHEAD_TICKS) {
			simonAhead = 0;
		}
		if (ownStarts > 0 && !simonSays.firstPhase() && ticks - ownStartTick > WrongClicks.AHEAD_TICKS) {
			ownStarts = 0;
		}
		if (blockedAt != null && ticks - blockedTick < BLOCKED_MARK_TICKS) {
			marks.block(new int[] {blockedAt.getX(), blockedAt.getY(), blockedAt.getZ()}, LATER, true);
		}
		if (arrows.get()) {
			arrowAlign(client);
		}
		if (lights.get()) {
			for (BlockPos lever : LIGHTS) {
				BlockState state = client.level.getBlockState(lever);
				if (state.is(Blocks.LEVER) && !state.getValue(BlockStateProperties.POWERED)) {
					marks.block(new int[] {lever.getX(), lever.getY(), lever.getZ()}, THEN, true);
				}
			}
		}
		if (sharpshooter.get()) {
			for (BlockPos target : TARGETS) {
				if (client.level.getBlockState(target).is(Blocks.EMERALD_BLOCK)) {
					marks.block(new int[] {target.getX(), target.getY(), target.getZ()}, NEXT, true);
				}
			}
		}
	}

	/**
	 * Reads what the device shows: the start button pressed, a lantern going out (it was shown), a button pressed (by anyone), and whether the
	 * button grid stands. The order, with the correction of a skip at the start, is {@link SimonSays}'s.
	 */
	private void simonSays(Minecraft client) {
		BlockState start = client.level.getBlockState(SIMON_START);
		boolean startNow = start.is(Blocks.STONE_BUTTON) && start.getValue(BlockStateProperties.POWERED);
		boolean wasFirstPhase = simonSays.firstPhase();
		if (startNow && !startDown) {
			simonSays.startPressed();
			simonAhead = 0;
		}
		startDown = startNow;
		int buttons = 0;
		for (int i = 0; i < lanternLit.length; i++) {
			int y = SIMON_Y + i / SIMON_SIDE;
			int z = SIMON_Z + i % SIMON_SIDE;
			boolean lit = client.level.getBlockState(at.set(SIMON_LANTERN_X, y, z)).is(Blocks.SEA_LANTERN);
			if (!lit && lanternLit[i]) {
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.DUNGEON, "Simon Says: lantern {} went out", i);
				}
				simonSays.shown(i);
			}
			lanternLit[i] = lit;
			BlockState button = client.level.getBlockState(at.set(SIMON_BUTTON_X, y, z));
			boolean isButton = button.is(Blocks.STONE_BUTTON);
			buttons += isButton ? 1 : 0;
			boolean down = isButton && button.getValue(BlockStateProperties.POWERED);
			if (down && !buttonDown[i]) {
				simonSays.pressed(i);
				// a press showed: one of the player's own on their way has arrived (none are left when the round is done)
				simonAhead = simonSays.size() == 0 ? 0 : WrongClicks.settle(simonAhead, 1);
			}
			buttonDown[i] = down;
		}
		simonSays.grid(buttons == lanternLit.length);
		if (wasFirstPhase && !simonSays.firstPhase()) {
			ownStarts = 0;
		}
		if (buttons == 0) {
			// no button for a long while: the device is done, or was never started; what is left is forgotten
			if (++gridGoneTicks == SIMON_DONE_TICKS && simonSays.size() > 0) {
				simonSays.reset();
			}
			return;
		}
		gridGoneTicks = 0;
		// the next button, and the two after it dimmer
		for (int i = 0; i < lanternLit.length; i++) {
			int place = simonSays.place(i);
			if (place >= 0 && place < SIMON_SHOWN) {
				marks.block(new int[] {SIMON_BUTTON_X, SIMON_Y + i / SIMON_SIDE, SIMON_Z + i % SIMON_SIDE}, place == 0 ? NEXT : place == 1 ? SOON : AFTER, true);
			}
		}
	}

	private void arrowAlign(Minecraft client) {
		if (client.player.blockPosition().distSqr(ARROW_MIDDLE) > NEAR_SQ) {
			arrowClicks = null;
			Arrays.fill(arrowPending, 0);
			return;
		}
		if (ticks % ARROW_SCAN_TICKS == 0) {
			System.arraycopy(rotations, 0, lastRotations, 0, rotations.length);
			Arrays.fill(rotations, -1);
			AABB grid = new AABB(ARROW_X, ARROW_Y, ARROW_Z, ARROW_X + 1, ARROW_Y + ARROW_SIDE, ARROW_Z + ARROW_SIDE);
			for (Entity entity : client.level.getEntities((Entity) null, grid, entity -> entity instanceof ItemFrame)) {
				ItemFrame frame = (ItemFrame) entity;
				if (!frame.getItem().is(Items.ARROW)) {
					continue;
				}
				BlockPos pos = frame.blockPosition();
				int index = (pos.getY() - ARROW_Y) + (pos.getZ() - ARROW_Z) * ARROW_SIDE;
				if (pos.getX() == ARROW_X && index >= 0 && index < Devices.FRAMES) {
					rotations[index] = frame.getRotation();
				}
			}
			arrowClicks = Devices.arrowClicks(rotations, layouts());
			for (int i = 0; i < Devices.FRAMES; i++) {
				if (arrowPending[i] == 0) {
					continue;
				}
				// a frame that turned shows that many of the player's clicks arrived; clicks never shown are forgotten
				int turned = rotations[i] < 0 || lastRotations[i] < 0 ? 0 : Math.floorMod(rotations[i] - lastRotations[i], Devices.FRAME_STEPS);
				arrowPending[i] = ticks - arrowPendingTick[i] > WrongClicks.AHEAD_TICKS ? 0 : WrongClicks.settle(arrowPending[i], turned);
			}
		}
		if (arrowClicks == null) {
			return;
		}
		for (int i = 0; i < Devices.FRAMES; i++) {
			int clicks = arrowClicks[i];
			if (clicks > 0) {
				int color = clicks < 3 ? NEXT : clicks < 5 ? THEN : LATER;
				marks.label(ARROW_X + 0.9, ARROW_Y + i % ARROW_SIDE + 0.5, ARROW_Z + i / ARROW_SIDE + 0.5, String.valueOf(clicks), color);
			}
		}
	}

	/** The known Arrow Align layouts (Odin's data, BSD-3-Clause, see CREDITS.md), read once. */
	private List<int[]> layouts() {
		if (layouts == null) {
			List<int[]> read = new ArrayList<>();
			JsonElement json = PuzzleData.read("arrow-align-solutions.json");
			if (json != null && json.isJsonArray()) {
				for (JsonElement row : json.getAsJsonArray()) {
					int[] layout = new int[Devices.FRAMES];
					int i = 0;
					for (JsonElement value : row.getAsJsonArray()) {
						if (i < Devices.FRAMES) {
							layout[i++] = value.getAsInt();
						}
					}
					read.add(layout);
				}
			}
			layouts = read;
		}
		return layouts;
	}

	public void draw(LevelRenderContext context) {
		drawing.draw(context, FILL);
	}

	public void drawLabels(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		drawing.drawLabels(graphics);
	}
}
