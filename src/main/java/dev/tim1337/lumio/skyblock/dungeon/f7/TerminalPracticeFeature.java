package dev.tim1337.lumio.skyblock.dungeon.f7;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Action;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The terminal practice: all six F7 terminals offline, on the player's screen only. Its own row on the F7 / M7 tab, with the button that starts it. */
public class TerminalPracticeFeature extends AddonFeature {
	private final Action start = add(make().action("practice", Component.literal("Practice the terminals"), Component.literal("Start practice"), false,
		TerminalPracticeFeature::open), "Practice", "Offline; nothing is sent");

	public TerminalPracticeFeature() {
		super("dungeon_practice", "Practice", "Practice terminals offline; nothing is sent.", true, false);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("play");
		summary("The six terminals, offline");
		badge("F7 · M7");
	}

	private static void open() {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(new TerminalPracticeScreen(client.gui.screen()));
	}
}
