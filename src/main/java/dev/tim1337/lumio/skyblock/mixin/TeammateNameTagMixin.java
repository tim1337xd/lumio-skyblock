package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;

/**
 * Hides the game's name tag above a dungeon teammate when the player chose "Hide the game's name tags" (the highlights draw the name in the class
 * color instead). Asked per player per frame, so it leaves after a lookup in a small set of ids filled a few times a second. The main mod does not
 * touch players' name tags.
 */
@Mixin(AvatarRenderer.class)
public abstract class TeammateNameTagMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void evskyblock$hideTeammateTag(Avatar entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		if (SkyBlockAddon.active() && SkyBlockFeatures.DUNGEON_HIGHLIGHTS.hidesGameName(entity)) {
			cir.setReturnValue(false);
		}
	}
}
