package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AmphixylonFieldTest {
    private AmphixylonFieldTest() {
    }

    public static void main(String[] args) {
        sameSeedAndChunkProduceSamePlan();
        differentSeedsVaryPlan();
        waistIsNarrowerThanUpperAndLowerTrunk();
        pieceBoundsStayInsideCompleteBounds();
        temporaryReferenceCrownBuildsDomedLayeredSilhouette();
        deterministicLowerRootGeometryAcrossSeeds();
        rootDensityThicknessAndBothHalvesUseUpdatedLogic();
        everyMainRootConnectsToTrunkAndFitsBounds();
        collarSectionsAreRoundedWedgesAcrossSeeds();
        buttressHeightDecaysToRoundedTips();
        secondaryRootsHaveFullRoundedSectionsInBothHalves();
        trunkHollowIsContinuousAndSmoothAcrossSeeds();
        rootTunnelsJoinTheTrunkHollow();
        rootTunnelsRetainWoodShell();
        rootSurfaceClassificationOnlyMarksOuterShell();
        chunkPartitionOrderDoesNotChangeRootVoxels();
        basinCandidatesAreAvailable();
        bulkPlansValidate();
    }

    private static void sameSeedAndChunkProduceSamePlan() {
        AmphixylonField first = representativeField(0x1234ABCDL, 7, -11);
        AmphixylonField second = representativeField(0x1234ABCDL, 7, -11);

        assertEquals(first.completeBounds(), second.completeBounds(), "same seed/chunk bounds");
        assertEquals(first.lowerRootCount(), second.lowerRootCount(), "same lower root count");
        assertEquals(first.upperRootCount(), second.upperRootCount(), "same upper root count");
        assertEquals(first.branchCount(), second.branchCount(), "same branch count");
        assertTrue(first.validatePlan(), "representative plan validates");
        assertTrue(second.validatePlan(), "recreated plan validates");
    }

    private static void differentSeedsVaryPlan() {
        AmphixylonField first = representativeField(0x1234ABCDL, 7, -11);
        AmphixylonField second = representativeField(0x5678EF90L, 7, -11);

        boolean varied = !first.completeBounds().equals(second.completeBounds())
                || first.lowerRootCount() != second.lowerRootCount()
                || first.upperRootCount() != second.upperRootCount()
                || first.branchCount() != second.branchCount();

        assertTrue(varied, "different world seeds should vary the tree plan");
    }

    private static void waistIsNarrowerThanUpperAndLowerTrunk() {
        AmphixylonField field = representativeField(0xCAFEF00DL, -3, 19);
        AmphixylonSite site = field.site();
        double waist = field.radiusAtY(site.waistY());
        double lower = field.radiusAtY(site.minY() + site.height() / 5);
        double upper = field.radiusAtY(site.minY() + site.height() * 4 / 5);

        assertTrue(waist < lower, "waist smaller than lower trunk");
        assertTrue(waist < upper, "waist smaller than upper trunk");
    }

    private static void pieceBoundsStayInsideCompleteBounds() {
        AmphixylonField field = representativeField(0xABCDEF01L, 21, 5);
        BoundingBox complete = field.completeBounds();

        assertContains(complete, field.trunkBounds(), "trunk bounds");
        for (int i = 0; i < field.lowerRootCount(); i++) {
            assertContains(complete, field.lowerRootBounds(i), "lower root " + i);
        }
        for (int i = 0; i < field.upperRootCount(); i++) {
            assertContains(complete, field.upperRootBounds(i), "upper root " + i);
        }
        for (int i = 0; i < field.branchCount(); i++) {
            assertContains(complete, field.branchCanopyBounds(i), "branch canopy " + i);
        }
    }

    private static void temporaryReferenceCrownBuildsDomedLayeredSilhouette() {
        AmphixylonField field = representativeField(0xABCDEF01L, 21, 5);
        AmphixylonSite site = field.site();
        int apexY = site.waistY() + (int) Math.round(site.height() * 0.32D);
        int skirtY = site.waistY() + (int) Math.round(site.height() * 0.035D);

        boolean apexClosed = false;
        int apexCenterX = (int) Math.round(field.centerXAtY(apexY));
        int apexCenterZ = (int) Math.round(field.centerZAtY(apexY));
        for (int x = apexCenterX - 3; x <= apexCenterX + 3 && !apexClosed; x++) {
            for (int z = apexCenterZ - 3; z <= apexCenterZ + 3; z++) {
                if (field.isLeaf(x, apexY, z)) {
                    apexClosed = true;
                    break;
                }
            }
        }

        double apexRadius = maxLeafRadiusAtY(field, apexY);
        double skirtRadius = maxLeafRadiusAtY(field, skirtY);
        assertTrue(apexClosed, "temporary crown closes over the trunk at its apex");
        assertTrue(apexRadius >= site.maxRadius() * 0.25D,
                "temporary crown has a substantial rounded apex");
        assertTrue(skirtRadius >= site.maxRadius() * 0.55D,
                "temporary crown retains broad low outer skirts");
        assertTrue(skirtRadius >= apexRadius * 1.35D,
                "temporary crown widens from apex to skirt");
    }

    private static double maxLeafRadiusAtY(AmphixylonField field, int y) {
        AmphixylonSite site = field.site();
        int reach = site.maxRadius() + 24;
        double centerX = field.centerXAtY(y);
        double centerZ = field.centerZAtY(y);
        double maximum = 0.0D;
        for (int x = site.centerX() - reach; x <= site.centerX() + reach; x++) {
            for (int z = site.centerZ() - reach; z <= site.centerZ() + reach; z++) {
                if (!field.isLeaf(x, y, z)) {
                    continue;
                }
                maximum = Math.max(
                        maximum,
                        Math.hypot(x + 0.5D - centerX, z + 0.5D - centerZ)
                );
            }
        }
        return maximum;
    }

    private static void deterministicLowerRootGeometryAcrossSeeds() {
        long[] seeds = {0x1234ABCDL, 0xCAFEF00DL, 0x1020304050607080L};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            AmphixylonField first = representativeField(seeds[seedIndex], seedIndex * 7 - 5, 13 - seedIndex * 9);
            AmphixylonField second = representativeField(seeds[seedIndex], seedIndex * 7 - 5, 13 - seedIndex * 9);
            assertEquals(
                    lowerRootFingerprint(first),
                    lowerRootFingerprint(second),
                    "same seed lower-root voxel geometry " + seedIndex
            );
            assertEquals(
                    upperRootFingerprint(first),
                    upperRootFingerprint(second),
                    "same seed upper-root voxel geometry " + seedIndex
            );
        }
    }

    private static void rootDensityThicknessAndBothHalvesUseUpdatedLogic() {
        assertNear(0.65D, AmphixylonField.rootThicknessScale(), 0.0001D,
                "root radius scale");
        long[] seeds = {0x1234ABCDL, 0xCAFEF00DL, 0x1020304050607080L};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            AmphixylonField field = representativeField(seeds[seedIndex], seedIndex * 5 - 8, 11 - seedIndex * 7);
            assertTrue(field.lowerRootCount() >= 12 && field.lowerRootCount() <= 21,
                    "lower root density is 150% of the former 8-14 range seed=" + seedIndex);
            assertTrue(field.upperRootCount() >= 12 && field.upperRootCount() <= 21,
                    "upper root density is 150% of the former 8-14 range seed=" + seedIndex);

            int pairedRootCount = Math.min(field.lowerRootCount(), field.upperRootCount());
            for (int rootIndex = 0; rootIndex < pairedRootCount; rootIndex++) {
                AmphixylonField.RootTopology lower = field.lowerRootTopology(rootIndex);
                AmphixylonField.RootTopology upper = field.upperRootTopology(rootIndex);
                assertTrue(lower.pathCount() >= 1 && lower.pathCount() <= 3,
                        "lower secondary topology left its original range root=" + rootIndex);
                assertTrue(upper.pathCount() >= 1 && upper.pathCount() <= 3,
                        "upper secondary topology left its original range root=" + rootIndex);
                assertEquals(5, lower.mainPointCount(), "lower five-stage root collar");
                assertEquals(5, upper.mainPointCount(), "upper five-stage root collar");
                assertTrue(lower.startBoundaryDepth() >= 17.0D && lower.startBoundaryDepth() <= 28.0D,
                        "lower root starts inside collar");
                assertTrue(upper.startBoundaryDepth() >= 17.0D && upper.startBoundaryDepth() <= 28.0D,
                        "upper root starts inside collar");
                assertTrue(lower.endBoundaryDepth() >= 1.5D && lower.endBoundaryDepth() <= 5.5D,
                        "lower root ends near its boundary");
                assertTrue(upper.endBoundaryDepth() >= 1.5D && upper.endBoundaryDepth() <= 5.5D,
                        "upper root ends near its boundary");
                assertTrue(lower.outwardVerticalTravel() > 10.0D
                                && upper.outwardVerticalTravel() > 10.0D,
                        "both halves grow outward from the trunk collar");

                AmphixylonField.RootCrossSection lowerCollar =
                        field.lowerMainRootCrossSection(rootIndex, 0.06D);
                AmphixylonField.RootCrossSection upperCollar =
                        field.upperMainRootCrossSection(rootIndex, 0.06D);
                AmphixylonField.RootCrossSection upperTip =
                        field.upperMainRootCrossSection(rootIndex, 0.96D);
                assertTrue(lowerCollar.buttress() && upperCollar.buttress(),
                        "main roots in both halves use buttress profiles");
                assertTrue(lowerCollar.verticalY() > 0.0D && upperCollar.verticalY() < 0.0D,
                        "upper buttress mirrors the lower buttress vertical orientation");
                assertTrue(lowerCollar.topHeight() > lowerCollar.baseHalfWidth(),
                        "lower main root retains a tall buttress collar");
                assertTrue(upperCollar.topHeight() > upperCollar.baseHalfWidth(),
                        "upper main root retains a tall buttress collar");
                assertTrue(upperTip.topHeight() < upperCollar.topHeight() * 0.30D
                                && upperTip.roundBlend() > 0.99D,
                        "upper buttress decays to a low rounded tip");
            }
        }
    }

    private static void everyMainRootConnectsToTrunkAndFitsBounds() {
        long[] seeds = {0x1234ABCDL, 0xCAFEF00DL, 0x1020304050607080L};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            AmphixylonField field = representativeField(seeds[seedIndex], seedIndex * 11 - 7, 9 - seedIndex * 5);
            BoundingBox complete = field.completeBounds();
            for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
                BoundingBox bounds = field.lowerRootBounds(rootIndex);
                assertContains(complete, bounds, "complete bounds contain lower root " + rootIndex);
                assertTrue(
                        rootOverlapsTrunk(field, rootIndex, false),
                        "lower main root connects to trunk seed=" + seedIndex + " root=" + rootIndex
                );
                assertNoGeometryOutsideBounds(field, rootIndex, bounds, 2, false);
            }
            for (int rootIndex = 0; rootIndex < field.upperRootCount(); rootIndex++) {
                BoundingBox bounds = field.upperRootBounds(rootIndex);
                assertContains(complete, bounds, "complete bounds contain upper root " + rootIndex);
                assertTrue(
                        rootOverlapsTrunk(field, rootIndex, true),
                        "upper main root connects to trunk seed=" + seedIndex + " root=" + rootIndex
                );
                assertNoGeometryOutsideBounds(field, rootIndex, bounds, 2, true);
            }
        }
    }

    private static void collarSectionsAreRoundedWedgesAcrossSeeds() {
        long[] seeds = {0x1234ABCDL, 0xCAFEF00DL, 0x1020304050607080L};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            AmphixylonField field = representativeField(seeds[seedIndex], seedIndex * 3 - 4, seedIndex * 5 + 2);
            for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
                AmphixylonField.RootCrossSection collar = field.lowerMainRootCrossSection(rootIndex, 0.06D);
                AmphixylonField.RootCrossSection middle = field.lowerMainRootCrossSection(rootIndex, 0.50D);
                AmphixylonField.RootCrossSection tip = field.lowerMainRootCrossSection(rootIndex, 0.96D);
                double middleWidth = collar.halfWidthAt(collar.topHeight() * 0.55D);
                double nearTopWidth = collar.halfWidthAt(collar.topHeight() * 0.82D);
                double ellipseMiddleWidth = collar.baseHalfWidth()
                        * Math.sqrt(1.0D - 0.55D * 0.55D);
                String diagram = "collar\n" + sectionDiagram(field, rootIndex, collar)
                        + "middle\n" + sectionDiagram(field, rootIndex, middle)
                        + "tip\n" + sectionDiagram(field, rootIndex, tip);

                assertTrue(collar.buttress(), "collar uses buttress profile\n" + diagram);
                assertTrue(
                        collar.topHeight() > collar.ridgeHalfWidth() * 3.0D,
                        "collar is a high wing rather than a thick tube seed=" + seedIndex
                                + " root=" + rootIndex + '\n' + diagram
                );
                assertTrue(
                        collar.baseHalfWidth() > middleWidth
                                && middleWidth > nearTopWidth,
                        "buttress width contracts continuously toward ridge seed=" + seedIndex
                                + " root=" + rootIndex + '\n' + diagram
                );
                assertTrue(
                        collar.ridgeHalfWidth() < collar.baseHalfWidth() * 0.35D,
                        "top ridge remains thin relative to foot seed=" + seedIndex
                                + " root=" + rootIndex + '\n' + diagram
                );
                assertTrue(
                        Math.abs(middleWidth - ellipseMiddleWidth) > collar.baseHalfWidth() * 0.18D,
                        "collar section is not an ordinary ellipse seed=" + seedIndex
                                + " root=" + rootIndex + '\n' + diagram
                );
                assertTrue(
                        collar.halfWidthAt(collar.topHeight()) < 0.01D,
                        "ridge ends in a rounded cap rather than a flat top\n" + diagram
                );
                assertTrue(
                        collar.topHeight() > middle.topHeight()
                                && middle.topHeight() > tip.topHeight(),
                        "collar/middle/tip side profile declines for fixed seed=" + seedIndex
                                + " root=" + rootIndex + '\n' + diagram
                );
                assertTrue(
                        middle.roundBlend() > 0.0D && middle.roundBlend() < 1.0D,
                        "middle section is the wedge-to-round transition\n" + diagram
                );
                assertTrue(
                        tip.roundBlend() > 0.99D
                                && tip.topHeight() < tip.baseHalfWidth(),
                        "tip section is low and rounded\n" + diagram
                );
            }
        }
    }

    private static void buttressHeightDecaysToRoundedTips() {
        AmphixylonField field = representativeField(0x6A09E667F3BCC909L, 2, -5);
        for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
            AmphixylonField.RootCrossSection collar = field.lowerMainRootCrossSection(rootIndex, 0.04D);
            double previousHeight = collar.topHeight();
            for (int sampleIndex = 1; sampleIndex <= 10; sampleIndex++) {
                double progress = sampleIndex / 10.0D;
                AmphixylonField.RootCrossSection sample = field.lowerMainRootCrossSection(rootIndex, progress);
                assertTrue(
                        sample.topHeight() <= previousHeight * 1.035D,
                        "buttress height does not regrow into a second wall root=" + rootIndex
                                + " progress=" + progress
                );
                previousHeight = sample.topHeight();
            }

            AmphixylonField.RootCrossSection outer = field.lowerMainRootCrossSection(rootIndex, 0.62D);
            AmphixylonField.RootCrossSection tip = field.lowerMainRootCrossSection(rootIndex, 0.97D);
            assertTrue(outer.topHeight() < collar.topHeight() * 0.58D,
                    "outer buttress is substantially lower root=" + rootIndex);
            assertTrue(tip.topHeight() < collar.topHeight() * 0.24D,
                    "tip no longer retains a high plate root=" + rootIndex);
            assertTrue(tip.roundBlend() > 0.99D,
                    "tip has blended to a rounded ordinary root root=" + rootIndex);
            assertTrue(tip.topHeight() < tip.baseHalfWidth(),
                    "tip is low and broad rather than wall-like root=" + rootIndex);
        }
    }

    private static void secondaryRootsHaveFullRoundedSectionsInBothHalves() {
        AmphixylonField field = representativeField(0x3141592653589793L, 4, -9);
        int lowerInspected = 0;
        for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
            for (int pathIndex = 1; pathIndex < field.lowerRootPathCount(rootIndex); pathIndex++) {
                AmphixylonField.RootCrossSection lower =
                        field.lowerSecondaryRootCrossSection(rootIndex, pathIndex, 0.18D);
                assertTrue(!lower.buttress(), "lower secondary roots remain rounded");
                assertTrue(lower.topHeight() / lower.radius() >= 0.77D,
                        "secondary root top is not pancake-flat");
                assertTrue(lower.bottomDepth() / lower.radius() >= 0.87D,
                        "secondary root bottom is not pancake-flat");
                assertTrue(lower.topHeight() + lower.bottomDepth() >= 3.2D,
                        "secondary split begins with more than a one/two-block sheet");
                lowerInspected++;
            }
        }
        assertTrue(lowerInspected > 0, "representative seed has lower secondary roots");

        int upperInspected = 0;
        for (int rootIndex = 0; rootIndex < field.upperRootCount(); rootIndex++) {
            for (int pathIndex = 1; pathIndex < field.upperRootPathCount(rootIndex); pathIndex++) {
                AmphixylonField.RootCrossSection upper =
                        field.upperSecondaryRootCrossSection(rootIndex, pathIndex, 0.18D);
                assertTrue(!upper.buttress(), "upper secondary roots remain rounded");
                assertTrue(upper.topHeight() / upper.radius() >= 0.77D,
                        "upper secondary root top is not pancake-flat");
                assertTrue(upper.bottomDepth() / upper.radius() >= 0.87D,
                        "upper secondary root bottom is not pancake-flat");
                assertTrue(upper.topHeight() + upper.bottomDepth() >= 3.2D,
                        "upper secondary split begins with more than a one/two-block sheet");
                upperInspected++;
            }
        }
        assertTrue(upperInspected > 0, "representative seed has upper secondary roots");
    }

    private static void trunkHollowIsContinuousAndSmoothAcrossSeeds() {
        long[] seeds = {0x1234ABCDL, 0xCAFEF00DL, 0x1020304050607080L};
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            AmphixylonField field = representativeField(seeds[seedIndex], seedIndex * 7 - 5, 9 - seedIndex * 3);
            AmphixylonSite site = field.site();
            int startY = site.minY() + (int) Math.ceil(site.height() * 0.10D);
            int endY = site.minY() + (int) Math.floor(site.height() * 0.90D);
            double previousRadius = -1.0D;

            for (int y = startY; y <= endY; y++) {
                double radius = field.trunkHollowRadiusAtY(y);
                assertTrue(radius >= 1.0D,
                        "trunk hollow remains open through its continuous span seed=" + seedIndex + " y=" + y);
                if (previousRadius >= 0.0D) {
                    assertTrue(Math.abs(radius - previousRadius) <= 4.0D,
                            "adjacent hollow slices taper smoothly seed=" + seedIndex + " y=" + y
                                    + " previous=" + previousRadius + " radius=" + radius);
                }
                previousRadius = radius;

                int centerX = (int) Math.floor(field.centerXAtY(y));
                int centerZ = (int) Math.floor(field.centerZAtY(y));
                assertTrue(field.isTrunkHollowGeometry(centerX, y, centerZ),
                        "hollow centerline has no vertical gaps seed=" + seedIndex + " y=" + y);

                double sampleRadius = radius * 0.55D;
                for (int direction = 0; direction < 8; direction++) {
                    double angle = direction * Math.PI / 4.0D;
                    int x = (int) Math.floor(field.centerXAtY(y) + Math.cos(angle) * sampleRadius);
                    int z = (int) Math.floor(field.centerZAtY(y) + Math.sin(angle) * sampleRadius);
                    assertTrue(field.isTrunkHollowGeometry(x, y, z),
                            "hollow core contains no interior pillar seed=" + seedIndex
                                    + " y=" + y + " direction=" + direction);
                }
            }
        }
    }

    private static void rootTunnelsJoinTheTrunkHollow() {
        long[] seeds = {0x1020304050607080L, 0x3141592653589793L, 0x6A09E667F3BCC909L};
        int inspected = 0;
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            AmphixylonField field = representativeField(seeds[seedIndex], seedIndex * 4 - 6, 7 - seedIndex * 5);
            for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
                if (!field.lowerRootHasTunnel(rootIndex)) {
                    continue;
                }

                boolean joinsTrunk = false;
                for (int collarSample = 3; collarSample <= 10; collarSample++) {
                    AmphixylonField.RootCrossSection collar =
                            field.lowerMainRootPathCrossSection(rootIndex, collarSample / 100.0D);
                    if (hasTunnelNear(field, rootIndex, collar, true)) {
                        joinsTrunk = true;
                        break;
                    }
                }
                assertTrue(joinsTrunk,
                        "root tunnel joins the trunk hollow seed=" + seedIndex + " root=" + rootIndex);

                for (int sampleIndex = 0; sampleIndex <= 5; sampleIndex++) {
                    double visibleProgress = sampleIndex * 0.08D;
                    AmphixylonField.RootCrossSection section =
                            field.lowerMainRootCrossSection(rootIndex, visibleProgress);
                    assertTrue(hasTunnelNear(field, rootIndex, section, false),
                            "root tunnel has no collar/mid gap seed=" + seedIndex
                                    + " root=" + rootIndex + " progress=" + visibleProgress);
                }
                inspected++;
            }
        }
        assertTrue(inspected > 0, "fixed seeds contain tunneled lower roots");
    }

    private static void rootTunnelsRetainWoodShell() {
        AmphixylonField field = representativeField(0x1020304050607080L, -6, 7);
        int tunnelRoots = 0;
        int tunnelVoxels = 0;
        for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
            if (!field.lowerRootHasTunnel(rootIndex)) {
                continue;
            }
            tunnelRoots++;
            AmphixylonField.RootCrossSection sample = field.lowerMainRootCrossSection(rootIndex, 0.34D);
            int reach = (int) Math.ceil(sample.radius() + 4.0D);
            int centerX = (int) Math.floor(sample.centerX());
            int centerY = (int) Math.floor(sample.centerY());
            int centerZ = (int) Math.floor(sample.centerZ());
            for (int y = centerY - reach; y <= centerY + reach; y++) {
                for (int x = centerX - reach; x <= centerX + reach; x++) {
                    for (int z = centerZ - reach; z <= centerZ + reach; z++) {
                        if (!field.isLowerRootHollow(rootIndex, x, y, z)) {
                            continue;
                        }
                        tunnelVoxels++;
                        assertTrue(field.isLowerRoot(rootIndex, x, y, z),
                                "tunnel is contained by root geometry");
                        assertTrue(field.lowerRootMargin(rootIndex, x, y, z) >= 3.0D,
                                "tunnel keeps the configured minimum wood shell");
                    }
                }
            }
        }
        assertTrue(tunnelRoots > 0, "representative seed enables root tunnels");
        assertTrue(tunnelVoxels > 0, "enabled root tunnels retain a usable hollow");
    }

    private static void rootSurfaceClassificationOnlyMarksOuterShell() {
        AmphixylonField field = representativeField(0xABCDEF01L, 3, -2);
        int rootIndex = 0;
        BoundingBox bounds = field.lowerRootBounds(rootIndex);
        int inside = 0;
        int surface = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!field.isLowerRoot(rootIndex, x, y, z)) {
                        continue;
                    }
                    inside++;
                    if (!field.isLowerRootSurface(rootIndex, x, y, z)) {
                        continue;
                    }
                    surface++;
                    assertTrue(hasExternalNeighbor(field, rootIndex, x, y, z),
                            "surface voxel has an actual exterior face");
                }
            }
        }
        assertTrue(inside > 0, "surface test root contains voxels");
        assertTrue(surface > 0 && surface < inside * 0.62D,
                "surface classification does not consume most root volume surface="
                        + surface + " inside=" + inside);
    }

    private static void chunkPartitionOrderDoesNotChangeRootVoxels() {
        AmphixylonField field = representativeField(0x5678EF90L, -17, 23);
        int rootIndex = 0;
        BoundingBox bounds = field.lowerRootBounds(rootIndex);
        List<Long> chunks = intersectingChunks(bounds);
        Set<Long> forward = collectRootByChunkOrder(field, rootIndex, bounds, chunks);
        Collections.reverse(chunks);
        Set<Long> reverse = collectRootByChunkOrder(field, rootIndex, bounds, chunks);
        assertEquals(forward, reverse, "chunk post-process partition order root voxels");
        assertTrue(!forward.isEmpty(), "chunk partition test produced root voxels");
    }

    private static void basinCandidatesAreAvailable() {
        AmphixylonField field = representativeField(0x5555AAAAL, 0, 0);

        assertTrue(!field.candidateDungeonBasinCenters().isEmpty(), "candidate basin centers exist");
        assertEquals(
                field.candidateDungeonBasinCenters().getFirst(),
                field.lowerRootBasinCenter(),
                "lower root basin center is first candidate"
        );
    }

    private static void bulkPlansValidate() {
        for (int index = 0; index < 128; index++) {
            AmphixylonField field = representativeField(
                    0x9E3779B97F4A7C15L * index,
                    index - 64,
                    37 - index
            );
            assertTrue(field.validatePlan(), "bulk tree plan validates for index " + index);
            assertTrue(
                    field.completeBounds().minY() >= field.site().minY()
                            && field.completeBounds().maxY() <= field.site().maxY(),
                    "bulk tree bounds stay inside build height for index " + index
            );
        }
    }

    static AmphixylonField representativeField(long worldSeed, int chunkX, int chunkZ) {
        return representativeField(worldSeed, chunkX, chunkZ, 88);
    }

    static AmphixylonField representativeField(
            long worldSeed,
            int chunkX,
            int chunkZ,
            int maxRadius
    ) {
        long treeSeed = mix(worldSeed
                ^ (long) chunkX * 0x632BE59BD9B4E019L
                ^ (long) chunkZ * 0x9E3779B97F4A7C15L
                ^ AmphixylonStructure.TREE_SEED_SALT);
        AmphixylonSite site = new AmphixylonSite(
                chunkX * 16 + 8,
                chunkZ * 16 + 8,
                4,
                123,
                maxRadius,
                treeSeed
        );
        return new AmphixylonField(site);
    }

    private static boolean hasTunnelNear(
            AmphixylonField field,
            int rootIndex,
            AmphixylonField.RootCrossSection section,
            boolean requireTrunkHollow
    ) {
        double tunnelCenterVertical = Math.min(1.35D, section.bottomDepth() * 0.28D);
        double centerX = section.centerX() + section.verticalX() * tunnelCenterVertical;
        double centerY = section.centerY() + section.verticalY() * tunnelCenterVertical;
        double centerZ = section.centerZ() + section.verticalZ() * tunnelCenterVertical;
        int reach = (int) Math.ceil(Math.min(6.0D, section.radius() + 2.0D));
        int blockX = (int) Math.floor(centerX);
        int blockY = (int) Math.floor(centerY);
        int blockZ = (int) Math.floor(centerZ);
        for (int y = blockY - reach; y <= blockY + reach; y++) {
            for (int x = blockX - reach; x <= blockX + reach; x++) {
                for (int z = blockZ - reach; z <= blockZ + reach; z++) {
                    if (!field.isLowerRootHollow(rootIndex, x, y, z)) {
                        continue;
                    }
                    if (!requireTrunkHollow
                            || field.isTrunkHollowGeometry(x, y, z)
                            || field.isTrunkHollowGeometry(x + 1, y, z)
                            || field.isTrunkHollowGeometry(x - 1, y, z)
                            || field.isTrunkHollowGeometry(x, y + 1, z)
                            || field.isTrunkHollowGeometry(x, y - 1, z)
                            || field.isTrunkHollowGeometry(x, y, z + 1)
                            || field.isTrunkHollowGeometry(x, y, z - 1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static long lowerRootFingerprint(AmphixylonField field) {
        long hash = 0xCBF29CE484222325L;
        for (int rootIndex = 0; rootIndex < field.lowerRootCount(); rootIndex++) {
            BoundingBox bounds = field.lowerRootBounds(rootIndex);
            hash = fingerprintValue(hash, bounds.minX());
            hash = fingerprintValue(hash, bounds.minY());
            hash = fingerprintValue(hash, bounds.minZ());
            hash = fingerprintValue(hash, bounds.maxX());
            hash = fingerprintValue(hash, bounds.maxY());
            hash = fingerprintValue(hash, bounds.maxZ());
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        if (field.isLowerRoot(rootIndex, x, y, z)) {
                            hash = fingerprintValue(hash, BlockPos.asLong(x, y, z));
                        }
                    }
                }
            }
        }
        return hash;
    }

    private static long upperRootFingerprint(AmphixylonField field) {
        long hash = 0x84222325CBF29CE4L;
        for (int rootIndex = 0; rootIndex < field.upperRootCount(); rootIndex++) {
            BoundingBox bounds = field.upperRootBounds(rootIndex);
            hash = fingerprintValue(hash, bounds.minX());
            hash = fingerprintValue(hash, bounds.minY());
            hash = fingerprintValue(hash, bounds.minZ());
            hash = fingerprintValue(hash, bounds.maxX());
            hash = fingerprintValue(hash, bounds.maxY());
            hash = fingerprintValue(hash, bounds.maxZ());
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        if (field.isUpperRoot(rootIndex, x, y, z)) {
                            hash = fingerprintValue(hash, BlockPos.asLong(x, y, z));
                        }
                    }
                }
            }
        }
        return hash;
    }

    private static long fingerprintValue(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    private static boolean rootOverlapsTrunk(
            AmphixylonField field,
            int rootIndex,
            boolean upper
    ) {
        BoundingBox root = upper
                ? field.upperRootBounds(rootIndex)
                : field.lowerRootBounds(rootIndex);
        BoundingBox trunk = field.trunkBounds();
        int minX = Math.max(root.minX(), trunk.minX());
        int minY = Math.max(root.minY(), trunk.minY());
        int minZ = Math.max(root.minZ(), trunk.minZ());
        int maxX = Math.min(root.maxX(), trunk.maxX());
        int maxY = Math.min(root.maxY(), trunk.maxY());
        int maxZ = Math.min(root.maxZ(), trunk.maxZ());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean rootGeometry = upper
                            ? field.isUpperRootGeometry(rootIndex, x, y, z)
                            : field.isLowerRootGeometry(rootIndex, x, y, z);
                    if (rootGeometry
                            && field.isTrunk(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void assertNoGeometryOutsideBounds(
            AmphixylonField field,
            int rootIndex,
            BoundingBox bounds,
            int shell,
            boolean upper
    ) {
        int minY = Math.max(field.site().minY(), bounds.minY() - shell);
        int maxY = Math.min(field.site().maxY(), bounds.maxY() + shell);
        for (int y = minY; y <= maxY; y++) {
            for (int x = bounds.minX() - shell; x <= bounds.maxX() + shell; x++) {
                for (int z = bounds.minZ() - shell; z <= bounds.maxZ() + shell; z++) {
                    boolean insideBounds = x >= bounds.minX() && x <= bounds.maxX()
                            && y >= bounds.minY() && y <= bounds.maxY()
                            && z >= bounds.minZ() && z <= bounds.maxZ();
                    if (!insideBounds) {
                        boolean rootGeometry = upper
                                ? field.isUpperRootGeometry(rootIndex, x, y, z)
                                : field.isLowerRootGeometry(rootIndex, x, y, z);
                        assertTrue(
                                !rootGeometry,
                                (upper ? "upper" : "lower")
                                        + " root geometry escapes piece bounds root=" + rootIndex
                                        + " at=" + x + ',' + y + ',' + z
                        );
                    }
                }
            }
        }
    }

    private static String sectionDiagram(
            AmphixylonField field,
            int rootIndex,
            AmphixylonField.RootCrossSection section
    ) {
        int sideReach = (int) Math.ceil(section.baseHalfWidth()) + 2;
        int topReach = (int) Math.ceil(section.topHeight()) + 1;
        int bottomReach = (int) Math.ceil(section.bottomDepth()) + 1;
        StringBuilder result = new StringBuilder();
        for (int vertical = topReach; vertical >= -bottomReach; vertical--) {
            for (int side = -sideReach; side <= sideReach; side++) {
                int x = (int) Math.floor(
                        section.centerX()
                                + section.sideX() * side
                                + section.verticalX() * vertical
                );
                int y = (int) Math.floor(
                        section.centerY()
                                + section.sideY() * side
                                + section.verticalY() * vertical
                );
                int z = (int) Math.floor(
                        section.centerZ()
                                + section.sideZ() * side
                                + section.verticalZ() * vertical
                );
                if (field.isLowerRootSurface(rootIndex, x, y, z)) {
                    result.append('o');
                } else if (field.isLowerRoot(rootIndex, x, y, z)) {
                    result.append('#');
                } else {
                    result.append(' ');
                }
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static boolean hasExternalNeighbor(
            AmphixylonField field,
            int rootIndex,
            int x,
            int y,
            int z
    ) {
        int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };
        for (int[] offset : offsets) {
            int neighborX = x + offset[0];
            int neighborY = y + offset[1];
            int neighborZ = z + offset[2];
            boolean rootOrTrunk = field.isLowerRoot(rootIndex, neighborX, neighborY, neighborZ)
                    || (neighborY >= field.site().minY()
                    && neighborY <= field.site().maxY()
                    && field.isTrunk(neighborX, neighborY, neighborZ));
            if (!rootOrTrunk) {
                return true;
            }
        }
        return false;
    }

    private static List<Long> intersectingChunks(BoundingBox bounds) {
        List<Long> result = new ArrayList<>();
        int minChunkX = Math.floorDiv(bounds.minX(), 16);
        int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                result.add(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
            }
        }
        return result;
    }

    private static Set<Long> collectRootByChunkOrder(
            AmphixylonField field,
            int rootIndex,
            BoundingBox bounds,
            List<Long> chunks
    ) {
        Set<Long> result = new HashSet<>();
        for (long packedChunk : chunks) {
            int chunkX = (int) (packedChunk >> 32);
            int chunkZ = (int) packedChunk;
            int minX = Math.max(bounds.minX(), chunkX * 16);
            int maxX = Math.min(bounds.maxX(), chunkX * 16 + 15);
            int minZ = Math.max(bounds.minZ(), chunkZ * 16);
            int maxZ = Math.min(bounds.maxZ(), chunkZ * 16 + 15);
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (field.isLowerRoot(rootIndex, x, y, z)) {
                            result.add(BlockPos.asLong(x, y, z));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void assertContains(BoundingBox outer, BoundingBox inner, String message) {
        assertTrue(
                inner.minX() >= outer.minX()
                        && inner.maxX() <= outer.maxX()
                        && inner.minY() >= outer.minY()
                        && inner.maxY() <= outer.maxY()
                        && inner.minZ() >= outer.minZ()
                        && inner.maxZ() <= outer.maxZ(),
                message + " should be inside complete bounds"
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(
            double expected,
            double actual,
            double tolerance,
            String message
    ) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
