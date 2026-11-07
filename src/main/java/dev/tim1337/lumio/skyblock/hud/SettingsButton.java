package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.ui.Menus;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.feature.AddonFeature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * The small square button with a gear, just outside the bottom right of an overlay's inventory: it opens the overlay's own settings page in the Lumio menu, and Esc or the back button comes back to the open menu of the game as it was. Nothing is
 * sent to the server, and the game's menu stays open behind it.
 */
public final class SettingsButton {
	/** As large as a slot. */
	public static final int SIZE = 18;
	/** The room between the inventory and the button. */
	public static final int GAP = 8;
	private static final int ICON = 11;
	private static final int EDGE = 2;
	private static final String GEAR = "gear";
	private static final float FILL = 0.09f;
	private static final float FILL_HOVER = 0.2f;

	private SettingsButton() {
	}

	/**
	 * Places the button beside the bottom of a panel.
	 *
	 * @param box         gets the button's place; all zero where the screen has no room for it
	 * @param panelRight  the right edge of the panel
	 * @param panelBottom the bottom edge of the panel
	 */
	public static void place(int[] box, int panelRight, int panelBottom, int screenWidth) {
		boolean fits = fits(panelRight, screenWidth);
		box[0] = fits ? panelRight + GAP : 0;
		box[1] = fits ? panelBottom - SIZE : 0;
		box[2] = fits ? SIZE : 0;
		box[3] = fits ? SIZE : 0;
	}

	/** @return whether the button fits on the screen right of a panel */
	static boolean fits(int panelRight, int screenWidth) {
		return panelRight + GAP + SIZE + EDGE <= screenWidth;
	}

	/** Draws the button, with its tooltip while the mouse is over it. */
	public static void draw(Canvas ui, GuiGraphicsExtractor graphics, int[] box, String tooltip, int mouseX, int mouseY) {
		if (box[2] == 0) {
			return;
		}
		boolean over = PanelKit.inside(box, mouseX, mouseY);
		HudTheme.panel(ui, box[0], box[1], box[2], box[3], Radius.SM, HudTheme.opacity());
		ui.roundedSmooth(box[0], box[1], box[2], box[3], Radius.SM, Draw.withOpacity(HudTheme.text(), over ? FILL_HOVER : FILL));
		ui.icon(GEAR, box[0] + (SIZE - ICON) / 2, box[1] + (SIZE - ICON) / 2, ICON, Draw.withOpacity(HudTheme.text(), over ? 1f : 0.85f));
		if (over) {
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(tooltip), mouseX, mouseY);
		}
	}

	/** @return whether the click was on the button: it opens the feature's page, unless the player holds an item, which must not stay on the cursor over the menu */
	public static boolean clicked(int[] box, AbstractContainerScreen<?> screen, AddonFeature feature, double mouseX, double mouseY) {
		if (box[2] == 0 || !PanelKit.inside(box, mouseX, mouseY)) {
			return false;
		}
		if (screen.getMenu().getCarried().isEmpty()) {
			UiSounds.click();
			Menus.openSettings(feature);
		}
		return true;
	}
}
