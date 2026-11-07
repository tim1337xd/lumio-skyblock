package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The Grappling Hook's cooldown while the hook is held: a small timer and a thin bar that empties. It only reads what the player does and what the game says. Hidden when the hook is not held or the cooldown is over. */
public class GrapplingHudFeature extends AddonPanel {
	private static final int WIDTH = 52;
	private static final int HEIGHT = 18;
	private static final int BAR_HEIGHT = 2;
	private static final int BAR_FROM_BOTTOM = 4;
	private static final double MILLIS = 1000.0;
	private static final float SAMPLE_SHARE = 0.6f;
	private static final String SAMPLE_TEXT = "1.2s";

	private final GrapplingCooldown cooldown = new GrapplingCooldown();
	private long lastTick;
	private boolean holding;
	private double shownLeft = -1.0;
	private String shownText = "";

	public GrapplingHudFeature() {
		super("grappling_hook", "Grappling Hook cooldown", "While you hold a Grappling Hook: its cooldown, as a small timer and a thin bar.", true, Anchor.CENTER, true, 125f, 18f);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("timer");
		summary("A timer while your hook cools down");
		offerNoBackground();
	}

	/** Called every tick: the time goes by, and what is held is looked at. */
	public void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		cooldown.advance(lastTick == 0L ? 0.0 : (now - lastTick) / MILLIS);
		lastTick = now;
		holding = client.player != null && GrapplingCooldown.HOOK_ID.equals(ItemGains.idOf(client.player.getMainHandItem()));
		if (client.player != null && isEnabled()) {
			Vec3 speed = client.player.getDeltaMovement();
			if (cooldown.moved(speed.x, speed.y, speed.z)) {
				Debug.log("Grappling Hook: pulled, the speed is now {}", speed);
			}
		}
	}

	/** The player used an item: with the hook out this reels it in, which may pull or not. Only looked at: the use goes on as it is. */
	public void onUse(Player player, ItemStack held) {
		if (isEnabled() && SkyBlock.allowed() && player.fishing != null && GrapplingCooldown.HOOK_ID.equals(ItemGains.idOf(held))) {
			cooldown.reeled(GrapplingCooldown.fromLore(ItemGains.loreLines(held)));
		}
	}

	/** A message from the server. */
	public void onMessage(Component message) {
		if (isEnabled() && holding && GrapplingCooldown.tooSoon(TextColors.noColors(message.getString()))) {
			Debug.log("Grappling Hook: the game says it was used too soon: {}", message.getString());
			cooldown.resync();
		}
	}

	@Override
	public boolean shown(Minecraft client) {
		return SkyBlock.allowed() && holding && cooldown.running();
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return HEIGHT;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, HEIGHT, PanelKit.RADIUS);
		boolean real = cooldown.running();
		float share = real ? cooldown.share() : SAMPLE_SHARE;
		ui.textAtScaledBold(real ? text() : SAMPLE_TEXT, PanelKit.PAD, 3f, TextScale.BODY, HudTheme.text());
		PanelKit.bar(ui, PanelKit.PAD, HEIGHT - BAR_FROM_BOTTOM, WIDTH - 2f * PanelKit.PAD, BAR_HEIGHT, share, accentArgb());
	}

	/** @return the seconds left, as text made again only when the tenths change */
	private String text() {
		double tenths = Math.ceil(cooldown.left() * 10.0) / 10.0;
		if (tenths != shownLeft) {
			shownLeft = tenths;
			shownText = String.format(Locale.ROOT, "%.1fs", tenths);
		}
		return shownText;
	}
}
