package com.cce.attune.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SsidGroupDao {

    /* ── Groups ── */

    @Insert
    long insertGroup(SsidGroup group);

    @Query("SELECT * FROM ssid_groups ORDER BY name ASC")
    List<SsidGroup> getAllGroups();

    @Query("DELETE FROM ssid_groups WHERE id = :id")
    void deleteGroup(int id);

    /* ── Members ── */

    @Insert
    void insertMember(SsidGroupMember member);

    @Query("SELECT * FROM ssid_group_members WHERE groupId = :groupId")
    List<SsidGroupMember> getMembersOf(int groupId);

    @Query("DELETE FROM ssid_group_members WHERE groupId = :groupId")
    void deleteMembersOf(int groupId);

    @Query("DELETE FROM ssid_group_members WHERE id = :id")
    void deleteMember(int id);

    /** Returns all members across all groups (used for detection). */
    @Query("SELECT * FROM ssid_group_members")
    List<SsidGroupMember> getAllMembers();
}
