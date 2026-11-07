package com.calismazamanlayici.veli;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Veli uygulamasında Anne/Baba rolünü ve karekodla eşleştirilen birden fazla çocuğun
 * bilgilerini (childId, userName, değişen IP adresi, sınıfı) kalıcı olarak saklar.
 */
public class VeliStorage {

    private static final String PREFS_NAME = "CalismaZamanlayiciVeliPrefs";
    private static final String KEY_ONBOARDED = "veli_onboarded";
    private static final String KEY_PARENT_ROLE = "parent_role"; // "ANNE" veya "BABA"
    private static final String KEY_PAIRED_CHILDREN = "paired_children_json";
    private static final String KEY_GITHUB_REPO = "custom_github_repo";
    private static final String KEY_VELI_THEME = "veli_theme_key";

    public static String getThemeKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_VELI_THEME, VeliThemeHelper.THEME_NAVY_EMERALD);
    }

    public static void setThemeKey(Context context, String themeKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VELI_THEME, themeKey).apply();
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
    }

    public static String getParentRole(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PARENT_ROLE, "ANNE");
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
    }

    /**
     * Karekod okutulduğunda veya IP taraması sonucunda çocuğun bilgilerini ekler/günceller.
     */
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

    /**
     * Sadece eşleşmiş bir çocuğun değişen IP adresini ve güncel kullanıcı ismini otomatik günceller.
     */
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
}
