package cn.pixelmc.shareabite;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.Set;

public final class ShareABiteConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_TARGET_HUNGER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_ALWAYS_EDIBLE_WHEN_FULL;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SNEAK_TO_FEED;
    public static final ForgeConfigSpec.DoubleValue HOLD_DURATION_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue TARGET_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FOOD_BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("feeding");

        MAX_TARGET_HUNGER =
                builder.comment(
                                "Players can only be fed when their hunger level is strictly below"
                                        + " this value.",
                                "Vanilla maximum hunger is 20. Set to 21 to allow feeding even at"
                                        + " full hunger.")
                        .defineInRange("maxTargetHunger", 20, 1, 21);

        ALLOW_ALWAYS_EDIBLE_WHEN_FULL =
                builder.comment(
                                "Allow feeding items marked as always-edible (e.g., Golden Apples,"
                                        + " Chorus Fruit, Honey Bottles)",
                                "even when the target player is at or above maxTargetHunger.")
                        .define("allowAlwaysEdibleWhenFull", true);

        REQUIRE_SNEAK_TO_FEED =
                builder.comment(
                                "Require the feeder to hold sneak while holding right-click to feed"
                                        + " another player.",
                                "Useful in modpacks with many right-click player interactions.")
                        .define("requireSneakToFeed", false);

        HOLD_DURATION_MULTIPLIER =
                builder.comment(
                                "Multiplier applied to the food item's normal use duration when"
                                        + " feeding another player.",
                                "1.0 matches vanilla eating time; lower is faster, higher is"
                                        + " slower.")
                        .defineInRange("holdDurationMultiplier", 1.0, 0.1, 5.0);

        TARGET_COOLDOWN_TICKS =
                builder.comment(
                                "Minimum ticks between completed feeds on the same recipient (20"
                                        + " ticks = 1 second).")
                        .defineInRange("targetCooldownTicks", 20, 0, 1200);

        FOOD_BLACKLIST =
                builder.comment(
                                "List of item registry IDs (e.g., \"minecraft:rotten_flesh\") that"
                                        + " cannot be fed to other players.",
                                "You can also add items via the datapack item tag"
                                        + " #shareabite:unfeedable.")
                        .defineListAllowEmpty(
                                "foodBlacklist",
                                List.of(),
                                value ->
                                        value instanceof String s
                                                && ResourceLocation.tryParse(s) != null);

        builder.pop();
        SPEC = builder.build();
    }

    public static int maxTargetHunger() {
        return SPEC.isLoaded() ? MAX_TARGET_HUNGER.get() : 20;
    }

    public static boolean allowAlwaysEdibleWhenFull() {
        return !SPEC.isLoaded() || ALLOW_ALWAYS_EDIBLE_WHEN_FULL.get();
    }

    public static boolean requireSneakToFeed() {
        return SPEC.isLoaded() && REQUIRE_SNEAK_TO_FEED.get();
    }

    public static double holdDurationMultiplier() {
        return SPEC.isLoaded() ? HOLD_DURATION_MULTIPLIER.get() : 1.0;
    }

    public static int targetCooldownTicks() {
        return SPEC.isLoaded() ? TARGET_COOLDOWN_TICKS.get() : 20;
    }

    public static boolean isBlacklistedId(ResourceLocation id) {
        if (id == null || !SPEC.isLoaded()) return false;
        return isBlacklisted(id.toString(), Set.copyOf(FOOD_BLACKLIST.get()));
    }

    static boolean isBlacklisted(String itemId, Set<String> blacklist) {
        return itemId != null && blacklist != null && blacklist.contains(itemId);
    }

    static boolean isHungerEligible(
            int targetFoodLevel,
            boolean alwaysEdible,
            int maxTargetHunger,
            boolean allowAlwaysEdibleWhenFull) {
        if (targetFoodLevel < maxTargetHunger) return true;
        return allowAlwaysEdibleWhenFull && alwaysEdible;
    }

    static int scaledHoldDuration(int baseUseDuration, double multiplier) {
        int base = Math.max(20, baseUseDuration);
        double clamped = Math.max(0.1, Math.min(5.0, multiplier));
        return Math.max(4, (int) Math.round(base * clamped));
    }

    private ShareABiteConfig() {}
}
