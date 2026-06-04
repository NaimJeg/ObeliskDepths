package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import io.github.naimjeg.obeliskdepths.network.SelectTemperingDirectionPayload;
import io.github.naimjeg.obeliskdepths.network.TemperingDirectionView;
import io.github.naimjeg.obeliskdepths.tempering.TemperingAffixPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public class ObeliskTemperingScreen
        extends AbstractContainerScreen<ObeliskTemperingMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "textures/gui/container/obelisk_tempering.png"
            );

    private static final int DIRECTION_LIST_X = 66;
    private static final int DIRECTION_LIST_Y = 16;
    private static final int DIRECTION_LIST_WIDTH = 74;
    private static final int DIRECTION_LIST_HEIGHT = 44;
    private static final int AFFIX_LIST_X = 182;
    private static final int AFFIX_LIST_Y = 7;
    private static final int AFFIX_LIST_WIDTH = 68;
    private static final int AFFIX_LIST_HEIGHT = 151;

    private static final int CONTENT_X_OFFSET = 2;
    private static final int CONTENT_Y_OFFSET = 2;

    private static final int ENTRY_BOX_X_OFFSET = -5;
    private static final int ENTRY_BOX_Y_OFFSET = -2;
    private static final int ENTRY_BOX_RIGHT_INSET = 1;
    private static final int ENTRY_BOX_BOTTOM_INSET = 1;

    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFB0B0B0;
    private static final int COLOR_ERROR = 0xFFA00000;
    private static final int COLOR_HOVER = 0x553C6A74;

    private DirectionSelectionList directionList;
    private AffixPreviewList affixList;
    private ItemStack lastWeapon = ItemStack.EMPTY;
    private ItemStack lastTemplate = ItemStack.EMPTY;
    private ItemStack lastIngredient = ItemStack.EMPTY;
    private Identifier lastDirection;
    private boolean lastValidRecipe;
    private int lastDirectionStateVersion = -1;

    public ObeliskTemperingScreen(
            ObeliskTemperingMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title, 256, 166);
    }

    @Override
    protected void init() {
        super.init();

        this.directionList = this.addRenderableWidget(new DirectionSelectionList(
                this.minecraft,
                this.leftPos + DIRECTION_LIST_X,
                this.topPos + DIRECTION_LIST_Y,
                DIRECTION_LIST_WIDTH,
                DIRECTION_LIST_HEIGHT
        ));
        this.affixList = this.addRenderableWidget(new AffixPreviewList(
                this.minecraft,
                this.leftPos + AFFIX_LIST_X,
                this.topPos + AFFIX_LIST_Y,
                AFFIX_LIST_WIDTH,
                AFFIX_LIST_HEIGHT
        ));

        this.refreshPreview(true);
    }

    @Override
    protected void containerTick() {
        this.refreshPreview(false);
    }

    private void refreshPreview(boolean force) {
        ItemStack weapon = this.menu
                .getSlot(ObeliskTemperingMenu.WEAPON_SLOT)
                .getItem();
        ItemStack template = this.menu
                .getSlot(ObeliskTemperingMenu.TEMPLATE_SLOT)
                .getItem();
        ItemStack ingredient = this.menu
                .getSlot(ObeliskTemperingMenu.INGREDIENT_SLOT)
                .getItem();
        Identifier direction = this.menu.selectedDirectionId().orElse(null);
        boolean validRecipe = this.menu.hasValidRecipe();
        int directionStateVersion = this.menu.directionStateVersion();

        boolean changed = force
                || !ItemStack.matches(weapon, this.lastWeapon)
                || !ItemStack.matches(template, this.lastTemplate)
                || !ItemStack.matches(ingredient, this.lastIngredient)
                || !Objects.equals(direction, this.lastDirection)
                || validRecipe != this.lastValidRecipe
                || directionStateVersion != this.lastDirectionStateVersion;

        if (!changed) {
            return;
        }

        this.lastWeapon = weapon.copy();
        this.lastTemplate = template.copy();
        this.lastIngredient = ingredient.copy();
        this.lastDirection = direction;
        this.lastValidRecipe = validRecipe;
        this.lastDirectionStateVersion = directionStateVersion;

        if (this.directionList != null) {
            this.directionList.setDirections(
                    this.menu.directionViews(),
                    direction
            );
        }

        if (this.affixList == null) {
            return;
        }

        List<TemperingAffixPreview> previews =
                validRecipe && direction != null
                        ? this.menu.selectedDirectionPreviews()
                        : List.of();

        this.affixList.setPreviews(
                previews,
                validRecipe,
                direction != null
        );
    }

    private void selectDirection(Identifier directionId) {
        if (this.minecraft == null) {
            return;
        }

        ClientPacketListener connection = this.minecraft.getConnection();

        if (connection == null) {
            return;
        }

        connection.send(new SelectTemperingDirectionPayload(
                this.menu.containerId,
                directionId
        ));
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                this.leftPos,
                this.topPos,
                0.0F,
                0.0F,
                this.imageWidth,
                this.imageHeight,
                256,
                256
        );
    }

    @Override
    protected void extractLabels(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY
    ) {
//        graphics.text(
//                this.font,
//                Component.translatable(
//                        "gui.obeliskdepths.tempering.directions"
//                ),
//                DIRECTION_LIST_X,
//                6,
//                0xFF404040,
//                false
//        );

//        graphics.text(
//                this.font,
//                Component.translatable(
//                        "gui.obeliskdepths.tempering.possible_affixes"
//                ),
//                AFFIX_LIST_X,
//                6,
//                0xFF404040,
//                false
//        );

        if (this.menu.hasRecipeError()) {
            graphics.text(
                    this.font,
                    Component.literal("!"),
                    ObeliskTemperingMenu.INGREDIENT_SLOT_X + 8,
                    ObeliskTemperingMenu.INGREDIENT_SLOT_Y + 4,
                    COLOR_ERROR,
                    false
            );
        }
    }

    private final class DirectionSelectionList
            extends ObjectSelectionList<DirectionSelectionList.Entry> {

        private static final int SCROLLBAR_RESERVED_WIDTH = 12;
        private static final int SCROLLBAR_RIGHT_MARGIN = 1;

        private DirectionSelectionList(
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int height
        ) {
            super(minecraft, width, height, y, 12);
            this.updateSizeAndPosition(width, height, x, y);
            this.centerListVertically = false;
        }

        @Override
        protected void extractSelection(
                GuiGraphicsExtractor graphics,
                Entry entry,
                int outlineColor
        ) {
            int boxX = entry.getX() + ENTRY_BOX_X_OFFSET;
            int boxY = entry.getY() + ENTRY_BOX_Y_OFFSET;

            int boxRight = entry.getX()
                    + entry.getWidth()
                    - ENTRY_BOX_RIGHT_INSET;

            int boxBottom = entry.getY()
                    + entry.getHeight()
                    - ENTRY_BOX_BOTTOM_INSET;

            graphics.fill(
                    boxX,
                    boxY,
                    boxRight,
                    boxBottom,
                    outlineColor
            );

            graphics.fill(
                    boxX + 1,
                    boxY + 1,
                    boxRight - 1,
                    boxBottom - 1,
                    0xFF000000
            );
        }

        @Override
        public int getRowWidth() {
            return Math.max(
                    16,
                    this.getWidth() - SCROLLBAR_RESERVED_WIDTH
            );
        }

        @Override
        protected int scrollBarX() {
            return this.getX()
                    + this.getWidth()
                    - SCROLLBAR_RIGHT_MARGIN
                    - SCROLLBAR_WIDTH;
        }

        private boolean handleMouseWheel(
                double mouseX,
                double mouseY,
                double scrollY
        ) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return false;
            }

            if (this.maxScrollAmount() <= 0) {
                return false;
            }

            this.setScrollAmount(
                    this.scrollAmount()
                            - scrollY * this.defaultEntryHeight
            );

            return true;
        }

        private void setDirections(
                List<TemperingDirectionView> directions,
                Identifier selectedDirection
        ) {
            double previousScroll = this.scrollAmount();

            this.replaceEntries(
                    directions.stream()
                            .map(Entry::new)
                            .toList()
            );

            this.setScrollAmount(previousScroll);
            this.setSelectedDirection(selectedDirection);
        }

        private void setSelectedDirection(Identifier direction) {
            this.setSelected(null);

            if (direction == null) {
                return;
            }

            for (Entry entry : this.children()) {
                if (!entry.view.id().equals(direction)) {
                    continue;
                }

                this.setSelected(entry);

                this.scrollToEntry(entry);
                return;
            }
        }

        private final class Entry
                extends ObjectSelectionList.Entry<
                DirectionSelectionList.Entry
                > {

            private final TemperingDirectionView view;

            private Entry(TemperingDirectionView view) {
                this.view = view;
            }

            @Override
            public void extractContent(
                    GuiGraphicsExtractor graphics,
                    int mouseX,
                    int mouseY,
                    boolean hovered,
                    float partialTick
            ) {
                int boxX = this.getX() + ENTRY_BOX_X_OFFSET;
                int boxY = this.getY() + ENTRY_BOX_Y_OFFSET;

                int boxRight = this.getX()
                        + this.getWidth()
                        - ENTRY_BOX_RIGHT_INSET;

                int boxBottom = this.getY()
                        + this.getHeight()
                        - ENTRY_BOX_BOTTOM_INSET;

                if (hovered
                        && DirectionSelectionList.this.getSelected() != this) {
                    graphics.fill(
                            boxX,
                            boxY,
                            boxRight,
                            boxBottom,
                            COLOR_HOVER
                    );
                }

                int textX = boxX + CONTENT_X_OFFSET;

                int textY = boxY
                        + (boxBottom - boxY
                        - ObeliskTemperingScreen.this.font.lineHeight) / 2
                        + CONTENT_Y_OFFSET;

                graphics.text(
                        ObeliskTemperingScreen.this.font,
                        this.view.displayName(),
                        textX,
                        textY,
                        COLOR_TEXT_PRIMARY,
                        false
                );
            }

            @Override
            public boolean mouseClicked(
                    MouseButtonEvent event,
                    boolean doubleClick
            ) {
                if (event.button() != 0) {
                    return false;
                }

                DirectionSelectionList.this.setSelected(this);

                ObeliskTemperingScreen.this.selectDirection(
                        this.view.id()
                );
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.translatable(
                        "narrator.select",
                        this.view.displayName()
                );
            }
        }
    }

    private final class AffixPreviewList
            extends ObjectSelectionList<AffixPreviewList.Entry> {

        private static final int SCROLLBAR_RESERVED_WIDTH = 12;
        private static final int SCROLLBAR_RIGHT_MARGIN = 1;

        private AffixPreviewList(
                Minecraft minecraft,
                int x,
                int y,
                int width,
                int height
        ) {
            super(minecraft, width, height, y, 26);
            this.updateSizeAndPosition(width, height, x, y);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return Math.max(
                    16,
                    this.getWidth() - SCROLLBAR_RESERVED_WIDTH
            );
        }

        @Override
        protected int scrollBarX() {
            return this.getX()
                    + this.getWidth()
                    - SCROLLBAR_RIGHT_MARGIN
                    - SCROLLBAR_WIDTH;
        }

        private boolean handleMouseWheel(
                double mouseX,
                double mouseY,
                double scrollY
        ) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                return false;
            }

            if (this.maxScrollAmount() <= 0) {
                return false;
            }

            this.setScrollAmount(
                    this.scrollAmount()
                            - scrollY * this.defaultEntryHeight
            );

            return true;
        }

        private void setPreviews(
                List<TemperingAffixPreview> previews,
                boolean validRecipe,
                boolean previewAvailable
        ) {
            double previousScroll = this.scrollAmount();
            List<Entry> entries;

            if (!validRecipe) {
                entries = List.of();
            } else if (!previewAvailable) {
                entries = List.of(this.messageEntry(
                        Component.translatable(
                                "gui.obeliskdepths.tempering.preview_unavailable"
                        )
                ));
            } else if (previews.isEmpty()) {
                entries = List.of(this.messageEntry(
                        Component.translatable(
                                "gui.obeliskdepths.tempering.no_possible_affixes"
                        )
                ));
            } else {
                entries = previews.stream()
                        .map(this::previewEntry)
                        .toList();
            }

            this.replaceEntries(entries);

            this.setScrollAmount(previousScroll);
        }

        private Entry messageEntry(Component message) {
            return new Entry(
                    message,
                    Component.empty(),
                    0,
                    true
            );
        }

        private Entry previewEntry(TemperingAffixPreview preview) {
            return new Entry(
                    preview.displayName(),
                    preview.description(),
                    preview.weight(),
                    false
            );
        }

        private final class Entry
                extends ObjectSelectionList.Entry<
                AffixPreviewList.Entry
                > {

            private final Component name;
            private final Component description;
            private final int weight;
            private final boolean message;

            private Entry(
                    Component name,
                    Component description,
                    int weight,
                    boolean message
            ) {
                this.name = name;
                this.description = description;
                this.weight = weight;
                this.message = message;
            }

            @Override
            public void extractContent(
                    GuiGraphicsExtractor graphics,
                    int mouseX,
                    int mouseY,
                    boolean hovered,
                    float partialTick
            ) {
                int boxX = this.getX() + ENTRY_BOX_X_OFFSET;
                int boxY = this.getY() + ENTRY_BOX_Y_OFFSET;

                int boxRight = this.getX()
                        + this.getWidth()
                        - ENTRY_BOX_RIGHT_INSET;

                int boxBottom = this.getY()
                        + this.getHeight()
                        - ENTRY_BOX_BOTTOM_INSET;

                if (hovered && !this.message) {
                    graphics.fill(
                            boxX,
                            boxY,
                            boxRight,
                            boxBottom,
                            COLOR_HOVER
                    );
                }

                int contentX = boxX + CONTENT_X_OFFSET;
                int contentY = boxY + CONTENT_Y_OFFSET;

                graphics.text(
                        ObeliskTemperingScreen.this.font,
                        this.name,
                        contentX,
                        contentY,
                        this.message
                                ? COLOR_TEXT_SECONDARY
                                : COLOR_TEXT_PRIMARY,
                        false
                );

                if (this.message) {
                    return;
                }

                Component detail =
                        this.description.getString().isBlank()
                                ? Component.literal("Weight: " + this.weight)
                                : this.description;

                graphics.text(
                        ObeliskTemperingScreen.this.font,
                        detail,
                        contentX,
                        contentY + 10,
                        COLOR_TEXT_SECONDARY,
                        false
                );
            }

            @Override
            public boolean mouseClicked(
                    MouseButtonEvent event,
                    boolean doubleClick
            ) {
                return false;
            }

            @Override
            public Component getNarration() {
                return this.name;
            }
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (this.directionList != null
                && this.directionList.handleMouseWheel(
                mouseX,
                mouseY,
                scrollY
        )) {
            return true;
        }

        if (this.affixList != null
                && this.affixList.handleMouseWheel(
                mouseX,
                mouseY,
                scrollY
        )) {
            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }
}
