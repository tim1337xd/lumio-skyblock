package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.mining.BreakResetFixFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;

/**
 * Break Reset Fix's debug log only: notes who changes the data of the held item in place, which makes the game start the block it breaks again. It
 * only reads; with the log off it returns at once.
 */
@Mixin(ItemStack.class)
public abstract class BreakTraceStackMixin {
	@Inject(method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"))
	private void evskyblock$traceSet(CallbackInfoReturnable<Object> cir) {
		if (BreakResetFixFeature.tracing) {
			BreakResetFixFeature.heldChanged((ItemStack) (Object) this, "a component of the held item was set");
		}
	}

	@Inject(method = "remove", at = @At("HEAD"))
	private void evskyblock$traceRemove(CallbackInfoReturnable<Object> cir) {
		if (BreakResetFixFeature.tracing) {
			BreakResetFixFeature.heldChanged((ItemStack) (Object) this, "a component of the held item was removed");
		}
	}

	@Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V", at = @At("HEAD"))
	private void evskyblock$tracePatch(CallbackInfo ci) {
		if (BreakResetFixFeature.tracing) {
			BreakResetFixFeature.heldChanged((ItemStack) (Object) this, "components of the held item were changed");
		}
	}
}
