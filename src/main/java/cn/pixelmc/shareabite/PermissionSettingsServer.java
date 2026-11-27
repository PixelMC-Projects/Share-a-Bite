package cn.pixelmc.shareabite;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PermissionSettingsServer {
    static final int PAGE_SIZE = 64;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void started(ServerStartedEvent event) {
        PlayerHistory.bootstrap(event.getServer());
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerHistory.observe(player);
    }

    static ShareNetwork.SettingsPage page(
            ServerPlayer recipient, int requestId, int page, String query) {
        FeedingPermissions data = FeedingPermissions.get(recipient.server);
        String search = query.strip().toLowerCase(Locale.ROOT);
        List<ShareNetwork.PlayerEntry> entries = new ArrayList<>();
        data.players()
                .forEach(
                        (id, name) -> {
                            if (id.equals(recipient.getUUID())
                                    || !name.toLowerCase(Locale.ROOT).contains(search)) return;
                            ServerPlayer online = recipient.server.getPlayerList().getPlayer(id);
                            entries.add(
                                    new ShareNetwork.PlayerEntry(
                                            id,
                                            name,
                                            online != null && !PlayerHistory.isFake(online),
                                            data.allows(recipient.getUUID(), id)));
                        });
        entries.sort(
                Comparator.comparing(ShareNetwork.PlayerEntry::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.id().toString()));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int selectedPage = Math.max(0, Math.min(page, pages - 1));
        int begin = selectedPage * PAGE_SIZE;
        return new ShareNetwork.SettingsPage(
                requestId,
                selectedPage,
                pages,
                entries.size(),
                query,
                List.copyOf(entries.subList(begin, Math.min(begin + PAGE_SIZE, entries.size()))));
    }

    static void request(ServerPlayer sender, ShareNetwork.SettingsRequest request) {
        if (PlayerHistory.isFake(sender)) return;
        ShareNetwork.settings(
                sender, page(sender, request.requestId(), request.page(), request.query()));
    }

    static ShareNetwork.PermissionAck change(
            ServerPlayer recipient, ShareNetwork.PermissionChange change) {
        FeedingPermissions data = FeedingPermissions.get(recipient.server);
        boolean accepted =
                !PlayerHistory.isFake(recipient)
                        && data.setAllowed(recipient.getUUID(), change.feeder(), change.allowed());
        if (accepted) {
            data.save(
                    recipient
                            .server
                            .getWorldPath(LevelResource.ROOT)
                            .resolve("data")
                            .resolve(FeedingPermissions.DATA_NAME + ".dat")
                            .toFile());
            FeedingServer.permissionsChanged(recipient);
        }
        return new ShareNetwork.PermissionAck(
                change.requestId(),
                change.feeder(),
                data.allows(recipient.getUUID(), change.feeder()),
                accepted);
    }
}
