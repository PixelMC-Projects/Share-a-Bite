package cn.pixelmc.shareabite;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(ShareABite.ID)
public final class ShareABite {
    public static final String ID = "shareabite";

    public ShareABite() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ShareABiteConfig.SPEC);
        ShareNetwork.register();
        MinecraftForge.EVENT_BUS.register(new FeedingServer());
        MinecraftForge.EVENT_BUS.register(new PermissionSettingsServer());
    }
}
