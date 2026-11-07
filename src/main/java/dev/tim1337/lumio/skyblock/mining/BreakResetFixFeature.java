package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
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
	private static final String ABOUT = "Item updates from the server no longer reset your block breaking progress (like NoFrills).";
	private final dev.tim1337.lumio.api.setting.InfoLine about = add(dev.tim1337.lumio.skyblock.SkyBlockAddon.make().info("about", net.minecraft.network.chat.Component.literal(""), () -> ABOUT), "General");

	/** The hotbar's first slot in the player's own menu, and that menu's id. */
	static final int FIRST_HOTBAR_SLOT = 36;
	static final int HOTBAR_SLOTS = 9;

	private final dev.tim1337.lumio.api.setting.Toggle debugLog = add(dev.tim1337.lumio.skyblock.SkyBlockAddon.make().toggle("debug_log", net.minecraft.network.chat.Component.literal("Debug log"), false),
		"Advanced", "Updates of the held item, and why breaking starts again or stops, in the game's log");
	/** Whether the debug log notes who changes the held item or sends the held slot: read by the mixins at every call, so they return at once without it. */
	public static volatile boolean tracing;
	/** What happened lately to the held item and the breaking, with who did it: written to the log when breaking starts again or stops. */
	private static final int NOTES = 24;
	private static final long RECENT_NANOS = 1_000_000_000L;
	private static final String[] noteText = new String[NOTES];
	private static final long[] noteNanos = new long[NOTES];
	private static int nextNote;

	/** Debug log only: notes something that happened, with a short stack of who did it. */
	public static void note(String what) {
		String caller = StackWalker.getInstance().walk(frames -> frames.skip(2).limit(5)
			.map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName())
			.collect(java.util.stream.Collectors.joining(" < ")));
		synchronized (noteText) {
			noteText[nextNote] = what + " (" + Thread.currentThread().getName() + ": " + caller + ")";
			noteNanos[nextNote] = System.nanoTime();
			nextNote = (nextNote + 1) % NOTES;
		}
	}

	/** Debug log only: a stack's data is about to change; noted if it is the item in the main hand. */
	public static void heldChanged(ItemStack stack, String what) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.getMainHandItem() == stack) {
			note(what);
		}
	}

	/** The line of the last update, finished in the tick after it. */
	private String pending;
	private float pendingHand;

	public BreakResetFixFeature() {
		super("break_reset_fix", "Break Reset Fix", "Item updates from the server no longer reset your block breaking progress (like NoFrills).", false);
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
		boolean logging = debugLog.get() && client.gameMode.isDestroying() && fix;
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

	/** Debug log only: @return whether the left mouse button is down, as the window system reports it, not as the game's attack key says */
	public static boolean leftButtonDown() {
		if (!Minecraft.getInstance().isSameThread()) {
			return false;
		}
		//#if 26.3
		//$ return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, null) & org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LMASK) != 0;
		//#else
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		//#endif
	}

	/**
	 * Debug log only: the game starts a block again or stops breaking while it was breaking. Says why, as far as the game's own state tells: another
	 * block, another held item, the attack key let go, a screen, the mouse not grabbed; and which code asked for it.
	 *
	 * @param what "start" or "stop"
	 * @param pos  the block a start is for, or null for a stop
	 */
	public void logBreaking(String what, net.minecraft.core.BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (!debugLog.get() || client.gameMode == null || client.player == null || !client.gameMode.isDestroying()) {
			return;
		}
		BreakingBlockAccess breaking = (BreakingBlockAccess) client.gameMode;
		ItemStack held = client.player.getMainHandItem();
		ItemStack breakingWith = breaking.evskyblock$destroyingItem();
		String caller = StackWalker.getInstance().walk(frames -> frames.skip(3).limit(3)
			.map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName())
			.collect(java.util.stream.Collectors.joining(" < ")));
		Logs.info("Break Reset Fix: breaking {}: other block {}, same held item {} (same kind {}), attack key down {} (left button really down {}), screen {}, mouse grabbed {}, from {}", what,
			pos != null && !pos.equals(breaking.evskyblock$destroyPos()), ItemStack.isSameItemSameComponents(held, breakingWith), ItemStack.isSameItem(held, breakingWith),
			client.options.keyAttack.isDown(), leftButtonDown(), client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName(), client.mouseHandler.isMouseGrabbed(), caller);
		long now = System.nanoTime();
		for (int i = 0; i < NOTES; i++) {
			int at = (nextNote + i) % NOTES;
			if (noteText[at] != null && now - noteNanos[at] < RECENT_NANOS) {
				Logs.info("Break Reset Fix:   {} ms before: {}", (now - noteNanos[at]) / 1_000_000L, noteText[at]);
			}
		}
	}

	/** Debug log only: the server sent a whole menu at once, which updates the held item without the slot update the fix listens to. */
	public void logMenuContent(int menu) {
		Minecraft client = Minecraft.getInstance();
		if (debugLog.get() && client.gameMode != null && client.gameMode.isDestroying()) {
			Logs.info("Break Reset Fix: whole menu {} sent while breaking", menu);
		}
	}

	/** Called every tick: finishes the line of the last update with what the game did with it in the tick after. */
	public void tick(Minecraft client) {
		tracing = debugLog.get();
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
		Logs.info("Break Reset Fix: {} progress after {} equip restarted {}", pending,
			String.format(java.util.Locale.ROOT, "%.3f", ((BreakingBlockAccess) client.gameMode).evskyblock$destroyProgress()), handNow < pendingHand - 0.01f);
		pending = null;
	}
}
