package dev.tim1337.lumio.skyblock.hud;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.util.KeyNames;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Loadout keys (inspired by the loadout keys of NoammAddons and Odin, written our own way): while the game's Loadouts menu is open, a key per
 * loadout and two for the page arrows. One press is exactly one click on that slot, as the mouse would make it (a documented exception to the
 * project rules, one press = one slot click); a second press within a moment, an empty or a locked loadout, and every other menu do nothing. The
 * menu is never opened or closed by it, and its keys take the press in that menu only, so no other keybind and no hotbar swap acts on it there.
 */
public final class LoadoutKeysFeature extends AddonFeature {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "loadouts"));
	/** The menu the keys act in, as the player knows it: the Keybinds page says so where a key is used instead of another one. */
	private static final String MENU_NAME = "Loadouts";
	private static final int SLOT_SIZE = 16;
	/** Slots in a row of a chest menu. */
	private static final int MENU_COLUMNS = 9;
	private static final float LABEL_SCALE = 0.5f;
	private static final int LABEL_PAD = 1;
	private static final int LABEL_HEIGHT = 5;
	private static final int LABEL_BOX = 0xC0101014;
	private static final int LABEL_TEXT = 0xFFFFFFFF;
	/** What a press does besides picking a loadout (1 to 12). */
	private static final int NEXT = -1;
	private static final int PREVIOUS = -2;
	private static final int NOTHING = 0;

	private final Toggle hotbarKeys = add(make().toggle("hotbar_keys", Component.literal("Use the hotbar keys"), false), "General");
	private final Toggle showKeys = add(make().toggle("show_keys", Component.literal("Show the keys on the slots"), true), "General");

	private final KeyMapping[] loadoutKeys = new KeyMapping[LoadoutMenu.LOADOUTS];
	private KeyMapping nextKey;
	private KeyMapping previousKey;
	private final LoadoutMenu.PressGuard guard = new LoadoutMenu.PressGuard();
	/** The keyboard key of the last press, until it is let go: the game repeats a held key, and a repeat is no new press. */
	private int heldKey = InputConstants.UNKNOWN.getValue();

	public LoadoutKeysFeature() {
		super("loadout_keys", "Loadout keys", "Loadout keys; one press, one click.", false);
		pageGroup("Inventory and menus");
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("keyboard");
		summary("A key for each loadout");
		hotbarKeys.setDescription("Keys 1 to 9 pick loadouts 1 to 9");
	}

	/** Registers the keys (all unbound), the labels on the slots and the release watch; once, while the add-on starts. */
	public void register() {
		for (int i = 0; i < loadoutKeys.length; i++) {
			loadoutKeys[i] = key("loadout_" + (i + 1));
		}
		previousKey = key("loadout_previous_page");
		nextKey = key("loadout_next_page");
		for (KeyMapping mapping : loadoutKeys) {
			addMenuKey(mapping, MENU_NAME);
		}
		addMenuKey(previousKey, MENU_NAME);
		addMenuKey(nextKey, MENU_NAME);
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> drawLabels(screen, graphics));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (heldKey != InputConstants.UNKNOWN.getValue() && !keyDown(heldKey)) {
				heldKey = InputConstants.UNKNOWN.getValue();
			}
			if (!(client.gui.screen() instanceof ContainerScreen screen) || !menuOpen(screen)) {
				guard.reset();
			}
		});
	}

	private static KeyMapping key(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + "." + name, InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	/** @return whether a menu is the game's Loadouts menu, with the six rows its loadouts need */
	private static boolean menuOpen(Screen screen) {
		return screen instanceof ContainerScreen chest && chest.getMenu().getRowCount() * MENU_COLUMNS >= LoadoutMenu.MENU_SLOTS
			&& LoadoutMenu.isMenu(TextColors.noColors(chest.getTitle().getString()));
	}

	/** @return whether this feature acts in this screen now */
	private boolean active(Screen screen) {
		return isEnabled() && menuOpen(screen);
	}

	/** @return the mapping that picks a loadout (1 to 12): the hotbar key for 1 to 9 while "Use the hotbar keys" is on, else its own key */
	private KeyMapping keyOf(int loadout) {
		if (hotbarKeys.get() && loadout <= LoadoutMenu.HOTBAR_KEYS) {
			return Minecraft.getInstance().options.keyHotbarSlots[loadout - 1];
		}
		return loadoutKeys[loadout - 1];
	}

	/** @return what a key or mouse button does in the menu: a loadout (1 to 12), {@link #NEXT}, {@link #PREVIOUS}, or {@link #NOTHING} */
	private int pressed(InputConstants.Key key) {
		for (int loadout = 1; loadout <= LoadoutMenu.LOADOUTS; loadout++) {
			if (bound(keyOf(loadout), key)) {
				return loadout;
			}
		}
		if (bound(nextKey, key)) {
			return NEXT;
		}
		return bound(previousKey, key) ? PREVIOUS : NOTHING;
	}

	private static boolean bound(KeyMapping mapping, InputConstants.Key key) {
		return mapping != null && !mapping.isUnbound() && KeyMappingHelper.getBoundKeyOf(mapping).equals(key);
	}

	/** @return whether this key is one of the feature's in the open Loadouts menu, so Lumio's command keybinds leave it alone there */
	public boolean ownsKey(Screen screen, InputConstants.Key key) {
		return active(screen) && pressed(key) != NOTHING;
	}

	/** A key in a menu. @return whether it was one of the feature's in the Loadouts menu: then nothing else gets it, not even a hotbar swap */
	public boolean keyPressed(Screen screen, KeyEvent event) {
		if (!active(screen)) {
			return false;
		}
		int action = pressed(InputConstants.getKey(event));
		if (action == NOTHING) {
			return false;
		}
		// a held key repeats: only its first press counts
		if (event.key() == heldKey) {
			return true;
		}
		heldKey = event.key();
		press((ContainerScreen) screen, action);
		return true;
	}

	/** A mouse button in a menu (as {@link ScreenInput#button} gives it). @return whether it was one of the feature's keys in the Loadouts menu */
	public boolean mouseClicked(Screen screen, int button) {
		if (!active(screen)) {
			return false;
		}
		int action = pressed(InputConstants.Type.MOUSE.getOrCreate(ScreenInput.gameButton(button)));
		if (action == NOTHING) {
			return false;
		}
		press((ContainerScreen) screen, action);
		return true;
	}

	/** One press: at most one click on the loadout's slot or on the page arrow, and only if it is there and the press is not a double press. */
	private void press(ContainerScreen screen, int action) {
		if (!guard.press(System.currentTimeMillis())) {
			return;
		}
		int slot = action > 0 ? loadoutSlot(screen, action) : arrowSlot(screen, action == NEXT ? LoadoutMenu.Arrow.NEXT : LoadoutMenu.Arrow.PREVIOUS);
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.PANELS, "Loadout keys: press {} clicks slot {}", action, slot);
		}
		if (slot >= 0) {
			WardrobeOverlay.clickOnce(screen, slot);
		}
	}

	/** @return the slot of a loadout, or -1 when it is empty or locked */
	private static int loadoutSlot(ContainerScreen screen, int loadout) {
		int slot = LoadoutMenu.slotOf(loadout);
		ItemStack stack = screen.getMenu().getSlot(slot).getItem();
		String name = stack.isEmpty() ? "" : TextColors.noColors(stack.getHoverName().getString());
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return LoadoutMenu.usable(name, ItemGains.loreLines(stack), path) ? slot : -1;
	}

	/** @return the slot of the menu's page arrow, found by its name, or -1 on a page without it */
	private static int arrowSlot(ContainerScreen screen, LoadoutMenu.Arrow arrow) {
		int menuSlots = screen.getMenu().getRowCount() * MENU_COLUMNS;
		for (int slot = 0; slot < menuSlots; slot++) {
			ItemStack stack = screen.getMenu().getSlot(slot).getItem();
			if (!stack.isEmpty() && LoadoutMenu.arrow(TextColors.noColors(stack.getHoverName().getString())) == arrow) {
				return slot;
			}
		}
		return -1;
	}

	/** The bound key in the top left corner of each loadout's slot, small, on a box. */
	private void drawLabels(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (!showKeys.get() || !active(screen)) {
			return;
		}
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		var font = Minecraft.getInstance().font;
		for (int loadout = 1; loadout <= LoadoutMenu.LOADOUTS; loadout++) {
			KeyMapping mapping = keyOf(loadout);
			String label = mapping == null || mapping.isUnbound() ? "" : LoadoutMenu.label(KeyNames.of(mapping));
			if (label.isEmpty()) {
				continue;
			}
			Slot slot = screen.getMenu().getSlot(LoadoutMenu.slotOf(loadout));
			int x = access.evskyblock$left() + slot.x;
			int y = access.evskyblock$top() + slot.y;
			int width = Math.round(font.width(label) * LABEL_SCALE) + 2 * LABEL_PAD;
			graphics.fill(x, y, x + Math.min(width, SLOT_SIZE), y + LABEL_HEIGHT + LABEL_PAD, LABEL_BOX);
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(x + LABEL_PAD, y + LABEL_PAD);
			pose.scale(LABEL_SCALE, LABEL_SCALE);
			graphics.text(font, label, 0, 0, LABEL_TEXT, false);
			pose.popMatrix();
		}
	}

	private static boolean keyDown(int key) {
		//#if 26.3
		//$ return InputConstants.isKeyDown(key);
		//#else
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
		//#endif
	}
}
