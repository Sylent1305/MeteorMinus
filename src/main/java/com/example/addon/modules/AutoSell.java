package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class AutoSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
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

    private final Setting<Boolean> antiHopper = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-hopper")
        .description("Prevents getting stuck on slots where hoppers refill items.")
        .defaultValue(true)
        .build()
    );

    private enum State { Idle, Stealing, OpeningSell, Dumping }
    private State state = State.Idle;
    private int timer;
    private final List<Integer> attemptedSlots = new ArrayList<>();

    public AutoSell() {
        super(AddonTemplate.METEOR_MINUS, "auto-sell", "Steals from chests and sells items based on filter.");
    }

    @Override
    public void onActivate() {
        state = State.Idle;
        timer = 0;
        attemptedSlots.clear();
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
                    attemptedSlots.clear();
                    state = State.Stealing;
                }
            }
            case Stealing -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    state = State.Idle;
                    return;
                }

                if (mc.player.getInventory().getEmptySlot() == -1) {
                    mc.player.closeHandledScreen();
                    startSelling();
                    return;
                }

                if (!stealOneItem(screen)) {
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
        timer = 10;
        attemptedSlots.clear();
    }

    private boolean stealOneItem(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            Slot slot = screen.getScreenHandler().slots.get(i);
            if (slot.inventory != mc.player.getInventory() && slot.hasStack()) {
                if (antiHopper.get() && attemptedSlots.contains(i)) continue;

                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                attemptedSlots.add(i);
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

                boolean contains = filterItems.get().contains(item);
                boolean shouldSell = (mode.get() == Mode.Whitelist) ? contains : !contains;

                if (shouldSell) {
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return true;
                }
            }
        }
        return false;
    }
}
