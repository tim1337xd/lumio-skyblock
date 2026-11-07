package dev.tim1337.lumio.skyblock.mining;

/**
 * The simple row of the pickaxe ability, laid out from what is in it: without a name only the icon, the bar and the status, with one as wide as the whole name, and with a width the player set the name shrinks a little before it is ever cut. Plain
 * numbers, so it is unit tested.
 *
 * @param width     the row's width
 * @param nameX     where the name starts
 * @param nameRoom  the room the name has
 * @param nameScale how far the name is scaled, from 1 down
 * @param cut       whether the name still does not fit, so it ends in an ellipsis
 * @param barX      where the bar starts
 * @param barWidth  how wide the bar is
 * @param statusEnd where the status ends: it is right-aligned there, whatever it says
 */
record AbilityRow(int width, float nameX, float nameRoom, float nameScale, boolean cut, float barX, float barWidth, float statusEnd) {
	static final int TILE = 11;
	static final float TILE_LEFT = 3f;
	private static final float AFTER_TILE = 5f;
	private static final float NO_ICON_LEFT = 5f;
	private static final float NAME_GAP = 5f;
	private static final float STATUS_GAP = 5f;
	private static final float RIGHT = 5f;
	/** The bar with a width of its own: what it has where the row grows to its content, and the least it keeps where the width is set. */
	static final float BAR = 36f;
	static final float LEAST_BAR = 20f;
	/** How far a name shrinks before it is cut. */
	static final float LEAST_SCALE = 0.8f;

	/**
	 * @param nameWidth  the whole name's width at its usual size, 0 without a name
	 * @param statusSlot the room of the status, as wide as what it can say at most, so that it does not move when it changes
	 * @param icon       whether the icon is shown
	 * @param setWidth   the width the player set, or 0 for as wide as the content
	 */
	static AbilityRow of(float nameWidth, float statusSlot, boolean icon, int setWidth) {
		float nameX = icon ? TILE_LEFT + TILE + AFTER_TILE : NO_ICON_LEFT;
		float around = nameX + (nameWidth > 0f ? NAME_GAP : 0f) + STATUS_GAP + statusSlot + RIGHT;
		int width = setWidth > 0 ? setWidth : (int) Math.ceil(around + nameWidth + BAR);
		float room = Math.max(0f, width - around - LEAST_BAR);
		float scale = 1f;
		float nameRoom = nameWidth;
		boolean cut = false;
		if (nameWidth > room) {
			scale = Math.max(LEAST_SCALE, room / nameWidth);
			nameRoom = Math.min(nameWidth * scale, room);
			cut = nameWidth * LEAST_SCALE > room;
		}
		float barX = nameX + nameRoom + (nameWidth > 0f ? NAME_GAP : 0f);
		float statusEnd = width - RIGHT;
		return new AbilityRow(width, nameX, nameRoom, scale, cut, barX, statusEnd - statusSlot - STATUS_GAP - barX, statusEnd);
	}
}
