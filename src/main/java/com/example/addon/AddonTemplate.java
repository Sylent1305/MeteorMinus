package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.InventoryLogHud;
import com.example.addon.modules.AutoCraft;
import com.example.addon.modules.VillagerReroller;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import net.minecraft.item.Items;
import com.example.addon.modules.AutoSell;
import com.example.addon.modules.AutoTrash;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("MeteorMinus");
    public static final HudGroup HUD_GROUP = new HudGroup("MeteorMinus");
    public static final Category METEOR_MINUS = new Category("MeteorMinus", Items.BARRIER.getDefaultStack());
    @Override
    public void onInitialize() {
        LOG.info("Initializing MeteorMinus");

        // Modules
        Modules.get().add(new AutoSell());
        Modules.get().add(new AutoTrash());
        Modules.get().add(new AutoCraft());
        Modules.get().add(new VillagerReroller());



        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(InventoryLogHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(METEOR_MINUS);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorMinus", "meteor-minus");
    }
}
