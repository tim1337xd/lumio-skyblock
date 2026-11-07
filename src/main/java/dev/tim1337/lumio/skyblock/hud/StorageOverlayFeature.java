package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * Hypixel SkyBlock storage (/storage) as one overlay, like SkySoft's: every ender chest page and backpack at once, with their contents as of the
 * last time they were open, in the inventory style. Pages are cached while open; the overlay covers the storage menu when it opens, and clicking a
 * page opens it. Its height and the pages per row are configurable.
 */
public class StorageOverlayFeature extends AddonFeature {
	private final Slider height = add(make().slider("height", Component.literal("Most height"), 80f, 40f, 100f, 0), "General", "It scrolls beyond this");
	private final Slider columns = add(make().slider("columns", Component.literal("Columns"), 3f, 1f, 8f, 0), "General", "Cards per row");
	private final Toggle showUnseen = add(make().toggle("show_unseen", Component.literal("Unopened pages"), true), "General",
		"Pages you have not opened yet");
	/** Whether the storages panel beside the inventory is expanded; its button says it, it has no row on the page. */
	private final Toggle panelOpen = add(make().toggle("tiles_open", Component.literal("Storages panel open"), true), "General");
	private final Choice<OverlayBackdrop.Kind> background = add(make().choice("background", Component.literal("Background"), OverlayBackdrop.Kind.BLUR_AND_DIM, OverlayBackdrop.Kind::label), "General");
	private final Slider backgroundStrength = add(make().slider("background_strength", Component.literal("Strength"), OverlayBackdrop.DEFAULT_STRENGTH_PERCENT, 0f, 100f, 0), "General");

	public StorageOverlayFeature() {
		super("storage_overlay", "Storage overlay", "The ender chest and all of the backpacks at one look, over the storage menu (/storage).", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("chest");
		summary("Your ender chest and backpacks at one look");
		panelOpen.visibleWhen(() -> false);
		backgroundStrength.visibleWhen(() -> background.get() != OverlayBackdrop.Kind.OFF);
	}

	/** @return what lies behind the overlay */
	OverlayBackdrop.Kind background() {
		return background.get();
	}

	/** @return the strength of what lies behind the overlay, in percent */
	float backgroundStrength() {
		return backgroundStrength.get();
	}

	public float heightFraction() {
		return height.get() / 100f;
	}

	/** @return the number of pages per row */
	public int columns() {
		return Math.round(columns.get());
	}


	/** The key of the overview: unset at first, and listed with the feature's keys. */
	public void register() {

		previewKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".storage_preview", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), ItemListFeature.CATEGORY));
		addKey(previewKey, KeyPlace.WORLD);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = false;
			while (previewKey.consumeClick()) {
				pressed = true;
			}
			if (pressed && client.gui.screen() == null && client.player != null && isEnabled() && SkyBlock.allowed()) {
				client.gui.setScreen(new StoragePreviewScreen(null));
			}
		});
	}

	private KeyMapping previewKey;

	/** @return whether the storages panel beside the inventory is expanded */
	public boolean panelOpen() {
		return panelOpen.get();
	}

	/** Folds the storages panel to its head or opens it again, and remembers that. */
	public void togglePanel() {
		panelOpen.set(!panelOpen.get());
		LumioApi.saveSoon();
	}

	public boolean showUnseen() {
		return showUnseen.get();
	}
}
