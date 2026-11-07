package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;

import net.minecraft.network.chat.Component;

/** The game's Wardrobe menu as your character in every set. Nothing is sent to open it: it covers the menu while the player has that open. */
public class WardrobeOverlayFeature extends AddonFeature {
	private final dev.tim1337.lumio.api.setting.InfoLine about = add(SkyBlockAddon.make().info("about", Component.literal(""), () -> "Your character in every armor set, over the game's Wardrobe menu."), "General");
	private final Toggle followMouse = add(SkyBlockAddon.make().toggle("follow_mouse", Component.literal("Players follow the mouse"), false), "General");
	private final Toggle setNames = add(SkyBlockAddon.make().toggle("show_set_names", Component.literal("Show set names when unnamed"), false), "General");
	private final Choice<OverlayBackdrop.Kind> background = add(SkyBlockAddon.make().choice("background", Component.literal("Background"), OverlayBackdrop.Kind.BLUR_AND_DIM, OverlayBackdrop.Kind::label));
	private final Slider backgroundStrength = add(SkyBlockAddon.make().slider("background_strength", Component.literal("Strength"), OverlayBackdrop.DEFAULT_STRENGTH_PERCENT, 0f, 100f, 0));

	public WardrobeOverlayFeature() {
		super("wardrobe_overlay", "Wardrobe overlay", "Your character in every armor set, over the game's Wardrobe menu.", true);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("person");
		summary("Your armor sets at a glance");
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

	/** @return whether the models turn toward the mouse: they are drawn again as it moves */
	public boolean followsMouse() {
		return followMouse.get();
	}

	/** @return whether a slot without an own name shows its set's name */
	public boolean showsSetNames() {
		return setNames.get();
	}
}
