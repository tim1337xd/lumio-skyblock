package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.AddonContext;
import dev.tim1337.lumio.api.LumioAddon;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.util.HudCover;
import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.skyblock.hud.StorageOverlay;
import dev.tim1337.lumio.skyblock.hud.WardrobeOverlay;

/**
 * Lumio: Hypixel SkyBlock (mod id {@value #ID}), the add-on with everything made for Hypixel SkyBlock. It registers itself
 * through the add-on API of Lumio; its name, version, description and the API versions it works with come from its fabric.mod.json.
 */
public final class SkyBlockAddon implements LumioAddon {
	/** The add-on's id, also the name of its settings file. */
	public static final String ID = "lumio-skyblock";

	/** @return the factory of settings for the add-on's features (static, so feature fields and unit tests can use it too) */
	public static SettingFactory make() {
		return LumioApi.settings();
	}

	/** Whether the add-on was initialized; its mixins, which load either way, do nothing while it is switched off. */
	private static volatile boolean active;

	/** @return whether the add-on runs (it was found, switched on, and initialized) */
	public static boolean active() {
		return active;
	}

	@Override
	public void onInitialize(AddonContext context) {
		SkyBlockRegistration.register(context);
		// the storage overlay has its own inventory and fills the screen: nothing of the HUD shares it
		HudCover.add(StorageOverlay::active);
		HudCover.add(WardrobeOverlay::active);
		HudCover.add(dev.tim1337.lumio.skyblock.hud.ItemListFeature::coversHud);
		// last: while the add-on did not start, its mixins do nothing
		active = true;
	}
}
