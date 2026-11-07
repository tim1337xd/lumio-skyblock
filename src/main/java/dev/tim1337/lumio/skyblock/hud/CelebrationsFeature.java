package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;

/** Celebrations: whether the panels show their small moments ({@link PanelMoment}), such as a pet leveling up or an ability getting ready. */
public final class CelebrationsFeature extends AddonFeature {
	private final dev.tim1337.lumio.api.setting.InfoLine about = add(dev.tim1337.lumio.skyblock.SkyBlockAddon.make().info("about", net.minecraft.network.chat.Component.literal(""), () -> "A short celebration for what you have reached. It works by itself."));

	private static final String TEXT = "Small moments on the SkyBlock panels";

	public CelebrationsFeature() {
		super("celebrations", "Celebrations", TEXT, true);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("sparkle");
		summary(TEXT);
	}
}
