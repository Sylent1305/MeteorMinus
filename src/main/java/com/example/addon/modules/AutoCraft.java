package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class AutoCraft extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§.");

    public enum Mode
    {
        Vanilla,
        Custom
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.Vanilla)
        .build());

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay between actions. 0 = Instant.")
        .defaultValue(1)
        .min(0)
        .sliderMax(20)
        .build());

    private final Setting<Boolean> stackMove = sgGeneral.add(new BoolSetting.Builder()
        .name("stack-move")
        .description("Moves the whole stack at once instead of individual items.")
        .defaultValue(false)
        .build());

    private final Setting<List<Item>> vanillaResult = sgGeneral.add(new ItemListSetting.Builder()
        .name("result-item")
        .defaultValue(Collections.emptyList())
        .visible(() -> mode.get() == Mode.Vanilla)
        .build());

    private final List<Setting<List<Item>>> vanillaGrid = new ArrayList<>();

    private final Setting<String> customResultName = sgGeneral.add(new StringSetting.Builder()
        .name("result-name")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.Custom)
        .build());

    private final List<Setting<String>> customGridNames = new ArrayList<>();
    private final List<Setting<Integer>> gridAmounts = new ArrayList<>();

    private final Setting<Boolean> dropCraft = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-craft")
        .defaultValue(false)
        .build());

    private int delayTimer;

    public AutoCraft()
    {
        super(AddonTemplate.METEOR_MINUS, "auto-craft", "Advanced crafting with stack move support.");

        for (int i = 1; i <= 9; i++)
        {
            int slotNum = i;

            vanillaGrid.add(sgGeneral.add(new ItemListSetting.Builder()
                .name("slot-" + slotNum + "-item")
                .defaultValue(Collections.emptyList())
                .visible(() -> mode.get() == Mode.Vanilla)
                .build()));

            customGridNames.add(sgGeneral.add(new StringSetting.Builder()
                .name("slot-" + slotNum + "-name")
                .defaultValue("")
                .visible(() -> mode.get() == Mode.Custom)
                .build()));

            gridAmounts.add(sgGeneral.add(new IntSetting.Builder()
                .name("slot-" + slotNum + "-amount")
                .defaultValue(1)
                .min(0)
                .visible(() -> !stackMove.get())
                .build()));
        }
    }

    @Override
    public void onActivate()
    {
        delayTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event)
    {
        if (mc.player == null || !(mc.currentScreen instanceof CraftingScreen screen))
        {
            return;
        }

        if (delayTimer > 0)
        {
            delayTimer--;
            return;
        }

        ItemStack resultStack = screen.getScreenHandler().getSlot(0).getStack();

        if (!resultStack.isEmpty() && isTargetResult(resultStack))
        {
            takeResult(screen);
            delayTimer = tickDelay.get();
            return;
        }

        for (int i = 0; i < 9; i++)
        {
            int gridSlot = i + 1;

            ItemStack currentInGrid = screen.getScreenHandler().getSlot(gridSlot).getStack();
            boolean needsItem = stackMove.get() ? currentInGrid.isEmpty() : currentInGrid.getCount() < gridAmounts.get(i).get();

            if (needsItem)
            {
                boolean success = (mode.get() == Mode.Vanilla)
                    ? fillVanilla(screen, gridSlot, i)
                    : fillCustom(screen, gridSlot, i);

                if (success)
                {
                    if (tickDelay.get() > 0)
                    {
                        delayTimer = tickDelay.get();
                        return;
                    }
                }
            }
        }
    }

    private boolean fillVanilla(CraftingScreen screen, int gridSlot, int index)
    {
        List<Item> targets = vanillaGrid.get(index).get();

        if (targets.isEmpty())
        {
            return false;
        }

        Item target = targets.get(0);

        for (int i = 10; i < screen.getScreenHandler().slots.size(); i++)
        {
            ItemStack invStack = screen.getScreenHandler().getSlot(i).getStack();

            if (invStack.getItem() == target)
            {
                doClick(screen, i, gridSlot);
                return true;
            }
        }

        return false;
    }

    private boolean fillCustom(CraftingScreen screen, int gridSlot, int index)
    {
        String targetName = customGridNames.get(index).get().trim();

        if (targetName.isEmpty())
        {
            return false;
        }

        for (int i = 10; i < screen.getScreenHandler().slots.size(); i++)
        {
            ItemStack invStack = screen.getScreenHandler().getSlot(i).getStack();

            if (stripColor(invStack.getName().getString()).trim().equalsIgnoreCase(targetName))
            {
                doClick(screen, i, gridSlot);
                return true;
            }
        }

        return false;
    }

    private void doClick(CraftingScreen screen, int from, int to)
    {
        if (stackMove.get())
        {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, from, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, to, 0, SlotActionType.PICKUP, mc.player);
        }
        else
        {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, from, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, to, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private boolean isTargetResult(ItemStack stack)
    {
        if (mode.get() == Mode.Vanilla)
        {
            return !vanillaResult.get().isEmpty() && stack.getItem() == vanillaResult.get().get(0);
        }

        return stripColor(stack.getName().getString()).trim().equalsIgnoreCase(customResultName.get().trim());
    }

    private void takeResult(CraftingScreen screen)
    {
        if (dropCraft.get())
        {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 0, 1, SlotActionType.THROW, mc.player);
        }
        else
        {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    private String stripColor(String text)
    {
        return text == null ? "" : STRIP_COLOR_PATTERN.matcher(text).replaceAll("");
    }
}
