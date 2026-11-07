package com.ipek.calismazamanlayici;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class CalendarActivity extends Activity {

    private TextView tvSelectedDateTitle;
    private TextView tvSelectedDateSummary;
    private LinearLayout layoutDateRecordsList;
    private StudyDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        dbHelper = new StudyDatabaseHelper(this);
        CalendarView calendarView = findViewById(R.id.calendarViewStudy);
        tvSelectedDateTitle = findViewById(R.id.tvSelectedDateTitle);
        tvSelectedDateSummary = findViewById(R.id.tvSelectedDateSummary);
        layoutDateRecordsList = findViewById(R.id.layoutDateRecordsList);
        Button btnBack = findViewById(R.id.btnBackFromCalendar);

        String today = StudyDatabaseHelper.getTodayDateString();
        loadRecordsForDate(today);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String pickedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            loadRecordsForDate(pickedDate);
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadRecordsForDate(String dateString) {
        tvSelectedDateTitle.setText("📌 Seçilen Gün: " + dateString);
        List<StudyDatabaseHelper.SessionRecord> records = dbHelper.getSessionsForDate(dateString);

        int hwSeconds = 0;
        int testSeconds = 0;
        for (StudyDatabaseHelper.SessionRecord r : records) {
            if (StudyDatabaseHelper.MODE_TEST.equals(r.mode)) {
                testSeconds += r.durationSeconds;
            } else {
                hwSeconds += r.durationSeconds;
            }
        }

        tvSelectedDateSummary.setText("📚 Toplam Ödev: " + StudyDatabaseHelper.formatDurationTr(hwSeconds)
                + "  •  📝 Toplam Test: " + StudyDatabaseHelper.formatDurationTr(testSeconds));

        layoutDateRecordsList.removeAllViews();
        int pad = (int) (10 * getResources().getDisplayMetrics().density);

        if (records.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Bu tarihte henüz kaydedilmiş bir ödev veya test çözümü bulunmuyor.");
            emptyTv.setTextColor(0xFF6E5773);
            emptyTv.setTextSize(14f);
            emptyTv.setPadding(0, pad, 0, pad);
            layoutDateRecordsList.addView(emptyTv);
            return;
        }

        for (StudyDatabaseHelper.SessionRecord rec : records) {
            TextView itemTv = new TextView(this);
            String icon = StudyDatabaseHelper.MODE_TEST.equals(rec.mode) ? "📝 [Test Çözümü] " : "📚 [Ödev] ";
            itemTv.setText(icon + rec.subject + " — " + StudyDatabaseHelper.formatDurationTr(rec.durationSeconds)
                    + " (Saat: " + rec.time + ")");
            itemTv.setTextColor(0xFF372549);
            itemTv.setTextSize(15f);
            itemTv.setPadding(0, pad / 2, 0, pad / 2);
            layoutDateRecordsList.addView(itemTv);
        }
    }
}
