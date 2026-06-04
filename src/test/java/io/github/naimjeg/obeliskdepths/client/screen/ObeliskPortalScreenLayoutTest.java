package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ObeliskPortalScreenLayoutTest {
    private static final Path TEXTURE = Path.of(
            "src/main/resources/assets/obeliskdepths/textures/gui/container/"
                    + "obelisk_portal.png"
    );

    private ObeliskPortalScreenLayoutTest() {
    }

    public static void main(String[] args) throws Exception {
        testTributeSlotFitsTextureRecess();
        testModeButtonsDoNotOverlapTributeSlot();
        testStartButtonDoesNotOverlapTributeSlot();
        testFooterStaysAboveInventory();
        testClippedFooterTextKeepsBeginning();
        testTextureExistsAndHasExpectedDimensions();
    }

    private static void testTributeSlotFitsTextureRecess() {
        check(ObeliskPortalMenu.TRIBUTE_SLOT_X
                        + ObeliskPortalScreen.TRIBUTE_SLOT_RENDER_OFFSET == 76,
                "Tribute item must align with the texture recess horizontally");
        check(ObeliskPortalMenu.TRIBUTE_SLOT_Y
                        + ObeliskPortalScreen.TRIBUTE_SLOT_RENDER_OFFSET == 33,
                "Tribute item must align with the texture recess vertically");
        check(ObeliskPortalScreen.TRIBUTE_SLOT_RENDER_SIZE == 20,
                "Tribute item must fill the 20x20 texture recess");
        check(ObeliskPortalScreen.TRIBUTE_SLOT_SCALE == 1.25F,
                "Tribute item must scale from 16x16 to 20x20");
    }

    private static void testModeButtonsDoNotOverlapTributeSlot() {
        int tributeX1 = ObeliskPortalMenu.TRIBUTE_SLOT_X - 1;
        int tributeY1 = ObeliskPortalMenu.TRIBUTE_SLOT_Y - 1;
        int tributeX2 = ObeliskPortalMenu.TRIBUTE_SLOT_X + 17;
        int tributeY2 = ObeliskPortalMenu.TRIBUTE_SLOT_Y + 17;

        check(!intersects(
                ObeliskPortalScreen.STARTER_BUTTON_X,
                ObeliskPortalScreen.MODE_BUTTON_Y,
                ObeliskPortalScreen.STARTER_BUTTON_X
                        + ObeliskPortalScreen.MODE_BUTTON_WIDTH,
                ObeliskPortalScreen.MODE_BUTTON_Y
                        + ObeliskPortalScreen.MODE_BUTTON_HEIGHT,
                tributeX1,
                tributeY1,
                tributeX2,
                tributeY2
        ), "Starter Only button must not overlap Tribute slot");

        check(!intersects(
                ObeliskPortalScreen.OPEN_BUTTON_X,
                ObeliskPortalScreen.MODE_BUTTON_Y,
                ObeliskPortalScreen.OPEN_BUTTON_X
                        + ObeliskPortalScreen.MODE_BUTTON_WIDTH,
                ObeliskPortalScreen.MODE_BUTTON_Y
                        + ObeliskPortalScreen.MODE_BUTTON_HEIGHT,
                tributeX1,
                tributeY1,
                tributeX2,
                tributeY2
        ), "Open button must not overlap Tribute slot");

        check(ObeliskPortalScreen.OPEN_BUTTON_X
                        >= ObeliskPortalScreen.STARTER_BUTTON_X
                        + ObeliskPortalScreen.MODE_BUTTON_WIDTH,
                "mode buttons must not overlap each other");
        check(ObeliskPortalScreen.TRIBUTE_LABEL_X + 40
                        <= ObeliskPortalMenu.TRIBUTE_SLOT_X - 1,
                "Tribute label must stay left of the Tribute slot");
    }

    private static void testStartButtonDoesNotOverlapTributeSlot() {
        int tributeX1 = ObeliskPortalMenu.TRIBUTE_SLOT_X - 1;
        int tributeY1 = ObeliskPortalMenu.TRIBUTE_SLOT_Y - 1;
        int tributeX2 = ObeliskPortalMenu.TRIBUTE_SLOT_X + 17;
        int tributeY2 = ObeliskPortalMenu.TRIBUTE_SLOT_Y + 17;

        check(!intersects(
                ObeliskPortalScreen.START_BUTTON_X,
                ObeliskPortalScreen.START_BUTTON_Y,
                ObeliskPortalScreen.START_BUTTON_X
                        + ObeliskPortalScreen.START_BUTTON_WIDTH,
                ObeliskPortalScreen.START_BUTTON_Y
                        + ObeliskPortalScreen.START_BUTTON_HEIGHT,
                tributeX1,
                tributeY1,
                tributeX2,
                tributeY2
        ), "Start button must not overlap Tribute slot");
    }

    private static void testFooterStaysAboveInventory() {
        check(ObeliskPortalScreen.STATUS_Y
                        >= ObeliskPortalScreen.START_BUTTON_Y
                        + ObeliskPortalScreen.START_BUTTON_HEIGHT,
                "footer must not overlap Start button");
        check(ObeliskPortalScreen.STATUS_Y + 10
                        < ObeliskPortalScreen.INVENTORY_SLOT_TOP,
                "footer must not overlap player inventory slots");
    }

    private static void testClippedFooterTextKeepsBeginning()
            throws Exception {
        ObeliskPortalScreen screen = allocate(ObeliskPortalScreen.class);
        Font font = allocate(TestFont.class);
        Field fontField = Screen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);

        Method clip = ObeliskPortalScreen.class.getDeclaredMethod(
                "clippedFooterText",
                Component.class
        );
        clip.setAccessible(true);

        String shortText = "Selected: Starter-only portal";
        check(shortText.equals(clip.invoke(screen, Component.literal(shortText))),
                "short footer text remains unchanged");

        String longText = "Preparing entrance chunks: 1234567890 / 1234567890 "
                + "with an intentionally long translated footer message";
        String clipped = (String) clip.invoke(
                screen,
                Component.literal(longText)
        );
        check(clipped.startsWith(longText.substring(0, 24)),
                "long footer text keeps its beginning");
        check(clipped.endsWith("..."),
                "long footer text ends with an ellipsis");
        check(clipped.length() <= ObeliskPortalScreen.STATUS_MAX_WIDTH,
                "clipped footer never exceeds the reserved width");
    }

    private static void testTextureExistsAndHasExpectedDimensions()
            throws Exception {
        check(Files.isRegularFile(TEXTURE),
                "obelisk_portal.png must exist");
        byte[] png = Files.readAllBytes(TEXTURE);
        check(png.length > 24
                        && png[0] == (byte) 0x89
                        && png[1] == 'P'
                        && png[2] == 'N'
                        && png[3] == 'G',
                "obelisk_portal.png must be a PNG");
        int width = readInt(png, 16);
        int height = readInt(png, 20);
        check(width == 256 && height == 256,
                "obelisk_portal.png must be 256x256");
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static boolean intersects(
            int x1,
            int y1,
            int x2,
            int y2,
            int a1,
            int b1,
            int a2,
            int b2
    ) {
        return x1 < a2 && a1 < x2 && y1 < b2 && b1 < y2;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod(
                "allocateInstance",
                Class.class
        );
        return type.cast(allocateInstance.invoke(unsafe, type));
    }

    private static final class TestFont extends Font {
        private TestFont() {
            super((Font.Provider) null);
        }

        @Override
        public int width(String text) {
            return text.length() * 2;
        }

        @Override
        public String plainSubstrByWidth(String text, int maxWidth) {
            int chars = Math.max(0, maxWidth / 2);
            return text.substring(0, Math.min(text.length(), chars));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
