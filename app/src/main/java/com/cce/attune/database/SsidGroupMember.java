package com.cce.attune.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/**
 * One Bluetooth device belonging to a {@link SsidGroup}.
 * Identified by its MAC address; display name stored for UI convenience.
 */
@Entity(
    tableName = "ssid_group_members",
    foreignKeys = @ForeignKey(
        entity = SsidGroup.class,
        parentColumns = "id",
        childColumns = "groupId",
        onDelete = ForeignKey.CASCADE
    )
)
public class SsidGroupMember {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** FK → ssid_groups.id */
    public int groupId;

    /** Bluetooth MAC address, e.g. "AA:BB:CC:DD:EE:FF".
     *  For manually-typed entries without a real scan, use the name as the address. */
    public String deviceAddress;

    /** Human-readable Bluetooth device name. May be empty if unknown. */
    public String deviceName;

    public SsidGroupMember() {}

    public SsidGroupMember(int groupId, String deviceAddress, String deviceName) {
        this.groupId = groupId;
        this.deviceAddress = deviceAddress;
        this.deviceName = deviceName;
    }
}
