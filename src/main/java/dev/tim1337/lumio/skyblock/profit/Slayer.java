package dev.tim1337.lumio.skyblock.profit;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/** The SkyBlock slayers, each with its own profit tracker and its mob's face cropped from the mob texture. */
public enum Slayer {
	REVENANT("Revenant", "zombie/zombie", 64, 64, 8, 8, 8, null),
	TARANTULA("Tarantula", "spider/spider", 64, 32, 40, 12, 8, null),
	SVEN("Sven", "wolf/wolf_angry", 64, 32, 4, 4, 6, null),
	VOIDGLOOM("Voidgloom", "enderman/enderman", 64, 32, 8, 8, 8, "enderman/enderman_eyes"),
	INFERNO("Inferno", "blaze/blaze", 64, 32, 8, 8, 8, null),
	RIFTSTALKER("Riftstalker", "player/wide/steve", 64, 64, 8, 8, 8, null);

	final String title;
	final Identifier texture;
	final Identifier overlay;
	final int textureWidth;
	final int textureHeight;
	final int u;
	final int v;
	final int size;

	Slayer(String title, String texture, int textureWidth, int textureHeight, int u, int v, int size, String overlay) {
		this.title = title;
		this.texture = Identifier.withDefaultNamespace("textures/entity/" + texture + ".png");
		this.overlay = overlay == null ? null : Identifier.withDefaultNamespace("textures/entity/" + overlay + ".png");
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.u = u;
		this.v = v;
		this.size = size;
	}

	/** @return the slayer of a boss as the scoreboard names it ("Tarantula Broodfather IV"), or null */
	public static Slayer of(String boss) {
		if (boss == null || boss.isBlank()) {
			return null;
		}
		String name = boss.toLowerCase(Locale.ROOT);
		if (name.contains("revenant") || name.contains("atoned")) {
			return REVENANT;
		}
		if (name.contains("tarantula") || name.contains("broodfather") || name.contains("conjoined")) {
			return TARANTULA;
		}
		if (name.contains("sven")) {
			return SVEN;
		}
		if (name.contains("voidgloom") || name.contains("seraph")) {
			return VOIDGLOOM;
		}
		if (name.contains("inferno") || name.contains("demonlord")) {
			return INFERNO;
		}
		if (name.contains("riftstalker") || name.contains("bloodfiend")) {
			return RIFTSTALKER;
		}
		return null;
	}

	/** @return the coin cost of starting a quest of a tier (1 to 5); the Riftstalker is paid in motes, which are not coins */
	long cost(int tier) {
		long[] costs = switch (this) {
			case INFERNO -> new long[]{10_000, 25_000, 60_000, 150_000};
			case RIFTSTALKER -> new long[0];
			default -> new long[]{2_000, 7_500, 20_000, 50_000, 100_000};
		};
		return tier >= 1 && tier <= costs.length ? costs[tier - 1] : 0;
	}
}
