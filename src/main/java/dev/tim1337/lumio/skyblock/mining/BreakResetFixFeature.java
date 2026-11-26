package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.mixin.accessor.BreakingBlockAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.HeldItemAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Item updates from the server no longer reset block breaking. The game restarts breaking and replays the equip animation when the item it remembers differs from the one held;
 * so after the server updated the held hotbar slot, the two places it remembers the item are set to the new one. Nothing is sent, changed or held back, and the
 * player's own item switches reset as usual. The idea and how it is done are NoFrills'; the code is this mod's own.
 */
public class BreakResetFixFeature extends AddonFeature {
	private static final String ABOUT = "Updates keep your break progress.";
	private final dev.tim1337.lumio.api.setting.InfoLine about = add(dev.tim1337.lumio.skyblock.SkyBlockAddon.make().info("about", net.minecraft.network.chat.Component.literal(""), () -> ABOUT), "General");

	/** The hotbar's first slot in the player's own menu, and that menu's id. */
	static final int FIRST_HOTBAR_SLOT = 36;
	static final int HOTBAR_SLOTS = 9;

	/** The line of the last update, finished in the tick after it. */
	private String pending;
	private float pendingHand;

	public BreakResetFixFeature() {
		super("break_reset_fix", "Break Reset Fix", "Updates keep your break progress.", false);
		pageGroup("While mining");
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("pickaxe");
		summary("Item updates no longer reset your breaking");
	}

	/**
	 * As NoFrills: any menu id (Hypixel's updates of the inventory do not all come with the player's menu), the hotbar's slots 36 to 44 of it, and only the
	 * held one.
	 *
	 * @param switchOn   the feature's switch
	 * @param allowed    whether the player is where SkyBlock's features work
	 * @param screenOpen whether a screen is open
	 * @param slot       the updated slot of the menu
	 * @param selected   the hotbar slot held now, from 0
	 * @return whether the update is one of the held item, to be remembered as it is
	 */
	static boolean applies(boolean switchOn, boolean allowed, boolean screenOpen, int slot, int selected) {
		return switchOn && allowed && !screenOpen && slot >= FIRST_HOTBAR_SLOT && slot < FIRST_HOTBAR_SLOT + HOTBAR_SLOTS && slot - FIRST_HOTBAR_SLOT == selected;
	}

	/** The same for the inventory packet, whose slots are the inventory's own: the hotbar is 0 to 8. */
	static boolean appliesToInventory(boolean switchOn, boolean allowed, boolean screenOpen, int slot, int selected) {
		return switchOn && allowed && !screenOpen && slot >= 0 && slot < HOTBAR_SLOTS && slot == selected;
	}

	/** The game has handled the server's update of a slot of a menu. */
	public void onSlotUpdate(int menu, int slot, ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null) {
			return;
		}
		boolean fix = applies(isEnabled(), SkyBlock.allowed(), client.gui.screen() != null, slot, client.player.getInventory().getSelectedSlot());
		handle("ContainerSetSlot", menu, slot, stack, fix);
	}

	/** The game has handled the server's update of a slot of the inventory. */
	public void onInventoryUpdate(int slot, ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null) {
			return;
		}
		boolean fix = appliesToInventory(isEnabled(), SkyBlock.allowed(), client.gui.screen() != null, slot, client.player.getInventory().getSelectedSlot());
		handle("SetPlayerInventory", -1, slot, stack, fix);
	}

	private void handle(String packet, int menu, int slot, ItemStack stack, boolean fix) {
		Minecraft client = Minecraft.getInstance();
		BreakingBlockAccess breaking = (BreakingBlockAccess) client.gameMode;
		//#if 26.3
		//$ HeldItemAccess hand = (HeldItemAccess) client.player.firstPersonHandsAndItems();
		//#else
		HeldItemAccess hand = (HeldItemAccess) client.getEntityRenderDispatcher().getItemInHandRenderer();
		//#endif
		// a debug tool's break trace: what the update did to the breaking, finished in the tick after
		boolean logging = DebugHooks.wants(SkyBlockDebug.BREAK) && client.gameMode.isDestroying() && fix;
		ItemStack destroyingBefore = breaking.evskyblock$destroyingItem();
		ItemStack handBefore = hand.evskyblock$mainHandItem();
		if (fix) {
			breaking.evskyblock$setDestroyingItem(stack);
			hand.evskyblock$setMainHandItem(stack);
		}
		if (logging) {
			// the game keeps breaking only while it takes the held item for the one it started with, and replays the hand's equip when its item differs
			pending = String.format(java.util.Locale.ROOT, "%s menu %d slot %d selected %d fix %s same target %s (same item %s) equip would restart %s progress before %.3f",
				packet, menu, slot, client.player.getInventory().getSelectedSlot(), fix, ItemStack.isSameItemSameComponents(destroyingBefore, stack),
				ItemStack.isSameItem(destroyingBefore, stack), !ItemStack.isSameItemSameComponents(handBefore, stack) && !fix, breaking.evskyblock$destroyProgress());
			pendingHand = hand.evskyblock$mainHandHeight();
		}
	}

	/** For a debug tool's break trace: the server sent a whole menu at once, which updates the held item without the slot update the fix listens to. */
	public void logMenuContent(int menu) {
		Minecraft client = Minecraft.getInstance();
		if (DebugHooks.wants(SkyBlockDebug.BREAK) && client.gameMode != null && client.gameMode.isDestroying()) {
			DebugHooks.log(SkyBlockDebug.MINING, "Break Reset Fix: whole menu {} sent while breaking", menu);
		}
	}

	/** Called every tick: finishes the line of the last update with what the game did with it in the tick after. */
	public void tick(Minecraft client) {
		if (pending == null || client.gameMode == null || client.player == null) {
			pending = null;
			return;
		}
		//#if 26.3
		//$ HeldItemAccess hand = (HeldItemAccess) client.player.firstPersonHandsAndItems();
		//#else
		HeldItemAccess hand = (HeldItemAccess) client.getEntityRenderDispatcher().getItemInHandRenderer();
		//#endif
		float handNow = hand.evskyblock$mainHandHeight();
		if (!DebugHooks.ACTIVE) {
			pending = null;
			return;
		}
		DebugHooks.log(SkyBlockDebug.MINING, "Break Reset Fix: {} progress after {} equip restarted {}", pending,
			String.format(java.util.Locale.ROOT, "%.3f", ((BreakingBlockAccess) client.gameMode).evskyblock$destroyProgress()), handNow < pendingHand - 0.01f);
		pending = null;
	}
}
