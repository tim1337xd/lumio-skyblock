package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.mining.BreakResetFixFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/** Break Reset Fix's debug log only: notes the packets about breaking and the held slot that go to the server, and who sent them. It only reads. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class BreakTracePacketMixin {
	@Inject(method = "send", at = @At("HEAD"))
	private void evskyblock$tracePacket(Packet<?> packet, CallbackInfo ci) {
		if (!BreakResetFixFeature.tracing) {
			return;
		}
		if (packet instanceof ServerboundSetCarriedItemPacket carried) {
			BreakResetFixFeature.note("sent: held slot " + carried.getSlot());
		} else if (packet instanceof ServerboundPlayerActionPacket action) {
			BreakResetFixFeature.note("sent: " + action.getAction() + " at " + action.getPos().toShortString() + " face " + action.getDirection().getName()
				+ " (attack key down " + net.minecraft.client.Minecraft.getInstance().options.keyAttack.isDown() + ", left button really down " + BreakResetFixFeature.leftButtonDown() + ")");
		}
	}
}
