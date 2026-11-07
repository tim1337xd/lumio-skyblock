package dev.tim1337.lumio.skyblock.slayer;

import net.minecraft.world.item.ItemStack;

/**
 * A slayer boss as read from its name tags in one tick.
 *
 * @param thirds whether the bar shows third markers; not for bosses with a single health phase, such as the Conjoined Brood
 * @param phases the phases of a boss with hit shields (the Voidgloom Seraph): each shield and each part between them; null for other bosses
 */
record SlayerBoss(String name, double health, double maxHealth, String phase, int phaseColor, String detail, String timer, ItemStack icon,
				  boolean thirds, VoidgloomPhases.View phases) {
}
