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
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradeOffer;

import java.util.List;

public class VillagerReroller extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety & Alerts");

    private final Setting<List<String>> targetList = sgGeneral.add(new StringListSetting.Builder()
        .name("targets")
        .description("Format: name:min_level:max_price (e.g. mending:1:12)")
        .defaultValue(List.of("mending:1:20", "protection:4:32", "sharpness:5:24"))
        .build());

    private final Setting<Boolean> playSound = sgSafety.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Plays a dinging noise when a match is found.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useSafetyPause = sgSafety.add(new BoolSetting.Builder()
        .name("safety-pause")
        .description("Pauses if the price is within a certain percentage of your max.")
        .defaultValue(true)
        .build());

    private final Setting<Double> priceTolerance = sgSafety.add(new DoubleSetting.Builder()
        .name("tolerance-%")
        .description("Percentage above max price to trigger a pause (e.g. 10% of 20 = 22).")
        .defaultValue(10.0)
        .min(0)
        .sliderMax(100)
        .visible(useSafetyPause::get)
        .build());

    private BlockPos stationPos;
    private VillagerEntity targetVillager;
    private State state = State.IDLE;
    private int ticksPassed = 0;
    private int interactAttempts = 0;
    private boolean pausedForPrice = false;

    public VillagerReroller()
    {
        super(AddonTemplate.METEOR_MINUS, "villager-reroller", "Advanced trade reroller with price detection.");
    }

    @Override
    public void onActivate()
    {
        ChatUtils.info("Reroller active. Select station, then villager.");

        state = State.SELECTING_STATION;
        pausedForPrice = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        if (pausedForPrice)
        {
            if (mc.options.attackKey.isPressed())
            {
                pausedForPrice = false;
                mc.player.closeHandledScreen();
                state = State.BREAKING;
                ChatUtils.info("Continuing reroll...");
            }

            return;
        }

        if (state == State.IDLE || targetVillager == null || stationPos == null)
        {
            return;
        }

        switch (state)
        {
            case BREAKING ->
            {
                if (mc.currentScreen != null)
                {
                    mc.player.closeHandledScreen();
                    return;
                }

                if (mc.world.getBlockState(stationPos).isAir())
                {
                    state = State.PLACING;
                    ticksPassed = 0;
                }
                else
                {
                    Rotations.rotate(Rotations.getYaw(stationPos), Rotations.getPitch(stationPos), () ->
                    {
                        mc.interactionManager.updateBlockBreakingProgress(stationPos, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    });
                }
            }

            case PLACING ->
            {
                ticksPassed++;

                if (ticksPassed < 10)
                {
                    return;
                }

                int slot = findLecternSlot();

                if (slot == -1)
                {
                    ChatUtils.error("No Lecterns!");
                    toggle();
                    return;
                }

                if (BlockUtils.place(stationPos, Hand.MAIN_HAND, slot, true, 0, true, true, false))
                {
                    state = State.INTERACTING;
                    ticksPassed = 0;
                    interactAttempts = 0;
                }
            }

            case INTERACTING ->
            {
                ticksPassed++;

                if (mc.currentScreen instanceof MerchantScreen)
                {
                    state = State.CHECKING;
                    ticksPassed = 0;
                    return;
                }

                if (ticksPassed > 20 && ticksPassed % 15 == 0)
                {
                    Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager), () ->
                    {
                        mc.interactionManager.interactEntity(mc.player, targetVillager, Hand.MAIN_HAND);
                        interactAttempts++;
                    });
                }

                if (interactAttempts > 10)
                {
                    state = State.BREAKING;
                }
            }

            case CHECKING ->
            {
                ticksPassed++;

                if (mc.player.currentScreenHandler instanceof MerchantScreenHandler handler)
                {
                    if (!handler.getRecipes().isEmpty())
                    {
                        processTrades(handler);
                    }
                    else if (ticksPassed > 60)
                    {
                        mc.player.closeHandledScreen();
                        state = State.INTERACTING;
                    }
                }
            }
        }
    }

    private void processTrades(MerchantScreenHandler handler)
    {
        for (TradeOffer offer : handler.getRecipes())
        {
            ItemStack book = offer.getSellItem();

            if (!book.isOf(Items.ENCHANTED_BOOK))
            {
                continue;
            }

            ItemEnchantmentsComponent enchants = book.get(DataComponentTypes.STORED_ENCHANTMENTS);

            if (enchants == null)
            {
                continue;
            }

            for (var entry : enchants.getEnchantmentEntries())
            {
                String foundName = entry.getKey().value().description().getString().toLowerCase();
                int foundLevel = entry.getIntValue();
                int price = offer.getOriginalFirstBuyItem().getCount();

                for (String config : targetList.get())
                {
                    try
                    {
                        String[] parts = config.split(":");
                        String targetName = parts[0].toLowerCase();
                        int minLevel = Integer.parseInt(parts[1]);
                        int maxPrice = Integer.parseInt(parts[2]);

                        if (foundName.contains(targetName) && foundLevel >= minLevel)
                        {
                            if (price <= maxPrice)
                            {
                                playMatchSound();
                                ChatUtils.info("§aPERFECT MATCH: " + foundName + " " + foundLevel + " for " + price + " emeralds!");

                                state = State.IDLE;
                                toggle();
                                return;
                            }
                            else if (useSafetyPause.get())
                            {
                                double limit = maxPrice * (1 + (priceTolerance.get() / 100.0));

                                if (price <= limit)
                                {
                                    playMatchSound();
                                    ChatUtils.warning("§6NEAR MATCH: " + foundName + " " + foundLevel + " at " + price + " (Max: " + maxPrice + ")");
                                    ChatUtils.info("§bLEFT CLICK to continue rerolling, or disable module to keep it.");

                                    pausedForPrice = true;
                                    return;
                                }
                            }
                        }
                    }
                    catch (Exception ignored) {}
                }

                ChatUtils.warning("§eSkipping: " + foundName + " " + foundLevel + " (" + price + " emeralds)");
            }
        }

        mc.player.closeHandledScreen();
        state = State.BREAKING;
    }

    private void playMatchSound()
    {
        if (playSound.get() && mc.player != null && mc.world != null)
        {
            mc.world.playSound(mc.player, mc.player.getBlockPos(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, mc.player.getSoundCategory(), 1f, 1f);
        }
    }

    private int findLecternSlot()
    {
        for (int i = 0; i < 9; i++)
        {
            if (mc.player.getInventory().getStack(i).getItem() == Items.LECTERN)
            {
                return i;
            }
        }

        return -1;
    }

    @EventHandler
    private void onInteractBlockSelect(InteractBlockEvent event)
    {
        if (state == State.SELECTING_STATION)
        {
            stationPos = event.result.getBlockPos();
            state = State.SELECTING_VILLAGER;
            event.cancel();
        }
    }

    @EventHandler
    private void onInteractEntitySelect(InteractEntityEvent event)
    {
        if (state == State.SELECTING_VILLAGER && event.entity instanceof VillagerEntity villager)
        {
            targetVillager = villager;
            state = State.BREAKING;
            event.cancel();
        }
    }

    private enum State
    {
        IDLE,
        SELECTING_STATION,
        SELECTING_VILLAGER,
        BREAKING,
        PLACING,
        INTERACTING,
        CHECKING
    }
}
