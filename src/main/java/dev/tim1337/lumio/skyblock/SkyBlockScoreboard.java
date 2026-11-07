package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.ScoreboardProvider;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.skyblock.hud.CustomScoreboardFeature;

import net.minecraft.network.chat.Component;

import java.util.List;

/** The custom scoreboard on SkyBlock: its lines in the chosen order, picked in the same kind of list as the info bar's parts. */
final class SkyBlockScoreboard implements ScoreboardProvider {
	@Override
	public boolean active() {
		return SkyBlockFeatures.CUSTOM_SCOREBOARD.active();
	}

	@Override
	public Component title() {
		return SkyBlockFeatures.CUSTOM_SCOREBOARD.title();
	}

	@Override
	public List<Component> lines(List<Component> sidebar) {
		return SkyBlockFeatures.CUSTOM_SCOREBOARD.lines(sidebar);
	}

	@Override
	public String settingsHeading() {
		return "Lines on SkyBlock";
	}

	@Override
	public List<? extends AddonSetting<?>> settings() {
		return List.of(SkyBlockFeatures.CUSTOM_SCOREBOARD.partsSetting());
	}
}
