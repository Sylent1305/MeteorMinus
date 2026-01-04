package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AutoSell extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode
    {
        Whitelist,
        Blacklist
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Whitelist: Only sell items in list. Blacklist: Sell everything EXCEPT items in list.")
        .defaultValue(Mode.Blacklist)
        .build()
    );

    private final Setting<List<Item>> filterItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("filter-items")
        .description("Items to filter for the sell process.")
        .defaultValue(Items.DIAMOND_PICKAXE, Items.GOLDEN_APPLE, Items.NETHERITE_SWORD)
        .build()
    );

    private final Setting<String> sellCommand = sgGeneral.add(new StringSetting.Builder()
        .name("sell-command")
        .description("The command to open the sell interface.")
        .defaultValue("/sellgui")
        .build()
    );

    private final Setting<Boolean> repeatedSelling = sgGeneral.add(new BoolSetting.Builder()
        .name("repeated-selling")
        .description("Automatically re-opens the last chest until it is empty.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> massSteal = sgGeneral.add(new BoolSetting.Builder()
        .name("mass-steal")
        .description("Steals everything from the chest in one tick.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> massDump = sgGeneral.add(new BoolSetting.Builder()
        .name("mass-dump")
        .description("Dumps everything into the sell GUI in one tick.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> stealDelay = sgGeneral.add(new IntSetting.Builder()
        .name("steal-delay")
        .description("Ticks between stealing items.")
        .defaultValue(2)
        .min(0)
        .visible(() -> !massSteal.get())
        .build()
    );

    private final Setting<Integer> dumpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("dump-delay")
        .description("Ticks between dumping items.")
        .defaultValue(2)
        .min(0)
        .visible(() -> !massDump.get())
        .build()
    );

    private final Setting<Boolean> antiHopper = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-hopper")
        .description("Prevents getting stuck on slots where hoppers refill items.")
        .defaultValue(true)
        .build()
    );

    private enum State
    {
        Idle,
        Stealing,
        OpeningSell,
        Dumping,
        Reopening
    }

    private State state = State.Idle;
    private int timer;
    private BlockPos lastChestPos;
    private boolean chestEmpty;
    private final List<Integer> attemptedSlots = new ArrayList<>();

    public AutoSell()
    {
        super(AddonTemplate.METEOR_MINUS, "auto-sell", "Steals from chests and sells items based on filter.");
    }

    @Override
    public void onActivate()
    {
        state = State.Idle;
        timer = 0;
        lastChestPos = null;
        chestEmpty = false;
        attemptedSlots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event)
    {
        if (mc.player == null || mc.world == null)
        {
            state = State.Idle;
            return;
        }

        if (timer > 0)
        {
            timer--;
            return;
        }

        switch (state)
        {
            case Idle ->
            {
                if (mc.currentScreen instanceof GenericContainerScreen)
                {
                    if (mc.crosshairTarget instanceof BlockHitResult hr)
                    {
                        lastChestPos = hr.getBlockPos();
                    }
                    attemptedSlots.clear();
                    chestEmpty = false;
                    state = State.Stealing;
                }
            }
            case Stealing ->
            {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen))
                {
                    state = State.Idle;
                    return;
                }

                if (mc.player.getInventory().getEmptySlot() == -1)
                {
                    chestEmpty = false;
                    mc.player.closeHandledScreen();
                    startSelling();
                    return;
                }

                if (massSteal.get())
                {
                    while (stealOneItem(screen) && mc.player.getInventory().getEmptySlot() != -1)
                    {
                    }

                    if (mc.player.getInventory().getEmptySlot() == -1)
                    {
                        chestEmpty = false;
                    }
                    else
                    {
                        chestEmpty = !canStealMore(screen);
                    }

                    mc.player.closeHandledScreen();
                    startSelling();
                }
                else
                {
                    if (!stealOneItem(screen))
                    {
                        chestEmpty = true;
                        mc.player.closeHandledScreen();
                        startSelling();
                    }
                    else
                    {
                        timer = stealDelay.get();
                    }
                }
            }
            case OpeningSell ->
            {
                if (mc.currentScreen instanceof HandledScreen && !(mc.currentScreen instanceof InventoryScreen))
                {
                    state = State.Dumping;
                }
            }
            case Dumping ->
            {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen) || mc.currentScreen instanceof InventoryScreen)
                {
                    state = State.Idle;
                    return;
                }

                if (massDump.get())
                {
                    while (dumpOneItem(screen))
                    {
                    }
                    finishDumping();
                }
                else
                {
                    if (!dumpOneItem(screen))
                    {
                        finishDumping();
                    }
                    else
                    {
                        timer = dumpDelay.get();
                    }
                }
            }
            case Reopening ->
            {
                if (lastChestPos == null || !repeatedSelling.get() || chestEmpty)
                {
                    state = State.Idle;
                    return;
                }

                Rotations.rotate(Rotations.getYaw(lastChestPos), Rotations.getPitch(lastChestPos), () ->
                {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(lastChestPos.getX(), lastChestPos.getY(), lastChestPos.getZ()), Direction.UP, lastChestPos, false));
                });

                state = State.Idle;
                timer = 10;
            }
        }
    }

    private void startSelling()
    {
        ChatUtils.sendPlayerMsg(sellCommand.get());
        state = State.OpeningSell;
        timer = 10;
        attemptedSlots.clear();
    }

    private void finishDumping()
    {
        mc.player.closeHandledScreen();
        if (repeatedSelling.get() && lastChestPos != null && !chestEmpty)
        {
            state = State.Reopening;
            timer = 5;
        }
        else
        {
            state = State.Idle;
        }
    }

    private boolean canStealMore(GenericContainerScreen screen)
    {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++)
        {
            Slot slot = screen.getScreenHandler().slots.get(i);
            if (slot.inventory != mc.player.getInventory() && slot.hasStack())
            {
                if (antiHopper.get() && attemptedSlots.contains(i))
                {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private boolean stealOneItem(GenericContainerScreen screen)
    {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++)
        {
            Slot slot = screen.getScreenHandler().slots.get(i);
            if (slot.inventory != mc.player.getInventory() && slot.hasStack())
            {
                if (antiHopper.get() && attemptedSlots.contains(i))
                {
                    continue;
                }
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                attemptedSlots.add(i);
                return true;
            }
        }
        return false;
    }

    private boolean dumpOneItem(HandledScreen<?> screen)
    {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++)
        {
            Slot slot = screen.getScreenHandler().slots.get(i);
            if (slot.inventory == mc.player.getInventory() && slot.hasStack())
            {
                Item item = slot.getStack().getItem();
                boolean contains = filterItems.get().contains(item);
                boolean shouldSell = (mode.get() == Mode.Whitelist) ? contains : !contains;

                if (shouldSell)
                {
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onDeactivate()
    {
        state = State.Idle;
        lastChestPos = null;
        attemptedSlots.clear();
    }
}

