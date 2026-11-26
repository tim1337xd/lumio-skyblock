package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** One puzzle solver: it works only while the player is in its room, and only marks; the player does every click. */
public interface RoomPuzzle {
	/** @return the name of the puzzle's room in the room data */
	String room();

	/** Looks at the room a few times a second and marks what helps. */
	void tick(Minecraft client, PuzzleMarks marks);

	/** A block the player used (a lever, a button, a block of the puzzle). */
	default void used(BlockPos pos) {
	}

	/** A tick of the server, for timers. */
	default void serverTick() {
	}

	/** The run ended or the room was left for good: everything worked out is forgotten. */
	void reset();
}
