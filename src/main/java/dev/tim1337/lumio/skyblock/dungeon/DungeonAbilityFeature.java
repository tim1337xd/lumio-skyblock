package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/**
 * The ability helper: two keys of the player's own for the class ability and the ultimate, and alerts when they are ready and when the
 * ultimate is needed. In a dungeon, one press of a key is exactly one of the game's own drop actions (the class ability is the drop of one item,
 * the ultimate the drop of the stack, as Hypixel reads them), as if the player had pressed the drop key; outside a dungeon the keys do nothing.
 * Never automatic: nothing happens without the press.
 */
public class DungeonAbilityFeature extends AddonFeature {
	private static final String ALERTS = "Alerts";

	private final Toggle abilityReady = add(make().toggle("ability_ready", Component.literal("Ability ready"), true), ALERTS);
	private final Toggle ultimateReady = add(make().toggle("ultimate_ready", Component.literal("Ultimate ready"), true), ALERTS);
	private final Toggle useUltimate = add(make().toggle("use_ultimate", Component.literal("Use your ultimate now"), true), ALERTS,
		"When to use it, by class");
	private final Choice<DungeonAlert.Style> style = add(make().choice("style", Component.literal("Style"), DungeonAlert.Style.TOAST,
		value -> Component.literal(value.label()), Choice.Style.SEGMENTED), ALERTS);

	private KeyMapping abilityKey;
	private KeyMapping ultimateKey;

	public DungeonAbilityFeature() {
		super("dungeon_abilities", "Ability helper", "Ability keys and ready alerts.", false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("bolt");
		summary("Ability keys and alerts");
		style.visibleWhen(() -> abilityReady.get() || ultimateReady.get() || useUltimate.get());
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String line) {
				onLine(line);
			}
		});
	}

	public void register() {
		abilityKey = DungeonKeys.register("dungeon_class_ability");
		ultimateKey = DungeonKeys.register("dungeon_ultimate");
		addKey(abilityKey, KeyPlace.WORLD);
		addKey(ultimateKey, KeyPlace.WORLD);
	}

	/** One of the game's drop actions per press, only in a dungeon and without a screen open, like the game's own drop key. */
	public void tick(Minecraft client) {
		int ability = DungeonKeys.presses(abilityKey);
		int ultimate = DungeonKeys.presses(ultimateKey);
		LocalPlayer player = client.player;
		if (!isEnabled() || !Dungeon.in() || player == null || client.gui.screen() != null || player.isSpectator()) {
			return;
		}
		if (ability > 0) {
			drop(player, false);
		}
		if (ultimate > 0) {
			drop(player, true);
		}
	}

	/** The game's own drop, as its drop key does it (one press, one drop, the arm swung when it went through). */
	private static void drop(LocalPlayer player, boolean stack) {
		//#if 26.3
		//$ Minecraft client = Minecraft.getInstance();
		//$ if (client.gameMode != null) {
		//$ 	client.gameMode.dropItem(player, stack);
		//$ }
		//#else
		if (player.drop(stack)) {
			player.swing(InteractionHand.MAIN_HAND);
		}
		//#endif
	}

	private void onLine(String line) {
		if (!isEnabled()) {
			return;
		}
		if (abilityReady.get() && DungeonParse.ABILITY_READY.matcher(line).matches()) {
			DungeonAlert.show(style.get(), "Ability ready", line.substring(0, line.indexOf(" is now available!")), SystemColors.GREEN);
		} else if (ultimateReady.get() && DungeonParse.ULTIMATE_READY.matcher(line).matches()) {
			DungeonAlert.show(style.get(), "Ultimate ready", line.substring(0, line.indexOf(" is ready to use!")), SystemColors.YELLOW);
		} else if (useUltimate.get()) {
			String moment = ultimateMoment(line, selfClass());
			if (moment != null) {
				DungeonAlert.show(style.get(), "Use your ultimate now", moment + " · " + keyHint(), SystemColors.YELLOW);
			}
		}
	}

	/** @return what calls for the ultimate in a line for the player's class ("Maxor is enraged"), or null */
	static String ultimateMoment(String line, DungeonClass dungeonClass) {
		boolean holder = dungeonClass == DungeonClass.HEALER || dungeonClass == DungeonClass.TANK;
		if (DungeonParse.GIANTS.matcher(line).matches()) {
			return "The giants are unleashed";
		}
		if (!holder) {
			return null;
		}
		if (DungeonParse.MAXOR_ENRAGED.matcher(line).matches()) {
			return "Maxor is enraged";
		}
		if (DungeonParse.GOLDOR_DONE.matcher(line).matches()) {
			return "Goldor's factory is down";
		}
		if (DungeonParse.LIVID_START.matcher(line).matches()) {
			return "Livid begins";
		}
		return null;
	}

	private String keyHint() {
		return ultimateKey == null || ultimateKey.isUnbound() ? "Set a key for Ultimate" : "press " + ultimateKey.getTranslatedKeyMessage().getString();
	}

	private static DungeonClass selfClass() {
		LocalPlayer self = Minecraft.getInstance().player;
		DungeonPlayer player = self == null ? null : Dungeon.player(self.getName().getString());
		return player == null ? null : player.dungeonClass();
	}
}
