package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.ui.SpeedStatusBar;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SpeedStat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Gives the Speed status bar its value: read from the player's movement speed attribute, which the server sets, without the game's sprint bonus. Nothing is asked of the server. The stats widget of the player list says the same number, so the
 * two are compared now and then for the debug log.
 */
public final class SpeedBar {
	/** The id of the game's sprint bonus on the attribute. */
	private static final Identifier SPRINT_BONUS = Identifier.withDefaultNamespace("sprinting");
	private static final int COMPARE_TICKS = 100;

	private static int speed;
	private static int lastTabSpeed = -1;
	private static int ticksToCompare;

	private SpeedBar() {
	}

	/** Called every tick. */
	public static void tick(Minecraft client) {
		AttributeInstance attribute = client.player == null ? null : client.player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attribute == null || !SkyBlock.allowed()) {
			SpeedStatusBar.hide();
			return;
		}
		speed = SpeedStat.fromAttribute(attribute.getValue(), attribute.hasModifier(SPRINT_BONUS));
		SpeedStatusBar.show(speed, SpeedStat.cap(speed));
		if (--ticksToCompare <= 0) {
			ticksToCompare = COMPARE_TICKS;
			compareWithTab();
		}
	}

	/** Says so in the debug log when the player list says something else than the attribute, once per value. */
	private static void compareWithTab() {
		int tab = SpeedStat.fromTab(SkyBlockArea.tab());
		if (tab >= 0 && tab != speed && tab != lastTabSpeed) {
			Debug.log("Speed: the attribute says {}, the player list {}", speed, tab);
		}
		lastTabSpeed = tab;
	}

	/** @return a line for the debug report: what the bar shows, and what the player list says */
	public static String debugLine() {
		int tab = SpeedStat.fromTab(SkyBlockArea.tab());
		return "Speed: attribute " + speed + ", player list " + (tab < 0 ? "not shown" : String.valueOf(tab));
	}
}
