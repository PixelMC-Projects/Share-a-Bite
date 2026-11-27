package cn.pixelmc.shareabite;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class FeedingServer {
    public static final TagKey<Item> UNFEEDABLE_TAG =
            ItemTags.create(new ResourceLocation(ShareABite.ID, "unfeedable"));
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> TARGET_COOLDOWN = new HashMap<>();
    private static long clock;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent.EntityInteract event) {
        if (shouldClaimInteract(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (shouldClaimInteract(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean shouldClaimInteract(Player feeder, Entity target) {
        if (!(target instanceof Player) || target == feeder) return false;
        if (ShareABiteConfig.requireSneakToFeed() && !feeder.isShiftKeyDown()) return false;
        return isFeedableFood(feeder.getMainHandItem());
    }

    static void input(ServerPlayer feeder, int action, int sequence, int targetId) {
        if (action == ShareNetwork.STOP) {
            Session session = SESSIONS.get(feeder.getUUID());
            if (session != null && session.sequence == sequence) cancel(feeder, session);
            return;
        }
        if (action == ShareNetwork.START) {
            Entity entity = feeder.level().getEntity(targetId);
            if (entity instanceof ServerPlayer target && !permitted(feeder, target)) {
                SESSIONS.remove(feeder.getUUID());
                ShareNetwork.feedback(feeder, sequence, ShareNetwork.DENIED, 0, 0);
                return;
            }
            if (!(entity instanceof ServerPlayer target) || !canFeed(feeder, target)) {
                ShareNetwork.feedback(feeder, sequence, ShareNetwork.CANCEL, 0, 0);
                return;
            }
            Session session = new Session(feeder, target, sequence, clock);
            SESSIONS.put(feeder.getUUID(), session);
            notifyIncoming(feeder, target, session.food);
            playEatingFeedback(target, session.food, false);
            ShareNetwork.feedback(
                    feeder, sequence, ShareNetwork.PROGRESS, 0, session.timer.duration);
            return;
        }
        if (action != ShareNetwork.PULSE) return;
        Session session = SESSIONS.get(feeder.getUUID());
        if (session == null || session.sequence != sequence || session.target.getId() != targetId)
            return;
        if (session.timer.expired(clock) || !valid(feeder, session)) {
            cancel(feeder, session);
            return;
        }
        if (session.timer.pulse(clock)) {
            SESSIONS.remove(feeder.getUUID());
            long cooldown = ShareABiteConfig.targetCooldownTicks();
            if (clock >= TARGET_COOLDOWN.getOrDefault(session.target.getUUID(), 0L)) {
                if (finishFeed(feeder, session.target)) {
                    if (cooldown > 0) {
                        TARGET_COOLDOWN.put(session.target.getUUID(), clock + cooldown);
                    }
                    ShareNetwork.feedback(
                            feeder,
                            sequence,
                            ShareNetwork.COMPLETE,
                            session.timer.duration,
                            session.timer.duration);
                } else {
                    clearIncomingNotice(session.target);
                    ShareNetwork.feedback(feeder, sequence, ShareNetwork.DENIED, 0, 0);
                }
            } else {
                clearIncomingNotice(session.target);
                ShareNetwork.feedback(feeder, sequence, ShareNetwork.CANCEL, 0, 0);
            }
        } else {
            int elapsed = session.timer.elapsed(clock);
            if (elapsed % 4 == 0) {
                ShareNetwork.feedback(
                        feeder, sequence, ShareNetwork.PROGRESS, elapsed, session.timer.duration);
            }
            if (elapsed > 0 && elapsed % 5 == 0) {
                playEatingFeedback(session.target, session.food, false);
            }
            if (elapsed > 0 && elapsed % 8 == 0) {
                notifyIncoming(feeder, session.target, session.food);
            }
        }
    }

    static boolean isFeedableFood(ItemStack food) {
        if (food.isEmpty() || !food.isEdible()) return false;
        if (food.is(UNFEEDABLE_TAG)) return false;
        return !ShareABiteConfig.isBlacklistedId(ForgeRegistries.ITEMS.getKey(food.getItem()));
    }

    static boolean canFeed(ServerPlayer feeder, ServerPlayer target) {
        ItemStack food = feeder.getMainHandItem();
        if (feeder == target
                || !feeder.isAlive()
                || !target.isAlive()
                || feeder.isSpectator()
                || target.isSpectator()
                || feeder.isUsingItem()
                || feeder.level() != target.level()
                || !isFeedableFood(food)
                || !permitted(feeder, target)) {
            return false;
        }
        if (ShareABiteConfig.requireSneakToFeed() && !feeder.isShiftKeyDown()) return false;

        FoodProperties properties = food.getFoodProperties(target);
        boolean alwaysEdible = properties != null && properties.canAlwaysEat();
        if (!ShareABiteConfig.isHungerEligible(
                target.getFoodData().getFoodLevel(),
                alwaysEdible,
                ShareABiteConfig.maxTargetHunger(),
                ShareABiteConfig.allowAlwaysEdibleWhenFull())) {
            return false;
        }
        if (!feeder.canReach(target, 0) || !feeder.hasLineOfSight(target)) return false;
        Vec3 eye = feeder.getEyePosition();
        Vec3 end = eye.add(feeder.getLookAngle().scale(feeder.getEntityReach()));
        return target.getBoundingBox().inflate(0.1).contains(eye)
                || target.getBoundingBox().inflate(0.1).clip(eye, end).isPresent();
    }

    private static boolean valid(ServerPlayer feeder, Session session) {
        return feeder.getInventory().selected == session.slot
                && ItemStack.matches(session.food, feeder.getMainHandItem())
                && canFeed(feeder, session.target);
    }

    static boolean permitted(ServerPlayer feeder, ServerPlayer target) {
        return FeedingPermissions.get(target.server).allows(target.getUUID(), feeder.getUUID());
    }

    static boolean finishFeed(ServerPlayer feeder, ServerPlayer target) {
        if (!permitted(feeder, target)) return false;
        ItemStack held = feeder.getMainHandItem();
        ItemStack before = held.copy();
        // Copy for creative feeders so finishUsingItem cannot shrink their hand stack.
        ItemStack consumed =
                (feeder.getAbilities().instabuild ? held.copy() : held)
                        .finishUsingItem(target.level(), target);
        // Fire the standard Forge item-use-finish event for the RECIPIENT so mods such as
        // Spice of Life: Carrot Edition, Farmer's Delight, and Diet automatically credit progress.
        ItemStack result = ForgeEventFactory.onItemUseFinish(target, before, 0, consumed);
        if (!feeder.getAbilities().instabuild) {
            feeder.setItemInHand(InteractionHand.MAIN_HAND, result);
        }
        playEatingFeedback(target, before, true);
        target.displayClientMessage(
                Component.translatable(
                        "message.shareabite.received",
                        feeder.getDisplayName(),
                        before.getHoverName()),
                true);
        feeder.getInventory().setChanged();
        feeder.containerMenu.broadcastChanges();
        return true;
    }

    private static void notifyIncoming(ServerPlayer feeder, ServerPlayer target, ItemStack food) {
        if (target.connection != null) {
            target.displayClientMessage(
                    Component.translatable(
                            "message.shareabite.incoming",
                            feeder.getDisplayName(),
                            food.getHoverName()),
                    true);
        }
    }

    private static void clearIncomingNotice(ServerPlayer target) {
        if (target.connection != null && target.isAlive()) {
            target.displayClientMessage(Component.empty(), true);
        }
    }

    private static void playEatingFeedback(ServerPlayer target, ItemStack food, boolean finished) {
        if (!(target.level() instanceof ServerLevel level)) return;
        boolean drink = food.getUseAnimation() == UseAnim.DRINK;
        SoundEvent sound = drink ? food.getDrinkingSound() : food.getEatingSound();
        float pitch = 0.9f + level.random.nextFloat() * 0.2f;
        level.playSound(
                null,
                target.getX(),
                target.getEyeY(),
                target.getZ(),
                sound,
                SoundSource.PLAYERS,
                finished ? 0.65f : 0.4f,
                pitch);
        if (finished && !drink) {
            level.playSound(
                    null,
                    target.getX(),
                    target.getEyeY(),
                    target.getZ(),
                    SoundEvents.PLAYER_BURP,
                    SoundSource.PLAYERS,
                    0.45f,
                    0.95f + level.random.nextFloat() * 0.1f);
        }
        if (!food.isEmpty()) {
            Vec3 mouth =
                    target.getEyePosition()
                            .add(target.getLookAngle().scale(0.32))
                            .add(0.0, -0.14, 0.0);
            level.sendParticles(
                    new ItemParticleOption(ParticleTypes.ITEM, food),
                    mouth.x,
                    mouth.y,
                    mouth.z,
                    finished ? 10 : 4,
                    0.12,
                    0.08,
                    0.12,
                    0.05);
        }
    }

    static void permissionsChanged(ServerPlayer recipient) {
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session session = it.next();
            if (session.target.getUUID().equals(recipient.getUUID())
                    && !permitted(session.feeder, recipient)) {
                it.remove();
                clearIncomingNotice(recipient);
                ShareNetwork.feedback(session.feeder, session.sequence, ShareNetwork.DENIED, 0, 0);
            }
        }
    }

    private static void cancel(ServerPlayer feeder, Session session) {
        SESSIONS.remove(feeder.getUUID());
        clearIncomingNotice(session.target);
        ShareNetwork.feedback(
                feeder,
                session.sequence,
                permitted(feeder, session.target) ? ShareNetwork.CANCEL : ShareNetwork.DENIED,
                0,
                0);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ++clock;
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session session = it.next();
            if (session.timer.expired(clock) || !valid(session.feeder, session)) {
                it.remove();
                clearIncomingNotice(session.target);
                ShareNetwork.feedback(
                        session.feeder,
                        session.sequence,
                        permitted(session.feeder, session.target)
                                ? ShareNetwork.CANCEL
                                : ShareNetwork.DENIED,
                        0,
                        0);
            }
        }
        TARGET_COOLDOWN.entrySet().removeIf(entry -> entry.getValue() <= clock);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Session outgoing = SESSIONS.remove(id);
        if (outgoing != null) clearIncomingNotice(outgoing.target);
        SESSIONS.values().removeIf(session -> session.target.getUUID().equals(id));
        TARGET_COOLDOWN.remove(id);
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        TARGET_COOLDOWN.clear();
        clock = 0;
    }

    private static final class Session {
        final ServerPlayer feeder, target;
        final int sequence, slot;
        final ItemStack food;
        final HoldTimer timer;

        Session(ServerPlayer feeder, ServerPlayer target, int sequence, long now) {
            this.feeder = feeder;
            this.target = target;
            this.sequence = sequence;
            this.slot = feeder.getInventory().selected;
            this.food = feeder.getMainHandItem().copy();
            int scaled =
                    ShareABiteConfig.scaledHoldDuration(
                            this.food.getUseDuration(), ShareABiteConfig.holdDurationMultiplier());
            this.timer = new HoldTimer(now, scaled);
        }
    }
}
