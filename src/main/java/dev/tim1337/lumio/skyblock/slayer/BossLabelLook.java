package dev.tim1337.lumio.skyblock.slayer;

/** What the label at a boss shows, as bits, and how tall that makes it. Plain numbers, so it is unit tested. */
final class BossLabelLook {
	static final int COUNT = 1;
	static final int KIND = 2;
	static final int VALUE = 4;
	static final int BAR = 8;
	static final int STEPS = 16;
	static final int ALL = COUNT | KIND | VALUE | BAR | STEPS;

	private BossLabelLook() {
	}

	/**
	 * @param parts what it shows
	 * @param card  whether it is the card, with a row for each, or a style with one line of text
	 * @return its height from the heights of its rows
	 */
	static float height(int parts, boolean card, float top, float valueRow, float barRow, float stepsRow, float lineRow, float bottom) {
		boolean first = (parts & (KIND | VALUE)) != 0;
		boolean last = (parts & (COUNT | STEPS)) != 0;
		float height = top + bottom;
		if (card) {
			height += first ? valueRow : 0f;
			height += last ? stepsRow : 0f;
		} else if (first || last) {
			height += lineRow;
		}
		return height + ((parts & BAR) != 0 ? barRow : 0f);
	}
}
