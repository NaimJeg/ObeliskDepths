/**
 * Data-driven dungeon content foundation.
 *
 * Room definitions describe authored room assets and explicit template-local
 * ports. Themes group weighted room references by semantic room type. Runtime
 * JSON reloads are the source of truth; Java built-in factories exist only to
 * generate those JSON resources. The active debug generator is not yet
 * connected to these definitions, and generated structure-piece metadata
 * remains the persistent generated-world contract.
 */
package io.github.naimjeg.obeliskdepths.dungeon.content;
