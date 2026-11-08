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
        return root;
    }

    public static void restoreFromVeliBackupJson(Context context, JSONObject root) throws Exception {
        String role = root.optString("parentRole", "ANNE");
        String theme = root.optString("themeKey", VeliThemeHelper.THEME_NAVY_EMERALD);
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
}
