package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.mining.BreakResetFixFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Break Reset Fix's debug log only: notes who puts another item into the held slot, or selects another slot. It only reads. */
@Mixin(Inventory.class)
public abstract class BreakTraceInventoryMixin {
	@Shadow
	public abstract int getSelectedSlot();

	@Inject(method = "setItem", at = @At("HEAD"))
	private void evskyblock$traceSlot(int slot, ItemStack stack, CallbackInfo ci) {
		if (BreakResetFixFeature.tracing && slot == getSelectedSlot()) {
			BreakResetFixFeature.note("an item was put into the held slot " + slot);
		}
	}

	@Inject(method = "setSelectedSlot", at = @At("HEAD"))
	private void evskyblock$traceSelected(int slot, CallbackInfo ci) {
		if (BreakResetFixFeature.tracing && slot != getSelectedSlot()) {
			BreakResetFixFeature.note("the selected slot changed to " + slot);
		}
	}
}
