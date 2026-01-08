package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;

public class InventoryLogHud extends HudElement
{
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

    private final Setting<Boolean> useCustomNames = sgGeneral.add(new BoolSetting.Builder()
        .name("use-custom-names")
        .description("Shows renamed item names instead of the default Minecraft name.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> iconOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("icon-only")
        .description("Hides the item name but keeps the +/- and amount.")
        .defaultValue(false)
        .build());

    private final List<ItemChange> changes = new ArrayList<>();
    private final Map<Item, Integer> lastInventory = new HashMap<>();
    private final Map<Item, String> nameCache = new HashMap<>();
    private boolean initialized = false;

    private static final long DISPLAY_TIME = 3000;
    private static final long MERGE_THRESHOLD = 2000;

    private final Color GREEN_COLOR = new Color(137, 243, 54);
    private final Color RED_COLOR = new Color(255, 44, 44);

    public InventoryLogHud()
    {
        super(INFO);
        EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        if (mc.player == null)
        {
            initialized = false;
            lastInventory.clear();
            nameCache.clear();
            return;
        }

        Map<Item, Integer> currentInventory = new HashMap<>();

        for (int i = 0; i < mc.player.getInventory().size(); i++)
        {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty())
            {
                continue;
            }

            Item item = stack.getItem();
            currentInventory.put(item, currentInventory.getOrDefault(item, 0) + stack.getCount());

            if (stack.get(DataComponentTypes.CUSTOM_NAME) != null)
            {
                nameCache.put(item, stack.getName().getString());
            }
        }

        if (!initialized)
        {
            lastInventory.putAll(currentInventory);
            initialized = true;
            return;
        }

        currentInventory.forEach((item, count) ->
        {
            int oldCount = lastInventory.getOrDefault(item, 0);

            if (count > oldCount)
            {
                addChange(item, count - oldCount, true);
            }
        });

        lastInventory.forEach((item, count) ->
        {
            int newCount = currentInventory.getOrDefault(item, 0);

            if (count > newCount)
            {
                addChange(item, count - newCount, false);
            }
        });

        lastInventory.clear();
        lastInventory.putAll(currentInventory);

        changes.removeIf(change -> System.currentTimeMillis() > change.timestamp + DISPLAY_TIME + 500);
    }

    private void addChange(Item item, int amount, boolean added)
    {
        for (ItemChange change : changes)
        {
            if (change.item == item && change.added == added && (System.currentTimeMillis() - change.timestamp) < MERGE_THRESHOLD)
            {
                change.amount += amount;
                change.timestamp = System.currentTimeMillis();
                return;
            }
        }

        String displayName = nameCache.getOrDefault(item, item.getName().getString());

        if (useCustomNames.get() && mc.player != null)
        {
            for (int i = 0; i < mc.player.getInventory().size(); i++)
            {
                ItemStack stack = mc.player.getInventory().getStack(i);

                if (stack.getItem() == item && stack.get(DataComponentTypes.CUSTOM_NAME) != null)
                {
                    displayName = stack.getName().getString();
                    nameCache.put(item, displayName);
                    break;
                }
            }
        }

        changes.add(0, new ItemChange(item, displayName, amount, added, System.currentTimeMillis()));

        if (changes.size() > 10)
        {
            changes.remove(changes.size() - 1);
        }
    }

    @Override
    public void render(HudRenderer renderer)
    {
        List<ItemChange> renderList = new ArrayList<>(changes);

        if (preview.get() && renderList.isEmpty())
        {
            renderList.add(new ItemChange(Items.DIAMOND, "God Diamond", 3, true, System.currentTimeMillis()));
            renderList.add(new ItemChange(Items.NETHERITE_INGOT, "Heavy Scrap", 1, false, System.currentTimeMillis()));
        }

        double currentWidth = 0;
        double currentHeight = 0;
        float s = scaleSetting.get().floatValue();

        for (int i = 0; i < renderList.size(); i++)
        {
            ItemChange change = renderList.get(i);
            long timeDiff = System.currentTimeMillis() - change.timestamp;

            int alpha = 255;

            if (timeDiff > DISPLAY_TIME)
            {
                alpha = (int) (255 * (1 - (double) (timeDiff - DISPLAY_TIME) / 500));
            }

            alpha = Math.max(0, Math.min(255, alpha));

            Color statusColor = change.added ? GREEN_COLOR.copy().a(alpha) : RED_COLOR.copy().a(alpha);
            Color whiteColor = Color.WHITE.copy().a(alpha);

            String prefix = change.added ? "+ " : "- ";
            String nameText = change.displayName;
            String amountText = " x" + change.amount;

            double rowOffset = i * (20 * s);
            double renderX = x;
            double renderY = y + rowOffset;

            renderer.item(change.item.getDefaultStack(), (int) renderX, (int) renderY, s, true);

            double textStartX = renderX + (20 * s);
            double textY = renderY + (5 * s);

            renderer.text(prefix, textStartX, textY, statusColor, true);
            double nextX = textStartX + renderer.textWidth(prefix, true);

            if (!iconOnly.get())
            {
                renderer.text(nameText, nextX, textY, whiteColor, true);
                nextX += renderer.textWidth(nameText, true);
            }

            renderer.text(amountText, nextX, textY, statusColor, true);
            double rowWidth = nextX + renderer.textWidth(amountText, true) - x;

            currentWidth = Math.max(currentWidth, rowWidth);
            currentHeight += (20 * s);
        }

        if (renderList.isEmpty())
        {
            String emptyMsg = "Inventory Log (Empty)";
            renderer.text(emptyMsg, x, y, Color.GRAY, true);
            setSize(renderer.textWidth(emptyMsg, true), renderer.textHeight(true));
        }
        else
        {
            setSize(currentWidth, currentHeight);
        }
    }

    private static class ItemChange
    {
        public final Item item;
        public final String displayName;
        public int amount;
        public final boolean added;
        public long timestamp;

        public ItemChange(Item item, String displayName, int amount, boolean added, long timestamp)
        {
            this.item = item;
            this.displayName = displayName;
            this.amount = amount;
            this.added = added;
            this.timestamp = timestamp;
        }
    }
}
