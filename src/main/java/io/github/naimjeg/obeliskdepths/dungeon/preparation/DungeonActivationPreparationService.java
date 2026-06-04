package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.WorldgenDungeonSiteProvisioner;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class DungeonActivationPreparationService {
    private DungeonActivationPreparationService() {
    }

    public static DungeonPreparationResult prepare(
            ServerLevel dungeonLevel,
            DungeonPreparationRequest request,
            ItemStack tributeStack
    ) {
        PreparationLookup lookup = new ServerPreparationLookup(dungeonLevel);
        return prepareWithLookup(
                request,
                () -> TributeResolver.resolve(tributeStack),
                lookup
        );
    }

    static DungeonPreparationResult prepareWithLookup(
            DungeonPreparationRequest request,
            Supplier<ResolvedTribute> tributeSupplier,
            PreparationLookup lookup
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Preparation request must be present.");
        }
        if (tributeSupplier == null) {
            throw new IllegalArgumentException("Tribute supplier must be present.");
        }
        if (lookup == null) {
            throw new IllegalArgumentException("Preparation lookup must be present.");
        }

        try {
            if (!lookup.targetDimensionValid()) {
                return fail(
                        request,
                        DungeonPreparationFailureReason.WRONG_TARGET_DIMENSION,
                        "wrong target dimension"
                );
            }

            if (request.requestedMode() == PortalAdmissionMode.OPEN_JOIN) {
                Optional<ExistingOpenJoinTarget> existing =
                        lookup.findExistingOpenJoinTarget(request);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }

            ResolvedTribute tribute = tributeSupplier.get();
            if (tribute == null || !tribute.valid()) {
                return fail(
                        request,
                        DungeonPreparationFailureReason.INVALID_TRIBUTE,
                        "invalid tribute"
                );
            }

            Optional<ResolvedDungeonSite> resolved =
                    lookup.findOrGenerateAuthoritativeSite(request);
            if (resolved.isEmpty()) {
                return fail(
                        request,
                        DungeonPreparationFailureReason.NO_SITE_AVAILABLE,
                        "no authoritative site available"
                );
            }

            if (!resolved.get().authoritative()) {
                return fail(
                        request,
                        DungeonPreparationFailureReason.NON_AUTHORITATIVE_SITE,
                        resolved.get().source().name()
                );
            }

            return new NewAuthoritativeSiteTarget(
                    request,
                    resolved.get(),
                    tribute
            );
        } catch (RuntimeException exception) {
            return fail(
                    request,
                    DungeonPreparationFailureReason.INTERNAL_ERROR,
                    exception.getMessage()
            );
        }
    }

    private static DungeonPreparationFailure fail(
            DungeonPreparationRequest request,
            DungeonPreparationFailureReason reason,
            String detail
    ) {
        return new DungeonPreparationFailure(request, reason, detail);
    }

    interface PreparationLookup {
        boolean targetDimensionValid();

        Optional<ExistingOpenJoinTarget> findExistingOpenJoinTarget(
                DungeonPreparationRequest request
        );

        Optional<ResolvedDungeonSite> findOrGenerateAuthoritativeSite(
                DungeonPreparationRequest request
        );
    }

    private record ServerPreparationLookup(
            ServerLevel dungeonLevel
    ) implements PreparationLookup {
        @Override
        public boolean targetDimensionValid() {
            return this.dungeonLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL);
        }

        @Override
        public Optional<ExistingOpenJoinTarget> findExistingOpenJoinTarget(
                DungeonPreparationRequest request
        ) {
            DungeonManagerSavedData data = DungeonManagerSavedData.get(this.dungeonLevel);
            long gameTime = this.dungeonLevel.getGameTime();
            Optional<PortalSession> session =
                    data.portalSessions().findActiveOpenJoinSession(
                            request.sourceDimension(),
                            request.obeliskPos(),
                            gameTime,
                            instanceId -> data.instances()
                                    .get(instanceId)
                                    .map(instance -> instance.status()
                                            == DungeonStatus.ACTIVE)
                                    .orElse(false)
                    );

            if (session.isEmpty()) {
                return Optional.empty();
            }

            Optional<DungeonInstance> instance =
                    data.instances().get(session.get().instanceId());
            if (instance.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new ExistingOpenJoinTarget(
                    request,
                    instance.get().id(),
                    session.get().id()
            ));
        }

        @Override
        public Optional<ResolvedDungeonSite> findOrGenerateAuthoritativeSite(
                DungeonPreparationRequest request
        ) {
            return WorldgenDungeonSiteProvisioner.findOrGenerateReservableSite(
                    this.dungeonLevel,
                    request.obeliskPos(),
                    DungeonManagerSavedData.get(this.dungeonLevel)
            );
        }
    }
}
