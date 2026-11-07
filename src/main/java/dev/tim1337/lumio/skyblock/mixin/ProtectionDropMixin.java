package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Holds the drop key back for a protected item in the hand (Q and Ctrl+Q): where the game acts on that key differs between the versions. */
//#if 26.3
//$ @Mixin(MultiPlayerGameMode.class)
//#else
@Mixin(LocalPlayer.class)
//#endif
public abstract class ProtectionDropMixin {
	//#if 26.3
	//$ @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	//$ private void lumio$holdBackDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
	//$ 	if (SkyBlockFeatures.ITEM_PROTECTION.holdsBackDropKey(player)) {
	//$ 		ci.cancel();
	//$ 	}
	//$ }
	//#else
	@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
	private void lumio$holdBackDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (SkyBlockFeatures.ITEM_PROTECTION.holdsBackDropKey((LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
	//#endif
}
