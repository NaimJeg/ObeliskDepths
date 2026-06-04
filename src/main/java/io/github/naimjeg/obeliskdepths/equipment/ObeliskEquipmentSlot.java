package io.github.naimjeg.obeliskdepths.equipment;

/** Equipment positions that ObeliskDepths explicitly supports. */
public enum ObeliskEquipmentSlot {
    WEAPON,
    ARMOR_HEAD,
    ARMOR_CHEST,
    ARMOR_LEGS,
    ARMOR_FEET;

    public boolean armor() {
        return this != WEAPON;
    }
}
