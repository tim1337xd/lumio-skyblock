package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import net.minecraft.network.chat.Component;

/** Helpers for every slayer: the Gummy Polar Bear reminder, shown in the slayer profit tracker or as a notification. */
public class SlayerHelpersFeature extends AddonFeature {
	/** Where the Gummy Polar Bear reminder is shown. */
	public enum GummyStyle {
		TRACKER("Profit tracker"),
		NOTIFICATION("Notification"),
		BOTH("Both");

		private final String label;

		GummyStyle(String label) {
			this.label = label;
		}

		public Component displayName() {
			return Component.literal(label);
		}
	}

	private final Toggle gummyReminder = add(make().toggle("gummy_reminder", Component.literal("Gummy Polar Bear reminder"), true), "Gummy",
		"When you hit a mob without it");
	private final Choice<GummyStyle> gummyStyle = add(make().choice("gummy_style", Component.literal("Show it in"), GummyStyle.TRACKER,
		GummyStyle::displayName, Choice.Style.SEGMENTED), "Gummy");

	{
		gummyStyle.under(gummyReminder);
	}

	public SlayerHelpersFeature() {
		super("slayer_helpers", "Slayer helpers", "Helpers for every slayer: a reminder when your Gummy Polar Bear runs out.", false);
		iconRgb(SystemColors.RED_RGB);
		iconName("sword");
		summary("Gummy Polar Bear reminder");
	}

	/** @return whether the Gummy Polar Bear reminder is enabled at all */
	public boolean gummyReminder() {
		return isEnabled() && gummyReminder.get();
	}

	/** @return whether the reminder is shown in the slayer profit tracker */
	public boolean gummyInTracker() {
		return gummyReminder() && gummyStyle.get() != GummyStyle.NOTIFICATION;
	}

	/** @return whether the reminder is shown as a notification */
	public boolean gummyAsNotification() {
		return gummyReminder() && gummyStyle.get() != GummyStyle.TRACKER;
	}
}
