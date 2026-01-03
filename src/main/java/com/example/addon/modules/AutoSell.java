package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

public class AutoSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<String> sellCommand = sgGeneral.add(new StringSetting.Builder()
        .name("sell-command")
        .description("The command to open the sell interface.")
        .defaultValue("/sellgui")
        .build()
    );

    private final Setting<Integer> stealDelay = sgGeneral.add(new IntSetting.Builder()
        .name("steal-delay")
        .description("Ticks between stealing each item from the chest.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> dumpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("dump-delay")
        .description("Ticks between dumping each item into the sell GUI.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<List<Item>> filterItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("filter-items")
        .description("Items to exclude or include during selling.")
        .defaultValue(Items.DIAMOND_PICKAXE, Items.GOLDEN_APPLE)
        .build()
    );

    private final Setting<Boolean> blacklistMode = sgGeneral.add(new BoolSetting.Builder()
        .name("blacklist-mode")
        .description("If enabled, items in the list will NOT be sold.")
        .defaultValue(true)
        .build()
    );

    private enum State {
        Idle,
        Stealing,
        OpeningSell,
        Dumping
    }

    private State state = State.Idle;
    private int timer;

    public AutoSell() {
        super(AddonTemplate.METEOR_MINUS, "auto-sell", "Steals from chests and dumps into sell GUI.");
    }

    @Override
    public void onActivate() {
        state = State.Idle;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        switch (state) {
            case Idle -> {
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    state = State.Stealing;
                }
            }
            case Stealing -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    state = State.Idle;
                    return;
                }

                // Check if inventory is full (getEmptySlot returns -1 if no slots are free)
                if (mc.player.getInventory().getEmptySlot() == -1) {
                    mc.player.closeHandledScreen();
                    startSelling();
                    return;
                }

                if (!stealOneItem(screen)) {
                    // Chest is empty
                    mc.player.closeHandledScreen();
                    startSelling();
                } else {
                    timer = stealDelay.get();
                }
            }
            case OpeningSell -> {
                if (mc.currentScreen instanceof HandledScreen) {
                    state = State.Dumping;
                }
            }
            case Dumping -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
                    state = State.Idle;
                    return;
                }

                if (!dumpOneItem(screen)) {
                    mc.player.closeHandledScreen();
                    state = State.Idle;
                } else {
                    timer = dumpDelay.get();
                }
            }
        }
    }

    private void startSelling() {
        ChatUtils.sendPlayerMsg(sellCommand.get());
        state = State.OpeningSell;
        timer = 10; // Small delay to let the screen open
    }

    private boolean stealOneItem(GenericContainerScreen screen) {
        // top inventory only scammer dumb bozo (chest inv)
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            Slot slot = screen.getScreenHandler().slots.get(i);
            if (slot.inventory != mc.player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private boolean dumpOneItem(HandledScreen<?> screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            Slot slot = screen.getScreenHandler().slots.get(i);

            if (slot.inventory == mc.player.getInventory() && slot.hasStack()) {
                Item item = slot.getStack().getItem();
                boolean inList = filterItems.get().contains(item);

                boolean shouldSell = blacklistMode.get() ? !inList : inList;

                if (shouldSell) {
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
        }
        return false;
    }
}
