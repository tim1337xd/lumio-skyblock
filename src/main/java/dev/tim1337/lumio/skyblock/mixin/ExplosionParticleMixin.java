package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

/**
 * Explosions Hider: every particle made on the client passes here, once, when it is spawned, so nothing is filtered per frame. No particle is what
 * the game also gets for a type without a provider, and it handles that.
 */
@Mixin(ParticleEngine.class)
public abstract class ExplosionParticleMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void evskyblock$hideExplosions(ParticleOptions options, double x, double y, double z, double speedX, double speedY, double speedZ,
		CallbackInfoReturnable<Particle> cir) {
		if (SkyBlockAddon.active() && SkyBlockFeatures.EXPLOSIONS_HIDER.hides(options.getType())) {
			cir.setReturnValue(null);
		}
	}
}
