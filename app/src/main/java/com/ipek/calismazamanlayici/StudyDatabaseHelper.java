package com.ipek.calismazamanlayici;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StudyDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "ipek_calisma_takvimi.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_SESSIONS = "study_sessions";
    public static final String COL_ID = "_id";
    public static final String COL_DATE = "study_date"; // YYYY-MM-DD
    public static final String COL_TIME = "study_time"; // HH:mm
    public static final String COL_MODE = "study_mode"; // "Ödev" veya "Test Çözümü"
    public static final String COL_SUBJECT = "subject_name";
    public static final String COL_DURATION = "duration_seconds";

    private static final String PREFS_NAME = "IpekCycleMemoryPrefs";
    private static final String KEY_CARRIED_SECONDS = "carried_cycle_seconds";
    private static final String KEY_CARRIED_DATE = "carried_cycle_date";
    private static final String KEY_GITHUB_REPO = "custom_github_repo";

    public static final int FULL_STUDY_CYCLE_SECONDS = 40 * 60; // 40 Dakika
    public static final int FULL_BREAK_CYCLE_SECONDS = 10 * 60; // 10 Dakika
    public static final int BREAK_WARNING_REMAINING_SECONDS = 60; // Son 1 dakika kala "Mola bitmek üzere" uyarısı

    public static final String MODE_HOMEWORK = "Ödev";
    public static final String MODE_TEST = "Test Çözümü";

    public static final String[] FOURTH_GRADE_SUBJECTS = new String[]{
            "Türkçe",
            "Matematik",
            "Sosyal Bilgiler",
            "İngilizce",
            "Din Kültürü",
            "Trafik Güvenliği",
            "İnsan Hakları"
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
        public int totalHomeworkSeconds;
        public int totalTestSeconds;
        public int activeDaysCount;
        public int distinctSubjectsCount;

        public int getTotalSeconds() {
            return totalHomeworkSeconds + totalTestSeconds;
        }

        public int getTotalSessionsCount() {
            return totalHomeworkCount + totalTestCount;
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
        // Güncellemelerde İpek'in takvim ve ödül verileri korunur.
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

    /**
     * Moladan sonraki veya mevcut 40 dakikalık döngü içindeki birikmiş çalışma süresini hafızada tutar.
     * Yeni derse geçildiğinde 40 dakikadan bu süre otomatik düşülür.
     */
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
