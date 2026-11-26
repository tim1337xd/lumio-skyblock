package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.tim1337.lumio.api.experimental.draw.PanelLook;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import org.junit.jupiter.api.Test;

/** The item search's own look: the themes, the accents, the background's strength, and the settings window's controls for them. */
class ItemSearchLookTest {
	private static final int MENU_ACCENT = 0x123456;

	@Test
	void theMenuThemeFollowsTheMenuAndIsTheFirstChoice() {
		// the default: drawn as before there was a choice, in the menu's theme
		assertEquals(ItemSearchLook.Theme.MENU, ItemSearchLook.Theme.values()[0]);
		assertNull(ItemSearchLook.Theme.MENU.look);
	}

	@Test
	void everyOwnThemeHasItsLook() {
		assertEquals(PanelLook.GLASS, ItemSearchLook.Theme.GLASS.look);
		assertEquals(PanelLook.DARK, ItemSearchLook.Theme.DARK.look);
		assertEquals(PanelLook.LIGHT, ItemSearchLook.Theme.LIGHT.look);
		assertEquals(PanelLook.COLORED, ItemSearchLook.Theme.COLORED.look);
		for (ItemSearchLook.Theme theme : ItemSearchLook.Theme.values()) {
			assertNotNull(theme.label);
		}
	}

	@Test
	void theSavedNamesStay() {
		// the settings keep the names of the choices: renaming one would lose the player's choice
		assertEquals("MENU", ItemSearchLook.Theme.MENU.name());
		assertEquals("DARK", ItemSearchLook.Theme.DARK.name());
		assertEquals("MENU", ItemSearchLook.Accent.MENU.name());
		assertEquals("PURPLE", ItemSearchLook.Accent.PURPLE.name());
	}

	@Test
	void theAccentIsTheMenusOrItsOwn() {
		assertEquals(MENU_ACCENT, ItemSearchLook.Accent.MENU.rgb(MENU_ACCENT));
		assertEquals(SystemColors.BLUE_RGB, ItemSearchLook.Accent.BLUE.rgb(MENU_ACCENT));
		assertEquals(SystemColors.ORANGE_RGB, ItemSearchLook.Accent.ORANGE.rgb(MENU_ACCENT));
	}

	@Test
	void theBackgroundsStrengthIsAShareOfTheThemesOwn() {
		// the default, 100 %: the theme's own opacity, as before
		assertEquals(1f, ItemSearchLook.share(ItemSearchLook.FULL_STRENGTH));
		assertEquals(0.7f, ItemSearchLook.share(70f), 0.0001f);
		// kept within its range
		assertEquals(0.2f, ItemSearchLook.share(0f), 0.0001f);
		assertEquals(1f, ItemSearchLook.share(250f));
	}

	@Test
	void theItemSizeSegmentIsTheNearestOfSmallNormalLarge() {
		assertEquals(0, ItemSearchSettings.sizeIndex(0.75f));
		assertEquals(1, ItemSearchSettings.sizeIndex(1f));
		assertEquals(1, ItemSearchSettings.sizeIndex(1.1f));
		assertEquals(2, ItemSearchSettings.sizeIndex(1.5f));
	}
}
