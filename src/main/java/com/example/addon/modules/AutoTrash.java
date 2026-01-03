package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

public class AutoTrash extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Which items to move into the trash GUI.")
        .defaultValue(Items.DIRT, Items.COBBLESTONE)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between opening the trash bin.")
        .defaultValue(100)
        .min(10)
        .sliderMax(500)
        .build()
    );

    private final Setting<Integer> delayDump = sgGeneral.add(new IntSetting.Builder()
        .name("dump-delay")
        .description("Ticks between moving each individual stack.")
        .defaultValue(2)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private int timer;
    private int dumpTimer;
    private boolean sentCommand;

    public AutoTrash() {
        super(AddonTemplate.METEOR_MINUS, "auto-trash", "Dumps all filtered items into /trash one by one.");
    }

    @Override
    public void onActivate() {
        timer = delay.get();
        dumpTimer = 0;
        sentCommand = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0 && !sentCommand) {
            timer--;
            return;
        }

        if (!sentCommand) {
            ChatUtils.sendPlayerMsg("/trash");
            sentCommand = true;
            return;
        }

        if (mc.currentScreen instanceof HandledScreen<?>) {
            if (dumpTimer > 0) {
                dumpTimer--;
                return;
            }

            boolean foundSomethingToDump = dumpOneItem((HandledScreen<?>) mc.currentScreen);

            if (foundSomethingToDump) {
                dumpTimer = delayDump.get();
            } else {
                mc.player.closeHandledScreen();
                sentCommand = false;
                timer = delay.get();
            }
        }
        else if (sentCommand && timer <= -20) {
            sentCommand = false;
            timer = delay.get();
        } else if (sentCommand) {
            timer--;
        }
    }

    private boolean dumpOneItem(HandledScreen<?> screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            Slot slot = screen.getScreenHandler().slots.get(i);

            if (!slot.hasStack() || slot.inventory != mc.player.getInventory()) continue;

            if (items.get().contains(slot.getStack().getItem())) {
                mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                return true;
            }
        }
        return false; // Found nothing!
    }
}
