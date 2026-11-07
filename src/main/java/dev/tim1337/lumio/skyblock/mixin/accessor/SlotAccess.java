package dev.tim1337.lumio.skyblock.mixin.accessor;

import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The add-on's own access to a slot's screen position, which the storage overlay moves. */
@Mixin(Slot.class)
public interface SlotAccess {
	@Mutable
	@Accessor("y")
	void evskyblock$setY(int y);

	@Mutable
	@Accessor("x")
	void evskyblock$setX(int x);
}
