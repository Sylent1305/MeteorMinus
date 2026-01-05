package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;

public class InventoryLogHud extends HudElement {
    public static final HudElementInfo<InventoryLogHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "inventory-log", "Shows items added or removed with merging and scaling.", InventoryLogHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scaleSetting = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the inventory log.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(3.0)
        .build());

    private final Setting<Boolean> preview = sgGeneral.add(new BoolSetting.Builder()
        .name("preview")
        .description("Shows dummy items to help position the HUD.")
        .defaultValue(false)
        .build());

    private final List<ItemChange> changes = new ArrayList<>();
    private final Map<Item, Integer> lastInventory = new HashMap<>();
    private boolean initialized = false;

    private static final long DISPLAY_TIME = 3000;
    private static final long MERGE_THRESHOLD = 2000;

    public InventoryLogHud() {
        super(INFO);
        EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) {
            initialized = false;
            lastInventory.clear();
            return;
        }

        Map<Item, Integer> currentInventory = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            currentInventory.put(stack.getItem(), currentInventory.getOrDefault(stack.getItem(), 0) + stack.getCount());
        }

        if (!initialized) {
            lastInventory.putAll(currentInventory);
            initialized = true;
            return;
        }

        currentInventory.forEach((item, count) -> {
            int oldCount = lastInventory.getOrDefault(item, 0);
            if (count > oldCount) addChange(item, count - oldCount, true);
        });

        lastInventory.forEach((item, count) -> {
            int newCount = currentInventory.getOrDefault(item, 0);
            if (count > newCount) addChange(item, count - newCount, false);
        });

        lastInventory.clear();
        lastInventory.putAll(currentInventory);

        changes.removeIf(change -> System.currentTimeMillis() > change.timestamp + DISPLAY_TIME + 500);
    }

    private void addChange(Item item, int amount, boolean added) {
        for (ItemChange change : changes) {
            if (change.item == item && change.added == added && (System.currentTimeMillis() - change.timestamp) < MERGE_THRESHOLD) {
                change.amount += amount;
                change.timestamp = System.currentTimeMillis();
                return;
            }
        }

        changes.add(0, new ItemChange(item, amount, added, System.currentTimeMillis()));
        if (changes.size() > 10) changes.remove(changes.size() - 1);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<ItemChange> renderList = new ArrayList<>(changes);

        if (preview.get() && renderList.isEmpty()) {
            renderList.add(new ItemChange(Items.DIAMOND, 3, true, System.currentTimeMillis()));
            renderList.add(new ItemChange(Items.NETHERITE_INGOT, 1, false, System.currentTimeMillis()));
        }

        double currentWidth = 0;
        double currentHeight = 0;
        float s = scaleSetting.get().floatValue();

        for (int i = 0; i < renderList.size(); i++) {
            ItemChange change = renderList.get(i);
            long timeDiff = System.currentTimeMillis() - change.timestamp;

            int alpha = 255;
            if (timeDiff > DISPLAY_TIME) {
                alpha = (int) (255 * (1 - (double) (timeDiff - DISPLAY_TIME) / 500));
            }
            alpha = Math.max(0, Math.min(255, alpha));

            String sign = change.added ? "§a+ " : "§c- ";
            String text = sign + change.item.getName().getString() + " x" + change.amount;

            double rowOffset = i * (20 * s);
            double renderX = x;
            double renderY = y + rowOffset;

            Color textColor = new Color(255, 255, 255, alpha);

            renderer.item(change.item.getDefaultStack(), (int) renderX, (int) renderY, s, true);
            renderer.text(text, renderX + (20 * s), renderY + (5 * s), textColor, true);

            currentWidth = Math.max(currentWidth, (20 * s) + (renderer.textWidth(text, true)));
            currentHeight += (20 * s);
        }

        if (renderList.isEmpty()) {
            String emptyMsg = "Inventory Log (Empty)";
            renderer.text(emptyMsg, x, y, Color.GRAY, true);
            setSize(renderer.textWidth(emptyMsg, true), renderer.textHeight(true));
        } else {
            setSize(currentWidth, currentHeight);
        }
    }

    private static class ItemChange {
        public final Item item;
        public int amount;
        public final boolean added;
        public long timestamp;

        public ItemChange(Item item, int amount, boolean added, long timestamp) {
            this.item = item;
            this.amount = amount;
            this.added = added;
            this.timestamp = timestamp;
        }
    }
}
