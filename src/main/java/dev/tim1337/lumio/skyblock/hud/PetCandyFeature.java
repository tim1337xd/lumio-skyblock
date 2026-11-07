package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.PetCandy;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;


import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Pet Candy Used: the number of Pet Candy a pet has used, small and in a soft red at the top right of its slot, in the look of the recipe grid's
 * counts. The screen's slots are looked at every frame, but a slot without a pet only costs an item comparison ({@link PetCandy}), and the canvas is
 * made only once a number is drawn. The idea is SkyHanni's.
 */
public final class PetCandyFeature extends AddonFeature {
	/** The size of an item in a slot, and how far the number's top sits above it, so it stays in the corner of the slot. */
	private static final int ITEM_SIZE = 16;
	private static final float ABOVE_ITEM = 1f;
	private static final float SHADOW_X = 0.5f;
	private static final float SHADOW_Y = 0.6f;
	private static final int SHADOW = 0xB0000000;

	private final Toggle hideOnMax = add(make().toggle("hide_on_max", Component.literal("Hide on max level pets"), true), "Pets");

	public PetCandyFeature() {
		super("pet_candy", "Pet Candy Used", "How much Pet Candy a pet has used, in the corner of its slot.", true);
		iconRgb(SystemColors.RED_RGB);
		iconName("gem");
		summary("How much Pet Candy a pet has used, in the corner of its slot");
	}

	/** Draws over every container screen as it opens. Called during mod initialization. */
	public void register() {
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> drawSlots(screen, graphics));
	}

	private void drawSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (!isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		var access = (ContainerScreenAccess) screen;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		var slots = screen.getMenu().slots;
		Canvas ui = null;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			ItemStack stack = slot.getItem();
			if (label(stack) == null) {
				continue;
			}
			int x = left + slot.x;
			// a slot the storage overlay moved off the screen
			if (x + ITEM_SIZE < 0) {
				continue;
			}
			if (ui == null) {
				Draw.setZoom(1f);
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, false);
				graphics.nextStratum();
			}
			draw(ui, stack, x, top + slot.y, ITEM_SIZE);
		}
	}

	/** @return the number to show on an item, or null for none: no pet, no candy, or a pet at its top level with the option on */
	private String label(ItemStack stack) {
		PetCandy.Info info = PetCandy.of(stack);
		return info.candy() == 0 || info.maxLevel() && hideOnMax.get() ? null : info.label();
	}

	/**
	 * Draws the number on an item drawn elsewhere (a card of the storage overlay, a search result), if it should have one.
	 *
	 * @param x    the item's left edge
	 * @param y    the item's top edge
	 * @param size the size the item is drawn at
	 */
	public void drawOn(Canvas ui, ItemStack stack, float x, float y, float size) {
		if (isEnabled() && SkyBlock.allowed()) {
			draw(ui, stack, x, y, size);
		}
	}

	private void draw(Canvas ui, ItemStack stack, float x, float y, float size) {
		String label = label(stack);
		if (label == null) {
			return;
		}
		float textX = x + size - ui.boldWidthScaled(label, TextScale.SMALL);
		float textY = y - ABOVE_ITEM;
		ui.textAtScaledBold(label, textX + SHADOW_X, textY + SHADOW_Y, TextScale.SMALL, SHADOW);
		ui.textAtScaledBold(label, textX, textY, TextScale.SMALL, SystemColors.RED_TEXT);
	}
}
