package dev.tim1337.lumio.skyblock.mixin.accessor;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The item the first person hand remembers, which it compares with the held one to replay the equip animation. */
//#if 26.3
//$ @Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
//#else
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
//#endif
public interface HeldItemAccess {
	@Accessor("mainHandItem")
	void evskyblock$setMainHandItem(ItemStack stack);

	@Accessor("mainHandItem")
	ItemStack evskyblock$mainHandItem();

	@Accessor("mainHandHeight")
	float evskyblock$mainHandHeight();
}
