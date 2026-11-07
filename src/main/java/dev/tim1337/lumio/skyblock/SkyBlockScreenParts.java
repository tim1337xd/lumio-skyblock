package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.BlockTint;
import dev.tim1337.lumio.api.InventoryColumn;
import dev.tim1337.lumio.api.draw.Canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** The SkyBlock parts of the game's screens and world: the equipment beside the player in the inventory, and tick glide's color on the block. */
final class SkyBlockScreenParts {
	private SkyBlockScreenParts() {
	}

	/** The worn equipment (necklace, cloak, belt, gloves) as a column in the inventory. */
	static final InventoryColumn EQUIPMENT = new InventoryColumn() {
		@Override
		public boolean shown() {
			return SkyBlockFeatures.EQUIPMENT.showsInInventory();
		}

		@Override
		public void draw(Canvas canvas, int x, int y) {
			SkyBlockFeatures.EQUIPMENT.drawInline(canvas, canvas.graphics(), Minecraft.getInstance(), x, y);
		}
	};

	/** The block being mined, in tick glide's color. */
	static final BlockTint TICK_GLIDE = (BlockPos pos) -> SkyBlockFeatures.TICK_GLIDE.overlayColor(pos);
}
