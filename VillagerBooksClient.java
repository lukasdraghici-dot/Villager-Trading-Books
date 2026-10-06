package de.villagerbooks;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reine Client-Mod: liest nur, was der Server beim Handeln ohnehin schickt.
 * Funktioniert deshalb auf jedem Server, ohne dass der Server etwas installiert.
 */
public class VillagerBooksClient implements ClientModInitializer {
	public static final String MOD_ID = "villagerbooks";

	private static final int ROW_H = 22;
	private static final int PANEL_W = 220;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private static KeyMapping toggleHudKey;
	private static boolean hudEnabled = false;

	private record Cached(String name, BlockPos pos, List<MerchantOffer> offers) {}

	private static final Map<UUID, Cached> CACHE = new HashMap<>();
	private static UUID lastUuid;
	private static String lastName = "";
	private static BlockPos lastPos = BlockPos.ZERO;

	@Override
	public void onInitializeClient() {
		toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.villagerbooks.toggle_hud",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				CATEGORY));

		// Merken, mit welchem Villager wir gerade handeln.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && entity instanceof AbstractVillager) {
				lastUuid = entity.getUUID();
				lastName = entity.getDisplayName().getString();
				lastPos = entity.blockPosition();
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (toggleHudKey.consumeClick()) {
				hudEnabled = !hudEnabled;
			}
			// Angebote zwischenspeichern, sobald der Server sie geschickt hat.
			if (mc.screen instanceof MerchantScreen ms && lastUuid != null) {
				MerchantOffers offers = ms.getMenu().getOffers();
				if (!offers.isEmpty()) {
					CACHE.put(lastUuid, new Cached(lastName, lastPos, new ArrayList<>(offers)));
				}
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CACHE.clear();
			lastUuid = null;
		});

		// Bessere Handelsübersicht neben dem Vanilla-Fenster.
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof MerchantScreen ms) {
				ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> renderTradePanel(mc, ms, g));
			}
		});

		// Bücher-Anzeige per Taste (ohne offenes Fenster).
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "book_hud"),
				(g, tick) -> renderBookHud(Minecraft.getInstance(), g));
	}

	// ---------------------------------------------------------------- Handelsfenster

	private static void renderTradePanel(Minecraft mc, MerchantScreen screen, GuiGraphics g) {
		MerchantOffers offers = screen.getMenu().getOffers();
		if (offers.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int guiW = 276; // Breite des Vanilla-Handelsfensters
		int guiLeft = (screen.width - guiW) / 2;

		int rows = Math.min(offers.size(), Math.max(1, (screen.height - 50) / ROW_H));
		int panelH = 14 + rows * ROW_H + 14;

		int x = guiLeft - PANEL_W - 6;
		if (x < 2) {
			x = guiLeft + guiW + 6;
			if (x + PANEL_W > screen.width - 2) {
				x = 2;
			}
		}
		int y = Math.max(2, (screen.height - panelH) / 2);

		drawFrame(g, x, y, PANEL_W, panelH);
		g.drawString(font,
				Component.translatable("villagerbooks.panel.title", offers.size()),
				x + 5, y + 4, 0xFFFFFFFF, true);

		int ry = y + 14;
		for (int i = 0; i < rows; i++) {
			drawRow(g, font, offers.get(i), x + 3, ry, PANEL_W - 6);
			ry += ROW_H;
		}
		g.drawString(font,
				Component.translatable("villagerbooks.panel.legend"),
				x + 5, y + panelH - 11, 0xFF909090, false);
	}

	// ---------------------------------------------------------------- Bücher-HUD

	private static void renderBookHud(Minecraft mc, GuiGraphics g) {
		if (!hudEnabled || mc.player == null || mc.screen != null || mc.options.hideGui) {
			return;
		}
		Entity target = mc.crosshairPickEntity;
		UUID id = (target instanceof AbstractVillager) ? target.getUUID() : lastUuid;
		Cached cached = id == null ? null : CACHE.get(id);
		Font font = mc.font;
		int x = 6;
		int y = 6;

		if (cached == null) {
			drawFrame(g, x, y, PANEL_W, 26);
			g.drawString(font, Component.translatable("villagerbooks.hud.unknown"),
					x + 5, y + 9, 0xFFFFFFFF, true);
			return;
		}

		List<MerchantOffer> books = cached.offers().stream()
				.filter(o -> o.getResult().is(Items.ENCHANTED_BOOK))
				.toList();
		int panelH = 14 + Math.max(1, books.size()) * ROW_H + 4;
		drawFrame(g, x, y, PANEL_W, panelH);

		BlockPos p = cached.pos();
		String where = cached.name() + " (" + p.getX() + " " + p.getY() + " " + p.getZ() + ")";
		g.drawString(font,
				font.plainSubstrByWidth(
						Component.translatable("villagerbooks.hud.title", where).getString(), PANEL_W - 10),
				x + 5, y + 4, 0xFFFFFFFF, true);

		if (books.isEmpty()) {
			g.drawString(font, Component.translatable("villagerbooks.hud.none"),
					x + 6, y + 20, 0xFFB0B0B0, false);
			return;
		}
		int ry = y + 14;
		for (MerchantOffer offer : books) {
			drawRow(g, font, offer, x + 3, ry, PANEL_W - 6);
			ry += ROW_H;
		}
	}

	// ---------------------------------------------------------------- Zeichnen

	private static void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xD0101018);
		int c = 0xFF5A5A7A;
		g.fill(x, y, x + w, y + 1, c);
		g.fill(x, y + h - 1, x + w, y + h, c);
		g.fill(x, y, x + 1, y + h, c);
		g.fill(x + w - 1, y, x + w, y + h, c);
	}

	private static void drawRow(GuiGraphics g, Font font, MerchantOffer offer, int x, int y, int w) {
		ItemStack a = offer.getCostA();
		ItemStack b = offer.getCostB();
		ItemStack result = offer.getResult();
		boolean mending = hasMending(result);
		boolean out = offer.isOutOfStock();

		int bg = mending ? 0x7040C040 : (out ? 0x50400000 : 0x30FFFFFF);
		g.fill(x, y, x + w, y + ROW_H - 2, bg);

		int ix = x + 3;
		int iy = y + 1;
		g.renderItem(a, ix, iy);
		g.renderItemDecorations(font, a, ix, iy);
		ix += 18;
		if (!b.isEmpty()) {
			g.renderItem(b, ix, iy);
			g.renderItemDecorations(font, b, ix, iy);
		}
		ix += 18;
		g.drawString(font, "→", ix, iy + 4, 0xFFAAAAAA, false);
		ix += 12;
		g.renderItem(result, ix, iy);
		g.renderItemDecorations(font, result, ix, iy);
		ix += 22;

		int textW = x + w - ix - 3;
		int nameColor = out ? 0xFF808080
				: mending ? 0xFFFFD83A
				: result.is(Items.ENCHANTED_BOOK) ? 0xFFE0A8FF
				: 0xFFFFFFFF;
		String name = (mending ? "★ " : "") + resultName(result).getString();
		g.drawString(font, font.plainSubstrByWidth(name, textW), ix, y + 2, nameColor, true);

		String sub;
		int subColor = 0xFFB0B0B0;
		if (out) {
			sub = Component.translatable("villagerbooks.out_of_stock").getString();
		} else {
			sub = priceText(a, b);
			int base = offer.getBaseCostA().getCount();
			if (a.getCount() < base) {
				subColor = 0xFF55FF55;
			} else if (a.getCount() > base) {
				subColor = 0xFFFF5555;
			}
		}
		g.drawString(font, font.plainSubstrByWidth(sub, textW), ix, y + 12, subColor, false);
	}

	// ---------------------------------------------------------------- Hilfsfunktionen

	private static String priceText(ItemStack a, ItemStack b) {
		String s = stackText(a);
		if (!b.isEmpty()) {
			s += " + " + stackText(b);
		}
		return s;
	}

	private static String stackText(ItemStack s) {
		return s.getCount() + "× " + s.getHoverName().getString();
	}

	private static ItemEnchantments stored(ItemStack stack) {
		ItemEnchantments e = stack.get(DataComponents.STORED_ENCHANTMENTS);
		return e == null ? ItemEnchantments.EMPTY : e;
	}

	private static boolean hasMending(ItemStack stack) {
		for (Object2IntMap.Entry<Holder<Enchantment>> e : stored(stack).entrySet()) {
			if (e.getKey().is(Enchantments.MENDING)) {
				return true;
			}
		}
		return false;
	}

	/** Bei Büchern: lokalisierter Verzauberungsname (z.B. "Reparatur", "Schärfe V"), sonst Itemname. */
	private static Component resultName(ItemStack stack) {
		for (Object2IntMap.Entry<Holder<Enchantment>> e : stored(stack).entrySet()) {
			return Enchantment.getFullname(e.getKey(), e.getIntValue());
		}
		return stack.getHoverName();
	}
}
