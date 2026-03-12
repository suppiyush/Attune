package com.cce.attune.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Represents a named group of Bluetooth devices.
 * When ALL devices in this group are visible in a BT scan,
 * the situation is treated as a social interaction.
 */
@Entity(tableName = "ssid_groups")
public class SsidGroup {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** User-facing label, e.g. "Family dinner", "Office crew" */
    public String name;

    public SsidGroup() {}

    public SsidGroup(String name) {
        this.name = name;
    }
}
