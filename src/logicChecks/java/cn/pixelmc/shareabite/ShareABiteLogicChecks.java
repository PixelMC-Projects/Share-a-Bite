package cn.pixelmc.shareabite;

import java.util.Set;
import java.util.UUID;

public final class ShareABiteLogicChecks {
    public static void main(String[] args) {
        testHoldTimer();
        testConfigRules();
        testPermissionsModel();
        System.out.println(
                "PASS: ShareABiteLogicChecks (HoldTimer, Config hunger/blacklist/multiplier,"
                    + " Permissions)");
    }

    private static void testHoldTimer() {
        HoldTimer timer = new HoldTimer(100L, 32);
        check(!timer.expired(100L), "fresh timer must not be expired");
        check(!timer.pulse(100L), "spam pulse on same tick cannot complete");
        check(!timer.pulse(110L), "pulse at +10 ticks within silence window");
        check(timer.elapsed(110L) == 10, "elapsed ticks accurate");
        check(!timer.pulse(120L), "pulse at +20 ticks not yet finished");
        check(!timer.pulse(130L), "pulse at +30 ticks not yet finished");
        check(timer.pulse(132L), "pulse at +32 ticks completes hold");

        HoldTimer silent = new HoldTimer(200L, 20);
        check(silent.expired(211L), "silence > 10 ticks expires hold");
        check(!silent.pulse(211L), "expired hold cannot complete");

        HoldTimer shortTimer = new HoldTimer(0L, 1);
        check(shortTimer.duration == 4, "minimum hold duration clamped to 4 ticks");
    }

    private static void testConfigRules() {
        check(
                ShareABiteConfig.isHungerEligible(19, false, 20, true),
                "19 < 20 eligible for normal food");
        check(
                !ShareABiteConfig.isHungerEligible(20, false, 20, true),
                "full hunger blocked when maxTargetHunger=20 and not alwaysEdible");
        check(
                ShareABiteConfig.isHungerEligible(20, true, 20, true),
                "alwaysEdible allowed at full hunger when allowAlwaysEdibleWhenFull=true");
        check(
                !ShareABiteConfig.isHungerEligible(20, true, 20, false),
                "alwaysEdible blocked at full hunger when allowAlwaysEdibleWhenFull=false");
        check(
                ShareABiteConfig.isHungerEligible(20, false, 21, false),
                "maxTargetHunger=21 allows feeding even at full hunger");

        Set<String> blacklist = Set.of("minecraft:rotten_flesh", "minecraft:pufferfish");
        check(
                ShareABiteConfig.isBlacklisted("minecraft:rotten_flesh", blacklist),
                "blacklisted item rejected");
        check(
                !ShareABiteConfig.isBlacklisted("minecraft:cooked_beef", blacklist),
                "non-blacklisted item allowed");

        check(
                ShareABiteConfig.scaledHoldDuration(32, 1.0) == 32,
                "1.0x multiplier keeps vanilla 32 ticks");
        check(
                ShareABiteConfig.scaledHoldDuration(32, 0.5) == 16,
                "0.5x multiplier halves 32 to 16 ticks");
        check(
                ShareABiteConfig.scaledHoldDuration(16, 1.0) == 20,
                "fast food base clamped to at least 20 ticks before scaling");
        check(
                ShareABiteConfig.scaledHoldDuration(20, 0.1) == 4,
                "very low multiplier safely clamped to at least 4 ticks");
    }

    private static void testPermissionsModel() {
        FeedingPermissions perms = new FeedingPermissions();
        UUID recipient = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        perms.remember(recipient, "Alice", true);
        perms.remember(friend, "Bob", true);

        check(perms.allows(recipient, friend), "default allows known players");
        check(!perms.setAllowed(recipient, recipient, false), "cannot deny self");
        check(!perms.setAllowed(recipient, stranger, false), "cannot toggle unknown player");
        check(perms.setAllowed(recipient, friend, false), "deny known player succeeds");
        check(!perms.allows(recipient, friend), "denied player blocked");
        check(perms.setAllowed(recipient, friend, true), "re-allow succeeds");
        check(perms.allows(recipient, friend), "re-allowed player permitted");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("Check failed: " + msg);
    }
}
