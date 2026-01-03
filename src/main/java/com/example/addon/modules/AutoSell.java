package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class AutoSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("sell-delay")
        .description("Ticks between selling items.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private int timer;

    public AutoSell() {
        super(AddonTemplate.METEOR_MINUS, "auto-sell", "Automatically sells items in your inventory.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (timer > 0) {
            timer--;
            return;
        }


        timer = delay.get();
    }
}
