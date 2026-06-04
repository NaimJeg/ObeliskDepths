package io.github.naimjeg.obeliskdepths.dungeon.site;

public record ResolvedDungeonSite(
        DungeonSite site,
        DungeonSiteProjectionSource source
) {
    public boolean authoritative() {
        return this.source.authoritative();
    }
}