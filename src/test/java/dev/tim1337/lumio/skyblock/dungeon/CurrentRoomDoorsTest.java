package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Only the doorways of the room the player is in count as its doors: never those of the next rooms, even when the map knows them. */
class CurrentRoomDoorsTest {
	private static final int HALF_TILE = CurrentRoom.TILE / 2;

	@Test
	void onlyTheDoorsOfTheCurrentRoom() {
		// a 1x1 room on the tile (2, 3), with a door east and one south of it
		int tileX = 2;
		int tileZ = 3;
		int middleX = CurrentRoom.middleOf(tileX);
		int middleZ = CurrentRoom.middleOf(tileZ);
		int minX = middleX - RoomLayout.CORNER;
		int maxX = middleX + RoomLayout.CORNER + 1;
		int minZ = middleZ - RoomLayout.CORNER;
		int maxZ = middleZ + RoomLayout.CORNER + 1;
		int[][] own = {{middleX + HALF_TILE, middleZ}, {middleX, middleZ + HALF_TILE}};
		// three more the map knows: beyond the next tile east, south of the next tile south, and two tiles west
		int[][] others = {{middleX + 3 * HALF_TILE, middleZ}, {middleX, middleZ + 3 * HALF_TILE}, {middleX - 3 * HALF_TILE, middleZ}};
		int outlined = 0;
		for (int[] door : own) {
			outlined += CurrentRoom.doorOf(door[0], door[1], minX, maxX, minZ, maxZ) ? 1 : 0;
		}
		for (int[] door : others) {
			outlined += CurrentRoom.doorOf(door[0], door[1], minX, maxX, minZ, maxZ) ? 1 : 0;
		}
		assertEquals(2, outlined);
	}
}
