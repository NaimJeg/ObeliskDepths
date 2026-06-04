package io.github.naimjeg.obeliskdepths.block.multipart;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

final class MultipartRemovalGuards {
    private static final int MAX_PRE_REMOVED_MARKERS = 256;

    private static final ThreadLocal<Set<MultipartStructureKey>>
            CURRENTLY_REMOVING = new ThreadLocal<>();
    private static final ThreadLocal<Set<MultipartStructureKey>>
            HOOK_EXECUTED = new ThreadLocal<>();
    private static final ThreadLocal<Set<MultipartStructureKey>>
            PRE_REMOVED = new ThreadLocal<>();

    private MultipartRemovalGuards() {
    }

    record MultipartStructureKey(
            Object level,
            Object block,
            BlockPos mainPos
    ) {
        MultipartStructureKey {
            level = Objects.requireNonNull(level, "level");
            block = Objects.requireNonNull(block, "block");
            mainPos = Objects.requireNonNull(mainPos, "mainPos")
                    .immutable();
        }

        static MultipartStructureKey of(
                Level level,
                Block block,
                BlockPos mainPos
        ) {
            return new MultipartStructureKey(level, block, mainPos);
        }
    }

    static boolean isCurrentlyRemoving(MultipartStructureKey key) {
        return contains(CURRENTLY_REMOVING, key);
    }

    static void runRemoving(MultipartStructureKey key, Runnable action) {
        Objects.requireNonNull(action, "action");
        boolean added = set(CURRENTLY_REMOVING).add(key);

        try {
            action.run();
        } finally {
            if (added) {
                remove(CURRENTLY_REMOVING, key);
                remove(HOOK_EXECUTED, key);
            }
        }
    }

    static boolean runHookOnce(MultipartStructureKey key, Runnable action) {
        Objects.requireNonNull(action, "action");
        Set<MultipartStructureKey> executed = set(HOOK_EXECUTED);

        if (!executed.add(key)) {
            return false;
        }

        action.run();
        return true;
    }

    static void markPreRemoved(MultipartStructureKey key) {
        Set<MultipartStructureKey> preRemoved = set(PRE_REMOVED);
        preRemoved.add(key);
        prunePreRemoved(preRemoved);
    }

    static boolean consumePreRemoved(MultipartStructureKey key) {
        Set<MultipartStructureKey> preRemoved = PRE_REMOVED.get();

        if (preRemoved == null || !preRemoved.remove(key)) {
            return false;
        }

        if (preRemoved.isEmpty()) {
            PRE_REMOVED.remove();
        }
        return true;
    }

    static void unmarkPreRemoved(MultipartStructureKey key) {
        remove(PRE_REMOVED, key);
    }

    static boolean isThreadStateClearForTests() {
        return isEmpty(CURRENTLY_REMOVING)
                && isEmpty(HOOK_EXECUTED)
                && isEmpty(PRE_REMOVED);
    }

    static int maxPreRemovedMarkersForTests() {
        return MAX_PRE_REMOVED_MARKERS;
    }

    static int preRemovedMarkerCountForTests() {
        Set<MultipartStructureKey> preRemoved = PRE_REMOVED.get();
        return preRemoved == null ? 0 : preRemoved.size();
    }

    static void clearForTests() {
        CURRENTLY_REMOVING.remove();
        HOOK_EXECUTED.remove();
        PRE_REMOVED.remove();
    }

    private static Set<MultipartStructureKey> set(
            ThreadLocal<Set<MultipartStructureKey>> local
    ) {
        Set<MultipartStructureKey> set = local.get();

        if (set == null) {
            set = new LinkedHashSet<>();
            local.set(set);
        }

        return set;
    }

    private static void prunePreRemoved(Set<MultipartStructureKey> preRemoved) {
        while (preRemoved.size() > MAX_PRE_REMOVED_MARKERS) {
            Iterator<MultipartStructureKey> iterator = preRemoved.iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static boolean contains(
            ThreadLocal<Set<MultipartStructureKey>> local,
            MultipartStructureKey key
    ) {
        Set<MultipartStructureKey> set = local.get();
        return set != null && set.contains(key);
    }

    private static void remove(
            ThreadLocal<Set<MultipartStructureKey>> local,
            MultipartStructureKey key
    ) {
        Set<MultipartStructureKey> set = local.get();

        if (set == null) {
            return;
        }

        set.remove(key);

        if (set.isEmpty()) {
            local.remove();
        }
    }

    private static boolean isEmpty(
            ThreadLocal<Set<MultipartStructureKey>> local
    ) {
        Set<MultipartStructureKey> set = local.get();
        return set == null || set.isEmpty();
    }
}
