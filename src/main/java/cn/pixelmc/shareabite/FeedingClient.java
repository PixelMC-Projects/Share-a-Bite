package cn.pixelmc.shareabite;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ShareABite.ID, value = Dist.CLIENT)
public final class FeedingClient {
    private static int serial;
    private static Active active;
    private static int deniedTarget = -1;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (FeedingKeys.OPEN.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive()) {
                stop(mc);
                mc.setScreen(new PermissionSettingsScreen());
            }
        }
        Player target = target(mc);
        if (mc.player == null
                || mc.level == null
                || mc.screen != null
                || !mc.isWindowActive()
                || !mc.options.keyUse.isDown()
                || target == null
                || mc.player.isUsingItem()) {
            stop(mc);
            deniedTarget = -1;
            return;
        }
        if (deniedTarget == target.getId()) return;
        deniedTarget = -1;
        if (active != null
                && (active.target != target.getId()
                        || active.slot != mc.player.getInventory().selected
                        || !ItemStack.matches(active.food, mc.player.getMainHandItem()))) {
            stop(mc);
        }
        if (active == null) {
            active =
                    new Active(
                            ++serial,
                            target.getId(),
                            target.getDisplayName(),
                            mc.player.getInventory().selected,
                            mc.player.getMainHandItem().copy());
            ShareNetwork.sendInput(ShareNetwork.START, active.sequence, active.target);
        } else {
            ShareNetwork.sendInput(ShareNetwork.PULSE, active.sequence, active.target);
        }
    }

    // Prevent vanilla from falling back to eating the held food while aiming at a player to feed.
    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem() && target(mc) != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static Player target(Minecraft mc) {
        if (mc.player == null || mc.player.isSpectator()) return null;
        if (ShareABiteConfig.requireSneakToFeed() && !mc.player.isShiftKeyDown()) return null;
        if (!FeedingServer.isFeedableFood(mc.player.getMainHandItem())) return null;
        return mc.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() instanceof Player player
                        && player != mc.player
                ? player
                : null;
    }

    private static void stop(Minecraft mc) {
        if (active != null) {
            if (mc.getConnection() != null) {
                ShareNetwork.sendInput(ShareNetwork.STOP, active.sequence, active.target);
            }
            active = null;
            if (mc.player != null) mc.player.displayClientMessage(Component.empty(), true);
        }
    }

    static void feedback(ShareNetwork.Feedback packet) {
        if (active == null || packet.sequence() != active.sequence) return;
        Minecraft mc = Minecraft.getInstance();
        if (packet.status() == ShareNetwork.PROGRESS) {
            if (mc.player != null && packet.duration() > 0) {
                mc.player.displayClientMessage(
                        Component.translatable(
                                "message.shareabite.progress",
                                Math.min(100, packet.ticks() * 100 / packet.duration())),
                        true);
            }
        } else {
            Component targetName = active.targetName;
            if (packet.status() == ShareNetwork.DENIED) deniedTarget = active.target;
            active = null;
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        packet.status() == ShareNetwork.COMPLETE
                                ? Component.translatable("message.shareabite.complete", targetName)
                                : packet.status() == ShareNetwork.DENIED
                                        ? Component.translatable("message.shareabite.denied")
                                        : Component.empty(),
                        true);
            }
        }
    }

    static void settings(ShareNetwork.SettingsPage page) {
        if (Minecraft.getInstance().screen instanceof PermissionSettingsScreen screen) {
            screen.receive(page);
        }
    }

    static void permission(ShareNetwork.PermissionAck ack) {
        if (Minecraft.getInstance().screen instanceof PermissionSettingsScreen screen) {
            screen.receive(ack);
        }
    }

    private record Active(
            int sequence, int target, Component targetName, int slot, ItemStack food) {}

    private FeedingClient() {}
}
