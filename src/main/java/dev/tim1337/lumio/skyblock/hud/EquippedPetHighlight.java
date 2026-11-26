package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.PetMenu;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Boxes the pet that is out in the Pets menu (any page, also opened from the SkyBlock Menu): a soft fill and a thin border in the accent color,
 * behind its item. Which slot it is ({@link PetMenu}) is worked out again only when the menu changes (a page, a summon, a despawn), not every
 * frame. Only the look: nothing is clicked.
 */
final class EquippedPetHighlight {
	private static final float FILL_OPACITY = 0.22f;
	private static final float BORDER_OPACITY = 0.9f;
	private static final int SLOT_BOX = 18;

	private final BooleanSupplier wanted;
	private Object screenFor;
	private int stateFor = -1;
	private PetMenu.Found found = PetMenu.NONE;

	EquippedPetHighlight(BooleanSupplier wanted) {
		this.wanted = wanted;
	}

	void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (!wanted.getAsBoolean() || !(screen instanceof ContainerScreen chest)) {
			return;
		}
		int state = chest.getMenu().getStateId();
		if (screen != screenFor || state != stateFor) {
			boolean opened = screen != screenFor;
			screenFor = screen;
			stateFor = state;
			boolean petsMenu = PetMenu.isTitle(TextColors.noColors(chest.getTitle().getString()));
			found = petsMenu ? find(chest) : PetMenu.NONE;
			if (opened && petsMenu) {
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "Pets menu: {}", found.slot() < 0 ? "no pet out" : "slot " + found.slot() + " by its " + found.reason());
				}
			}
		}
		if (found.slot() < 0 || found.slot() >= chest.getMenu().slots.size()) {
			return;
		}
		Slot slot = chest.getMenu().slots.get(found.slot());
		var access = (ContainerScreenAccess) chest;
		int accent = 0xFF000000 | MainSettings.accentRgb();
		Canvas ui = Draw.hudCanvas(graphics, accent, 0f, false);
		int x = access.evskyblock$left() + slot.x - 1;
		int y = access.evskyblock$top() + slot.y - 1;
		ui.roundedSmooth(x, y, SLOT_BOX, SLOT_BOX, Radius.XXS, Draw.withOpacity(accent, FILL_OPACITY));
		ui.ring(x, y, SLOT_BOX, SLOT_BOX, Radius.XXS, 1, Draw.withOpacity(accent, BORDER_OPACITY));
	}

	/** @return the pet that is out among the menu's own slots (not the player's inventory) */
	private static PetMenu.Found find(ContainerScreen chest) {
		var slots = chest.getMenu().slots;
		int menuSlots = Math.min(chest.getMenu().getRowCount() * 9, slots.size());
		List<String> names = new ArrayList<>(menuSlots);
		List<List<String>> lores = new ArrayList<>(menuSlots);
		for (int i = 0; i < menuSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			names.add(stack.isEmpty() ? "" : TextColors.noColors(stack.getHoverName().getString()));
			lores.add(loreOf(stack));
		}
		// the player list is read here only, once when the menu opens and when it changes
		PetTracker.readTabPetNow(net.minecraft.client.Minecraft.getInstance());
		return PetMenu.find(names, lores, PetTracker.tabPetName(), PetTracker.tabPetLevel());
	}

	private static List<String> loreOf(ItemStack stack) {
		ItemLore lore = stack.isEmpty() ? null : stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>(lore.lines().size());
		for (Component line : lore.lines()) {
			lines.add(TextColors.noColors(line.getString()));
		}
		return lines;
	}
}
