package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Break Reset Fix's debug log only: when the game starts a block again or stops breaking while it was breaking, the log says why. It only reads; the
 * game goes on as it would.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class BreakLogMixin {
	@Inject(method = "startDestroyBlock", at = @At("HEAD"))
	private void evskyblock$logStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		SkyBlockFeatures.BREAK_RESET_FIX.logBreaking("start", pos);
	}

	@Inject(method = "stopDestroyBlock", at = @At("HEAD"))
	private void evskyblock$logStop(CallbackInfo ci) {
		SkyBlockFeatures.BREAK_RESET_FIX.logBreaking("stop", null);
	}
}
