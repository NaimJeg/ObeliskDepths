package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;

import java.util.Objects;
import java.util.Optional;

record DungeonPreparationLoadedSiteResult(
        Optional<DungeonSite> site,
        DungeonPreparationJobFailureReason failureReason,
        String detail,
        Optional<Throwable> failure
) {
    DungeonPreparationLoadedSiteResult {
        site = site == null ? Optional.empty() : site;
        detail = detail == null ? "" : detail;
        failure = failure == null ? Optional.empty() : failure;
        if (site.isPresent() && failureReason != null) {
            throw new IllegalArgumentException(
                    "Accepted loaded site result must not have a failure reason."
            );
        }
        if (site.isEmpty() && failureReason == null) {
            throw new IllegalArgumentException(
                    "Rejected loaded site result requires a failure reason."
            );
        }
    }

    static DungeonPreparationLoadedSiteResult accepted(DungeonSite site) {
        return new DungeonPreparationLoadedSiteResult(
                Optional.of(Objects.requireNonNull(site, "site")),
                null,
                "",
                Optional.empty()
        );
    }

    static DungeonPreparationLoadedSiteResult rejected(
            DungeonPreparationJobFailureReason reason,
            String detail
    ) {
        return rejected(reason, detail, null);
    }

    static DungeonPreparationLoadedSiteResult rejected(
            DungeonPreparationJobFailureReason reason,
            String detail,
            Throwable failure
    ) {
        return new DungeonPreparationLoadedSiteResult(
                Optional.empty(),
                Objects.requireNonNull(reason, "reason"),
                detail,
                Optional.ofNullable(failure)
        );
    }

    boolean accepted() {
        return this.site.isPresent();
    }
}
