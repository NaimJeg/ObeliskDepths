package io.github.naimjeg.obeliskdepths.dungeon.portal;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.PortalSessionStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PortalSessionStoreMaintenanceTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("minecraft", "overworld")
    );
    private PortalSessionStoreMaintenanceTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();
        maintenanceBatchesAreBoundedAndRotateFairly();
        validSourceObeliskLookupIgnoresExpiredSessions();
    }

    private static void validSourceObeliskLookupIgnoresExpiredSessions() {
        PortalSessionStore store = new PortalSessionStore(() -> { });
        BlockPos sharedPos = new BlockPos(4, 64, 7);
        PortalSession expired = new PortalSession(
                PortalSessionId.create(), DungeonInstanceId.create(), UUID.randomUUID(),
                OVERWORLD, sharedPos, sharedPos, SessionAccessPolicy.STARTER_ONLY, 10L
        );
        PortalSession active = new PortalSession(
                PortalSessionId.create(), DungeonInstanceId.create(), UUID.randomUUID(),
                OVERWORLD, sharedPos, sharedPos, SessionAccessPolicy.STARTER_ONLY, 30L
        );
        store.add(expired);
        store.add(active);

        check(store.findValidBySourceObelisk(OVERWORLD, sharedPos, 10L)
                        .orElseThrow() == active,
                "source lookup ignores expired session and returns active conflict");
        check(store.findValidBySourceObelisk(OVERWORLD, sharedPos, 30L).isEmpty(),
                "source lookup returns empty after every session expires");
    }

    private static void maintenanceBatchesAreBoundedAndRotateFairly() {
        int[] dirtyCalls = {0};
        PortalSessionStore store = new PortalSessionStore(() -> dirtyCalls[0]++);
        ArrayList<PortalSessionId> ids = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            PortalSession session = session(index);
            ids.add(session.id());
            store.add(session);
        }
        int dirtyAfterAdds = dirtyCalls[0];

        List<PortalSessionId> first = store.nextMaintenanceBatch(2).stream()
                .map(PortalSession::id)
                .toList();
        List<PortalSessionId> second = store.nextMaintenanceBatch(2).stream()
                .map(PortalSession::id)
                .toList();
        List<PortalSessionId> third = store.nextMaintenanceBatch(2).stream()
                .map(PortalSession::id)
                .toList();

        check(first.equals(ids.subList(0, 2)), "first bounded batch");
        check(second.equals(ids.subList(2, 4)), "second bounded batch");
        check(third.equals(List.of(ids.get(4), ids.get(0))),
                "rotation reaches tail then wraps");
        check(dirtyCalls[0] == dirtyAfterAdds,
                "maintenance rotation does not dirty persistent content");
    }

    private static PortalSession session(int index) {
        BlockPos pos = new BlockPos(index, 64, index);
        return new PortalSession(
                PortalSessionId.create(),
                DungeonInstanceId.create(),
                UUID.randomUUID(),
                OVERWORLD,
                pos,
                pos,
                SessionAccessPolicy.STARTER_ONLY,
                1_000L
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
