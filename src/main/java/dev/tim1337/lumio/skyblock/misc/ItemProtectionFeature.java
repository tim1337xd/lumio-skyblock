package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.hud.StorageOverlay;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;
import dev.tim1337.lumio.api.experimental.ui.Toasts;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Item protection, an idea from Skyblocker: the items the player marked can't be given away by accident. It only ever holds back the player's own click or key
 * press, and sends nothing. The checks sit where the game acts on them, so they hold for every screen: the mixins ask here before a click, a drop or a use.
 */
public class ItemProtectionFeature extends AddonFeature {
	/** How a blocked action is told. */
	public enum Notice {
		TOAST("Toast"),
		CHAT("Chat"),
		OFF("Off");

		private final Component label;

		Notice(String label) {
			this.label = Component.literal(label);
		}

		Component label() {
			return label;
		}
	}


	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "item_protection"));
	private static final String FILE = "item_protection.json";
	private static final String TITLE = "Item protected";
	private static final String ICON = "lock";
	private static final String SELL_ALL_BUTTON = "Sell Inventory";
	private static final String SELL_BUTTON = "Sell Item";
	private static final String BUYBACK_WORD = "buyback";
	private static final float TOAST_SECONDS = 2.5f;
	private static final long NOTICE_GAP_MILLIS = 1000L;
	private static final int PLAYER_SLOTS = 36;
	private static final int HOTBAR_SIZE = 9;

	// the lock, in steps of half a GUI pixel: seven of them are seven real pixels at GUI scale 2
	private static final int LOCK_COLOR = 0xB3FFFFFF;
	private static final float LOCK_STEP = 0.5f;
	private static final float LOCK_CENTER = 3.5f;
	private static final int[][] LOCK_RUNS = {{0, 2, 5}, {1, 1, 2}, {1, 5, 6}, {2, 1, 2}, {2, 5, 6}, {3, 0, 7}, {4, 0, 3}, {4, 4, 7}, {5, 0, 3}, {5, 4, 7}, {6, 0, 7}};
	private static final long PULSE_MILLIS = 350L;
	private static final float PULSE_GROWTH = 0.7f;

	private static final ExecutorService FILES = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio item protection");
		thread.setDaemon(true);
		return thread;
	});

	private static SettingFactory make() {
		return SkyBlockAddon.make();
	}

	private final Toggle blockDrop = add(make().toggle("block_drop", Component.literal("Dropping"), true), "Blocked");
	private final Toggle blockSell = add(make().toggle("block_sell", Component.literal("NPC selling"), true), "Blocked");
	private final Toggle blockSalvage = add(make().toggle("block_salvage", Component.literal("Salvaging"), true), "Blocked");
	private final Toggle blockTrade = add(make().toggle("block_trade", Component.literal("Trading"), true), "Blocked");
	private final Toggle blockAuction = add(make().toggle("block_auction", Component.literal("Auction house"), true), "Blocked");
	private final Toggle blockDungeonChest = add(make().toggle("block_dungeon_chest", Component.literal("Swapping in dungeon chests"), true), "Blocked");
	private final Toggle blockFrame = add(make().toggle("block_frame", Component.literal("Item frames"), true), "Blocked", "On your island or the Garden");
	private final Toggle consumables = add(make().toggle("valuable_consumables", Component.literal("Valuable consumables"), true), "Extras",
		"Bottle of Jyrre, Dark Cacao Truffle, Discrite, Moby-Duck and Rosewater Flask can't be used before they have evolved");
	private final Toggle dropInDungeons = add(make().toggle("drop_in_dungeons", Component.literal("Allow dropping in dungeons"), false), "Extras");
	private final Choice<Notice> notice = add(make().choice("notice", Component.literal("Notice"), Notice.TOAST, Notice::label, Choice.Style.SEGMENTED), "Extras");

	private final ProtectedItems items = new ProtectedItems();
	/** The UUID per custom data instance, "" for none: an item's data never changes, and it can only be read as a full copy. */
	private CompletableFuture<ProtectedItems> reading;
	private KeyMapping key;
	private String pendingRemoval;
	private long pendingSince;
	private String pulsing;
	private long pulsedAt;
	private long noticedAt;

	public ItemProtectionFeature() {
		super("item_protection", "Item protection", "Marks items so that they can't be given away by accident: press its key over one, or /lumio protect.", true);
		iconRgb(SystemColors.BLUE_RGB);
		iconName(ICON);
		summary("Keep your best items from being given away");
	}

	/** The key, the command and what the file kept. */
	public void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".item_protection", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY));
		addKey(key, KeyPlace.WORLD);
		addKey(key, KeyPlace.SCREENS);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(ClientCommands.literal("lumio")
			.then(ClientCommands.literal("protect").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					toggle(client.player.getMainHandItem());
				}
				return 1;
			}))));
		Path file = ModFiles.file(FILE);
		reading = CompletableFuture.supplyAsync(() -> read(file), FILES);
	}

	private static ProtectedItems read(Path file) {
		try {
			return Files.isRegularFile(file) ? ProtectedItems.fromJson(JsonParser.parseString(Files.readString(file)).getAsJsonObject()) : null;
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the protected items", e);
			return null;
		}
	}

	private void writeFile() {
		String json = new Gson().toJson(items.toJson());
		Path file = ModFiles.file(FILE);
		FILES.execute(() -> {
			try {
				ModFiles.writeAtomically(file, json);
			} catch (IOException e) {
				Logs.warn("Could not keep the protected items", e);
			}
		});
	}

	private void tick(Minecraft client) {
		if (reading != null && reading.isDone()) {
			ProtectedItems kept = reading.join();
			reading = null;
			if (kept != null) {
				items.adopt(kept);
			}
		}
		// the game counts presses only while no screen is open; in a screen the key arrives through keyPressed
		boolean pressed = false;
		while (key != null && key.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.gui.screen() == null && client.player != null && on()) {
			toggle(client.player.getMainHandItem());
		}
	}

	private boolean on() {
		return isEnabled() && SkyBlock.allowed();
	}

	/** @return whether the player protected this item; cheap, as it is asked for every item drawn */
	public boolean isProtected(ItemStack stack) {
		if (items.isEmpty() || !on() || stack.isEmpty()) {
			return false;
		}
		String uuid = ItemGains.uuidOf(stack);
		return !uuid.isEmpty() && items.has(uuid);
	}

	/** Protects an item, or takes the protection away with a second press soon after the first. */
	private void toggle(ItemStack stack) {
		String uuid = stack.isEmpty() ? "" : ItemGains.uuidOf(stack);
		if (uuid.isEmpty()) {
			tell("Can't protect this", "Only items with their own id can be");
			return;
		}
		long now = System.currentTimeMillis();
		if (!items.has(uuid)) {
			if (items.add(StorageTracker.profile(), uuid)) {
				writeFile();
				pulse(uuid);
				tell(TITLE, "It can't be given away now");
			}
			return;
		}
		if (uuid.equals(pendingRemoval) && ProtectionRules.secondPress(pendingSince, now)) {
			pendingRemoval = null;
			items.remove(uuid);
			writeFile();
			tell("Protection removed", "");
			return;
		}
		pendingRemoval = uuid;
		pendingSince = now;
		pulse(uuid);
		tell("Press again to unprotect", "");
	}

	/** The key over an item in a screen. @return whether it was used */
	public boolean keyPressed(Screen screen, KeyEvent event) {
		if (key == null || !key.matches(event) || !on()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		ItemStack hovered = StorageOverlay.hoveredItem();
		if (hovered.isEmpty() && screen instanceof AbstractContainerScreen<?> && client.player != null) {
			Slot slot = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
			// only the player's own: what lies in a shop or in another player's trade is no item to mark
			if (slot != null && (slot.container == client.player.getInventory() || StorageTracker.isStorageMenu(screen.getTitle().getString()))) {
				hovered = slot.getItem();
			}
		}
		if (hovered.isEmpty()) {
			return false;
		}
		toggle(hovered);
		return true;
	}

	/** @return whether the drop key has to do nothing now */
	public boolean holdsBackDropKey(Player player) {
		ItemStack held = player.getMainHandItem();
		if (!isProtected(held) || !blockDrop.get() || dropInDungeons.get() && inDungeon()) {
			return false;
		}
		block(ProtectionRules.Blocked.DROP, held);
		return true;
	}

	/** @return whether a click in a menu has to do nothing */
	public boolean holdsBackClick(int containerId, int slotId, int button, ContainerInput input, Player player) {
		if (items.isEmpty() || !on()) {
			return false;
		}
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null || menu.containerId != containerId) {
			return false;
		}
		boolean outside = slotId == ProtectionRules.OUTSIDE;
		Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
		ItemStack clicked = slot == null ? ItemStack.EMPTY : slot.getItem();
		ItemStack carried = menu.getCarried();
		ItemStack swapped = input == ContainerInput.SWAP && button >= 0 && button < HOTBAR_SIZE ? player.getInventory().getItem(button) : ItemStack.EMPTY;
		boolean onProtected = isProtected(clicked);
		boolean carrying = isProtected(carried);
		boolean swapping = isProtected(swapped);
		// the shop's button that sells all at once: it would take the protected items along
		boolean sellsAll = menu instanceof ChestMenu && !clicked.isEmpty() && SELL_ALL_BUTTON.equals(clicked.getHoverName().getString()) && holdsProtected(player);
		if (!onProtected && !carrying && !swapping && !sellsAll) {
			return false;
		}
		boolean dungeon = inDungeon();
		ProtectionRules.Menu kind = menuOf(menu, dungeon);
		boolean inMenuPart = slot != null && slot.container != player.getInventory();
		ProtectionRules.Click click = new ProtectionRules.Click(onProtected || sellsAll, carrying, swapping, outside, input == ContainerInput.THROW, input == ContainerInput.QUICK_MOVE, inMenuPart);
		ProtectionRules.Blocked blocked = ProtectionRules.check(click, kind, switches(), dungeon);
		if (blocked == ProtectionRules.Blocked.NOTHING) {
			return false;
		}
		block(blocked, onProtected ? clicked : carrying ? carried : swapped);
		return true;
	}

	/** @return whether using the held item has to do nothing: it is yet to evolve */
	public boolean holdsBackUse(Player player, InteractionHand hand) {
		if (!consumables.get() || !on()) {
			return false;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!ProtectionRules.evolving(ItemGains.idOf(held))) {
			return false;
		}
		block(ProtectionRules.Blocked.CONSUME, held);
		return true;
	}

	/** @return whether right-clicking this entity has to do nothing: a protected item would go into an empty frame on the player's own land */
	public boolean holdsBackInteract(Player player, Entity entity, InteractionHand hand) {
		if (!(entity instanceof ItemFrame frame) || !frame.getItem().isEmpty() || !blockFrame.get() || !ownLand()) {
			return false;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!isProtected(held)) {
			return false;
		}
		block(ProtectionRules.Blocked.FRAME, held);
		return true;
	}

	/** @return the result of an action held back: it is over, and the hand does not swing */
	public static InteractionResult heldBack() {
		return InteractionResult.FAIL;
	}

	private ProtectionRules.Switches switches() {
		return new ProtectionRules.Switches(blockDrop.get(), blockSell.get(), blockSalvage.get(), blockTrade.get(), blockAuction.get(), blockDungeonChest.get(), dropInDungeons.get());
	}

	private boolean holdsProtected(Player player) {
		for (int i = 0; i < PLAYER_SLOTS; i++) {
			if (isProtected(player.getInventory().getItem(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean inDungeon() {
		return SkyBlockIsland.ofArea(SkyBlockArea.island()) == SkyBlockIsland.DUNGEONS;
	}

	private static boolean ownLand() {
		return SkyBlockArea.inGarden() || SkyBlockIsland.ofArea(SkyBlockArea.island()) == SkyBlockIsland.PRIVATE_ISLAND;
	}

	private static ProtectionRules.Menu menuOf(AbstractContainerMenu menu, boolean inDungeon) {
		Minecraft client = Minecraft.getInstance();
		String title = client.gui.screen() == null ? "" : client.gui.screen().getTitle().getString();
		ProtectionRules.Menu kind = ProtectionRules.menuOf(title, inDungeon);
		return kind == ProtectionRules.Menu.OTHER && isShop(menu) ? ProtectionRules.Menu.SHOP : kind;
	}

	/** A shop has no title of its own; it has a button to sell at, or one for buying back what was sold. */
	private static boolean isShop(AbstractContainerMenu menu) {
		if (!(menu instanceof ChestMenu)) {
			return false;
		}
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getHoverName().getString();
			if (name.equals(SELL_BUTTON) || name.equals(SELL_ALL_BUTTON)) {
				return true;
			}
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					if (line.getString().toLowerCase(Locale.ROOT).contains(BUYBACK_WORD)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void block(ProtectionRules.Blocked blocked, ItemStack stack) {
		pulse(ItemGains.uuidOf(stack));
		long now = System.currentTimeMillis();
		// one notice for a key held down
		if (now - noticedAt >= NOTICE_GAP_MILLIS) {
			noticedAt = now;
			tell(TITLE, blocked.notice);
		}
	}

	private void tell(String title, String text) {
		switch (notice.get()) {
			case TOAST -> Toasts.brief(ICON, title, text, TOAST_SECONDS);
			case CHAT -> {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal(text.isEmpty() ? title : title + ": " + text));
				}
			}
			case OFF -> {
			}
		}
	}

	private void pulse(String uuid) {
		pulsing = uuid;
		pulsedAt = System.currentTimeMillis();
	}

	/** Draws the lock over a protected item in the slot at this place. */
	public void drawLock(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		float size = LOCK_STEP;
		if (pulsing != null) {
			float step = (System.currentTimeMillis() - pulsedAt) / (float) PULSE_MILLIS;
			if (step >= 1f) {
				pulsing = null;
			} else if (pulsing.equals(ItemGains.uuidOf(stack))) {
				size *= 1f + PULSE_GROWTH * Mth.sin(step * Mth.PI);
			}
		}
		drawLockAt(graphics, x, y, size);
	}

	private static void drawLockAt(GuiGraphicsExtractor graphics, int x, int y, float size) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + LOCK_CENTER * LOCK_STEP, y + LOCK_CENTER * LOCK_STEP);
		graphics.pose().scale(size);
		graphics.pose().translate(-LOCK_CENTER, -LOCK_CENTER);
		for (int[] run : LOCK_RUNS) {
			graphics.fill(run[1], run[0], run[2], run[0] + 1, LOCK_COLOR);
		}
		graphics.pose().popMatrix();
	}
}
