package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * An item's tile in the item search's panels and its window: Lumio's slot with a line in the item's rarity color, the item in its exact middle, and a
 * pet's candy. A hovered tile gets a soft fill in the menu's accent and nothing else: no border, no rise. In the panels the slot and the line are
 * the player's choice (the slot is off at first, the item floats on the panel).
 * <p>
 * Drawn here rather than with the API's tile, which lifts the item a pixel above the middle (room for the rarity line) and moves a hovered tile by a
 * fraction of a pixel. The item is placed on whole screen pixels at every GUI scale, so it sits in the middle of its slot without blur.
 */
final class ItemTiles {
	/** The game draws an item 16 GUI pixels wide. */
	static final int ITEM = 16;
	/** The room kept between the item and its slot's edge, the largest the item grows to, and the tile sizes it stays at its own size in. */
	private static final int INSET = 2;
	private static final float MOST_SCALE = 1.25f;
	private static final int NATIVE_FROM = ITEM + INSET;
	private static final int NATIVE_TO = ITEM + 3 * INSET;
	/** The slot's own edge, the hovered tile's accent fill, and the rarity glow and line. */
	private static final float EDGE = 0.08f;
	private static final float HOVER_FILL = 0.3f;
	private static final float GLOW = 0.42f;
	private static final float GLOW_OPACITY = 0.16f;
	private static final float RARITY_LINE = 0.42f;
	private static final float RARITY_LINE_HEIGHT = 1.2f;
	private static final float RARITY_LINE_FROM_BOTTOM = 2.2f;
	private static final float RARITY_OPACITY = 0.9f;
	private static final int LEAST_RARITY_LINE = 4;

	private ItemTiles() {
	}

	/**
	 * @param hover how far it is hovered, from 0 to 1: the accent fill
	 * @param ring  its accent ring, from 0 to 1 (the first result, a match of the search)
	 */
	static void draw(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Entry entry, int x, int y, int size, float hover, float ring) {
		draw(ui, graphics, entry, x, y, size, hover, ring, true, true);
	}

	/** A tile in the panels Items, Pinned and Recent: the item alone, without a slot background or a rarity line, over the game. */
	static void drawInPanel(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Entry entry, int x, int y, int size, float hover, float ring) {
		PanelFrame.noteContent(y + size);
		draw(ui, graphics, entry, x, y, size, hover, ring, false, false);
	}

	private static void draw(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Entry entry, int x, int y, int size, float hover, float ring, boolean slot,
		boolean rarityLine) {
		int rarity = ItemTypes.of(entry).rank() >= 0 ? 0xFF000000 | entry.rarityColor() : 0;
		if (slot) {
			ui.rounded(x, y, size, size, Radius.SM, Draw.mix(HudTheme.slot(), HudTheme.text(), 0.03f));
		}
		if (hover > 0.01f) {
			ui.rounded(x, y, size, size, Radius.SM, Draw.withOpacity(ui.accent(), HOVER_FILL * hover));
		}
		if (slot) {
			ui.ring(x, y, size, size, Radius.SM, 1, Draw.withOpacity(HudTheme.text(), EDGE));
		}
		if (rarity != 0 && slot) {
			ui.softLight(x + size / 2f, y + size / 2f, size * GLOW, size * GLOW, Draw.withOpacity(rarity, GLOW_OPACITY));
		}
		if (rarity != 0 && rarityLine) {
			int line = Math.max(LEAST_RARITY_LINE, Math.round(size * RARITY_LINE));
			ui.roundedSmooth(x + (size - line) / 2f, y + size - RARITY_LINE_FROM_BOTTOM - RARITY_LINE_HEIGHT, line, RARITY_LINE_HEIGHT,
				RARITY_LINE_HEIGHT / 2f, Draw.withOpacity(rarity, RARITY_OPACITY));
		}
		if (ring > 0.01f) {
			ui.ring(x, y, size, size, Radius.SM, 1, Draw.withOpacity(ui.accent(), ring));
		}
		float itemSize = item(graphics, entry.stack(), x, y, size);
		SkyBlockFeatures.PET_CANDY.drawOn(ui, entry.stack(), x + (size - itemSize) / 2f, y + (size - itemSize) / 2f, itemSize);
	}

	/**
	 * Draws an item in the exact middle of a square box: at its own 16 pixels where that fits with a little room, else scaled to the box. Its
	 * place and size are rounded to whole screen pixels, so it is not blurred and is off by at most half a screen pixel where the room left is odd.
	 *
	 * @return the item's drawn size, in GUI pixels
	 */
	static float item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int size) {
		if (stack == null || stack.isEmpty()) {
			return ITEM;
		}
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		float drawn = drawnSize(size, guiScale);
		float offset = centered(size, drawn, guiScale);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + offset, y + offset);
		if (drawn != ITEM) {
			pose.scale(drawn / ITEM, drawn / ITEM);
		}
		graphics.item(stack, 0, 0);
		pose.popMatrix();
		return drawn;
	}

	/** @return an item's size in a box, in GUI pixels: its own 16 where it fits with a little room, else the box less its inset, on whole screen pixels */
	static float drawnSize(int size, int guiScale) {
		if (size >= NATIVE_FROM && size <= NATIVE_TO) {
			return ITEM;
		}
		float wanted = Math.min(ITEM * MOST_SCALE, size - 2f * INSET);
		return Math.max(1, Math.round(wanted * guiScale)) / (float) guiScale;
	}

	/** @return how far from a box's edge an item of a size starts so that it is in the middle, on whole screen pixels */
	static float centered(int size, float drawn, int guiScale) {
		return Math.round((size - drawn) / 2f * guiScale) / (float) guiScale;
	}
}
