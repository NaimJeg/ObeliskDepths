 package io.github.naimjeg.obeliskdepths.worldgen;
 
 /**
  * Single source of truth for the vertical bounds of the Great Swamp cavern dimension.
  *
  * <p>All worldgen code, runtime placement, and tests that need dimension vertical
  * semantics should read from this profile. This prevents duplicated unexplained literals
  * and guarantees the noise settings, dimension type, tree placement, and dungeon
  * placement agree on the same values.</p>
  */
 public final class GreatSwampCavernProfile {
     /** Dimension minimum Y, aligned with the dimension type. */
     public static final int MIN_Y = -64;
     /** Total dimension height from {@link #MIN_Y}. */
     public static final int HEIGHT = 384;
     /** Exclusive maximum Y. */
     public static final int MAX_Y_EXCLUSIVE = MIN_Y + HEIGHT;
 
     /** Nominal floor of the cavern air pocket. */
     public static final int NOMINAL_FLOOR_Y = -48;
     /** Nominal ceiling of the cavern air pocket. */
     public static final int NOMINAL_CEILING_Y = 96;
 
     /** Total vertical span of the Amphixylon. */
     public static final int TREE_VERTICAL_SPAN = 119;
     /** Blocks kept clear below the tree trunk. */
     public static final int TREE_LOWER_MARGIN = 4;
     /** Blocks kept clear above the tree canopy. */
     public static final int TREE_UPPER_MARGIN = 5;
     /** Tree base is placed one block above the resolved surface. */
     public static final int TREE_SURFACE_OFFSET = 1;
     /** Expected tree base Y when the surface is at {@link #NOMINAL_FLOOR_Y}. */
     public static final int TREE_MIN_Y = NOMINAL_FLOOR_Y + TREE_SURFACE_OFFSET;
     /** Maximum allowed tree base Y so the full tree fits under the cavern ceiling. */
     public static final int TREE_MAX_Y = NOMINAL_CEILING_Y - TREE_UPPER_MARGIN - TREE_VERTICAL_SPAN;
 
     /** Dungeon foundation is placed one block above the resolved surface. */
     public static final int DUNGEON_SURFACE_OFFSET = 1;
     /** Expected dungeon base Y when the surface is at {@link #NOMINAL_FLOOR_Y}. */
     public static final int DUNGEON_BASE_Y = NOMINAL_FLOOR_Y + DUNGEON_SURFACE_OFFSET;
 
     private GreatSwampCavernProfile() {}
 }
