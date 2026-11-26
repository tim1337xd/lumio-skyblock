package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.ScreenHooks;
import dev.tim1337.lumio.skyblock.data.HotmTree;
import dev.tim1337.lumio.skyblock.hud.StorageOverlay;
import dev.tim1337.lumio.skyblock.hud.WardrobeOverlay;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;

/**
 * The SkyBlock parts of the container screens: the storage overlay over the storage menu, the leap overlay over the Spirit Leap menu (one click on a card is one click on that head), the loadout keys in the Loadouts menu (one press is one click on that loadout), the terminal grid (one click on a cell is one click on that slot), the item list's search and keys, the equipment shown in
 * the inventory (a click opens /equipment, as the player would type it), and the Heart of the Mountain tree being picked (only observed).
 */
final class SkyBlockScreenHooks implements ScreenHooks {
	@Override
	public boolean takesOver(Screen screen) {
		return StorageOverlay.active(screen) || WardrobeOverlay.active(screen) || SkyBlockFeatures.DUNGEON_LEAP.active(screen) || SkyBlockFeatures.DUNGEON_TERMINALS.takesOver(screen);
	}

	@Override
	public void init(Screen screen) {
		// the slots move to their displayed positions right after init, so the first click lands where it is seen
		if (SkyBlockFeatures.DUNGEON_LEAP.active(screen)) {
			SkyBlockFeatures.DUNGEON_LEAP.layout((ContainerScreen) screen);
		} else if (SkyBlockFeatures.DUNGEON_TERMINALS.takesOver(screen)) {
			SkyBlockFeatures.DUNGEON_TERMINALS.layout((ContainerScreen) screen);
		} else if (StorageOverlay.active(screen)) {
			StorageOverlay.layout((ContainerScreen) screen);
		} else if (WardrobeOverlay.active(screen)) {
			WardrobeOverlay.layout((ContainerScreen) screen);
		}
	}

	@Override
	public void drawBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (SkyBlockFeatures.DUNGEON_LEAP.active(screen)) {
			SkyBlockFeatures.DUNGEON_LEAP.draw((ContainerScreen) screen, graphics, mouseX, mouseY);
			return;
		}
		if (SkyBlockFeatures.DUNGEON_TERMINALS.takesOver(screen)) {
			SkyBlockFeatures.DUNGEON_TERMINALS.draw((ContainerScreen) screen, graphics, mouseX, mouseY);
			return;
		}
		if (WardrobeOverlay.active(screen)) {
			WardrobeOverlay.drawBackground((ContainerScreen) screen, graphics, mouseX, mouseY);
			return;
		}
		StorageOverlay.drawBackground((ContainerScreen) screen, graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(Screen screen, Slot hovered, double mouseX, double mouseY, int button) {
		// loadout keys on a mouse button: in the Loadouts menu, one press is one click on that loadout
		if (SkyBlockFeatures.LOADOUT_KEYS.mouseClicked(screen, button)) {
			return true;
		}
		// slot binds: a shift-click on a bound slot of the player's own inventory is the one swap click with its hotbar slot
		if (SkyBlockFeatures.SLOT_BINDS.shiftClick(screen, hovered, button)) {
			return true;
		}
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
		// the terminal grid: one click on a cell is one click on that slot; on the game's menu a click goes through unless it is too early after opening
		if (SkyBlockFeatures.DUNGEON_TERMINALS.takesOver(screen)) {
			return SkyBlockFeatures.DUNGEON_TERMINALS.mouseClicked((ContainerScreen) screen, mouseX, mouseY, button);
		}
		if (SkyBlockFeatures.DUNGEON_TERMINALS.clickOnMenu(screen, hovered, button)) {
			return true;
		}
		// the leap overlay: one click on a card is one click on that player's head, nothing else
		if (SkyBlockFeatures.DUNGEON_LEAP.active(screen)) {
			return SkyBlockFeatures.DUNGEON_LEAP.mouseClicked((ContainerScreen) screen, mouseX, mouseY, button);
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
		// loadout keys: in the Loadouts menu their keys are theirs alone, so no hotbar swap or other key acts there
		if (!typing(screen) && SkyBlockFeatures.LOADOUT_KEYS.keyPressed(screen, event)) {
			return true;
		}
		if (SkyBlockFeatures.DUNGEON_LEAP.active(screen) && SkyBlockFeatures.DUNGEON_LEAP.keyPressed((ContainerScreen) screen, event.key())) {
			return true;
		}
		// while the storage search has focus, keys go to it (including the inventory key, which would close the screen)
		if (StorageOverlay.active(screen) && StorageOverlay.keyPressed(event.key(), ScreenInput.modifiers(event.modifiers()))) {
			return true;
		}
		if ((WardrobeOverlay.active(screen) || WardrobeOverlay.editing(screen)) && WardrobeOverlay.keyPressed(event.key(), (ContainerScreen) screen)) {
			return true;
		}
		if (!typing(screen) && SkyBlockFeatures.SLOT_BINDS.keyPressed(screen, event)) {
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
	public boolean ownsKey(Screen screen, InputConstants.Key key) {
		return !typing(screen) && SkyBlockFeatures.LOADOUT_KEYS.ownsKey(screen, key);
	}

	@Override
	public boolean ownsWorldKey(InputConstants.Key key) {
		return SkyBlockFeatures.FARMING_KEYS.ownsKey(key);
	}

	@Override
	public boolean typing(Screen screen) {
		return StorageOverlay.active(screen) && StorageOverlay.searching() || WardrobeOverlay.active(screen) && WardrobeOverlay.typing() || SkyBlockFeatures.ITEM_LIST.searchFocused();
	}
}
