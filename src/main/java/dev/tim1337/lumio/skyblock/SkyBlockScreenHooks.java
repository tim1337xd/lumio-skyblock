package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.ScreenHooks;
import dev.tim1337.lumio.skyblock.data.HotmTree;
import dev.tim1337.lumio.skyblock.hud.StorageOverlay;
import dev.tim1337.lumio.skyblock.hud.WardrobeOverlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;

/**
 * The SkyBlock parts of the container screens: the storage overlay over the storage menu, the item list's search and keys, the equipment shown in
 * the inventory (a click opens /equipment, as the player would type it), and the Heart of the Mountain tree being picked (only observed).
 */
final class SkyBlockScreenHooks implements ScreenHooks {
	@Override
	public boolean takesOver(Screen screen) {
		return StorageOverlay.active(screen) || WardrobeOverlay.active(screen);
	}

	@Override
	public void init(Screen screen) {
		// the slots move to their displayed positions right after init, so the first click lands where it is seen
		if (StorageOverlay.active(screen)) {
			StorageOverlay.layout((ContainerScreen) screen);
		} else if (WardrobeOverlay.active(screen)) {
			WardrobeOverlay.layout((ContainerScreen) screen);
		}
	}

	@Override
	public void drawBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (WardrobeOverlay.active(screen)) {
			WardrobeOverlay.drawBackground((ContainerScreen) screen, graphics, mouseX, mouseY);
			return;
		}
		StorageOverlay.drawBackground((ContainerScreen) screen, graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(Screen screen, Slot hovered, double mouseX, double mouseY, int button) {
		// the equipment shown in the inventory is not a vanilla slot: clicking it opens /equipment
		if (screen instanceof InventoryScreen && hovered == null && SkyBlockFeatures.EQUIPMENT.clickInInventory(mouseX, mouseY, button)) {
			return true;
		}
		// observe only, the click continues to the game unchanged: a Heart of the Mountain tree being selected
		HotmTree.onClick(screen.getTitle().getString(), hovered, button);
		// which card, color or tier the player clicked at the Experimentation Table; in Chronomatron and Ultrasequencer a wrong click can be held back here, and
		// the right one is sent as the same click of the middle button (pick block), so the item never lands on the cursor
		if (SkyBlockFeatures.EXPERIMENTS.onSlotClick(screen, hovered, button)) {
			return true;
		}
		if (WardrobeOverlay.active(screen)) {
			return WardrobeOverlay.mouseClicked((ContainerScreen) screen, mouseX, mouseY, button);
		}
		// while the game's menu is edited everything is the game's, but for the way back
		if (WardrobeOverlay.editing(screen)) {
			return WardrobeOverlay.editClicked((ContainerScreen) screen, mouseX, mouseY, button);
		}
		// over the storage overlay, clicks on slots go to the game untouched; only clicks beside the slots (a card, the search) go to it
		return StorageOverlay.active(screen) && StorageOverlay.mouseClicked((ContainerScreen) screen, mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(Screen screen, double scrollY) {
		return WardrobeOverlay.active(screen) ? WardrobeOverlay.mouseScrolled(scrollY) : !WardrobeOverlay.editing(screen) && StorageOverlay.mouseScrolled(scrollY);
	}

	@Override
	public boolean keyPressed(Screen screen, KeyEvent event) {
		// while the storage search has focus, keys go to it (including the inventory key, which would close the screen)
		if (StorageOverlay.active(screen) && StorageOverlay.keyPressed(event.key(), ScreenInput.modifiers(event.modifiers()))) {
			return true;
		}
		if ((WardrobeOverlay.active(screen) || WardrobeOverlay.editing(screen)) && WardrobeOverlay.keyPressed(event.key(), (ContainerScreen) screen)) {
			return true;
		}
		if (!typing(screen) && !SkyBlockFeatures.ITEM_LIST.searchFocused() && SkyBlockFeatures.ITEM_PROTECTION.keyPressed(screen, event)) {
			return true;
		}
		// the item list's search, and its keys over an item (recipes, usages, wiki, favorite)
		return SkyBlockFeatures.ITEM_LIST.keyPressed(screen, event);
	}

	@Override
	public boolean charTyped(Screen screen, int codepoint) {
		if (StorageOverlay.active(screen) && StorageOverlay.searching() && StorageOverlay.charTyped(codepoint)) {
			return true;
		}
		if (WardrobeOverlay.active(screen) && WardrobeOverlay.charTyped(codepoint)) {
			return true;
		}
		return SkyBlockFeatures.ITEM_LIST.charTyped(screen, codepoint);
	}

	@Override
	public boolean typing(Screen screen) {
		return StorageOverlay.active(screen) && StorageOverlay.searching() || WardrobeOverlay.active(screen) && WardrobeOverlay.typing() || SkyBlockFeatures.ITEM_LIST.searchFocused();
	}
}
