package dev.tim1337.lumio.skyblock.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The game's settings for a textured piece of the interface, for the add-on's own shader of the same kind. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccess {
	@Accessor("GUI_TEXTURED_SNIPPET")
	static RenderPipeline.Snippet evskyblock$guiTextured() {
		throw new UnsupportedOperationException();
	}
}
