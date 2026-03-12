package com.cce.attune.context;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cce.attune.database.AppDatabase;
import com.cce.attune.database.SocialSession;
import com.cce.attune.database.SocialSessionDao;
import com.cce.attune.database.SsidGroup;
import com.cce.attune.database.SsidGroupDao;
import com.cce.attune.database.SsidGroupMember;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Determines if the user is currently in a social interaction.
 *
 * <p>Primary:   User-declared schedule windows (SocialSession).</p>
 * <p>Secondary: Bluetooth device groups — if ALL devices in any configured group
 *               are visible in the most recent BT scan, it counts as social context.</p>
 */
public class SocialContextManager {

    private static final String TAG = "SocialContextManager";

    /** SharedPreferences file written by MonitoringService during BT discovery. */
    public static final String BT_PREFS        = "bt_scan_prefs";
    /** Key holding a Set<String> of MAC addresses seen in the last BT scan. */
    public static final String KEY_BT_SEEN     = "bt_seen_devices";

    private final SocialSessionDao sessionDao;
    private final SsidGroupDao     groupDao;

    public SocialContextManager(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.sessionDao = db.socialSessionDao();
        this.groupDao   = db.ssidGroupDao();
    }

    /** Returns true if there is an active declared social session at the given time. */
    public boolean isSocialWindowActive(long nowMs) {
        try {
            SocialSession active = sessionDao.getActiveSession(nowMs);
            if (active != null) {
                Log.d(TAG, "Active social session: " + active.name);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking social window", e);
        }
        return false;
    }

    public List<SocialSession> getAllSessions()          { return sessionDao.getAllSessions(); }
    public void addSession(SocialSession session)        { sessionDao.insert(session); }
    public void deleteSession(int sessionId)             { sessionDao.deleteById(sessionId); }

    // ── Bluetooth group detection ─────────────────────────────────────────────

    /**
     * Returns true if at least 2 devices from any configured Bluetooth group
     * are present in the most recent BT scan cache stored by MonitoringService.
     */
    public boolean isBluetoothGroupActive(Context context) {
        try {
            List<SsidGroup> groups = groupDao.getAllGroups();
            if (groups.isEmpty()) return false;

            // Read the cached set of recently-seen MAC addresses
            SharedPreferences prefs = context.getSharedPreferences(BT_PREFS, Context.MODE_PRIVATE);
            Set<String> seenMacs = prefs.getStringSet(KEY_BT_SEEN, new HashSet<>());
            if (seenMacs.isEmpty()) return false;

            // Normalise to upper-case for comparison
            Set<String> seenUpper = new HashSet<>();
            for (String mac : seenMacs) seenUpper.add(mac.toUpperCase());

            for (SsidGroup group : groups) {
                List<SsidGroupMember> members = groupDao.getMembersOf(group.id);
                if (members.isEmpty()) continue;

                int matchCount = 0;
                for (SsidGroupMember m : members) {
                    if (seenUpper.contains(m.deviceAddress.toUpperCase())) {
                        matchCount++;
                    }
                }
                // At least 2 devices from the group must be visible nearby
                if (matchCount >= 2 || (members.size() == 1 && matchCount == 1)) {
                    Log.d(TAG, "BT group matched: " + group.name + " (" + matchCount + " devices seen)");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in BT group detection", e);
        }
        return false;
    }
}
