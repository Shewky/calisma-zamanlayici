package com.calismazamanlayici.veli;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VeliStorage {

    private static final String PREFS_NAME = "CalismaZamanlayiciVeliPrefs";
    private static final String KEY_ONBOARDED = "veli_onboarded";
    private static final String KEY_PARENT_ROLE = "parent_role"; // "ANNE" veya "BABA"
    private static final String KEY_PAIRED_CHILDREN = "paired_children_json";
    private static final String KEY_GITHUB_REPO = "custom_github_repo";
    private static final String KEY_VELI_THEME = "veli_theme_key";
    private static final String KEY_OTHER_PARENT_IP = "other_parent_ip";
    private static final String KEY_OTHER_PARENT_LAST_SEEN = "other_parent_last_seen_ms";
    private static final String KEY_LAST_PARENT_MSG = "last_received_parent_message";
    private static final String KEY_CACHED_REPORT_PREFIX = "cached_child_full_report_";

    /**
     * Aynı ağda çocuğun verileri alındığında tüm takvim, haftalık istatistik ve ödül geçmişini
     * Veli telefonunun kalıcı hafızasına kaydeder; böylece ağdan çıkılsa (offline olunsa) bile
     * Veli takvimdeki tüm geçmişi gün gün görebilir!
     */
    public static synchronized void saveCachedChildReport(Context context, String childId, String reportJsonStr) {
        if (childId == null || childId.isEmpty() || reportJsonStr == null || reportJsonStr.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CACHED_REPORT_PREFIX + childId.trim(), reportJsonStr).commit();
    }

    public static JSONObject getCachedChildReport(Context context, String childId) {
        if (childId == null || childId.isEmpty()) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CACHED_REPORT_PREFIX + childId.trim(), null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveLastReceivedParentMessage(Context context, String msg) {
        if (msg == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_PARENT_MSG, msg.trim()).apply();
    }

    public static String getLastReceivedParentMessage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_PARENT_MSG, "");
    }

    public static class PairedChild {
        public String childId;
        public String userName;
        public int gradeLevel;
        public String ipAddress;
        public int port = 45678;
        public String pairedRole;
        public String liveStatusText = "🔍 Ağda aranıyor...";
        public boolean online = false;
    }

    public static boolean isOnboarded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ONBOARDED, false);
    }

    public static void saveParentRole(Context context, String role) {
        String cleanRole = "BABA".equalsIgnoreCase(role) ? "BABA" : "ANNE";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_PARENT_ROLE, cleanRole)
                .apply();
        VeliBackupManager.saveVeliBackupAsync(context);
    }

    public static String getParentRole(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PARENT_ROLE, "ANNE");
    }

    public static String getOtherParentRole(Context context) {
        return "BABA".equalsIgnoreCase(getParentRole(context)) ? "ANNE" : "BABA";
    }

    public static String getOtherParentTitleTr(Context context) {
        return "BABA".equalsIgnoreCase(getParentRole(context)) ? "👩 Anne" : "👨 Baba";
    }

    public static void updateOtherParentSeen(Context context, String ip) {
        if (ip == null || ip.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_OTHER_PARENT_IP, ip.trim())
                .putLong(KEY_OTHER_PARENT_LAST_SEEN, System.currentTimeMillis())
                .apply();
    }

    public static String getOtherParentIp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_OTHER_PARENT_IP, "");
    }

    public static boolean isOtherParentOnline(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastSeen = prefs.getLong(KEY_OTHER_PARENT_LAST_SEEN, 0L);
        return (System.currentTimeMillis() - lastSeen) < 18000L && !getOtherParentIp(context).isEmpty();
    }

    public static String getThemeKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_VELI_THEME, VeliThemeHelper.THEME_NAVY_EMERALD);
    }

    public static void setThemeKey(Context context, String themeKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VELI_THEME, themeKey).apply();
        VeliBackupManager.saveVeliBackupAsync(context);
    }

    public static List<PairedChild> getPairedChildren(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_PAIRED_CHILDREN, "[]");
        List<PairedChild> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                PairedChild c = new PairedChild();
                c.childId = o.optString("childId", "");
                c.userName = o.optString("userName", "Öğrenci");
                c.gradeLevel = o.optInt("gradeLevel", 4);
                c.ipAddress = o.optString("ipAddress", "");
                c.port = o.optInt("port", 45678);
                c.pairedRole = o.optString("pairedRole", getParentRole(context));
                if (!c.childId.isEmpty()) {
                    list.add(c);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static synchronized void savePairedChildren(Context context, List<PairedChild> list) {
        JSONArray arr = new JSONArray();
        try {
            for (PairedChild c : list) {
                JSONObject o = new JSONObject();
                o.put("childId", c.childId);
                o.put("userName", c.userName);
                o.put("gradeLevel", c.gradeLevel);
                o.put("ipAddress", c.ipAddress);
                o.put("port", c.port);
                o.put("pairedRole", c.pairedRole);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PAIRED_CHILDREN, arr.toString()).apply();
        VeliBackupManager.saveVeliBackupAsync(context);
    }

    public static synchronized void upsertPairedChild(
            Context context,
            String childId,
            String userName,
            int gradeLevel,
            String ipAddress,
            String pairedRole
    ) {
        if (childId == null || childId.trim().isEmpty()) return;
        List<PairedChild> list = getPairedChildren(context);
        boolean found = false;
        for (PairedChild existing : list) {
            if (existing.childId.equalsIgnoreCase(childId.trim())) {
                if (userName != null && !userName.trim().isEmpty()) {
                    existing.userName = userName.trim();
                }
                if (gradeLevel >= 1 && gradeLevel <= 8) {
                    existing.gradeLevel = gradeLevel;
                }
                if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                    existing.ipAddress = ipAddress.trim();
                }
                if (pairedRole != null && !pairedRole.trim().isEmpty()) {
                    existing.pairedRole = pairedRole.trim();
                }
                found = true;
                break;
            }
        }
        if (!found) {
            PairedChild c = new PairedChild();
            c.childId = childId.trim();
            c.userName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "Öğrenci";
            c.gradeLevel = (gradeLevel >= 1 && gradeLevel <= 8) ? gradeLevel : 4;
            c.ipAddress = ipAddress != null ? ipAddress.trim() : "";
            c.pairedRole = (pairedRole != null && !pairedRole.trim().isEmpty()) ? pairedRole.trim() : getParentRole(context);
            list.add(c);
        }
        savePairedChildren(context, list);
    }

    public static synchronized boolean updateExistingChildNetworkInfo(
            Context context,
            String childId,
            String currentUserName,
            int gradeLevel,
            String newIp
    ) {
        if (childId == null || childId.isEmpty()) return false;
        List<PairedChild> list = getPairedChildren(context);
        boolean updated = false;
        for (PairedChild c : list) {
            if (c.childId.equalsIgnoreCase(childId.trim())) {
                if (currentUserName != null && !currentUserName.trim().isEmpty()) {
                    c.userName = currentUserName.trim();
                }
                if (gradeLevel >= 1 && gradeLevel <= 8) {
                    c.gradeLevel = gradeLevel;
                }
                if (newIp != null && !newIp.trim().isEmpty()) {
                    c.ipAddress = newIp.trim();
                }
                updated = true;
                break;
            }
        }
        if (updated) {
            savePairedChildren(context, list);
        }
        return updated;
    }

    public static synchronized void removePairedChild(Context context, String childId) {
        List<PairedChild> list = getPairedChildren(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).childId.equalsIgnoreCase(childId)) {
                list.remove(i);
            }
        }
        savePairedChildren(context, list);
    }

    public static String getGitHubRepo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String custom = prefs.getString(KEY_GITHUB_REPO, "");
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        return BuildConfig.DEFAULT_GITHUB_REPO;
    }

    public static void setGitHubRepo(Context context, String repo) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_GITHUB_REPO, repo.trim()).apply();
    }

    public static JSONObject exportFullVeliBackupJson(Context context) throws Exception {
        JSONObject root = new JSONObject();
        String nowStr = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date());
        root.put("backupType", "VELI");
        root.put("backupDate", nowStr);
        root.put("parentRole", getParentRole(context));
        root.put("themeKey", getThemeKey(context));
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String childrenJsonStr = prefs.getString(KEY_PAIRED_CHILDREN, "[]");
        root.put("pairedChildren", new JSONArray(childrenJsonStr));
        root.put("gameProgress", exportGameProgressJson(context));
        return root;
    }

    public static void restoreFromVeliBackupJson(Context context, JSONObject root) throws Exception {
        String role = root.optString("parentRole", "ANNE");
        String theme = root.optString("themeKey", VeliThemeHelper.THEME_NAVY_EMERALD);
        restoreGameProgressJson(context, root.optJSONObject("gameProgress"));
        JSONArray childrenArr = root.optJSONArray("pairedChildren");
        if (childrenArr == null) childrenArr = new JSONArray();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_PARENT_ROLE, role)
                .putString(KEY_VELI_THEME, theme)
                .putString(KEY_PAIRED_CHILDREN, childrenArr.toString())
                .apply();
    }

    // =========================================================================
    // KALICI OYUN SEVİYESİ HAFIZASI VE YEDEKTEN GERİ YÜKLEME SİSTEMİ
    // =========================================================================
    private static final String KEY_GAME_LEVEL_PREFIX = "saved_game_level_";
    private static final String PREFS_TOWER = "BulmacaKulesiSavePrefs";

    public static int getSavedGameLevel(Context context, String gameKey, int defaultLevel) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int lvl = prefs.getInt(KEY_GAME_LEVEL_PREFIX + gameKey, defaultLevel);
        return Math.max(1, lvl);
    }

    public static void saveGameLevel(Context context, String gameKey, int level) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_GAME_LEVEL_PREFIX + gameKey, Math.max(1, level)).apply();
        VeliBackupManager.saveVeliBackupAsync(context);
    }

    public static JSONObject exportGameProgressJson(Context context) {
        JSONObject gp = new JSONObject();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String[] keys = new String[]{
                    "jump_current_stage", "jump_max_stage", "jump_high_score",
                    "car_race_level", "car_race_high_score",
                    "xox_level", "xox_wins",
                    "checkers_level", "checkers_wins",
                    "quiz_level_0", "quiz_level_1", "quiz_level_2", "quiz_level_3",
                    "quiz_level_4", "quiz_level_5", "quiz_level_6", "quiz_level_7"
            };
            for (String k : keys) {
                gp.put(k, prefs.getInt(KEY_GAME_LEVEL_PREFIX + k, 1));
            }
            SharedPreferences towerPrefs = context.getSharedPreferences(PREFS_TOWER, Context.MODE_PRIVATE);
            gp.put("tower_unlockedMaxFloor", towerPrefs.getInt("unlockedMaxFloor", 1));
            gp.put("tower_currentFloor", towerPrefs.getInt("currentFloor", 1));
            gp.put("tower_totalGoldenScrews", towerPrefs.getInt("totalGoldenScrews", 6));
            gp.put("tower_totalCrystalsCollected", towerPrefs.getInt("totalCrystalsCollected", 0));
            gp.put("tower_equippedHat", towerPrefs.getInt("equippedHat", 0));
            gp.put("tower_equippedCape", towerPrefs.getInt("equippedCape", 0));
            gp.put("tower_equippedBag", towerPrefs.getInt("equippedBag", 0));
            gp.put("tower_ownedMask", towerPrefs.getInt("ownedMask", 1));
        } catch (Exception ignored) {
        }
        return gp;
    }

    public static void restoreGameProgressJson(Context context, JSONObject gp) {
        if (gp == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = prefs.edit();
            String[] keys = new String[]{
                    "jump_current_stage", "jump_max_stage", "jump_high_score",
                    "car_race_level", "car_race_high_score",
                    "xox_level", "xox_wins",
                    "checkers_level", "checkers_wins",
                    "quiz_level_0", "quiz_level_1", "quiz_level_2", "quiz_level_3",
                    "quiz_level_4", "quiz_level_5", "quiz_level_6", "quiz_level_7"
            };
            for (String k : keys) {
                if (gp.has(k)) {
                    ed.putInt(KEY_GAME_LEVEL_PREFIX + k, gp.optInt(k, 1));
                }
            }
            ed.apply();

            SharedPreferences towerPrefs = context.getSharedPreferences(PREFS_TOWER, Context.MODE_PRIVATE);
            towerPrefs.edit()
                    .putInt("unlockedMaxFloor", gp.optInt("tower_unlockedMaxFloor", 1))
                    .putInt("currentFloor", gp.optInt("tower_currentFloor", 1))
                    .putInt("totalGoldenScrews", gp.optInt("tower_totalGoldenScrews", 6))
                    .putInt("totalCrystalsCollected", gp.optInt("tower_totalCrystalsCollected", 0))
                    .putInt("equippedHat", gp.optInt("tower_equippedHat", 0))
                    .putInt("equippedCape", gp.optInt("tower_equippedCape", 0))
                    .putInt("equippedBag", gp.optInt("tower_equippedBag", 0))
                    .putInt("ownedMask", gp.optInt("tower_ownedMask", 1))
                    .apply();
        } catch (Exception ignored) {
        }
    }


    private static final String KEY_ALLOW_OTHER_FEATURES_PREFIX = "child_allow_other_features_";
    private static final String KEY_PENDING_BREAK_REQ_CHILD_ID = "pending_break_req_child_id";
    private static final String KEY_PENDING_BREAK_REQ_CHILD_NAME = "pending_break_req_child_name";
    private static final String KEY_PENDING_BREAK_REQ_CHILD_IP = "pending_break_req_child_ip";

    public static boolean isChildAllowedOtherPhoneFeatures(Context context, String childId) {
        if (childId == null || childId.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ALLOW_OTHER_FEATURES_PREFIX + childId.trim(), false);
    }

    public static void setChildAllowedOtherPhoneFeatures(Context context, String childId, boolean allow) {
        if (childId == null || childId.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ALLOW_OTHER_FEATURES_PREFIX + childId.trim(), allow).apply();
        VeliBackupManager.saveVeliBackupAsync(context);
    }

    public static void savePendingBreakPhoneRequest(Context context, String childId, String childName, String childIp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PENDING_BREAK_REQ_CHILD_ID, childId != null ? childId : "")
                .putString(KEY_PENDING_BREAK_REQ_CHILD_NAME, childName != null ? childName : "Öğrenci")
                .putString(KEY_PENDING_BREAK_REQ_CHILD_IP, childIp != null ? childIp : "")
                .apply();
    }

    public static void clearPendingBreakPhoneRequest(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_PENDING_BREAK_REQ_CHILD_ID)
                .remove(KEY_PENDING_BREAK_REQ_CHILD_NAME)
                .remove(KEY_PENDING_BREAK_REQ_CHILD_IP)
                .apply();
    }

    public static String getPendingBreakReqChildId(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PENDING_BREAK_REQ_CHILD_ID, "");
    }

    public static String getPendingBreakReqChildName(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PENDING_BREAK_REQ_CHILD_NAME, "");
    }

    public static String getPendingBreakReqChildIp(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PENDING_BREAK_REQ_CHILD_IP, "");
    }

}
