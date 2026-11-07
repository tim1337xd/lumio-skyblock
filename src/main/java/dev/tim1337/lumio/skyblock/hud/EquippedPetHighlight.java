package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Lights up the equipped pet in the Pets menu with a green tint, as the experiment helper marks a slot. Which slot it is is worked out again only
 * when the menu changes, not every frame. Only the look: nothing is clicked.
 */
final class EquippedPetHighlight {
	/** The Pets menu's title: "Pets", or "Pets (1/2)" with more than one page. */
	private static final Pattern PETS_MENU = Pattern.compile("^Pets(?: \\(\\d+/\\d+\\))?$");
	private static final int GREEN = 0xFF30D158;
	private static final float TINT_OPACITY = 0.5f;
	private static final int ITEM_SIZE = 16;
	private static final int NONE = -1;

	private final BooleanSupplier wanted;
	private Object screenFor;
	private int stateFor = -1;
	private int equippedSlot = NONE;

	EquippedPetHighlight(BooleanSupplier wanted) {
		this.wanted = wanted;
	}

	void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (!wanted.getAsBoolean() || !(screen instanceof ContainerScreen chest)) {
			return;
		}
		int state = chest.getMenu().getStateId();
		if (screen != screenFor || state != stateFor) {
			screenFor = screen;
			stateFor = state;
			equippedSlot = find(chest);
		}
		if (equippedSlot == NONE) {
			return;
		}
		Slot slot = chest.getMenu().slots.get(equippedSlot);
		var access = (ContainerScreenAccess) chest;
		Canvas ui = Draw.hudCanvas(graphics, GREEN, 0f, true);
		ui.fill(access.evskyblock$left() + slot.x, access.evskyblock$top() + slot.y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(GREEN, TINT_OPACITY));
	}

	/** @return the slot of the equipped pet in the menu (not in the player's inventory), or {@link #NONE} when this is no Pets menu or none is equipped */
	private static int find(ContainerScreen chest) {
		if (!PETS_MENU.matcher(TextColors.noColors(chest.getTitle().getString()).strip()).matches()) {
			return NONE;
		}
		var slots = chest.getMenu().slots;
		int menuSlots = chest.getMenu().getRowCount() * 9;
		for (int i = 0; i < menuSlots && i < slots.size(); i++) {
			if (!slots.get(i).getItem().isEmpty() && PetTracker.isEquipped(slots.get(i).getItem())) {
				return i;
			}
		}
		return NONE;
	}
}
