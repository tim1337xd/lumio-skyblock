package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.armorstand.ArmorStandModel;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;

/**
 * A Tarantula egg sac is a head worn by an invisible armor stand. The game outlines glowing invisible entities by their model, so the stand's sticks
 * were outlined under the sac: for egg sac stands only the worn head is rendered, and the outline surrounds the sac alone. A pest of the Garden is
 * built the same way, and the Pest highlight outlines only its head the same way.
 */
@Mixin(ArmorStandModel.class)
public abstract class ArmorStandModelMixin {
	/** The model is shared by all stands: first restore the parts hidden for a sac, then the game hides what it hides itself. */
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;)V", at = @At("HEAD"))
	private void evskyblock$showAll(ArmorStandRenderState state, CallbackInfo ci) {
		if (evskyblock$hidden) {
			evskyblock$hidden = false;
			for (ModelPart part : ((Model<?>) (Object) this).root().getAllParts()) {
				part.visible = true;
			}
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;)V", at = @At("TAIL"))
	private void evskyblock$hideSacStand(ArmorStandRenderState state, CallbackInfo ci) {
		if (!SkyBlockAddon.active()) {
			return;
		}
		var phases = SkyBlockFeatures.TARANTULA_HELPER;
		var pests = SkyBlockFeatures.PEST_HIGHLIGHT;
		if (state.isInvisible && state.outlineColor != 0 && ((state.outlineColor & 0xFFFFFF) == phases.eggColor() && phases.anyEggGlows() || pests.anyGlows() && pests.pestAt(state.x, state.y, state.z))) {
			evskyblock$hidden = true;
			for (ModelPart part : ((Model<?>) (Object) this).root().getAllParts()) {
				part.visible = false;
			}
		}
	}

	private boolean evskyblock$hidden;
}
