package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.TabCard;
import dev.tim1337.lumio.api.TabCardProvider;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.TabInfoCards;

import net.minecraft.network.chat.Component;

import java.util.List;

/** SkyBlock's info columns of the player list ("Area", "Skills", "Stats", ...) as cards of the tab list ({@link TabInfoCards}). */
final class SkyBlockTabCards implements TabCardProvider {
	@Override
	public boolean active() {
		return SkyBlock.inSkyBlock();
	}

	@Override
	public List<TabCard> cards(List<Component> lines) {
		return TabInfoCards.read(lines);
	}
}
