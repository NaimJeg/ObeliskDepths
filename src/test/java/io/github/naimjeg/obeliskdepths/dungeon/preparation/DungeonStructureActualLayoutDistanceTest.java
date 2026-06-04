package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationException;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

public final class DungeonStructureActualLayoutDistanceTest {
    private static final int CANDIDATE_SEED_COUNT = Integer.getInteger(
            "obelisk.test.layoutSeeds",
            Integer.parseInt(System.getenv()
                    .getOrDefault("OBELISK_TEST_LAYOUT_SEEDS", "10000"))
    );
    private static final double MINIMUM_SUCCESS_RATE = 0.99D;
    private static final BlockPos START_CHUNK_LAYOUT_ORIGIN =
            new BlockPos(8, 64, 8);
    private static final DungeonStructureDistanceReport.ChunkCoordinate
            START_CHUNK = new DungeonStructureDistanceReport.ChunkCoordinate(0, 0);

    private DungeonStructureActualLayoutDistanceTest() {
    }

    public static void main(String[] args) {
        productionPlannerAcceptsOnlyVanillaReferenceCompliantLayouts();
        sameSeedAndStartChunkProduceSameAcceptedPieces();
    }

    private static void productionPlannerAcceptsOnlyVanillaReferenceCompliantLayouts() {
        int accepted = 0;
        int rejected = 0;
        int primaryEntryViolations = 0;
        int bossRoomViolations = 0;
        int maxDistance = 0;
        int maxBossRoomDistance = 0;
        int maxXSpan = 0;
        int maxZSpan = 0;
        long totalRoomCount = 0L;
        long totalCorridorPieceCount = 0L;
        long maxRoomCount = 0L;
        long maxCorridorPieceCount = 0L;
        Map<Integer, Integer> attemptDistribution = new TreeMap<>();
        Map<Integer, Integer> distanceDistribution = new TreeMap<>();
        Map<Integer, Integer> xSpanDistribution = new TreeMap<>();
        Map<Integer, Integer> zSpanDistribution = new TreeMap<>();
        Map<Long, Integer> roomCountDistribution = new TreeMap<>();
        Map<Long, Integer> corridorPieceCountDistribution = new TreeMap<>();
        Map<Integer, Integer> primaryEntryDistanceDistribution = new TreeMap<>();
        Map<Integer, Integer> bossRoomDistanceDistribution = new TreeMap<>();
        Map<Integer, Integer> sideBranchDistribution = new TreeMap<>();
        Map<Integer, Integer> loopDistribution = new TreeMap<>();
        List<String> firstRejectedSeeds = new ArrayList<>();

        for (long seed = 0L; seed < CANDIDATE_SEED_COUNT; seed++) {
            AcceptedPiecePlan acceptedPlan = acceptedPlan(seed, firstRejectedSeeds);
            if (acceptedPlan == null) {
                rejected++;
                continue;
            }
            DungeonPiecePlan piecePlan = acceptedPlan.piecePlan();
            DungeonStructureReferenceCompliance compliance =
                    DungeonStructureReferenceCompliance.analyze(
                            START_CHUNK,
                            pieceBounds(piecePlan)
                    );
            DungeonStructureDistanceReport report =
                    compliance.distanceReport();

            if (!report.hasPieces() || !report.hasExactlyOnePrimaryEntry()) {
                primaryEntryViolations++;
                throw new AssertionError(failure(seed, report));
            }
            if (!compliance.compliant()) {
                throw new AssertionError(failure(seed, report));
            }
            List<DungeonPieceMetadata> bossRooms = piecePlan.pieces().stream()
                    .filter(piece -> piece.role() == ObeliskDungeonPieceRole.BOSS_ROOM)
                    .toList();
            if (bossRooms.size() != 1) {
                bossRoomViolations++;
                throw new AssertionError(
                        "Expected exactly one boss room for seed " + seed
                                + ", found=" + bossRooms.size()
                );
            }
            int bossRoomDistance = distanceFromStartChunk(bossRooms.getFirst());
            if (bossRoomDistance
                    > DungeonStructureDistanceValidator.VANILLA_REFERENCE_DISTANCE_CHUNKS) {
                bossRoomViolations++;
                throw new AssertionError(
                        "Boss room exceeds structure-reference distance for seed "
                                + seed + ": distance=" + bossRoomDistance
                );
            }
            accepted++;

            int xSpan = report.overallChunkBounds().maxChunkX()
                    - report.overallChunkBounds().minChunkX()
                    + 1;
            int zSpan = report.overallChunkBounds().maxChunkZ()
                    - report.overallChunkBounds().minChunkZ()
                    + 1;
            long roomCount = piecePlan.roomCount();
            long corridorPieceCount = piecePlan.corridorCount();

            maxDistance = Math.max(maxDistance, report.maximumPieceDistance());
            maxBossRoomDistance = Math.max(maxBossRoomDistance, bossRoomDistance);
            maxXSpan = Math.max(maxXSpan, xSpan);
            maxZSpan = Math.max(maxZSpan, zSpan);
            totalRoomCount += roomCount;
            totalCorridorPieceCount += corridorPieceCount;
            maxRoomCount = Math.max(maxRoomCount, roomCount);
            maxCorridorPieceCount = Math.max(
                    maxCorridorPieceCount,
                    corridorPieceCount
            );
            increment(attemptDistribution, acceptedPlan.attemptIndex() + 1);
            increment(distanceDistribution, report.maximumPieceDistance());
            increment(xSpanDistribution, xSpan);
            increment(zSpanDistribution, zSpan);
            increment(roomCountDistribution, roomCount);
            increment(corridorPieceCountDistribution, corridorPieceCount);
            increment(sideBranchDistribution, countSideBranches(acceptedPlan.graph()));
            increment(loopDistribution, acceptedPlan.graph().loopEdges().size());
            increment(bossRoomDistanceDistribution, bossRoomDistance);
            report.primaryEntryDistance()
                    .ifPresent(distance -> increment(
                            primaryEntryDistanceDistribution,
                            distance
                    ));
        }

        double successRate = accepted / (double) CANDIDATE_SEED_COUNT;
        if (successRate < MINIMUM_SUCCESS_RATE) {
            throw new AssertionError(
                    "Expected at least "
                            + (MINIMUM_SUCCESS_RATE * 100.0D)
                            + "% compliant generation success, accepted="
                            + accepted
                            + " rejected="
                            + rejected
                            + " firstRejectedSeeds="
                            + firstRejectedSeeds
            );
        }
        System.out.println(
                "Representative production planner compliance audit: candidateSeeds="
                        + CANDIDATE_SEED_COUNT
                        + " accepted="
                        + accepted
                        + " rejected="
                        + rejected
                        + " maxDistance="
                        + maxDistance
                        + " maxXSpan="
                        + maxXSpan
                        + " maxZSpan="
                        + maxZSpan
                        + " averageRoomCount="
                        + average(totalRoomCount, accepted)
                        + " maxRoomCount="
                        + maxRoomCount
                        + " averageCorridorPieceCount="
                        + average(totalCorridorPieceCount, accepted)
                        + " maxCorridorPieceCount="
                        + maxCorridorPieceCount
                        + " primaryEntryViolations="
                        + primaryEntryViolations
                        + " bossRoomViolations="
                        + bossRoomViolations
                        + " maxBossRoomDistance="
                        + maxBossRoomDistance
                        + " attemptDistribution="
                        + attemptDistribution
                        + " distanceDistribution="
                        + distanceDistribution
                        + " xSpanDistribution="
                        + xSpanDistribution
                        + " zSpanDistribution="
                        + zSpanDistribution
                        + " roomCountDistribution="
                        + roomCountDistribution
                        + " corridorPieceCountDistribution="
                        + corridorPieceCountDistribution
                        + " sideBranchDistribution="
                        + sideBranchDistribution
                        + " loopDistribution="
                        + loopDistribution
                        + " primaryEntryDistanceDistribution="
                        + primaryEntryDistanceDistribution
                        + " bossRoomDistanceDistribution="
                        + bossRoomDistanceDistribution
        );
        if (!firstRejectedSeeds.isEmpty()) {
            System.out.println("Rejected seeds: " + firstRejectedSeeds);
        }
    }

    private static int distanceFromStartChunk(DungeonPieceMetadata piece) {
        DungeonStructureDistanceReport.ChunkBounds bounds =
                DungeonStructureDistanceValidator.chunkBounds(piece.bounds());
        int maxDx = Math.max(
                Math.abs(bounds.minChunkX() - START_CHUNK.x()),
                Math.abs(bounds.maxChunkX() - START_CHUNK.x())
        );
        int maxDz = Math.max(
                Math.abs(bounds.minChunkZ() - START_CHUNK.z()),
                Math.abs(bounds.maxChunkZ() - START_CHUNK.z())
        );
        return Math.max(maxDx, maxDz);
    }

    private static void sameSeedAndStartChunkProduceSameAcceptedPieces() {
        for (long seed = 0L; seed < 256L; seed++) {
            AcceptedPiecePlan first = acceptedPlan(seed, null);
            AcceptedPiecePlan second = acceptedPlan(seed, null);
            if (first == null || second == null) {
                continue;
            }
            assertEquals(
                    first.attemptIndex(),
                    second.attemptIndex(),
                    "deterministic accepted attempt for seed " + seed
            );
            assertEquals(
                    signature(first.piecePlan()),
                    signature(second.piecePlan()),
                    "deterministic emitted pieces for seed " + seed
            );
        }
    }

    private static AcceptedPiecePlan acceptedPlan(
            long seed,
            List<String> firstRejectedSeeds
    ) {
        DungeonGraph graph = DungeonGraphGenerator.generate(seed);
        DungeonGraphValidator.validate(graph);
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
        String lastReason = "<none>";

        for (int attempt = 0;
             attempt < DungeonProceduralTestSupport.PRODUCTION_SPATIAL_LAYOUT_ATTEMPTS;
             attempt++) {
            try {
                DungeonProceduralTestSupport.AcceptedProceduralLayout layout =
                        DungeonProceduralTestSupport.acceptedProceduralLayout(
                        graph,
                        START_CHUNK_LAYOUT_ORIGIN,
                        seed,
                        attempt
                );
                DungeonPiecePlan shiftedPlan = shiftedToEnvelope(layout.pieces());
                DungeonStructureReferenceCompliance compliance =
                        DungeonStructureReferenceCompliance.analyze(
                                START_CHUNK,
                                pieceBounds(shiftedPlan)
                        );
                if (!compliance.compliant()) {
                    lastReason = "attempt="
                            + (attempt + 1)
                            + " noncompliant "
                            + compliance.describeSummary()
                            + " outside="
                            + compliance.distanceReport().describeOutsidePieces();
                    continue;
                }
                return new AcceptedPiecePlan(attempt, shiftedPlan, graph, analysis);
            } catch (DungeonLayoutGenerationException exception) {
                lastReason = "attempt="
                        + (attempt + 1)
                        + " layoutFailure="
                        + exception.getMessage();
                // Some graph seeds have no accepted production layout attempt.
            }
        }

        if (firstRejectedSeeds != null && firstRejectedSeeds.size() < 8) {
            firstRejectedSeeds.add("seed=" + seed + " " + lastReason);
        }
        return null;
    }

    private static DungeonPiecePlan shiftedToEnvelope(DungeonPiecePlan plan) {
        DungeonStructureReferenceCompliance initial =
                DungeonStructureReferenceCompliance.analyze(
                        START_CHUNK,
                        pieceBounds(plan)
                );
        return initial.referenceEnvelope()
                .chunkAlignedOffsetToContain(initial.overallPieceChunkBounds())
                .map(offset -> plan.translated(offset.x(), 0, offset.z()))
                .orElse(plan);
    }

    private static List<DungeonStructureDistanceValidator.PieceBounds> pieceBounds(
            DungeonPiecePlan plan
    ) {
        return plan.pieces()
                .stream()
                .map(piece -> new DungeonStructureDistanceValidator.PieceBounds(
                        piece.role().serializedName()
                                + ":"
                                + piece.id(),
                        piece.bounds(),
                        piece.primaryEntry()
                ))
                .toList();
    }

    private static String failure(
            long seed,
            DungeonStructureDistanceReport report
    ) {
        String farthest = report.pieces()
                .stream()
                .max(Comparator.comparingInt(
                        DungeonStructurePieceDistance::chebyshevDistanceFromStart
                ))
                .map(piece -> piece.label()
                        + " distance="
                        + piece.chebyshevDistanceFromStart()
                        + " bounds="
                        + piece.blockBounds())
                .orElse("<none>");
        long roomPieces = report.pieces()
                .stream()
                .filter(piece -> !piece.label().startsWith("corridor:"))
                .count();
        long corridorPieces = report.pieces()
                .stream()
                .filter(piece -> piece.label().startsWith("corridor:"))
                .count();
        long apronPieces = report.pieces()
                .stream()
                .filter(piece -> piece.label().contains("_apron_"))
                .count();
        return "layout distance safety failed"
                + " seed="
                + seed
                + " startChunk="
                + report.startChunk()
                + " maximumDistance="
                + report.maximumPieceDistance()
                + " overallChunkBounds="
                + report.overallChunkBounds()
                + " outsidePieces="
                + report.describeOutsidePieces()
                + " roomPieces="
                + roomPieces
                + " corridorPieces="
                + corridorPieces
                + " corridorApronPieces="
                + apronPieces
                + " farthestPiece="
                + farthest
                + " primaryEntryPieceCount="
                + report.primaryEntryPieceCount()
                + " primaryEntryDistance="
                + (report.primaryEntryDistance().isPresent()
                ? report.primaryEntryDistance().getAsInt()
                : "<missing>");
    }

    private static List<String> signature(DungeonPiecePlan plan) {
        return plan.pieces()
                .stream()
                .map(DungeonStructureActualLayoutDistanceTest::signature)
                .toList();
    }

    private static String signature(DungeonPieceMetadata piece) {
        return piece.role().serializedName()
                + ":"
                + piece.id()
                + ":"
                + piece.bounds()
                + ":"
                + piece.primaryEntry();
    }

    private static int countSideBranches(DungeonGraph graph) {
        int count = 0;
        for (DungeonGraphNode node : graph.nodes()) {
            if (node.id().startsWith("side_")) {
                count++;
            }
        }
        return count;
    }

    private static double average(long total, int count) {
        if (count == 0) {
            return 0.0D;
        }
        return Math.round((total / (double) count) * 100.0D) / 100.0D;
    }

    private static <T> void increment(
            Map<T, Integer> distribution,
            T key
    ) {
        distribution.merge(key, 1, Integer::sum);
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    private record AcceptedPiecePlan(
            int attemptIndex,
            DungeonPiecePlan piecePlan,
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
    }
}
