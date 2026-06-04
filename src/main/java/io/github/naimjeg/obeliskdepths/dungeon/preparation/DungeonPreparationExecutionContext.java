package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScan;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonSiteCandidateCursor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class DungeonPreparationExecutionContext {
    private final DungeonPreparationJobId jobId;
    private int generation;
    private DungeonSiteCandidateCursor candidateCursor;
    private final List<DungeonSiteKey> enumeratedCandidateKeys =
            new ArrayList<>();
    private List<DungeonSiteKey> orderedCandidateKeys = List.of();
    private AsyncDungeonSiteProbe scanner;
    private DungeonSiteProbeReport scanReport;
    private Throwable scanFailure;
    private DungeonCandidateClassificationState classificationState;
    private List<DungeonSiteKey> persistedCandidates = List.of();
    private List<DungeonSiteKey> generationCandidates = List.of();
    private int nextPersistedCandidateIndex;
    private int nextGenerationCandidateIndex;
    private DungeonSiteKey currentCandidate;
    private DungeonPreparationStartChunkLease currentStartLease;
    private DungeonSiteClaim currentClaim;
    private DungeonSite resolvedSite;
    private DungeonEntryChunkPlan entryChunkPlan;
    private final List<DungeonPreparationStartChunkLease> entryChunkLeases =
            new ArrayList<>();
    private int nextEntryChunkRequestIndex;
    private int nextEntryLeaseValidationIndex;
    private int nextLoadedChunkValidationIndex;
    private DungeonSafeSpawnScan safeSpawnScan;
    private PreparedDungeonDestination preparedDestination;
    private DungeonActivationCommitPlan commitPlan;
    private String diagnosticDetail = "";
    private DungeonActivationCommitResult committedResult;

    DungeonPreparationExecutionContext(DungeonPreparationJobId jobId) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
    }

    DungeonPreparationJobId jobId() {
        return this.jobId;
    }

    int generation() {
        return this.generation;
    }

    void invalidateGeneration() {
        this.generation++;
    }

    Optional<DungeonSiteCandidateCursor> candidateCursor() {
        return Optional.ofNullable(this.candidateCursor);
    }

    void candidateCursor(DungeonSiteCandidateCursor candidateCursor) {
        this.candidateCursor = Objects.requireNonNull(
                candidateCursor,
                "candidateCursor"
        );
    }

    void addEnumeratedCandidateKey(DungeonSiteKey key) {
        this.enumeratedCandidateKeys.add(Objects.requireNonNull(key, "key"));
    }

    List<DungeonSiteKey> enumeratedCandidateKeys() {
        return List.copyOf(this.enumeratedCandidateKeys);
    }

    void clearCandidateCursor() {
        this.candidateCursor = null;
        this.enumeratedCandidateKeys.clear();
    }

    List<DungeonSiteKey> orderedCandidateKeys() {
        return this.orderedCandidateKeys;
    }

    void orderedCandidateKeys(List<DungeonSiteKey> orderedCandidateKeys) {
        this.orderedCandidateKeys = copyKeys(orderedCandidateKeys);
    }

    Optional<AsyncDungeonSiteProbe> scanner() {
        return Optional.ofNullable(this.scanner);
    }

    void scanner(AsyncDungeonSiteProbe scanner) {
        this.scanner = scanner;
    }

    Optional<DungeonSiteProbeReport> scanReport() {
        return Optional.ofNullable(this.scanReport);
    }

    Optional<Throwable> scanFailure() {
        return Optional.ofNullable(this.scanFailure);
    }

    void completeScan(DungeonSiteProbeReport report, Throwable failure) {
        this.scanReport = report;
        this.scanFailure = failure;
    }

    void clearScanner() {
        this.scanner = null;
        this.scanReport = null;
        this.scanFailure = null;
        this.classificationState = null;
    }

    DungeonCandidateClassificationState classificationState() {
        if (this.classificationState == null) {
            this.classificationState = new DungeonCandidateClassificationState();
        }
        return this.classificationState;
    }

    List<DungeonSiteKey> persistedCandidates() {
        return this.persistedCandidates;
    }

    List<DungeonSiteKey> generationCandidates() {
        return this.generationCandidates;
    }

    void candidateLists(
            List<DungeonSiteKey> persistedCandidates,
            List<DungeonSiteKey> generationCandidates
    ) {
        this.persistedCandidates = copyKeys(persistedCandidates);
        this.generationCandidates = copyKeys(generationCandidates);
        this.nextPersistedCandidateIndex = 0;
        this.nextGenerationCandidateIndex = 0;
    }

    Optional<DungeonSiteKey> nextPersistedCandidate() {
        if (this.nextPersistedCandidateIndex >= this.persistedCandidates.size()) {
            return Optional.empty();
        }
        return Optional.of(this.persistedCandidates.get(this.nextPersistedCandidateIndex++));
    }

    boolean hasRemainingPersistedCandidates() {
        return this.nextPersistedCandidateIndex < this.persistedCandidates.size();
    }

    Optional<DungeonSiteKey> nextGenerationCandidate() {
        if (this.nextGenerationCandidateIndex
                >= this.generationCandidates.size()) {
            return Optional.empty();
        }
        return Optional.of(
                this.generationCandidates.get(this.nextGenerationCandidateIndex++)
        );
    }

    boolean hasRemainingGenerationCandidates() {
        return this.nextGenerationCandidateIndex
                < this.generationCandidates.size();
    }

    int attemptedGenerationCandidateCount() {
        return this.nextGenerationCandidateIndex;
    }

    Optional<DungeonSiteKey> nextAvailableCandidate() {
        Optional<DungeonSiteKey> persisted = nextPersistedCandidate();
        if (persisted.isPresent()) {
            return persisted;
        }
        return nextGenerationCandidate();
    }

    Optional<DungeonSiteKey> currentCandidate() {
        return Optional.ofNullable(this.currentCandidate);
    }

    void currentCandidate(DungeonSiteKey currentCandidate) {
        this.currentCandidate = Objects.requireNonNull(
                currentCandidate,
                "currentCandidate"
        );
    }

    Optional<DungeonPreparationStartChunkLease> currentStartLease() {
        return Optional.ofNullable(this.currentStartLease);
    }

    void currentStartLease(
            DungeonSiteKey candidate,
            DungeonPreparationStartChunkLease lease
    ) {
        this.currentCandidate = Objects.requireNonNull(candidate, "candidate");
        this.currentStartLease = Objects.requireNonNull(lease, "lease");
    }

    void clearCurrentStartLease() {
        this.currentCandidate = null;
        this.currentStartLease = null;
    }

    DungeonSiteClaim currentClaim() {
        return this.currentClaim;
    }

    void currentClaim(DungeonSiteClaim currentClaim) {
        this.currentClaim = currentClaim;
    }

    Optional<DungeonSite> resolvedSite() {
        return Optional.ofNullable(this.resolvedSite);
    }

    void resolvedSite(DungeonSite resolvedSite) {
        this.resolvedSite = resolvedSite;
    }

    Optional<DungeonEntryChunkPlan> entryChunkPlan() {
        return Optional.ofNullable(this.entryChunkPlan);
    }

    void entryChunkPlan(DungeonEntryChunkPlan entryChunkPlan) {
        this.entryChunkPlan = entryChunkPlan;
        resetEntryValidationIndices();
    }

    List<DungeonPreparationStartChunkLease> entryChunkLeases() {
        return new ArrayList<>(this.entryChunkLeases);
    }

    void addEntryChunkLease(DungeonPreparationStartChunkLease lease) {
        Objects.requireNonNull(lease, "lease");
        this.entryChunkLeases.add(lease);
    }

    void clearEntryChunkLeases() {
        this.entryChunkLeases.clear();
        resetEntryValidationIndices();
    }

    DungeonPreparationStartChunkLease entryChunkLease(int index) {
        return this.entryChunkLeases.get(index);
    }

    int nextEntryChunkRequestIndex() {
        return this.nextEntryChunkRequestIndex;
    }

    int entryChunkLeaseCount() {
        return this.entryChunkLeases.size();
    }

    void advanceEntryChunkRequestIndex(int count) {
        this.nextEntryChunkRequestIndex += count;
    }

    void resetEntryChunkRequestIndex() {
        this.nextEntryChunkRequestIndex = 0;
        resetEntryValidationIndices();
    }

    int nextEntryLeaseValidationIndex() {
        return this.nextEntryLeaseValidationIndex;
    }

    void advanceEntryLeaseValidationIndex() {
        this.nextEntryLeaseValidationIndex++;
    }

    int nextLoadedChunkValidationIndex() {
        return this.nextLoadedChunkValidationIndex;
    }

    void advanceLoadedChunkValidationIndex() {
        this.nextLoadedChunkValidationIndex++;
    }

    void resetEntryValidationIndices() {
        this.nextEntryLeaseValidationIndex = 0;
        this.nextLoadedChunkValidationIndex = 0;
    }

    Optional<DungeonSafeSpawnScan> safeSpawnScan() {
        return Optional.ofNullable(this.safeSpawnScan);
    }

    void safeSpawnScan(DungeonSafeSpawnScan safeSpawnScan) {
        this.safeSpawnScan = Objects.requireNonNull(
                safeSpawnScan,
                "safeSpawnScan"
        );
    }

    void clearSafeSpawnScan() {
        if (this.safeSpawnScan != null) {
            this.safeSpawnScan.cancel();
            this.safeSpawnScan = null;
        }
    }

    Optional<PreparedDungeonDestination> preparedDestination() {
        return Optional.ofNullable(this.preparedDestination);
    }

    void preparedDestination(PreparedDungeonDestination preparedDestination) {
        this.preparedDestination = preparedDestination;
    }

    Optional<DungeonActivationCommitPlan> commitPlan() {
        return Optional.ofNullable(this.commitPlan);
    }

    void commitPlan(DungeonActivationCommitPlan commitPlan) {
        this.commitPlan = Objects.requireNonNull(commitPlan, "commitPlan");
    }

    void clearCommitPlan() {
        this.commitPlan = null;
    }

    String diagnosticDetail() {
        return this.diagnosticDetail;
    }

    void diagnosticDetail(String diagnosticDetail) {
        this.diagnosticDetail = diagnosticDetail == null ? "" : diagnosticDetail;
    }

    DungeonActivationCommitResult committedResult() {
        return this.committedResult;
    }

    void committedResult(DungeonActivationCommitResult committedResult) {
        this.committedResult = committedResult;
    }

    void clearTransientCandidateState() {
        clearCandidateCursor();
        clearScanner();
        clearSafeSpawnScan();
        clearCommitPlan();
        this.currentCandidate = null;
        this.currentStartLease = null;
    }

    private static List<DungeonSiteKey> copyKeys(List<DungeonSiteKey> keys) {
        Objects.requireNonNull(keys, "keys");
        ArrayList<DungeonSiteKey> copy = new ArrayList<>(keys.size());
        for (DungeonSiteKey key : keys) {
            copy.add(Objects.requireNonNull(key, "key"));
        }
        return List.copyOf(copy);
    }
}
