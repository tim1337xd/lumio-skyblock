package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The lock on a protected item: every item in a slot gets its decorations here, in menus, on the hotbar and in the overlays. Called per item, so it asks once and cheaply. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ProtectionLockMixin {
	@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
	private void lumio$lock(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
		if (SkyBlockFeatures.ITEM_PROTECTION.isProtected(stack)) {
			SkyBlockFeatures.ITEM_PROTECTION.drawLock((GuiGraphicsExtractor) (Object) this, stack, x, y);
		}
	}
}
