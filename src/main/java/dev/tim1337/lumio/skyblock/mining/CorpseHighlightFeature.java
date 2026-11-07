package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.skyblock.data.ChatGuard;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlights Glacite Mineshaft corpses through walls, like SkyHanni, once they were seen: a corpse that was never in front of the player, inside
 * the view and in line of sight, is not highlighted, so nothing is revealed that the player has not seen. The armor stands wearing a corpse's
 * armor (Lapis, Umber, Tungsten, Vanguard) get the vanilla glowing outline in their kind's color, pulsing between the color and a bright neon
 * version of it. Opened corpses are dimmed or not highlighted at all.
 */
public class CorpseHighlightFeature extends AddonFeature {
	/** A corpse kind: the helmet it wears and its color. */
	private enum Kind {
		LAPIS("LAPIS_ARMOR_HELMET", 0x2F6BFF),
		UMBER("ARMOR_OF_YOG_HELMET", 0xFF8A2A),
		TUNGSTEN("MINERAL_HELMET", 0xC8D2E0),
		VANGUARD("VANGUARD_HELMET", 0x3AF2FF);

		private final String helmet;
		private final int rgb;

		Kind(String helmet, int rgb) {
			this.helmet = helmet;
			this.rgb = rgb;
		}

		/** @return the display name, as used by SkyHanni and the server: "Vanguard" */
		String title() {
			return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
		}

		static Kind byTitle(String title) {
			for (Kind kind : values()) {
				if (kind.name().equalsIgnoreCase(title)) {
					return kind;
				}
			}
			return null;
		}
	}

	private final Toggle pulse = add(make().toggle("pulse", Component.literal("Pulse"), true), "General",
		"Off: a steady outline");
	private final Toggle hideLooted = add(make().toggle("hide_looted", Component.literal("Hide looted corpses"), false), "General",
		"Off: they are dimmed");
	private final Toggle announce = add(make().toggle("announce", Component.literal("Announce found corpses"), true), "Sharing",
		"With a button to share it with your party");
	private final Toggle waypoints = add(make().toggle("waypoints", Component.literal("Party waypoints"), true), "Sharing",
		"Corpses shared in party chat, also from SkyHanni");

	/** Known corpses by entity id with their kind; and the opened ones. */
	private final Map<Integer, Kind> corpses = new HashMap<>();
	private final Set<Integer> looted = new HashSet<>();
	/** Corpses that were in the player's line of sight at least once; only these are highlighted. */
	private final Set<Integer> seen = new HashSet<>();
	/** Maximum distance at which a corpse counts as seen. */
	private static final double SEEN_DISTANCE = 48.0;
	private Object level;
	private int tick;

	public CorpseHighlightFeature() {
		super("corpse_highlight", "Corpse highlight", "The corpses of the Glacite Mineshafts you have seen, lit through the walls in the color of their kind, with a breathing neon light.", false);
		iconRgb(0x3AF2FF);
		iconName("skull");
		summary("Lights up the mineshaft corpses you have seen");
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::drawWaypoints);
		// a clicked corpse is opened (with a key, or it tells what it needs): it is dimmed from then on
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() && corpses.containsKey(entity.getId())) {
				looted.add(entity.getId());
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() && corpses.containsKey(entity.getId())) {
				looted.add(entity.getId());
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean inMineshaft() {
		return SkyBlockArea.inMineshaft();
	}

	/** Positions of the corpses already announced in this mineshaft. */
	private final Set<BlockPos> announcedAt = new HashSet<>();

	/** Scans the armor stands twice per second: corpses do not move, so that is often enough, and the outline queries a map every frame. */
	private void tick(Minecraft client) {
		if (client.level != level) {
			level = client.level;
			corpses.clear();
			looted.clear();
			seen.clear();
			shared.clear();
			announcedAt.clear();
		}
		if (++tick % 10 != 0 || client.level == null || !isEnabled() || !SkyBlock.inSkyBlock() || !inMineshaft()) {
			if (client.level == null || !isEnabled()) {
				corpses.clear();
			}
			return;
		}
		Map<Integer, Kind> found = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand) {
				String helmet = ItemGains.idOf(stand.getItemBySlot(EquipmentSlot.HEAD));
				if (helmet == null) {
					continue;
				}
				for (Kind kind : Kind.values()) {
					if (kind.helmet.equals(helmet)) {
						found.put(stand.getId(), kind);
						// seen: close enough, in front of the player inside the view, and nothing solid between the player's eyes and it (a raycast
						// twice per second, only for corpses not seen yet)
						if (!seen.contains(stand.getId()) && client.player != null && client.player.distanceTo(stand) <= SEEN_DISTANCE
							&& Sight.visible(client, stand)) {
							seen.add(stand.getId());
							// A corpse leaving and re-entering render distance is a new entity with a new id: announce it once per position, which stays
							// fixed within the same mineshaft.
							if (announcedAt.add(stand.blockPosition().immutable())) {
								announce(client, kind, stand.blockPosition());
							}
						}
						break;
					}
				}
			}
		}
		corpses.clear();
		corpses.putAll(found);
		// a nearby corpse that is seen is highlighted itself: its shared waypoint is no longer needed
		if (!shared.isEmpty() && client.player != null) {
			shared.removeIf(point -> client.player.blockPosition().distSqr(point.pos()) < 6 * 6);
		}
		looted.retainAll(found.keySet());
		seen.retainAll(found.keySet());
	}

	/** @return whether the entity is a currently highlighted corpse */
	public boolean glows(Entity entity) {
		if (!isEnabled() || corpses.isEmpty()) {
			return false;
		}
		Kind kind = corpses.get(entity.getId());
		return kind != null && seen.contains(entity.getId()) && !(hideLooted.get() && looted.contains(entity.getId()));
	}

	/** @return the outline color of a corpse in this frame, or -1 for an entity that is not a corpse */
	public int color(Entity entity) {
		// the game queries every entity's color often: outside a mineshaft this check is all it costs
		if (corpses.isEmpty()) {
			return -1;
		}
		Kind kind = corpses.get(entity.getId());
		if (kind == null) {
			return -1;
		}
		if (looted.contains(entity.getId())) {
			// opened: a muted gray version of its color, without pulsing
			return Draw.mix(kind.rgb, 0x404040, 0.65f);
		}
		if (!pulse.get()) {
			return kind.rgb;
		}
		// pulse: from the color up to a bright, almost white neon version and back, with per-corpse phase offsets so they do not blink in unison
		double seconds = System.nanoTime() / 1_000_000_000.0;
		float wave = 0.5f + 0.5f * (float) Math.sin(seconds * Math.PI * 2.0 * 0.6 + entity.getId() * 0.7);
		float eased = wave * wave * (3f - 2f * wave);
		return Draw.mix(kind.rgb, 0xFFFFFF, 0.08f + 0.5f * eased);
	}

	// ------------------------------------------------------------------------------------------------ sharing, compatible with SkyHanni

	/** A corpse shared in chat: its position, kind, who shared it, and when. */
	private record SharedCorpse(BlockPos pos, Kind kind, String by, long at) {
	}

	private final List<SharedCorpse> shared = new ArrayList<>();
	private static final int MAX_WAYPOINTS = 20;
	/** "x: 152, y: 120, z: 340 | (Vanguard Corpse)": SkyHanni's share format, which this both reads and writes. */
	private static final Pattern SHARED = Pattern.compile(
		"x:\\s*(-?\\d{1,8}),\\s*y:\\s*(-?\\d{1,8}),\\s*z:\\s*(-?\\d{1,8})\\s*\\|\\s*\\((Lapis|Umber|Tungsten|Vanguard) Corpse\\)", Pattern.CASE_INSENSITIVE);
	/** The sender of a party message: "Party > [MVP+] Name: ...". */
	private static final Pattern SENDER = Pattern.compile("(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{2,16}):");
	/** Legacy formatting codes, removed before matching: every chat message passes through here. */
	private static final Pattern COLOR_CODES = Pattern.compile("§.");

	/** @return the chat line that shares a corpse, in SkyHanni's format */
	private static String shareLine(Kind kind, BlockPos pos) {
		return "x: " + pos.getX() + ", y: " + pos.getY() + ", z: " + pos.getZ() + " | (" + kind.title() + " Corpse)";
	}

	/**
	 * Posts a client-side message that a corpse was found: a bar in its color, its name and position, and two buttons, one sharing it in party chat
	 * (in SkyHanni's format, so SkyHanni reads it too) and one copying the position. Only the player sees it.
	 */
	private void announce(Minecraft client, Kind kind, BlockPos pos) {
		if (!announce.get() || client.player == null) {
			return;
		}
		TextColor color = TextColor.fromRgb(kind.rgb);
		String line = shareLine(kind, pos);
		String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		var message = Component.empty()
			.append(Component.literal("\u258C ").withStyle(style -> style.withColor(color)))
			.append(Component.literal("\u2726 ").withStyle(style -> style.withColor(color)))
			.append(Component.literal(kind.title().toUpperCase(Locale.ROOT) + " CORPSE").withStyle(style -> style.withColor(color).withBold(true)))
			.append(Component.literal("  found  ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(coords).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("   "))
			.append(Component.literal("[ Share ]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand("/pc " + line))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Share it in the party chat\n")
					.append(Component.literal(line).withStyle(ChatFormatting.GRAY))
					.append(Component.literal("\nSkyHanni reads it as a waypoint").withStyle(ChatFormatting.DARK_GRAY))))))
			.append(Component.literal(" "))
			.append(Component.literal("[ Copy ]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.CopyToClipboard(line))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy where it is")))));
		client.player.sendSystemMessage(message);
	}

	/** Handles a chat message: a corpse shared by someone (in SkyHanni's format) becomes a waypoint until it is reached. */
	public void onMessage(Component message) {
		if (!isEnabled() || !waypoints.get()) {
			return;
		}
		String text = COLOR_CODES.matcher(message.getString()).replaceAll("");
		// only the party's shares: anyone can write the format in public chat
		if (!ChatGuard.fromParty(text)) {
			return;
		}
		Matcher matcher = SHARED.matcher(text);
		if (!matcher.find()) {
			return;
		}
		Kind kind = Kind.byTitle(matcher.group(4));
		if (kind == null) {
			return;
		}
		BlockPos pos = new BlockPos(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
		Matcher sender = SENDER.matcher(text.substring(0, matcher.start()));
		String by = "";
		while (sender.find()) {
			by = sender.group(1);
		}
		// one waypoint per position, however often it is shared
		for (SharedCorpse point : shared) {
			if (point.pos().equals(pos)) {
				return;
			}
		}
		// a mineshaft has only a few corpses: more waypoints than that are spam, and the oldest give way
		if (shared.size() >= MAX_WAYPOINTS) {
			shared.remove(0);
		}
		shared.add(new SharedCorpse(pos, kind, by, System.currentTimeMillis()));
	}

	/** Draws the waypoints: a light beam in the kind's color above the position, and a block outline. */
	private void drawWaypoints(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (shared.isEmpty() || !isEnabled() || !waypoints.get() || client.player == null) {
			return;
		}
		Vec3 cam = client.gameRenderer.mainCamera().position();
		var pose = context.poseStack();
		double seconds = System.nanoTime() / 1_000_000_000.0;
		List<SharedCorpse> points = List.copyOf(shared);
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (last, consumer) -> {
			for (SharedCorpse point : points) {
				float breath = 0.75f + 0.25f * (float) Math.sin(seconds * 2.4 + point.pos().getX());
				float x = (float) (point.pos().getX() + 0.5 - cam.x);
				float y = (float) (point.pos().getY() - cam.y);
				float z = (float) (point.pos().getZ() + 0.5 - cam.z);
				// the beam: four faces, bright at the bottom and fading out at the top
				int bottom = (Math.round(0.55f * breath * 255f) << 24) | point.kind().rgb;
				int top = point.kind().rgb;
				float half = 0.18f;
				float height = 60f;
				float[][] sides = {{-half, -half, half, -half}, {half, -half, half, half}, {half, half, -half, half}, {-half, half, -half, -half}};
				for (float[] side : sides) {
					consumer.addVertex(last, x + side[0], y, z + side[1]).setColor(bottom);
					consumer.addVertex(last, x + side[2], y, z + side[3]).setColor(bottom);
					consumer.addVertex(last, x + side[2], y + height, z + side[3]).setColor(top);
					consumer.addVertex(last, x + side[0], y + height, z + side[1]).setColor(top);
				}
			}
		});
		for (SharedCorpse point : points) {
			pose.pushPose();
			pose.translate(point.pos().getX() - cam.x, point.pos().getY() - cam.y, point.pos().getZ() - cam.z);
			context.submitNodeCollector().submitShapeOutline(pose, Shapes.block(),
				RenderTypes.lines(), 0xF0000000 | point.kind().rgb, 3f, false);
			pose.popPose();
		}
	}
}
