package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;

/** After the game has handled the server's update of a slot: at the end, on the game's own thread, so the update itself is the game's. */
@Mixin(ClientPacketListener.class)
public abstract class HeldSlotUpdateMixin {
	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void evskyblock$heldSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		SkyBlockFeatures.BREAK_RESET_FIX.onSlotUpdate(packet.getContainerId(), packet.getSlot(), packet.getItem());
		SkyBlockFeatures.EXPERIMENTS.onMenuUpdate();
	}

	/** The inventory's own update of a slot (its hotbar is 0 to 8): Break Reset Fix keeps the held item for it as well. */
	@Inject(method = "handleSetPlayerInventory", at = @At("TAIL"))
	private void evskyblock$inventorySlot(net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
		SkyBlockFeatures.BREAK_RESET_FIX.onInventoryUpdate(packet.slot(), packet.contents());
	}

	/** A whole menu sent at once: read by the memory games as well. */
	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void evskyblock$menuContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		SkyBlockFeatures.BREAK_RESET_FIX.logMenuContent(packet.containerId());
		SkyBlockFeatures.EXPERIMENTS.onMenuUpdate();
	}
}
