package dev.tim1337.lumio.skyblock.hud.buttons;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How a button looks, in the game and in the editor: a small glass box with its icon (the real item, or a Lumio symbol in its color, glowing a
 * little in its own color), lifted with the accent border while hovered, pressed in while clicked, faint where it does not apply; or the icon alone.
 * And the small card over a hovered button with its name in its color and its command.
 */
final class ButtonLook {
	/** How a button is shown now. */
	enum State {
		NORMAL, HOVER, PRESSED, NOT_HERE
	}

	/** How far a hovered button rises, in GUI pixels. */
	private static final float LIFT = 1.5f;
	private static final float GLOW = 0.75f;
	private static final float GLOW_OPACITY = 0.22f;
	private static final float NOT_HERE_OPACITY = 0.35f;
	private static final int CARD_PAD = 5;
	private static final int CARD_GAP = 4;
	private static final float COMMAND_SCALE = TextScale.SMALL;
	private static final int MOST_STACKS = 256;
	/** The items of icons, made once each (a bounded cache: a few dozen icons at most are in use). */
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private ButtonLook() {
	}

	/** @return the icon's item, or empty for a symbol or an id the game and the item list do not know (yet) */
	static ItemStack stackOf(ButtonIcon icon) {
		if (icon.kind() != ButtonIcon.Kind.ITEM) {
			return ItemStack.EMPTY;
		}
		ItemStack known = STACKS.get(icon.value());
		if (known != null) {
			return known;
		}
		ItemStack stack = ItemStack.EMPTY;
		if (icon.value().contains(":")) {
			Identifier key = Identifier.tryParse(icon.value().toLowerCase(Locale.ROOT));
			if (key != null && BuiltInRegistries.ITEM.containsKey(key) && BuiltInRegistries.ITEM.getValue(key) != Items.AIR) {
				stack = new ItemStack(BuiltInRegistries.ITEM.getValue(key));
			}
		} else {
			ItemRepo.Entry entry = ItemRepo.get(icon.value());
			if (entry == null) {
				// the item list is not loaded yet: try again later
				return ItemStack.EMPTY;
			}
			stack = entry.stack();
		}
		if (STACKS.size() >= MOST_STACKS) {
			STACKS.clear();
		}
		STACKS.put(icon.value(), stack);
		return stack;
	}

	/** @return the button's color: its symbol's, or its item's rarity color (white while unknown) */
	static int colorOf(ButtonIcon icon) {
		if (icon.kind() == ButtonIcon.Kind.SYMBOL) {
			return 0xFF000000 | icon.rgb();
		}
		ItemRepo.Entry entry = icon.value().contains(":") ? null : ItemRepo.get(icon.value());
		return entry == null ? 0xFFFFFFFF : entry.rarityColor();
	}

	/**
	 * Draws a button.
	 *
	 * @param boxed whether it has its glass box (or only the icon)
	 * @param glow  whether the icon glows a little in its color
	 */
	static void draw(Canvas ui, GuiGraphicsExtractor graphics, ButtonIcon icon, int x, int y, int size, State state, boolean boxed, boolean glow) {
		float before = Draw.alpha();
		if (state == State.NOT_HERE) {
			Draw.setAlpha(before * NOT_HERE_OPACITY);
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		float rise = state == State.HOVER ? -LIFT : state == State.PRESSED ? 0.5f : 0f;
		pose.translate(0f, rise);
		try {
			int color = colorOf(icon);
			if (boxed) {
				HudTheme.panel(ui, x, y, size, size, Radius.SM, HudTheme.opacity());
				if (state == State.PRESSED) {
					ui.rounded(x, y, size, size, Radius.SM, Draw.withOpacity(ui.accent(), 0.25f));
				}
				int ring = state == State.HOVER || state == State.PRESSED ? ui.accent() : Draw.withOpacity(HudTheme.text(), 0.1f);
				ui.ring(x, y, size, size, Radius.SM, 1, ring);
			}
			if (glow) {
				ui.softLight(x + size / 2f, y + size / 2f, size * GLOW, size * GLOW, Draw.withOpacity(color, GLOW_OPACITY));
			}
			drawIcon(ui, graphics, icon, x, y, size, color);
		} finally {
			pose.popMatrix();
			Draw.setAlpha(before);
		}
	}

	/** Draws only the icon, centered in a box of the size. */
	static void drawIcon(Canvas ui, GuiGraphicsExtractor graphics, ButtonIcon icon, int x, int y, int size, int color) {
		if (icon.kind() == ButtonIcon.Kind.SYMBOL) {
			int iconSize = Math.max(6, size - 6);
			ui.icon(icon.value(), x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, color);
			return;
		}
		ItemStack stack = stackOf(icon);
		if (stack.isEmpty()) {
			int iconSize = Math.max(6, size - 6);
			ui.icon(ButtonIcon.DEFAULT.value(), x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, HudTheme.secondary());
			return;
		}
		float scale = Math.min(1f, (size - 2) / 16f);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + size / 2f - 8f * scale, y + size / 2f - 8f * scale);
		pose.scale(scale, scale);
		graphics.item(stack, 0, 0);
		pose.popMatrix();
	}

	/** The card over a hovered button: its name in its color, and the command it runs. Kept on the screen. */
	static void card(Canvas ui, InventoryButton button, int buttonX, int buttonY, int size, int screenWidth, boolean showName) {
		String command = button.command();
		float nameWidth = showName ? ui.boldWidthScaled(button.name(), TextScale.BODY) : 0f;
		float commandWidth = ui.widthScaled(command, COMMAND_SCALE);
		int width = Math.round(Math.max(nameWidth, commandWidth)) + 2 * CARD_PAD;
		int height = (showName ? 20 : 11) + CARD_PAD;
		int x = Math.clamp(buttonX + size / 2 - width / 2, 2, Math.max(2, screenWidth - width - 2));
		int y = buttonY - CARD_GAP - height;
		if (y < 2) {
			y = buttonY + size + CARD_GAP;
		}
		HudTheme.panel(ui, x, y, width, height, Radius.SM, Math.max(0.9f, HudTheme.opacity()));
		ui.ring(x, y, width, height, Radius.SM, 1, Draw.withOpacity(HudTheme.text(), 0.12f));
		int textY = y + CARD_PAD;
		if (showName) {
			ui.textAtScaledBold(button.name(), x + CARD_PAD, textY, TextScale.BODY, colorOf(button.icon()));
			textY += 11;
		}
		ui.textAtScaled(command, x + CARD_PAD, textY, COMMAND_SCALE, HudTheme.secondary());
	}
}
