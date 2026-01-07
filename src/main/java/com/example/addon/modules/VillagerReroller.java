package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Set;

public class VillagerReroller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<RegistryKey<Enchantment>>> targetEnchants = sgGeneral.add(new EnchantmentListSetting.Builder()
        .name("enchantments")
        .description("The enchantments you are looking for.")
        .defaultValue(new HashSet<>())
        .build());

    private final Setting<Integer> minLevel = sgGeneral.add(new IntSetting.Builder()
        .name("min-level")
        .description("Minimum level.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .build());

    private BlockPos stationPos;
    private VillagerEntity targetVillager;
    private State state = State.IDLE;
    private int ticksPassed = 0;
    private int interactAttempts = 0;

    public VillagerReroller() {
        super(AddonTemplate.METEOR_MINUS, "villager-reroller", "Automated villager trade rerolling.");
    }

    @Override
    public void onActivate() {
        ChatUtils.info("Reroller: Select station, then villager.");
        state = State.SELECTING_STATION;
        stationPos = null;
        targetVillager = null;
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (state == State.SELECTING_STATION) {
            stationPos = event.result.getBlockPos();
            ChatUtils.info("Station set. Click the villager.");
            state = State.SELECTING_VILLAGER;
            event.cancel();
        }
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (state == State.SELECTING_VILLAGER && event.entity instanceof VillagerEntity villager) {
            targetVillager = villager;
            ChatUtils.info("Villager set. Starting...");
            state = State.BREAKING;
            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (state == State.IDLE || targetVillager == null || stationPos == null) return;

        switch (state) {
            case BREAKING -> {
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                    return;
                }
                if (mc.world.getBlockState(stationPos).isAir()) {
                    mc.interactionManager.cancelBlockBreaking();
                    state = State.PLACING;
                    ticksPassed = 0;
                } else {
                    Rotations.rotate(Rotations.getYaw(stationPos), Rotations.getPitch(stationPos), () -> {
                        mc.interactionManager.updateBlockBreakingProgress(stationPos, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    });
                }
            }
            case PLACING -> {
                ticksPassed++;
                if (ticksPassed < 10) return;

                int slot = findLecternSlot();
                if (slot == -1) {
                    ChatUtils.error("No Lecterns found!");
                    toggle();
                    return;
                }
                if (BlockUtils.place(stationPos, Hand.MAIN_HAND, slot, true, 0, true, true, false)) {
                    state = State.INTERACTING;
                    ticksPassed = 0;
                    interactAttempts = 0;
                }
            }
            case INTERACTING -> {
                ticksPassed++;
                if (mc.currentScreen instanceof MerchantScreen) {
                    state = State.CHECKING;
                    ticksPassed = 0;
                    return;
                }

                if (ticksPassed > 20 && ticksPassed % 15 == 0) {
                    ChatUtils.info("Attempting to open trades...");
                    Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager), () -> {
                        mc.interactionManager.interactEntity(mc.player, targetVillager, Hand.MAIN_HAND);
                        interactAttempts++;
                    });
                }

                if (interactAttempts > 10) {
                    state = State.BREAKING;
                }
            }
            case CHECKING -> {
                ticksPassed++;
                if (mc.player.currentScreenHandler instanceof MerchantScreenHandler handler) {
                    if (!handler.getRecipes().isEmpty()) {
                        if (checkTrades(handler)) {
                            ChatUtils.info("§aMATCH FOUND!");
                            state = State.IDLE;
                            toggle();
                        } else {
                            mc.player.closeHandledScreen();
                            state = State.BREAKING;
                        }
                    } else if (ticksPassed > 60) {
                        mc.player.closeHandledScreen();
                        state = State.INTERACTING;
                    }
                } else if (mc.currentScreen == null) {
                    state = State.INTERACTING;
                }
            }
        }
    }

    private boolean checkTrades(MerchantScreenHandler handler) {
        for (var offer : handler.getRecipes()) {
            ItemStack output = offer.getSellItem();
            if (output.isOf(Items.ENCHANTED_BOOK)) {
                ItemEnchantmentsComponent enchants = output.get(DataComponentTypes.STORED_ENCHANTMENTS);
                if (enchants != null) {
                    for (var entry : enchants.getEnchantmentEntries()) {
                        String name = entry.getKey().value().description().getString();
                        int level = entry.getIntValue();

                        for (RegistryKey<Enchantment> targetKey : targetEnchants.get()) {
                            if (entry.getKey().matchesKey(targetKey) && level >= minLevel.get()) {
                                ChatUtils.info("§aMATCH: " + name + " " + level);
                                return true;
                            }
                        }
                        ChatUtils.warning("§eFound: " + name + " " + level);
                    }
                }
            }
        }
        return false;
    }

    private int findLecternSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.LECTERN) return i;
        }
        return -1;
    }

    private enum State {
        IDLE, SELECTING_STATION, SELECTING_VILLAGER, BREAKING, PLACING, INTERACTING, CHECKING
    }
}
