package com.calismazamanlayici.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class StudyDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "calisma_zamanlayici_takvim.db";
    private static final int DB_VERSION = 3;

    public static final String TABLE_SESSIONS = "study_sessions";
    public static final String COL_ID = "_id";
    public static final String COL_DATE = "study_date"; // YYYY-MM-DD
    public static final String COL_TIME = "study_time"; // HH:mm
    public static final String COL_MODE = "study_mode"; // "Ödev", "Test Çözümü", "Kitap Okuma"
    public static final String COL_SUBJECT = "subject_name";
    public static final String COL_DURATION = "duration_seconds";

    private static final String PREFS_NAME = "CalismaZamanlayiciPrefs";
    private static final String KEY_ONBOARDED = "user_onboarded";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_GRADE_LEVEL = "user_grade_level"; // 1..8
    private static final String KEY_THEME_COLOR = "theme_color_key";
    private static final String KEY_CARRIED_SECONDS = "carried_cycle_seconds";
    private static final String KEY_CARRIED_DATE = "carried_cycle_date";
    private static final String KEY_LAST_SUBJECT = "last_studied_subject";
    private static final String KEY_LAST_MODE = "last_studied_mode";
    private static final String KEY_GITHUB_REPO = "custom_github_repo";
    private static final String KEY_CHILD_DEVICE_ID = "child_unique_device_id";
    private static final String KEY_SUBJECTS_PREFIX = "custom_subjects_grade_";
    private static final String KEY_PARENT_IP_PREFIX = "paired_parent_ip_"; // ANNE or BABA

    public static final int FULL_STUDY_CYCLE_SECONDS = 40 * 60; // 40 Dakika
    public static final int FULL_BREAK_CYCLE_SECONDS = 10 * 60; // 10 Dakika
    public static final int BREAK_WARNING_REMAINING_SECONDS = 60;

    public static final String MODE_HOMEWORK = "Ödev";
    public static final String MODE_TEST = "Test Çözümü";
    public static final String MODE_BOOK = "Kitap Okuma";

    // Varsayılan 4. Sınıf Dersleri (Matematik'ten hemen sonra Fen Bilimleri dahil)
    public static final String[] DEFAULT_FOURTH_GRADE_SUBJECTS = new String[]{
            "Türkçe",
            "Matematik",
            "Fen Bilimleri",
            "Sosyal Bilgiler",
            "İngilizce",
            "Din Kültürü",
            "Trafik Güvenliği",
            "İnsan Hakları"
    };

    public static final String[] GRADE_LABELS = new String[]{
            "1. Sınıf", "2. Sınıf", "3. Sınıf", "4. Sınıf",
            "5. Sınıf", "6. Sınıf", "7. Sınıf", "8. Sınıf"
    };

    private static final String[] RANDOM_WELCOME_WISHES = new String[]{
            "Sana derslerinde üstün başarılar dilerim, bugün harika işler başaracaksın!",
            "Zihin açıklığı ve bol başarılı bir gün seninle olsun!",
            "Hedeflerine adım adım ilerlediğin çok başarılı bir gün dilerim!",
            "Çalışmalarında kolaylıklar ve nice güzel başarılar dilerim!",
            "Azminle ve çalışkanlığınla parlayacağın harika bir gün olsun!"
    };

    public static class SessionRecord {
        public long id;
        public String date;
        public String time;
        public String mode;
        public String subject;
        public int durationSeconds;
    }

    public static class WeeklyStats {
        public int totalHomeworkCount;
        public int totalTestCount;
        public int totalBookCount;
        public int totalHomeworkSeconds;
        public int totalTestSeconds;
        public int totalBookSeconds;
        public int activeDaysCount;
        public int distinctSubjectsCount;

        public int getTotalSeconds() {
            return totalHomeworkSeconds + totalTestSeconds + totalBookSeconds;
        }

        public int getTotalSessionsCount() {
            return totalHomeworkCount + totalTestCount + totalBookCount;
        }
    }

    public StudyDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_SESSIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_DATE + " TEXT NOT NULL, "
                + COL_TIME + " TEXT NOT NULL, "
                + COL_MODE + " TEXT NOT NULL, "
                + COL_SUBJECT + " TEXT NOT NULL, "
                + COL_DURATION + " INTEGER NOT NULL)";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static String getChildDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_CHILD_DEVICE_ID, "");
        if (id == null || id.isEmpty()) {
            id = "CZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.US);
            prefs.edit().putString(KEY_CHILD_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static void setPairedParentIp(Context context, String role, String ip) {
        if (role == null || ip == null || ip.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PARENT_IP_PREFIX + role.toUpperCase(Locale.US), ip.trim()).apply();
    }

    public static String getPairedParentIp(Context context, String role) {
        if (role == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PARENT_IP_PREFIX + role.toUpperCase(Locale.US), "");
    }

    public static boolean isUserOnboarded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ONBOARDED, false);
    }

    public static void saveOnboardingProfile(Context context, String userName, int gradeLevel, boolean isGirl) {
        String cleanName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "Öğrenci";
        if (gradeLevel < 1 || gradeLevel > 8) gradeLevel = 4;
        String gender = isGirl ? "GIRL" : "BOY";
        String defaultTheme = isGirl ? ThemeHelper.THEME_PINK : ThemeHelper.THEME_BLUE;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_USER_NAME, cleanName)
                .putInt(KEY_GRADE_LEVEL, gradeLevel)
                .putString(KEY_USER_GENDER, gender)
                .putString(KEY_THEME_COLOR, defaultTheme)
                .apply();
    }

    public static String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(KEY_USER_NAME, "");
        if (name == null || name.trim().isEmpty()) {
            return "Öğrenci";
        }
        return name.trim();
    }

    public static void setUserName(Context context, String userName) {
        if (userName == null || userName.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_NAME, userName.trim()).apply();
    }

    public static int getGradeLevel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int g = prefs.getInt(KEY_GRADE_LEVEL, 4);
        if (g < 1 || g > 8) g = 4;
        return g;
    }

    public static void setGradeLevel(Context context, int gradeLevel) {
        if (gradeLevel < 1 || gradeLevel > 8) gradeLevel = 4;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_GRADE_LEVEL, gradeLevel).apply();
    }

    /**
     * Seçili sınıfa ait ders listesini döndürür.
     * 4. sınıfta varsayılan olarak bizim eklediğimiz 8 ders otomatik gelir.
     * Kullanıcı dilediği sınıfta yeni ders ekleyebilir veya silebilir.
     */
    public static List<String> getSubjectsForCurrentGrade(Context context) {
        int grade = getGradeLevel(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = KEY_SUBJECTS_PREFIX + grade;
        String jsonStr = prefs.getString(key, null);

        List<String> list = new ArrayList<>();
        if (jsonStr != null) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) list.add(s);
                }
                return list;
            } catch (Exception ignored) {
            }
        }

        // Eğer 4. sınıf ise varsayılan dersler otomatik gelsin
        if (grade == 4) {
            for (String s : DEFAULT_FOURTH_GRADE_SUBJECTS) {
                list.add(s);
            }
            saveSubjectsForGrade(context, grade, list);
        }
        return list;
    }

    public static void saveSubjectsForGrade(Context context, int grade, List<String> subjects) {
        JSONArray arr = new JSONArray();
        for (String s : subjects) {
            if (s != null && !s.trim().isEmpty()) {
                arr.put(s.trim());
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SUBJECTS_PREFIX + grade, arr.toString()).apply();
    }

    public static void addSubjectToCurrentGrade(Context context, String newSubject) {
        if (newSubject == null || newSubject.trim().isEmpty()) return;
        String clean = newSubject.trim();
        int grade = getGradeLevel(context);
        List<String> current = getSubjectsForCurrentGrade(context);
        for (String existing : current) {
            if (existing.equalsIgnoreCase(clean)) {
                return;
            }
        }
        current.add(clean);
        saveSubjectsForGrade(context, grade, current);
    }

    public static void removeSubjectFromCurrentGrade(Context context, int index) {
        int grade = getGradeLevel(context);
        List<String> current = getSubjectsForCurrentGrade(context);
        if (index >= 0 && index < current.size()) {
            current.remove(index);
            saveSubjectsForGrade(context, grade, current);
        }
    }

    public static String getThemeKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME_COLOR, ThemeHelper.THEME_BLUE);
    }

    public static void setThemeKey(Context context, String themeKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_COLOR, themeKey).apply();
    }

    public static String buildRandomWelcomeAnnouncement(String userName) {
        Random rnd = new Random();
        String wish = RANDOM_WELCOME_WISHES[rnd.nextInt(RANDOM_WELCOME_WISHES.length)];
        return "Hoş geldin " + userName + "! " + wish;
    }

    public static void saveLastStudiedSubject(Context context, String mode, String subject) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAST_MODE, mode)
                .putString(KEY_LAST_SUBJECT, subject)
                .apply();
    }

    public static String getLastStudiedSubject(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SUBJECT, "Türkçe");
    }

    public static String getLastStudiedMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_MODE, MODE_HOMEWORK);
    }

    public static String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String getNowTimeString() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    public void insertSession(String mode, String subject, int durationSeconds) {
        if (durationSeconds <= 0) {
            durationSeconds = 1;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DATE, getTodayDateString());
        cv.put(COL_TIME, getNowTimeString());
        cv.put(COL_MODE, mode);
        cv.put(COL_SUBJECT, subject);
        cv.put(COL_DURATION, durationSeconds);
        db.insert(TABLE_SESSIONS, null, cv);
    }

    public List<SessionRecord> getSessionsForDate(String dateString) {
        List<SessionRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_SESSIONS,
                null,
                COL_DATE + " = ?",
                new String[]{dateString},
                null,
                null,
                COL_ID + " DESC"
        );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                SessionRecord rec = new SessionRecord();
                rec.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                rec.date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                rec.time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
                rec.mode = cursor.getString(cursor.getColumnIndexOrThrow(COL_MODE));
                rec.subject = cursor.getString(cursor.getColumnIndexOrThrow(COL_SUBJECT));
                rec.durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DURATION));
                list.add(rec);
            }
            cursor.close();
        }
        return list;
    }

    public List<SessionRecord> getRecentSessions(int limit) {
        List<SessionRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_SESSIONS,
                null,
                null,
                null,
                null,
                null,
                COL_ID + " DESC",
                String.valueOf(limit)
        );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                SessionRecord rec = new SessionRecord();
                rec.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                rec.date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                rec.time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
                rec.mode = cursor.getString(cursor.getColumnIndexOrThrow(COL_MODE));
                rec.subject = cursor.getString(cursor.getColumnIndexOrThrow(COL_SUBJECT));
                rec.durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DURATION));
                list.add(rec);
            }
            cursor.close();
        }
        return list;
    }

    public int getTotalSecondsForDate(String dateString) {
        List<SessionRecord> records = getSessionsForDate(dateString);
        int sum = 0;
        for (SessionRecord r : records) {
            sum += r.durationSeconds;
        }
        return sum;
    }

    public WeeklyStats getWeeklyStats() {
        WeeklyStats stats = new WeeklyStats();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String endDate = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -6);
        String startDate = sdf.format(cal.getTime());

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_SESSIONS,
                null,
                COL_DATE + " >= ? AND " + COL_DATE + " <= ?",
                new String[]{startDate, endDate},
                null,
                null,
                null
        );

        Set<String> activeDays = new HashSet<>();
        Set<String> subjects = new HashSet<>();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                String mode = cursor.getString(cursor.getColumnIndexOrThrow(COL_MODE));
                String subject = cursor.getString(cursor.getColumnIndexOrThrow(COL_SUBJECT));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DURATION));

                activeDays.add(date);
                subjects.add(subject);

                if (MODE_TEST.equals(mode)) {
                    stats.totalTestCount++;
                    stats.totalTestSeconds += duration;
                } else if (MODE_BOOK.equals(mode)) {
                    stats.totalBookCount++;
                    stats.totalBookSeconds += duration;
                } else {
                    stats.totalHomeworkCount++;
                    stats.totalHomeworkSeconds += duration;
                }
            }
            cursor.close();
        }
        stats.activeDaysCount = activeDays.size();
        stats.distinctSubjectsCount = subjects.size();
        return stats;
    }

    public static int getCarriedCycleSeconds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDate = prefs.getString(KEY_CARRIED_DATE, "");
        String today = getTodayDateString();
        if (!today.equals(savedDate)) {
            prefs.edit()
                    .putString(KEY_CARRIED_DATE, today)
                    .putInt(KEY_CARRIED_SECONDS, 0)
                    .apply();
            return 0;
        }
        int carried = prefs.getInt(KEY_CARRIED_SECONDS, 0);
        if (carried < 0 || carried >= FULL_STUDY_CYCLE_SECONDS) {
            return 0;
        }
        return carried;
    }

    public static void setCarriedCycleSeconds(Context context, int seconds) {
        int normalized = seconds;
        if (normalized < 0 || normalized >= FULL_STUDY_CYCLE_SECONDS) {
            normalized = 0;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_CARRIED_DATE, getTodayDateString())
                .putInt(KEY_CARRIED_SECONDS, normalized)
                .apply();
    }

    public static void resetCarriedCycleSeconds(Context context) {
        setCarriedCycleSeconds(context, 0);
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

    public static String formatDurationTr(int totalSeconds) {
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        if (mins > 0 && secs > 0) {
            return mins + " dk " + secs + " sn";
        } else if (mins > 0) {
            return mins + " dakika";
        } else {
            return secs + " saniye";
        }
    }

    public static String formatClock(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }
}
