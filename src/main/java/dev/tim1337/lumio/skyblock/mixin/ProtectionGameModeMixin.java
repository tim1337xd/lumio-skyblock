package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.misc.ItemProtectionFeature;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item protection holds the player's own action back here, before the game acts on it or sends anything: every screen and key ends in these methods, so no
 * way around them is left. It only ever stops an action; nothing is sent in its place.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ProtectionGameModeMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void lumio$holdBackClick(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (SkyBlockFeatures.ITEM_PROTECTION.holdsBackClick(containerId, slotId, button, input, player)) {
			ci.cancel();
		}
	}

	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void lumio$holdBackUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (SkyBlockFeatures.ITEM_PROTECTION.holdsBackUse(player, hand)) {
			cir.setReturnValue(ItemProtectionFeature.heldBack());
		}
	}

	/** Using an item on a block counts as well: some of these are heads that would be placed. */
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void lumio$holdBackUseOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (SkyBlockFeatures.ITEM_PROTECTION.holdsBackUse(player, hand)) {
			cir.setReturnValue(ItemProtectionFeature.heldBack());
		}
	}

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void lumio$holdBackFrame(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (SkyBlockFeatures.ITEM_PROTECTION.holdsBackInteract(player, entity, hand)) {
			cir.setReturnValue(ItemProtectionFeature.heldBack());
		}
	}
}
