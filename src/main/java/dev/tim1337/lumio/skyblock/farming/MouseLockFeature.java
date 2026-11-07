package dev.tim1337.lumio.skyblock.farming;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.CameraTurn;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Locks or slows what the mouse does to the camera in the Garden, by a key or /lumio mouselock, for farming in a straight line: only the mouse's effect on the camera
 * changes, nothing is sent and nothing is automated. A small pill shows while it is on. It always unlocks when the game loses focus, on death, on disconnect, on leaving
 * the Garden and when the feature is switched off, so the player can never get stuck. The idea is SkyHanni's Mouse Sensitivity Reducer.
 */
public class MouseLockFeature extends AddonPanel {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "mouse_lock"));
	private static final float TOAST_SECONDS = 2f;
	/** The default place: above the crosshair at every GUI scale, out of the way. */
	private static final float DEFAULT_Y = 80f;
	/** The pill. */
	private static final int HEIGHT = 16;
	private static final int ICON = 8;
	private static final float ICON_GAP = 4f;
	private static final int LOCKED_WIDTH = 86;
	private static final int SLOWED_WIDTH = 118;
	private static final float SHOWN = 0.01f;
	private static final float TEXT_TOP = 4.5f;

	/** What the mode is called. */
	public enum ModeName {
		LOCK("Lock", MouseLock.Mode.LOCK),
		SLOW("Slow", MouseLock.Mode.SLOW);

		private final String title;
		private final MouseLock.Mode mode;

		ModeName(String title, MouseLock.Mode mode) {
			this.title = title;
			this.mode = mode;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	private final Choice<ModeName> mode = add(make().choice("mode", Component.literal("Mode"), ModeName.LOCK, ModeName::displayName, Choice.Style.SEGMENTED), "General",
		"Lock: the camera doesn't turn. Slow: for small adjustments");
	private final Slider percent = add(make().slider("percent", Component.literal("Slow to"), 10f, 1f, 99f, 0), "General");
	private final Toggle unlockOnTeleport = add(make().toggle("unlock_on_teleport", Component.literal("Unlock when teleporting"), true), "General",
		"To another plot or the barn");
	private final Toggle notices = add(make().toggle("notices", Component.literal("Notifications"), true), "General", "A short note when it locks or unlocks");

	{
		percent.under(mode, () -> mode.get() == ModeName.SLOW);
	}

	private final MouseLock lock = new MouseLock();
	private final Spring amount = Spring.smooth(Spring.DEFAULT);
	private KeyMapping key;
	private boolean hinted;
	private long lastFrame;

	public MouseLockFeature() {
		super("mouse_lock", "Mouse lock", "Locks or slows what the mouse does to the camera in the Garden, by a key or /lumio mouselock. Only the camera changes: nothing is sent.", true,
			Anchor.CENTER, false, 0f, DEFAULT_Y);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("lock");
		summary("Keep the camera still while you farm");
	}

	public void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".mouse_lock", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		addKey(key, KeyPlace.WORLD);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(ClientCommands.literal("lumio")
			.then(ClientCommands.literal("mouselock").executes(context -> {
				toggle();
				return 1;
			}))));
	}

	private void tick(Minecraft client) {
		// the game counts presses only while no screen is open; one left from before is dropped with the rest
		boolean pressed = false;
		while (key != null && key.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.gui.screen() == null && isEnabled()) {
			toggle();
		}
		boolean connected = client.player != null && client.level != null;
		if (lock.check(isEnabled(), connected, SkyBlockArea.inGarden(), client.isWindowActive(), connected && !client.player.isDeadOrDying()) && connected && isEnabled()) {
			notice("Mouse unlocked");
		}
		CameraTurn.set(lock.scale(mode.get().mode, percent.get()));
	}

	/** Locks or unlocks, as the key and the command do. */
	private void toggle() {
		if (!isEnabled()) {
			return;
		}
		switch (lock.toggle(SkyBlockArea.inGarden())) {
			case LOCKED -> notice(text());
			case UNLOCKED -> notice("Mouse unlocked");
			case NOT_HERE -> {
				if (!hinted) {
					hinted = true;
					Toasts.brief("lock", "Mouse lock works in the Garden", "", TOAST_SECONDS);
				}
			}
		}
		CameraTurn.set(lock.scale(mode.get().mode, percent.get()));
	}

	/** Reads the game's message about a teleport. */
	public void onMessage(Component message) {
		if (lock.onChat(SkyBlockArea.plainLine(message.getString()).strip(), unlockOnTeleport.get())) {
			CameraTurn.reset();
			notice("Mouse unlocked");
		}
	}

	private void notice(String title) {
		if (notices.get()) {
			Toasts.brief("lock", title, "", TOAST_SECONDS);
		}
	}

	private boolean slow() {
		return mode.get() == ModeName.SLOW;
	}

	private String text() {
		return slow() ? "Mouse slowed · " + Math.round(percent.get()) + " %" : "Mouse locked";
	}

	@Override
	public boolean shown(Minecraft client) {
		return lock.on() || amount.value() > SHOWN || simulated();
	}

	@Override
	public int panelWidth() {
		return slow() ? SLOWED_WIDTH : LOCKED_WIDTH;
	}

	@Override
	public int panelHeight() {
		return HEIGHT;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		long now = System.nanoTime();
		float seconds = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		// it comes and goes softly; in the HUD editor it stands there to be placed
		float shown = amount.follow(lock.on() || simulated() || HudEditor.isOpen() ? 1f : 0f, seconds);
		float before = Draw.alpha();
		Draw.setAlpha(before * shown);
		try {
			int width = panelWidth();
			drawGlass(ui, width, HEIGHT, Radius.pill(HEIGHT));
			String text = text();
			float textWidth = ui.boldWidthScaled(text, TextScale.SMALL);
			float x = (width - ICON - ICON_GAP - textWidth) / 2f;
			ui.icon("lock", Math.round(x), (HEIGHT - ICON) / 2, ICON, HudTheme.text());
			ui.textAtScaledBold(text, x + ICON + ICON_GAP, TEXT_TOP, TextScale.SMALL, HudTheme.text());
		} finally {
			Draw.setAlpha(before);
		}
	}
}
