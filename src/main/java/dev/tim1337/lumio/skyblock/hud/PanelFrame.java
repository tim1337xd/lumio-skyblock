package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

/**
 * The frame every panel of the item search shares: only a slim header with a calm dark body (a grip of six dots, an icon, the title, a quiet count
 * and, for Pinned and Recent, the arrow that folds the panel up to its header); the items sit directly over the game. No frame under the mouse:
 * only the grip lights up, and the small resize handle shows while the mouse is right at the bottom-right corner. Picked up, it lifts (a stronger shadow and an accent border); a faint
 * outline stays where it was. Also where a point lies on it: the grip (the only place that moves it), the arrow, or an edge or corner (to pull it).
 */
final class PanelFrame {
	static final int PAD = 8;
	/**
	 * The header's slim bar, about as tall as the menu's tabs: its grip, icon, title and small controls are centered on it. The content starts a
	 * small gap below it. A collapsed panel is only this bar.
	 */
	static final int BAR = 16;
	private static final int BAR_GAP = 3;
	/** The header: from the panel's top to where its content starts. */
	static final int HEADER = BAR + BAR_GAP;
	/** A line of quiet text in a panel (an empty panel's hint), for where its content ends. */
	static final int TEXT_LINE = 14;
	static final float RADIUS = Radius.LG;
	/** The header's icon size, and the room after the grip and after the icon. */
	private static final int ICON = 8;
	private static final int AFTER_GRIP = 4;
	private static final int AFTER_ICON = 5;
	private static final int AFTER_TITLE = 5;
	/** A text's top above the middle of its row: its letters' height, halved, at the body and small sizes. */
	static final float BODY_HALF = 4f;
	static final float SMALL_HALF = 3.5f;
	/** The grip of six dots: two columns, three rows. */
	private static final int GRIP_DOT = 1;
	private static final int GRIP_STEP = 3;
	private static final float GRIP_OPACITY = 0.35f;
	/** The grip's size as drawn. */
	static final int GRIP_WIDTH = GRIP_STEP + GRIP_DOT + 1;
	static final int GRIP_HEIGHT = 2 * GRIP_STEP + GRIP_DOT + 1;
	/** How far around its dots the grip takes the mouse, and the soft fill under it while the mouse is on it. */
	private static final int GRIP_REACH = 4;
	private static final int GRIP_FILL_ROOM = 2;
	private static final float GRIP_LIT = 0.9f;
	private static final float GRIP_FILL = 0.12f;
	/** The fold arrow at the header's right end: its box, the chevron in it, and the room it leaves the header's other controls. */
	static final int ARROW = 11;
	private static final int CHEVRON = 6;
	private static final int ARROW_GAP = 3;
	private static final float ARROW_HOVER = 0.1f;
	/** How far inside and outside an edge a point counts as on it, and the corner's size. */
	private static final int EDGE_REACH = 3;
	private static final int CORNER = 9;
	/** The resize handle's two strokes, and how far in from the corner they start. */
	private static final int RESIZE_LONG = 6;
	private static final int RESIZE_SHORT = 3;
	private static final int RESIZE_INSET = 4;
	/** The resize handle shows while the mouse is this near the panel's bottom-right corner. */
	private static final int CORNER_REACH = 8;
	private static final float RESIZE_OPACITY = 0.3f;
	/** The lifted panel's shadow (how far it falls and spreads), the glow of a ringed one, and the outline left where it was. */
	private static final int LIFT_SHADOW = 0x8C000000;
	private static final float LIFT_DROP = 6f;
	private static final float LIFT_SPREAD = 10f;
	private static final float RING_GLOW_SPREAD = 6f;
	private static final float RING_GLOW = 0.18f;
	private static final float LEAST_HIGHLIGHT = 0.01f;
	private static final float GHOST_FILL = 0.04f;
	private static final float GHOST_LINE = 0.18f;
	/** The calm body: a dark fill (a light one in a light theme) with a breath of the accent, and no outline. */
	private static final int BODY_DARK = 0xFF121118;
	private static final int BODY_LIGHT = 0xFFF1F1F4;
	private static final float BODY_ACCENT = 0.1f;
	private static final float BODY_OPACITY = 0.9f;
	private static final float LIGHT_TEXT = 0.5f;
	/** A quarter turn: the fold arrow points down while open, to the right while folded. */
	private static final float QUARTER_TURN = (float) (Math.PI / 2.0);

	/** The edges of a panel a point lies on, as flags. */
	static final int LEFT = 1;
	static final int TOP = 2;
	static final int RIGHT = 4;
	static final int BOTTOM = 8;

	/** Where the mouse is this frame, and whether the panels can be moved (the layout is not locked and nothing else is held). */
	private static int pointerX;
	private static int pointerY;
	private static boolean pointerFree;

	private PanelFrame() {
	}

	/** Notes the mouse for the frame: a panel under it shows its soft border and its resize handle, a grip under it lights up. */
	static void pointer(int mouseX, int mouseY, boolean free) {
		pointerX = mouseX;
		pointerY = mouseY;
		pointerFree = free;
	}

	/** Draws the calm body every part of the item search shares: no outline, no glass edge. */
	static void body(Canvas ui, int x, int y, int width, int height, float radius, float opacity) {
		int base = Draw.luminance(HudTheme.text()) > LIGHT_TEXT ? BODY_DARK : BODY_LIGHT;
		ui.rounded(x, y, width, height, radius, Draw.withOpacity(Draw.mix(base, ui.accent() | 0xFF000000, BODY_ACCENT), Math.min(1f, opacity)));
	}

	/** @return how opaque the bodies are: see-through at the chosen strength */
	static float bodyOpacity() {
		return BODY_OPACITY * ItemSearchLook.strength();
	}

	/**
	 * Draws a panel: only its slim header bar has a body, the content (the items) sits directly over the game under it. No frame at rest or under
	 * the mouse; only the grip brightens, and the resize handle shows while the mouse is right at the bottom-right corner. Picked up it lifts and is
	 * ringed in the accent.
	 *
	 * @param icon      the header's icon, or null
	 * @param meta      the quiet count after the title, or empty
	 * @param highlight 0 for none, else how strongly it is ringed in the accent (lifted, or a drop target)
	 * @param resizable whether its height can be pulled: a collapsed panel's cannot
	 */
	static void draw(Canvas ui, int[] box, String icon, int iconColor, String title, String meta, float highlight, boolean lifted, boolean resizable) {
		contentBottom = box[1] + BAR;
		int[] frame = area != null ? area : box;
		area = null;
		if (lifted) {
			ui.softLight(box[0] + box[2] / 2f, box[1] + box[3] / 2f + LIFT_DROP, box[2] / 2f + LIFT_SPREAD, box[3] / 2f + LIFT_SPREAD, LIFT_SHADOW);
		}
		body(ui, box[0], box[1], box[2], BAR, Radius.SM, bodyOpacity());
		outline(ui, frame, highlight, resizable);
		if (title != null) {
			header(ui, box, icon, iconColor, title, meta);
		} else {
			headerGrip(ui, box);
		}
	}

	/** The accent ring of a lifted panel or a drop target; the resize handle while the mouse is right at the bottom-right corner (or while lifted). */
	private static void outline(Canvas ui, int[] box, float highlight, boolean resizable) {
		boolean over = pointerX >= box[0] && pointerX < box[0] + box[2] && pointerY >= box[1] && pointerY < box[1] + box[3];
		boolean onGrip = over && pointerFree && onGrip(box, pointerX, pointerY);
		boolean ringed = highlight > LEAST_HIGHLIGHT;
		if (ringed) {
			ui.softLight(box[0] + box[2] / 2f, box[1] + box[3] / 2f, box[2] / 2f + RING_GLOW_SPREAD, box[3] / 2f + RING_GLOW_SPREAD,
				Draw.withOpacity(ui.accent(), RING_GLOW * highlight));
			ui.ring(box[0], box[1], box[2], box[3], RADIUS, 1, Draw.withOpacity(ui.accent(), highlight));
		}
		boolean atCorner = Math.abs(pointerX - (box[0] + box[2])) <= CORNER_REACH && Math.abs(pointerY - (box[1] + box[3])) <= CORNER_REACH;
		if (resizable && pointerFree && (atCorner && !onGrip || ringed)) {
			int right = box[0] + box[2] - RESIZE_INSET;
			int bottom = box[1] + box[3] - RESIZE_INSET;
			int color = Draw.withOpacity(HudTheme.text(), RESIZE_OPACITY);
			diagonal(ui, right, bottom, RESIZE_LONG, color);
			diagonal(ui, right, bottom, RESIZE_SHORT, color);
		}
	}

	/** The header's row: the grip, the icon, the title and the count. */
	private static void header(Canvas ui, int[] box, String icon, int iconColor, String title, String meta) {
		int middle = middle(box);
		headerGrip(ui, box);
		int x = box[0] + PAD + GRIP_WIDTH + AFTER_GRIP;
		if (icon != null) {
			ui.icon(icon, x, middle - ICON / 2, ICON, iconColor);
			x += ICON + AFTER_ICON;
		}
		ui.textAtScaledBold(title, x, middle - BODY_HALF, TextScale.BODY, HudTheme.text());
		if (meta != null && !meta.isEmpty()) {
			float titleWidth = ui.boldWidthScaled(title, TextScale.BODY);
			ui.textAtScaled(meta, x + titleWidth + AFTER_TITLE, middle - SMALL_HALF, TextScale.SMALL, HudTheme.secondary());
		}
	}

	/** @return where a header's own controls start, from the left: the room after its grip */
	static int afterGrip(int[] box) {
		return box[0] + PAD + GRIP_WIDTH + AFTER_GRIP;
	}

	/** A panel's grip at the start of its header: the one place it is picked up by. Under the mouse it brightens on a soft fill. */
	static void headerGrip(Canvas ui, int[] box) {
		int x = box[0] + PAD;
		int y = middle(box) - GRIP_HEIGHT / 2;
		boolean over = pointerFree && onGrip(box, pointerX, pointerY);
		if (over) {
			ui.rounded(x - GRIP_REACH + 1, y - GRIP_FILL_ROOM, GRIP_WIDTH + 2 * GRIP_REACH - 2, GRIP_HEIGHT + 2 * GRIP_FILL_ROOM, Radius.XS,
				Draw.withOpacity(HudTheme.text(), GRIP_FILL));
			ui.graphics().requestCursor(CursorTypes.RESIZE_ALL);
		}
		grip(ui, x, y, over ? GRIP_LIT : GRIP_OPACITY);
	}

	/** @return whether a point is on a panel's grip: only there a press picks the panel up */
	static boolean onGrip(int[] box, double x, double y) {
		return x >= box[0] + PAD - GRIP_REACH && x < box[0] + PAD + GRIP_WIDTH + GRIP_REACH && y >= box[1] && y < box[1] + BAR;
	}

	/** Where the content of the panel being drawn ends, as it is drawn: the panel's area is fitted to it (see {@link ItemPanels}). */
	private static int contentBottom;
	/** The area the next panel drawn takes, fitted to its content last frame, or null for its whole box. */
	private static int[] area;

	/** The next panel drawn takes this area for its resize corner and accent ring: its content, not its whole box. */
	static void useArea(int[] fitted) {
		area = fitted;
	}

	/** Notes that the panel being drawn has content down to here. */
	static void noteContent(int bottom) {
		contentBottom = Math.max(contentBottom, bottom);
	}

	/** @return where the content of the panel drawn last ends */
	static int contentBottom() {
		return contentBottom;
	}

	/** The fold arrow at the header's right end: down while the panel is open, to the right while it is folded up. */
	static void collapseArrow(Canvas ui, int[] box, boolean collapsed) {
		int x = box[0] + box[2] - PAD - ARROW;
		int y = middle(box) - ARROW / 2;
		boolean over = onCollapse(box, pointerX, pointerY);
		if (over) {
			ui.rounded(x, y, ARROW, ARROW, Radius.XS, Draw.withOpacity(HudTheme.text(), ARROW_HOVER));
		}
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(x + ARROW / 2f, y + ARROW / 2f);
		if (collapsed) {
			pose.rotate(-QUARTER_TURN);
		}
		ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, over ? HudTheme.text() : HudTheme.secondary());
		pose.popMatrix();
	}

	/** @return whether a point is on a panel's fold arrow: from it to the panel's right edge, the height of the header bar */
	static boolean onCollapse(int[] box, double x, double y) {
		int right = box[0] + box[2];
		return x >= right - PAD - ARROW - ARROW_GAP && x < right - EDGE_REACH && y >= box[1] && y < box[1] + BAR;
	}

	/**
	 * @param collapses whether the panel has the fold arrow
	 * @return where the header's controls on the right end, left of the fold arrow
	 */
	static int controlsRight(int[] box, boolean collapses) {
		return box[0] + box[2] - PAD - (collapses ? ARROW + ARROW_GAP : 0);
	}

	/** @return the middle of a panel's header bar, where everything in it is centered */
	static int middle(int[] box) {
		return box[1] + BAR / 2;
	}

	/** Puts a small control's box into {@code into}: ending at {@code right}, centered on the header's bar. */
	static void headerControl(int[] box, int right, int width, int height, int[] into) {
		into[0] = right - width;
		into[1] = middle(box) - height / 2;
		into[2] = width;
		into[3] = height;
	}

	/** Six dots in two columns: the sign that the panel can be picked up. */
	static void grip(Canvas ui, int x, int y) {
		grip(ui, x, y, GRIP_OPACITY);
	}

	static void grip(Canvas ui, int x, int y, float opacity) {
		int color = Draw.withOpacity(HudTheme.text(), opacity);
		for (int column = 0; column < 2; column++) {
			for (int row = 0; row < 3; row++) {
				ui.fill(x + column * GRIP_STEP, y + row * GRIP_STEP, GRIP_DOT + 1, GRIP_DOT + 1, color);
			}
		}
	}

	/** One stroke of the resize grip, from the corner up to the left, in steps of a pixel. */
	private static void diagonal(Canvas ui, int right, int bottom, int length, int color) {
		for (int i = 0; i < length; i++) {
			ui.fill(right - i - 1, bottom - length + i, 1, 1, color);
		}
	}

	/** The faint outline that stays where a picked-up panel was. */
	static void ghost(Canvas ui, int[] box) {
		ui.rounded(box[0], box[1], box[2], box[3], RADIUS, Draw.withOpacity(HudTheme.text(), GHOST_FILL));
		ui.ring(box[0], box[1], box[2], box[3], RADIUS, 1, Draw.withOpacity(HudTheme.text(), GHOST_LINE));
	}

	/**
	 * @param tall whether its height can be pulled; else only its sides
	 * @return the edges a point is on, as flags (the bottom-right corner is both), or 0
	 */
	static int edgesAt(int[] box, double x, double y, boolean tall) {
		int left = box[0];
		int top = box[1];
		int right = box[0] + box[2];
		int bottom = box[1] + box[3];
		if (x < left - EDGE_REACH || x >= right + EDGE_REACH || y < top - EDGE_REACH || y >= bottom + EDGE_REACH) {
			return 0;
		}
		if (tall && x >= right - CORNER && y >= bottom - CORNER) {
			return RIGHT | BOTTOM;
		}
		int edges = 0;
		if (Math.abs(x - left) <= EDGE_REACH) {
			edges |= LEFT;
		} else if (Math.abs(x - right) <= EDGE_REACH) {
			edges |= RIGHT;
		}
		// the top edge only just above the panel: just inside it is the header
		if (tall && Math.abs(y - bottom) <= EDGE_REACH) {
			edges |= BOTTOM;
		} else if (tall && y < top + 1) {
			edges |= TOP;
		}
		return edges;
	}

	/**
	 * The height a panel keeps in its place: its full height also while it is folded up, so it opens again as tall as it was.
	 *
	 * @param collapsed whether it is folded up to its header bar
	 * @param shown     its height as drawn now (only the header bar while folded)
	 * @param full      its full height
	 */
	static int keptHeight(boolean collapsed, int shown, int full) {
		return collapsed ? Math.max(shown, full) : shown;
	}
}
