package dev.tim1337.lumio.skyblock.hud;

/**
 * The sizes of the item grids in the item search's panels: a tile per item, a cell with its gap, and how many columns and rows a panel of a size
 * holds. The Items panel snaps to whole cells, so no item is cut off. Plain numbers, so it is unit tested.
 */
final class ItemGrid {
	/** An item's tile at item size 1, and the gap between tiles. */
	static final int BASE_TILE = 20;
	static final int TILE_GAP = 6;
	/** The Items panel's least grid: fewer columns or rows are not worth a panel. */
	static final int LEAST_COLUMNS = 3;
	static final int LEAST_ROWS = 2;
	/** The room the Items panel keeps right of its grid for the scrollbar. */
	static final int SCROLLBAR_ROOM = 6;

	private ItemGrid() {
	}

	static int tile(float scale) {
		return Math.max(8, Math.round(BASE_TILE * scale));
	}

	static int cell(float scale) {
		return tile(scale) + TILE_GAP;
	}

	/** @return how many columns fit in a width */
	static int columns(int width, float scale) {
		return Math.max(1, (width + TILE_GAP) / cell(scale));
	}

	/** @return how many rows fit in a height */
	static int rows(int height, float scale) {
		return Math.max(0, (height + TILE_GAP) / cell(scale));
	}

	/** @return the width of so many columns, without padding */
	static int span(int count, float scale) {
		return Math.max(0, count * cell(scale) - TILE_GAP);
	}

	/** @return the Items panel's width for so many columns: its padding, the grid and the scrollbar's room */
	static int itemsWidth(int columns, float scale) {
		return 2 * PanelFrame.PAD + span(columns, scale) + SCROLLBAR_ROOM;
	}

	/** @return the Items panel's height for so many rows: its header, the grid and the padding */
	static int itemsHeight(int rows, float scale) {
		return PanelFrame.HEADER + span(rows, scale) + PanelFrame.PAD;
	}

	/** @return the Items panel's grid in a panel of a size: columns and rows */
	static int[] itemsGrid(int width, int height, float scale) {
		int columns = columns(width - 2 * PanelFrame.PAD - SCROLLBAR_ROOM, scale);
		int rows = rows(height - PanelFrame.HEADER - PanelFrame.PAD, scale);
		return new int[]{columns, rows};
	}

	/**
	 * Snaps a size the player pulls the Items panel to, to whole items: the nearest number of columns and rows, at least the least grid.
	 *
	 * @return width, height, columns, rows
	 */
	static int[] snapItems(int width, int height, float scale) {
		int cell = cell(scale);
		int columns = Math.max(LEAST_COLUMNS, Math.round((width - 2 * PanelFrame.PAD - SCROLLBAR_ROOM + TILE_GAP) / (float) cell));
		int rows = Math.max(LEAST_ROWS, Math.round((height - PanelFrame.HEADER - PanelFrame.PAD + TILE_GAP) / (float) cell));
		return new int[]{itemsWidth(columns, scale), itemsHeight(rows, scale), columns, rows};
	}

	/** Trims an Items panel the fit made smaller to whole items. @return the trimmed box, or the box as it was when it is below the least grid */
	static int[] trimItems(int[] box, float scale) {
		int[] grid = itemsGrid(box[2], box[3], scale);
		if (grid[0] < LEAST_COLUMNS || grid[1] < LEAST_ROWS) {
			return box;
		}
		return new int[]{box[0], box[1], itemsWidth(grid[0], scale), itemsHeight(grid[1], scale)};
	}

	/** @return the badge while pulling the Items panel: "7 × 8 · 56 shown" */
	static String badge(int columns, int rows) {
		return columns + " × " + rows + " · " + columns * rows + " shown";
	}
}
