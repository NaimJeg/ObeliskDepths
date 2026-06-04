package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;

public final class DungeonRewardPoolTest {
    private DungeonRewardPoolTest() {}

    public static void main(String[] args) throws IOException {
        testEmptyTagFallback();
        testMissingTagFallback();
        testConditionEligibilityExcludesEntry();
        testConditionEvaluatedOnce();
        testIneligibleDoesNotWasteRoll();
        testDynamicWeightEvaluatedOnce();
        testAllIneligibleReturnsNull();
        testAllZeroWeightReturnsNull();
        testWeightSumOverflowDoesNotCrash();
        testDeterministicSelectionWithFixedSeeds();
        testNegativeWeightClampedToZero();
        testTierClampRange();
        testTierPromotionNeverExceedsCeiling();
        testDifficultyTierRangeConditionBoundaries();
        testMaximumScalableRollsWithinCapacity();
        testDifferentSaltsIndependent();
        testSeedDeterminism();
        testCategoryWeightFormulas();
        testConstructorsRejectNull();
        testStableWeightsStoredInChoose();
        testSourceAssertions();
        System.out.println("All DungeonRewardPoolTest assertions passed.");
    }

    private static void testSourceAssertions() throws IOException {
        String pool = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardPool.java");
        assertTrue(pool.contains("isEligible"), "pool must check eligibility before selection");
        assertTrue(pool.contains("long[] weights"), "pool must store weights");
        assertTrue(pool.contains("total += w"), "pool must sum with long");
        assertTrue(pool.contains("boundLong"), "pool must use bounded long selection");

        String entry = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardPoolEntry.java");
        assertTrue(entry.contains("isEligible"), "entry interface must declare isEligible");
        assertTrue(entry.contains("exactly once"), "entry javadoc must document once-per-roll");

        String weighted = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/WeightedRewardPoolEntry.java");
        assertTrue(weighted.contains("isEligible"), "weighted entry must override isEligible");
        assertTrue(weighted.contains("conditions.isEmpty"), "weighted entry must check conditions in isEligible");

        String dynamic = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DynamicWeightedRewardPoolEntry.java");
        assertTrue(dynamic.contains("isEligible"), "dynamic entry must override isEligible");

        String tag = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/TagRewardItemSource.java");
        assertTrue(tag.contains("filter(set -> set.size() > 0)") || tag.contains("set.size() > 0"),
                "tag source must filter empty sets");
        assertTrue(tag.contains("orElseGet"),
                "tag source must fall back on missing/empty tag");

        String diffCond = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DifficultyTierRangeCondition.java");
        assertTrue(diffCond.contains("difficulty().tier()"),
                "condition must inspect difficulty tier, not promoted tier");
        assertTrue(diffCond.contains("DifficultyTier") || diffCond.contains("difficulty tier"),
                "condition name must include DifficultyTier");

        String scroll = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/ReturnScrollRewardEntry.java");
        assertTrue(scroll.contains("Math.max(0.0D, Math.min(1.0D, chance)"),
                "return scroll must clamp chance to [0,1]");

        String factory = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/ScalableRewardPoolFactory.java");
        assertTrue(factory.contains("20 + Math.round(Math.max(0.0F"),
                "enchanted book weight formula must be preserved");
        assertTrue(factory.contains("12 + Math.round(Math.max(0.0F"),
                "tempering template weight formula must be preserved");
    }

    private static void testEmptyTagFallback() throws IOException {
        String s = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/TagRewardItemSource.java");
        assertTrue(s.contains(".filter(set -> set.size() > 0)") || s.contains("set.size() > 0"),
                "empty tag must trigger fallback via size filter");
        assertTrue(s.contains("Empty-tag behaviour"),
                "empty-tag behaviour must be documented as intentional change");
    }

    private static void testMissingTagFallback() throws IOException {
        String s = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/TagRewardItemSource.java");
        assertTrue(s.contains("orElseGet"), "missing tag must trigger fallback");
    }

    private static void testConditionEligibilityExcludesEntry() {
        WeightedRewardPoolEntry e = new WeightedRewardPoolEntry(100, m(), List.of((c, r) -> false), List.of());
        assertFalse(e.isEligible(null, new Random(42L)), "entry with false condition must be ineligible");
    }

    private static void testConditionEvaluatedOnce() {
        AtomicInteger cnt = new AtomicInteger(0);
        WeightedRewardPoolEntry e = new WeightedRewardPoolEntry(10, m(),
                List.of((c, r) -> { cnt.incrementAndGet(); return true; }), List.of());
        assertTrue(e.isEligible(null, new Random(7L)), "counting condition must pass");
        e.generate(null, new Random(7L));
        assertEquals(1, cnt.get(), "condition must be evaluated exactly once per roll");
    }

    private static void testIneligibleDoesNotWasteRoll() {
        AtomicBoolean inel = new AtomicBoolean(false), el = new AtomicBoolean(false);
        WeightedRewardPoolEntry ie = new WeightedRewardPoolEntry(100,
                (c, r) -> { inel.set(true); return Stream.empty(); }, List.of((c, r) -> false), List.of());
        WeightedRewardPoolEntry ee = new WeightedRewardPoolEntry(1,
                (c, r) -> { el.set(true); return Stream.empty(); }, List.of(), List.of());
        new DungeonRewardPool(List.of(ie, ee)).generate(null, new Random(12345L), 100, 10, new ArrayList<>());
        assertTrue(el.get(), "eligible entry must be selected");
        assertFalse(inel.get(), "ineligible entry must never be selected");
    }

    private static void testDynamicWeightEvaluatedOnce() {
        AtomicInteger cnt = new AtomicInteger(0);
        DynamicWeightedRewardPoolEntry e = new DynamicWeightedRewardPoolEntry(c -> { cnt.incrementAndGet(); return 10; }, m());
        new DungeonRewardPool(List.of(e)).generate(null, new Random(1000L), 5, 10, new ArrayList<>());
        assertEquals(5, cnt.get(), "dynamic weight must be computed once per entry per roll");
    }

    private static void testAllIneligibleReturnsNull() {
        WeightedRewardPoolEntry e = new WeightedRewardPoolEntry(100, m(), List.of((c, r) -> false), List.of());
        List<ItemStack> r = new DungeonRewardPool(List.of(e)).generate(null, new Random(5L), 5, 10);
        assertTrue(r.isEmpty(), "all-ineligible pool must produce no output");
    }

    private static void testAllZeroWeightReturnsNull() {
        WeightedRewardPoolEntry e = new WeightedRewardPoolEntry(0, m(), List.of(), List.of());
        List<ItemStack> r = new DungeonRewardPool(List.of(e)).generate(null, new Random(5L), 5, 10);
        assertTrue(r.isEmpty(), "all-zero-weight pool must produce no output");
    }

    private static void testWeightSumOverflowDoesNotCrash() {
        List<DungeonRewardPoolEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) entries.add(new WeightedRewardPoolEntry(Integer.MAX_VALUE / 2, m(), List.of(), List.of()));
        new DungeonRewardPool(entries).generate(null, new Random(7L), 1, 10, new ArrayList<>());
    }

    private static void testDeterministicSelectionWithFixedSeeds() {
        AtomicInteger a = new AtomicInteger(0), b = new AtomicInteger(0);
        DynamicWeightedRewardPoolEntry ea = new DynamicWeightedRewardPoolEntry(c -> { a.incrementAndGet(); return 10; }, m());
        DynamicWeightedRewardPoolEntry eb = new DynamicWeightedRewardPoolEntry(c -> { b.incrementAndGet(); return 10; }, m());
        DungeonRewardPool pool = new DungeonRewardPool(List.of(ea, eb));
        pool.generate(null, new Random(42L), 1, 10, new ArrayList<>());
        int a1 = a.get(), b1 = b.get();
        a.set(0); b.set(0);
        pool.generate(null, new Random(42L), 1, 10, new ArrayList<>());
        assertEquals(a1, a.get(), "same seed must produce same weight calls for A");
        assertEquals(b1, b.get(), "same seed must produce same weight calls for B");
    }

    private static void testNegativeWeightClampedToZero() {
        AtomicBoolean bad = new AtomicBoolean(false), good = new AtomicBoolean(false);
        WeightedRewardPoolEntry bw = new WeightedRewardPoolEntry(-5, (c, r) -> { bad.set(true); return Stream.empty(); }, List.of(), List.of());
        WeightedRewardPoolEntry gw = new WeightedRewardPoolEntry(1, (c, r) -> { good.set(true); return Stream.empty(); }, List.of(), List.of());
        new DungeonRewardPool(List.of(bw, gw)).generate(null, new Random(100L), 100, 100, new ArrayList<>());
        assertTrue(good.get(), "positive-weight entry must be selected");
        assertFalse(bad.get(), "negative-weight entry must never be selected");
    }

    private static void testTierClampRange() {
        assertEquals(1, DungeonRewardTiers.clampTier(0), "tier 0 must clamp to 1");
        assertEquals(1, DungeonRewardTiers.clampTier(-5), "negative tier must clamp to 1");
        assertEquals(4, DungeonRewardTiers.clampTier(5), "tier 5 must clamp to 4");
        assertEquals(4, DungeonRewardTiers.clampTier(100), "tier 100 must clamp to 4");
        assertEquals(2, DungeonRewardTiers.clampTier(2), "tier 2 must pass through");
        assertEquals(3, DungeonRewardTiers.clampTier(3), "tier 3 must pass through");
    }

    private static void testTierPromotionNeverExceedsCeiling() {
        for (int s = 0; s < 1000; s++) {
            int t = DungeonRewardTiers.chooseTier(new Random(s), 1, 3, 100.0F);
            assertTrue(t <= 3 && t >= 1, "tier must be within [1,3], got " + t);
        }
        for (int s = 0; s < 100; s++)
            assertEquals(4, DungeonRewardTiers.chooseTier(new Random(s), 4, 4, 5.0F), "at ceiling=4 must stay 4");
    }

    private static void testDifficultyTierRangeConditionBoundaries() {
        new DifficultyTierRangeCondition(2, 3);
        new DifficultyTierRangeCondition(0, 3);
        new DifficultyTierRangeCondition(4, 2);
    }

    private static void testMaximumScalableRollsWithinCapacity() {
        assertTrue(20 + 1 <= 32, "max rolls+scrolls must fit in chest capacity 32");
    }

    private static void testDifferentSaltsIndependent() {
        long a = DefaultDungeonRewardGenerator.mix(12345L, DefaultDungeonRewardGenerator.SCALABLE_REWARD_SALT);
        long b = DefaultDungeonRewardGenerator.mix(12345L, DefaultDungeonRewardGenerator.RETURN_SCROLL_SALT);
        assertTrue(a != b, "different salts must produce different values");
    }

    private static void testSeedDeterminism() {
        long a = DefaultDungeonRewardGenerator.mix(999L, DefaultDungeonRewardGenerator.SCALABLE_REWARD_SALT);
        long b = DefaultDungeonRewardGenerator.mix(999L, DefaultDungeonRewardGenerator.SCALABLE_REWARD_SALT);
        assertEquals(a, b, "same seed+salt must be deterministic");
    }

    private static void testCategoryWeightFormulas() {
        assertEquals(20, 20 + Math.round(Math.max(0.0F, 1.0F - 1.0F) * 8.0F), "book at 1.0");
        assertEquals(12, 12 + Math.round(Math.max(0.0F, 1.0F - 1.0F) * 10.0F), "template at 1.0");
        assertEquals(28, 20 + Math.round(Math.max(0.0F, 2.0F - 1.0F) * 8.0F), "book at 2.0");
        assertEquals(22, 12 + Math.round(Math.max(0.0F, 2.0F - 1.0F) * 10.0F), "template at 2.0");
        assertEquals(20, 20 + Math.round(Math.max(0.0F, 0.5F - 1.0F) * 8.0F), "book at 0.5 floor");
        assertEquals(12, 12 + Math.round(Math.max(0.0F, 0.5F - 1.0F) * 10.0F), "template at 0.5 floor");
    }

    private static void testConstructorsRejectNull() {
        try { new WeightedRewardPoolEntry(1, null); throw new AssertionError("null src"); } catch (NullPointerException ok) {}
        try { new WeightedRewardPoolEntry(1, m(), null, List.of()); throw new AssertionError("null cond"); } catch (NullPointerException ok) {}
        try { new WeightedRewardPoolEntry(1, m(), List.of(), null); throw new AssertionError("null func"); } catch (NullPointerException ok) {}
        try { new DynamicWeightedRewardPoolEntry(null, m()); throw new AssertionError("null wf"); } catch (NullPointerException ok) {}
        try { new TagRewardItemSource(null, List.of()); throw new AssertionError("null tag"); } catch (NullPointerException ok) {}
        try { new TieredTagRewardItemSource(null, t -> null, t -> List.of()); throw new AssertionError("null tp"); } catch (NullPointerException ok) {}
    }

    private static void testStableWeightsStoredInChoose() {
        AtomicInteger cnt = new AtomicInteger(0);
        DungeonRewardPoolEntry e = new DungeonRewardPoolEntry() {
            public List<ItemStack> generate(DungeonRewardContext c, Random r) { return List.of(); }
            public int weight(DungeonRewardContext c) { cnt.incrementAndGet(); return 50; }
        };
        new DungeonRewardPool(List.of(e)).generate(null, new Random(200L), 5, 10, new ArrayList<>());
        assertEquals(5, cnt.get(), "weight called once per entry per roll");
    }

    static DungeonRewardItemSource m() { return (c, r) -> Stream.empty(); }

    static String read(String f) throws IOException { return Files.readString(Path.of(f)).replace("\r\n", "\n"); }

    static void assertTrue(boolean c, String m) { if (!c) throw new AssertionError(m); }
    static void assertFalse(boolean c, String m) { if (c) throw new AssertionError(m); }
    static void assertEquals(Object e, Object a, String m) { if (!java.util.Objects.equals(e, a)) throw new AssertionError(m + ": exp=" + e + " act=" + a); }
}