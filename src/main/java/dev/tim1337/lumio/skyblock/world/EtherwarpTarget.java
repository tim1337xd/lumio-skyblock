package dev.tim1337.lumio.skyblock.world;

import dev.tim1337.lumio.skyblock.data.ItemGains;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Finds where an etherwarp lands, the way the server decides it: a ray from the eyes along the look direction, block by block, up to the item's
 * reach. The first block the ray cannot pass is the target, and the warp works when the two blocks above it (above its collision top, for fences
 * and walls) leave room for the feet and the head. Which blocks the ray passes and which take room follows the rules NoammAddons and Odin
 * worked out; this is our own implementation of them.
 * <p>
 * Used on the render thread only. The block rules are computed once per block state and kept in a table, so a ray allocates nothing.
 */
final class EtherwarpTarget {
	/** Etherwarp reach without Tuned Transmission; each tuner adds one block. */
	private static final int BASE_RANGE_BLOCKS = 57;
	/** Bound for the tuner count read from the item, which is external data; the game allows only a few. */
	private static final int MAX_TUNERS = 10;
	/** Upper bound of the steps of one ray: a ray of the longest reach crosses far fewer blocks. */
	private static final int MAX_STEPS = 1000;

	private static final String CONDUIT = "ETHERWARP_CONDUIT";

	// the rules of a block state, as bits; COMPUTED tells a computed entry from an empty one
	private static final int COMPUTED = 1;
	private static final int AIR = 1 << 1;
	/** The ray goes through it. */
	private static final int PASSABLE = 1 << 2;
	/** Takes the room the feet or the head would need. */
	private static final int OBSTRUCTS = 1 << 3;
	/** The bits above hold how many blocks above this one the feet would be: 1, or 2 for blocks taller than a block (fences, walls). */
	private static final int STAND_SHIFT = 4;
	private static int[] rules;

	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	private boolean found;
	private boolean valid;

	/** The custom data of the item last checked, and what it allows; an item's data never changes, a changed item gets new data. */
	private CustomData checkedData;
	private int checkedRange;
	private boolean checkedConduit;

	/**
	 * @return the etherwarp reach of the item in blocks: an Aspect of the Void or End with Etherwarp merged, or an Etherwarp Conduit, plus one per
	 *         Tuned Transmission; 0 for any other item
	 */
	int range(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == checkedData) {
			return checkedRange;
		}
		checkedData = data;
		checkedRange = 0;
		checkedConduit = false;
		String id = data == null ? null : ItemGains.idOf(stack);
		if (id == null) {
			return 0;
		}
		boolean conduit = id.equals(CONDUIT);
		if (!conduit && !id.equals("ASPECT_OF_THE_VOID") && !id.equals("ASPECT_OF_THE_END")) {
			return 0;
		}
		CompoundTag tag = data.copyTag();
		if (!conduit && tag.getIntOr("ethermerge", 0) != 1) {
			return 0;
		}
		checkedConduit = conduit;
		checkedRange = BASE_RANGE_BLOCKS + Mth.clamp(tag.getIntOr("tuned_transmission", 0), 0, MAX_TUNERS);
		return checkedRange;
	}

	/** @return whether the item last passed to {@link #range} only etherwarps while sneaking (every one but the conduit) */
	boolean needsSneak() {
		return !checkedConduit;
	}

	/** @return whether the last ray hit a block within reach */
	boolean found() {
		return found;
	}

	/** @return whether the etherwarp to the last ray's block works */
	boolean valid() {
		return valid;
	}

	/** @return the block the last ray hit; only meaningful while {@link #found} */
	BlockPos pos() {
		return pos;
	}

	/** Casts a ray from a start point along a vector whose length is the reach; read the result with {@link #found}, {@link #valid}, {@link #pos}. */
	void cast(ClientLevel level, double startX, double startY, double startZ, double alongX, double alongY, double alongZ) {
		found = false;
		valid = false;
		ClientChunkCache chunks = level.getChunkSource();
		int x = Mth.floor(startX);
		int y = Mth.floor(startY);
		int z = Mth.floor(startZ);
		int stepX = (int) Math.signum(alongX);
		int stepY = (int) Math.signum(alongY);
		int stepZ = (int) Math.signum(alongZ);
		// Amanatides and Woo: the ray parameter (0 at the start, 1 at the end) where it next crosses a block border on each axis, and how much it
		// grows from one border to the next
		double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / alongX);
		double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / alongY);
		double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / alongZ);
		double nextX = stepX > 0 ? (x + 1 - startX) * deltaX : stepX < 0 ? (startX - x) * deltaX : Double.MAX_VALUE;
		double nextY = stepY > 0 ? (y + 1 - startY) * deltaY : stepY < 0 ? (startY - y) * deltaY : Double.MAX_VALUE;
		double nextZ = stepZ > 0 ? (z + 1 - startZ) * deltaZ : stepZ < 0 ? (startZ - z) * deltaZ : Double.MAX_VALUE;
		// counting the blocks to the end is exact, where comparing positions could miss the last block by rounding
		int remaining = Math.abs(Mth.floor(startX + alongX) - x) + Math.abs(Mth.floor(startY + alongY) - y)
			+ Math.abs(Mth.floor(startZ + alongZ) - z);
		for (int step = 0; step < MAX_STEPS; step++) {
			LevelChunk chunk = chunks.getChunkNow(x >> 4, z >> 4);
			if (chunk == null) {
				return; // not loaded: nothing is known there
			}
			int here = rulesOf(chunk.getBlockState(pos.set(x, y, z)));
			if ((here & PASSABLE) == 0) {
				found = true;
				int feetY = y + (here >>> STAND_SHIFT);
				valid = !obstructs(chunk, x, feetY, z) && !obstructs(chunk, x, feetY + 1, z);
				return;
			}
			if (remaining-- == 0) {
				// the reach ends inside a passable block such as tall grass: the server targets it and the warp fails; in air there is no target
				found = (here & AIR) == 0;
				return;
			}
			if (nextX <= nextY && nextX <= nextZ) {
				nextX += deltaX;
				x += stepX;
			} else if (nextY <= nextZ) {
				nextY += deltaY;
				y += stepY;
			} else {
				nextZ += deltaZ;
				z += stepZ;
			}
		}
	}

	/** @return whether the block takes the room the feet or the head need; the blocks above the target are in the same chunk column */
	private boolean obstructs(LevelChunk chunk, int x, int y, int z) {
		return (rulesOf(chunk.getBlockState(probe.set(x, y, z))) & OBSTRUCTS) != 0;
	}

	private static int rulesOf(BlockState state) {
		int id = Block.getId(state);
		if (id < 0) {
			return computeRules(state);
		}
		int[] table = rules;
		if (table == null || id >= table.length) {
			int[] grown = new int[Math.max(Block.BLOCK_STATE_REGISTRY.size(), id + 1)];
			if (table != null) {
				System.arraycopy(table, 0, grown, 0, table.length);
			}
			rules = table = grown;
		}
		int found = table[id];
		if (found == 0) {
			table[id] = found = computeRules(state);
		}
		return found;
	}

	private static int computeRules(BlockState state) {
		try {
			Block block = state.getBlock();
			VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			// signs, banners, tripwire hooks and pressure plates stop the ray, but take no room above the block they are on
			boolean noRoom = block instanceof SignBlock || block instanceof AbstractBannerBlock || block instanceof TripWireHookBlock
				|| block instanceof BasePressurePlateBlock;
			// small blocks the ray goes through although they have a hitbox
			boolean thin = block instanceof ButtonBlock || block instanceof AbstractSkullBlock || block instanceof LadderBlock
				|| block instanceof BubbleColumnBlock || block instanceof FlowerPotBlock || block instanceof PistonHeadBlock
				|| block instanceof LeverBlock || block instanceof NetherWartBlock || block instanceof DiodeBlock || block instanceof RedstoneTorchBlock;
			boolean passable = !noRoom && (thin || collision.isEmpty());
			// some of those still take the room of the feet
			boolean blocksFeet = block instanceof AbstractSkullBlock || block instanceof DiodeBlock || block instanceof FlowerPotBlock
				|| block instanceof LadderBlock;
			int stand = collision.isEmpty() ? 1 : Math.max(1, (int) Math.ceil(collision.max(Direction.Axis.Y)));
			return COMPUTED | stand << STAND_SHIFT | (state.isAir() ? AIR : 0) | (passable ? PASSABLE : 0)
				| ((!passable && !noRoom) || blocksFeet ? OBSTRUCTS : 0);
		} catch (RuntimeException e) {
			// a block that cannot answer without a real world: treat it as a full block
			return COMPUTED | 1 << STAND_SHIFT | OBSTRUCTS;
		}
	}
}
