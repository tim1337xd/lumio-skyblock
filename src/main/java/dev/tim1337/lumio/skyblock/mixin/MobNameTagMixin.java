package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Hides the floating name tag of a dungeon mob without ✯ when the player chose "Hide plain nametags", and the bite countdown at the player's own bobber when the Bite timer shows it instead (visual only). Asked per armor
 * stand per frame, so it leaves after a lookup in a small set of ids that the highlights fill a few times a second. The main mod does not touch armor
 * stands' name tags.
 */
@Mixin(ArmorStandRenderer.class)
public abstract class MobNameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ArmorStand;D)Z", at = @At("HEAD"), cancellable = true)
	private void evskyblock$hideUnstarredTag(ArmorStand stand, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		if (SkyBlockAddon.active() && (SkyBlockFeatures.DUNGEON_HIGHLIGHTS.hidesTag(stand) || SkyBlockFeatures.BITE_TIMER.hidesTag(stand))) {
			cir.setReturnValue(false);
		}
	}
}
