package io.github.naimjeg.obeliskdepths.tempering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TemperingDomainExtractionSourceTest {
    private TemperingDomainExtractionSourceTest() {
    }

    public static void main(String[] args) throws IOException {
        testMenuDoesNotOwnTemperingRules();
        testResolverOwnsResolutionOnly();
        testTransactionRevalidatesAndConsumesInputs();
        testMenuCommitsOnlyAfterSuccessfulOutputTransfer();
        testPayloadCarriesViewStateOnly();
        testClientSynchronizationUsesImmutableViewState();
    }

    private static void testMenuDoesNotOwnTemperingRules()
            throws IOException {
        String menu = read("src/main/java/io/github/naimjeg/obeliskdepths/menu/ObeliskTemperingMenu.java");

        assertFalse(menu.contains("public ObeliskTemperingRecipeInput createRecipeInput"),
                "menu must not expose the old recipe-input helper API");
        assertFalse(menu.contains("record ClientTemperingState"),
                "client view state must not be an internal menu record");
        assertFalse(menu.contains("record ResolvedTemperingState"),
                "resolved tempering state must not be an internal menu record");
        assertFalse(menu.contains("ObeliskTemperingRecipeResolver"),
                "menu must not find tempering recipes directly");
        assertFalse(menu.contains("ObeliskTemperingDirectionPoolResolver"),
                "menu must not aggregate direction pools directly");
        assertFalse(menu.contains("ObeliskTemperingPreviewResolver"),
                "menu must not build affix previews directly");
        assertFalse(menu.contains("ObeliskTemperingRoller.temper"),
                "menu must not execute tempering rolls directly");
        assertFalse(menu.contains("Loaded tempering recipe count"),
                "per-rebuild debug recipe logging should be removed");
        assertFalse(menu.contains("Tempering candidate"),
                "per-rebuild candidate debug logging should be removed");
        assertTrue(menu.contains("TemperingResolver.resolve"),
                "menu should invoke the resolver for state rebuilds");
        assertTrue(menu.contains("TemperingTransaction.execute"),
                "menu should invoke the transaction for server execution");
    }

    private static void testResolverOwnsResolutionOnly() throws IOException {
        String resolver = read("src/main/java/io/github/naimjeg/obeliskdepths/tempering/TemperingResolver.java");

        assertTrue(resolver.contains("ObeliskTemperingRecipeResolver.findBaseMatches"),
                "resolver owns recipe matching");
        assertTrue(resolver.contains("ObeliskTemperingDirectionPoolResolver.resolve"),
                "resolver owns available direction aggregation");
        assertTrue(resolver.contains("ObeliskTemperingPreviewResolver::resolveDirectionPreview"),
                "resolver owns preview construction");
        assertTrue(resolver.contains("requestedDirectionId"),
                "resolver validates the requested active direction");
        assertTrue(resolver.contains("ObeliskTemperingRoller.checkAvailability"),
                "resolver determines whether inputs are actionable");
        assertFalse(resolver.contains("containerId"),
                "resolver must not know menu container ids");
        assertFalse(resolver.contains("PacketDistributor"),
                "resolver must not send packets");
        assertFalse(resolver.contains("Slot"),
                "resolver must not manipulate menu slots");
    }

    private static void testTransactionRevalidatesAndConsumesInputs()
            throws IOException {
        String transaction = read("src/main/java/io/github/naimjeg/obeliskdepths/tempering/TemperingTransaction.java");

        assertOrder(transaction, "TemperingResolver.resolve", "ObeliskTemperingRoller.temper",
                "transaction must revalidate before rolling");
        assertTrue(transaction.contains("resolved.selectedDirectionId().filter(selectedDirectionId::equals).isEmpty()"),
                "transaction must reject stale or forged direction selection");
        assertTrue(transaction.contains("TemperingTemplateItems.isTemperingTemplate"),
                "transaction must validate the template server-side");
        assertTrue(transaction.contains("removeOne(input.weapon())"),
                "transaction must return consumed weapon state");
        assertTrue(transaction.contains("removeOne(input.template())"),
                "transaction must return consumed template state");
        assertTrue(transaction.contains("removeOne(input.ingredient())"),
                "transaction must return consumed ingredient state when present");
        assertFalse(transaction.contains("ResultContainer"),
                "transaction must not manipulate result slots");
        assertFalse(transaction.contains("PacketDistributor"),
                "transaction must not synchronize clients");
    }

    private static void testMenuCommitsOnlyAfterSuccessfulOutputTransfer()
            throws IOException {
        String menu = read("src/main/java/io/github/naimjeg/obeliskdepths/menu/ObeliskTemperingMenu.java");
        String onTake = slice(menu, "private void onTake", "private boolean copyFinalResultIntoTakenStack");
        String quickMove = slice(menu, "if (slotIndex == RESULT_SLOT)", "if (slotIndex >= WEAPON_SLOT");
        String commit = slice(menu, "private void applySuccessfulTransaction", "@Override\n    public ItemStack quickMoveStack");

        assertOrder(onTake, "copyFinalResultIntoTakenStack", "applySuccessfulTransaction",
                "normal take must copy the rolled output before committing consumed inputs");
        assertOrder(quickMove, "moveItemStackTo", "applySuccessfulTransaction",
                "shift-click must move the rolled output before committing consumed inputs");
        assertTrue(onTake.contains("carried.setCount(0)"),
                "failed normal take must clear the preview stack instead of duplicating output");
        assertTrue(commit.contains("transaction.remainingWeapon()"),
                "commit must use transaction-provided remaining weapon state");
        assertTrue(commit.contains("transaction.remainingTemplate()"),
                "commit must use transaction-provided remaining template state");
        assertTrue(commit.contains("transaction.remainingIngredient()"),
                "commit must use transaction-provided remaining ingredient state");
        assertFalse(menu.contains("finalizeTakenResult"),
                "old split validation/finalize helper must be removed");
        assertFalse(menu.contains("shrinkStackInSlot"),
                "menu must not duplicate transaction consumption logic");
    }

    private static void testPayloadCarriesViewStateOnly() throws IOException {
        String payload = read("src/main/java/io/github/naimjeg/obeliskdepths/network/ClientboundTemperingDirectionStatePayload.java");
        String viewState = read("src/main/java/io/github/naimjeg/obeliskdepths/network/TemperingViewState.java");

        assertTrue(payload.contains("TemperingViewState state"),
                "clientbound payload must carry a single immutable view state");
        assertTrue(payload.contains("TemperingViewState.STREAM_CODEC"),
                "payload codec must delegate view-state serialization");
        assertFalse(payload.contains("List<TemperingDirectionView> directions"),
                "payload must not expose individual direction fields");
        assertFalse(payload.contains("List<TemperingAffixPreview> selectedPreviews"),
                "payload must not expose individual preview fields");
        assertTrue(viewState.contains("record TemperingViewState"),
                "view state must be an immutable record");
        assertTrue(viewState.contains("selectedIsAvailable"),
                "view state must normalize invalid selected directions");
    }

    private static void testClientSynchronizationUsesImmutableViewState()
            throws IOException {
        String menu = read("src/main/java/io/github/naimjeg/obeliskdepths/menu/ObeliskTemperingMenu.java");
        String payload = read("src/main/java/io/github/naimjeg/obeliskdepths/network/ClientboundTemperingDirectionStatePayload.java");

        assertTrue(menu.contains("applyTemperingViewStateFromServer"),
                "menu must apply client state through the new view-state API");
        assertTrue(menu.contains("TemperingViewStateFactory.create"),
                "menu must build sync data through the view-state factory");
        assertTrue(payload.contains("menu.applyTemperingViewStateFromServer(payload.state())"),
                "packet handler must pass view state through unchanged");
        assertTrue(slice(menu, "private void onTake", "private boolean copyFinalResultIntoTakenStack")
                        .contains("executeCurrentTransaction"),
                "normal output take must use the authoritative transaction");
        assertTrue(slice(menu, "if (slotIndex == RESULT_SLOT)", "if (slotIndex >= WEAPON_SLOT")
                        .contains("executeCurrentTransaction"),
                "shift-click output take must use the authoritative transaction");
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Unable to slice source between " + start + " and " + end);
        }

        return source.substring(startIndex, endIndex);
    }

    private static void assertOrder(
            String source,
            String first,
            String second,
            String message
    ) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
            throw new AssertionError(message + ": expected '" + first + "' before '" + second + "'");
        }
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
