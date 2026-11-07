package dev.tim1337.lumio.skyblock.profit;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reminder for the Re-heated Gummy Polar Bear (the Smoldering Polarization buff), used by the slayer profit tracker. Works like SkyHanni's
 * "No Gummy Warning": hitting a mob with a weapon during a slayer quest in the Smoldering Tomb or The Wasteland (or while wearing armor with Habanero
 * Tactics) without the buff plays a warning sound, or shows a notification, and the tracker shows a "missing" row for a minute afterwards.
 * <p>
 * The buff is known from the chat (eaten / expired) and from the effects widget of the tab list, if the player has turned it on with /widgets.
 */
final class GummyReminder {
	private static final long BUFF_DURATION_MILLIS = 60 * 60_000L;
	/** Used when the tab list shows the buff but no duration could be read. */
	private static final long UNKNOWN_DURATION_MILLIS = 60_000L;
	private static final long TAB_READ_INTERVAL_MILLIS = 1000L;
	private static final long SOUND_INTERVAL_MILLIS = 10_000L;
	/** A notification has its own sound, so it is shown at most this often; the plain sound repeats in between. */
	private static final long NOTIFICATION_INTERVAL_MILLIS = 60_000L;
	/** How long the tracker shows the "missing" row after the last warning. */
	private static final long SHOWN_AFTER_WARNING_MILLIS = 60_000L;
	/** The buff in the effects widget: "Smoldering Polarization 59m 12s" or "Smoldering Polarization 59:12". */
	private static final Pattern TAB_LINE = Pattern.compile("Smoldering Polarization[^\\d]*(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?(?:(\\d+):(\\d{2})(?::(\\d{2}))?)?", Pattern.CASE_INSENSITIVE);

	private final ProfitHudFeature tracker;
	/** Until when the buff lasts, 0 when it is not known to be active. Saved with the tracker. */
	private long activeUntil;
	private long tabReadAt;
	private long warnedAt;
	private long notifiedAt;

	GummyReminder(ProfitHudFeature tracker) {
		this.tracker = tracker;
	}

	long activeUntil() {
		return activeUntil;
	}

	void setActiveUntil(long activeUntil) {
		this.activeUntil = activeUntil;
	}

	/** Reads chat messages about the buff. Called even while the tracker is off, as the reminder can also be a notification. */
	void onMessage(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (text.strip().equals("You ate a Re-heated Gummy Polar Bear!")) {
			activeUntil = System.currentTimeMillis() + BUFF_DURATION_MILLIS;
			LumioApi.saveSoon();
		} else if (lower.contains("smoldering polarization") && (lower.contains("expired") || lower.contains("wore off") || lower.contains("ran out") || lower.contains("ended"))) {
			activeUntil = 0;
			LumioApi.saveSoon();
		}
	}

	/**
	 * @param showingSample whether the tracker shows its example numbers (in the HUD editor), where the row is never shown
	 * @return whether the tracker shows the "missing" row: the player was warned within the last minute and the buff is still not active
	 */
	boolean missing(boolean showingSample) {
		if (!inTracker() || showingSample || System.currentTimeMillis() - warnedAt > SHOWN_AFTER_WARNING_MILLIS) {
			return false;
		}
		readFromTab();
		return activeUntil < System.currentTimeMillis();
	}

	/** The player attacked something: warns when that was a slayer fight that wants the buff and the buff is not active. */
	void onAttack(Player player) {
		if (!wanted() || !SkyBlock.inSkyBlock() || !SkyBlockArea.slayerQuest()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (player != client.player || ItemGains.idOf(player.getMainHandItem()) == null) {
			return;
		}
		String zone = SkyBlockArea.zone();
		boolean smolderingArea = zone.contains("Smoldering Tomb") || zone.contains("The Wasteland");
		if (!smolderingArea && !hasHabaneroTactics(player)) {
			return;
		}
		readFromTab();
		long now = System.currentTimeMillis();
		if (activeUntil > now || now - warnedAt < SOUND_INTERVAL_MILLIS) {
			return;
		}
		warnedAt = now;
		if (asNotification() && now - notifiedAt > NOTIFICATION_INTERVAL_MILLIS) {
			notifiedAt = now;
			Toasts.info("Gummy Polar Bear", "No Gummy Polar Bear active");
		} else {
			player.playSound(SoundEvents.ANVIL_LAND, 0.5f, 1f);
		}
	}

	/** @return whether the reminder is turned on in the slayer helpers, either in this tracker or as a notification */
	private boolean wanted() {
		return inTracker() || asNotification();
	}

	private boolean inTracker() {
		return tracker.isEnabled() && SkyBlockFeatures.SLAYER_HELPERS != null && SkyBlockFeatures.SLAYER_HELPERS.gummyInTracker();
	}

	private static boolean asNotification() {
		return SkyBlockFeatures.SLAYER_HELPERS != null && SkyBlockFeatures.SLAYER_HELPERS.gummyAsNotification();
	}

	/** Reads the remaining time of the buff from the effects widget of the tab list, at most once a second. */
	private void readFromTab() {
		long now = System.currentTimeMillis();
		if (now - tabReadAt < TAB_READ_INTERVAL_MILLIS) {
			return;
		}
		tabReadAt = now;
		for (String line : SkyBlockArea.tab()) {
			Matcher matcher = TAB_LINE.matcher(SkyBlockArea.plainLine(line));
			if (!matcher.find()) {
				continue;
			}
			long left;
			if (matcher.group(4) != null) {
				// "59:12" or "1:00:00"
				long a = Long.parseLong(matcher.group(4));
				long b = Long.parseLong(matcher.group(5));
				left = matcher.group(6) != null ? (a * 3600 + b * 60 + Long.parseLong(matcher.group(6))) : (a * 60 + b);
			} else {
				left = (matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1)) * 3600) + (matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2)) * 60)
					+ (matcher.group(3) == null ? 0 : Long.parseLong(matcher.group(3)));
			}
			activeUntil = now + (left > 0 ? left * 1000L : UNKNOWN_DURATION_MILLIS);
			return;
		}
	}

	/** @return whether a worn armor piece has the Habanero Tactics enchantment, which also wants the buff */
	private static boolean hasHabaneroTactics(Player player) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			var data = player.getItemBySlot(slot).get(DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().getCompoundOrEmpty("enchantments").contains("ultimate_habanero_tactics")) {
				return true;
			}
		}
		return false;
	}
}
