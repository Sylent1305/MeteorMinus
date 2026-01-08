package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradeOffer;

import java.util.List;

public class VillagerReroller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety & Alerts");

    private final Color GREEN = new Color(137, 243, 54);
    private final Color YELLOW = new Color(255, 222, 33);
    private final Color BLUE = new Color(143, 217, 251);
    private final Color ORANGE = new Color(255, 117, 24);

    private final Setting<List<String>> targetList = sgGeneral.add(new StringListSetting.Builder()
        .name("targets")
        .description("Format: name:min_level:max_price")
        .defaultValue(List.of("mending:1:20", "protection:4:32", "sharpness:5:24"))
        .build());

    private final Setting<Boolean> lockTrade = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-trade")
        .description("Automatically buys the book to lock the trade if a perfect match is found.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> playSound = sgSafety.add(new BoolSetting.Builder()
        .name("play-sound")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useSafetyPause = sgSafety.add(new BoolSetting.Builder()
        .name("safety-pause")
        .defaultValue(true)
        .build());

    private final Setting<Double> priceTolerance = sgSafety.add(new DoubleSetting.Builder()
        .name("tolerance-%")
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

    public VillagerReroller() {
        super(AddonTemplate.METEOR_MINUS, "villager-reroller", "Advanced trade reroller.");
    }

    @Override
    public void onActivate() {
        ChatUtils.info("Reroller active. Select station, then villager.");
        state = State.SELECTING_STATION;
        pausedForPrice = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pausedForPrice) {
            if (mc.options.attackKey.isPressed()) {
                pausedForPrice = false;
                mc.player.closeHandledScreen();
                state = State.BREAKING;
                ChatUtils.info("Continuing reroll...");
            }
            return;
        }

        if (state == State.IDLE || targetVillager == null || stationPos == null) return;

        switch (state) {
            case BREAKING -> handleBreakingState();
            case PLACING -> handlePlacingState();
            case INTERACTING -> handleInteractingState();
            case CHECKING -> handleCheckingState();
        }
    }

    private void handleBreakingState() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            return;
        }

        if (mc.world.getBlockState(stationPos).isAir()) {
            state = State.PLACING;
            ticksPassed = 0;
        } else {
            Rotations.rotate(Rotations.getYaw(stationPos), Rotations.getPitch(stationPos), () -> {
                mc.interactionManager.updateBlockBreakingProgress(stationPos, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);
            });
        }
    }

    private void handlePlacingState() {
        ticksPassed++;
        if (ticksPassed < 10) return;

        int slot = findLecternSlot();
        if (slot == -1) {
            ChatUtils.error("No Lecterns!");
            toggle();
            return;
        }

        if (BlockUtils.place(stationPos, Hand.MAIN_HAND, slot, true, 0, true, true, false)) {
            state = State.INTERACTING;
            ticksPassed = 0;
            interactAttempts = 0;
        }
    }

    private void handleInteractingState() {
        ticksPassed++;
        if (mc.currentScreen instanceof MerchantScreen) {
            state = State.CHECKING;
            ticksPassed = 0;
            return;
        }

        if (ticksPassed > 20 && ticksPassed % 15 == 0) {
            Rotations.rotate(Rotations.getYaw(targetVillager), Rotations.getPitch(targetVillager), () -> {
                mc.interactionManager.interactEntity(mc.player, targetVillager, Hand.MAIN_HAND);
                interactAttempts++;
            });
        }

        if (interactAttempts > 10) state = State.BREAKING;
    }

    private void handleCheckingState() {
        ticksPassed++;
        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler handler) {
            if (!handler.getRecipes().isEmpty()) {
                processTrades(handler);
            } else if (ticksPassed > 60) {
                mc.player.closeHandledScreen();
                state = State.INTERACTING;
            }
        }
    }

    private void processTrades(MerchantScreenHandler handler) {
        for (int i = 0; i < handler.getRecipes().size(); i++) {
            TradeOffer offer = handler.getRecipes().get(i);
            ItemStack book = offer.getSellItem();
            if (!book.isOf(Items.ENCHANTED_BOOK)) continue;

            ItemEnchantmentsComponent enchants = book.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (enchants == null) continue;

            for (var entry : enchants.getEnchantmentEntries()) {
                String foundName = entry.getKey().value().description().getString();
                int foundLevel = entry.getIntValue();
                int price = offer.getOriginalFirstBuyItem().getCount();

                for (String config : targetList.get()) {
                    if (evaluateTrade(config, foundName, foundLevel, price, i, handler)) return;
                }

                logSkippedTrade(foundName, foundLevel, price);
            }
        }

        mc.player.closeHandledScreen();
        state = State.BREAKING;
    }

    private boolean evaluateTrade(String config, String name, int level, int price, int index, MerchantScreenHandler handler) {
        try {
            String[] parts = config.split(":");
            String targetName = parts[0].trim().toLowerCase();
            int minLevel = Integer.parseInt(parts[1]);
            int maxPrice = Integer.parseInt(parts[2]);

            if (!isTargetMatch(name, targetName) || level < minLevel) return false;

            if (price <= maxPrice) {
                notifyPerfectMatch(name, level, price);
                if (lockTrade.get()) performLockBuy(index, handler);
                state = State.IDLE;
                toggle();
                return true;
            } else if (useSafetyPause.get()) {
                double limit = maxPrice * (1 + (priceTolerance.get() / 100.0));
                if (price <= limit) {
                    notifyNearMatch(name, level, price);
                    pausedForPrice = true;
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void performLockBuy(int tradeIndex, MerchantScreenHandler handler) {
        TradeOffer offer = handler.getRecipes().get(tradeIndex);
        int emeraldsNeeded = offer.getOriginalFirstBuyItem().getCount();

        if (getInventoryCount(Items.EMERALD) >= emeraldsNeeded && getInventoryCount(Items.BOOK) >= 1) {
            mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(tradeIndex));
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
            ChatUtils.info("Trade locked!");
        } else {
            ChatUtils.error("Could not lock trade: Missing emeralds or books.");
        }
    }

    private int getInventoryCount(net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private boolean isTargetMatch(String foundName, String targetName) {
        String lowerFound = foundName.toLowerCase();
        if (lowerFound.equals(targetName)) return true;
        for (String word : lowerFound.split(" ")) {
            if (word.equals(targetName)) return true;
        }
        return false;
    }

    private void notifyPerfectMatch(String name, int level, int price) {
        playMatchSound();
        mc.player.sendMessage(Text.literal("PERFECT MATCH: ").setStyle(Style.EMPTY.withColor(GREEN.getPacked()))
            .append(Text.literal(name + " " + level).setStyle(Style.EMPTY.withColor(YELLOW.getPacked())))
            .append(Text.literal(" (").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
            .append(Text.literal(price + " emeralds").setStyle(Style.EMPTY.withColor(GREEN.getPacked())))
            .append(Text.literal(")").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))), false);
    }

    private void notifyNearMatch(String name, int level, int price) {
        playMatchSound();
        mc.player.sendMessage(Text.literal("NEAR MATCH: ").setStyle(Style.EMPTY.withColor(ORANGE.getPacked()))
            .append(Text.literal(name + " " + level).setStyle(Style.EMPTY.withColor(YELLOW.getPacked())))
            .append(Text.literal(" at ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
            .append(Text.literal(price + " emeralds").setStyle(Style.EMPTY.withColor(ORANGE.getPacked()))), false);

        mc.player.sendMessage(Text.literal("Press ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))
            .append(Text.literal("LEFT CLICK").setStyle(Style.EMPTY.withColor(BLUE.getPacked())))
            .append(Text.literal(" to continue or ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
            .append(Text.literal("DISABLE MODULE").setStyle(Style.EMPTY.withColor(ORANGE.getPacked())))
            .append(Text.literal(" to stop.").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))), false);
    }

    private void logSkippedTrade(String name, int level, int price) {
        mc.player.sendMessage(Text.literal("Skipping: ").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))
            .append(Text.literal(name + " " + level + " ").setStyle(Style.EMPTY.withColor(YELLOW.getPacked())))
            .append(Text.literal("(").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY)))
            .append(Text.literal(price + " emeralds").setStyle(Style.EMPTY.withColor(GREEN.getPacked())))
            .append(Text.literal(")").setStyle(Style.EMPTY.withFormatting(Formatting.GRAY))), false);
    }

    private void playMatchSound() {
        if (playSound.get() && mc.player != null) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, mc.player.getSoundCategory(), 1f, 1f);
        }
    }

    private int findLecternSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.LECTERN)) return i;
        }
        return -1;
    }

    @EventHandler
    private void onInteractBlockSelect(InteractBlockEvent event) {
        if (state == State.SELECTING_STATION) {
            stationPos = event.result.getBlockPos();
            state = State.SELECTING_VILLAGER;
            event.cancel();
        }
    }

    @EventHandler
    private void onInteractEntitySelect(InteractEntityEvent event) {
        if (state == State.SELECTING_VILLAGER && event.entity instanceof VillagerEntity villager) {
            targetVillager = villager;
            state = State.BREAKING;
            event.cancel();
        }
    }

    private enum State {
        IDLE, SELECTING_STATION, SELECTING_VILLAGER, BREAKING, PLACING, INTERACTING, CHECKING
    }
}
