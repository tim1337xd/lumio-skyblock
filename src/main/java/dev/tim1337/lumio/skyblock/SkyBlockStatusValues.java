package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.StatusStat;
import dev.tim1337.lumio.api.StatusValueProvider;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockStats;

import net.minecraft.network.chat.Component;

/** SkyBlock's health, mana, defense, vitality and drill fuel for the status bars, read from Hypixel's action bar ({@link SkyBlockStats}). */
final class SkyBlockStatusValues implements StatusValueProvider {
	/** Above the game's own values; there is no other provider yet. */
	private static final int PRIORITY = 100;

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean owns(Component actionBar) {
		// the server is Hypixel, or the text has both the health and the mana symbol, which no other text has
		return SkyBlock.onHypixel() || SkyBlockStats.looksLikeSkyBlock(actionBar.getString());
	}

	@Override
	public void observe(Component actionBar, boolean wantHealth, boolean wantMana) {
		SkyBlockStats.read(actionBar, wantHealth, wantMana, false);
	}

	@Override
	public Component filter(Component actionBar, boolean wantHealth, boolean wantMana, boolean takeOut, int remove) {
		return SkyBlockStats.restyled(SkyBlockStats.read(actionBar, wantHealth, wantMana, takeOut, remove));
	}

	@Override
	public boolean fresh(StatusStat stat) {
		return switch (stat) {
			case HEALTH -> SkyBlockStats.healthFresh();
			case MANA, OVERFLOW_MANA -> SkyBlockStats.manaFresh();
			case DEFENSE -> SkyBlockStats.defenseFresh();
			case VITALITY -> SkyBlockStats.vitalityFresh();
			case FUEL -> SkyBlockStats.fuelFresh();
		};
	}

	@Override
	public int value(StatusStat stat) {
		return switch (stat) {
			case HEALTH -> SkyBlockStats.health();
			case MANA -> SkyBlockStats.mana();
			case OVERFLOW_MANA -> SkyBlockStats.overflowMana();
			case DEFENSE -> SkyBlockStats.defense();
			case VITALITY -> SkyBlockStats.vitality();
			case FUEL -> SkyBlockStats.fuel();
		};
	}

	@Override
	public int max(StatusStat stat) {
		return switch (stat) {
			case HEALTH -> SkyBlockStats.maxHealth();
			case MANA -> SkyBlockStats.maxMana();
			case VITALITY -> SkyBlockStats.maxVitality();
			case FUEL -> SkyBlockStats.maxFuel();
			case OVERFLOW_MANA, DEFENSE -> 0;
		};
	}
}
