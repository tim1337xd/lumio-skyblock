package dev.tim1337.lumio.skyblock.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Explosions Hider: on SkyBlock, the particles of explosions and the "poof" are not made at all, so they cost nothing afterwards. Only particles:
 * sounds, blocks, entities and damage stay as they are. The idea is SkyHanni's.
 */
public final class ExplosionsHiderFeature extends AddonFeature {
	private final dev.tim1337.lumio.api.setting.InfoLine about = add(dev.tim1337.lumio.skyblock.SkyBlockAddon.make().info("about", net.minecraft.network.chat.Component.literal(""), () -> "Explosions you can't do anything about are not drawn. It works by itself."));

	public ExplosionsHiderFeature() {
		super("explosions_hider", "Explosions Hider", "Hides the particles of explosions and the poof, only on SkyBlock.", false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("sparkle");
		summary("Hides the particles of explosions and the poof");
	}

	/** @return whether a particle about to be made is left out; the type comes first, as almost none of the particles are these */
	public boolean hides(ParticleType<?> type) {
		return (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.POOF) && isEnabled() && SkyBlock.inSkyBlock();
	}
}
