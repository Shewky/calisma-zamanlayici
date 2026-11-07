package com.calismazamanlayici.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/**
 * Kitap Oku Özelliği:
 * Ekran açıldığında "Hangi kitabı okuyacaksın" diye sesli sorar.
 * Kullanıcı klavyeden kitap ismini girip "Oku" butonuna tıkladığında
 * "[Kitap İsmi] kitabını iyi okumalar" diyerek okuma süresini başlatır ve takvime kaydeder.
 */
public class BookReadActivity extends Activity {

    private EditText etBookTitleInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_read);

        ScrollView scrollRootBook = findViewById(R.id.scrollRootBook);
        LinearLayout layoutBookHeaderCard = findViewById(R.id.layoutBookHeaderCard);
        etBookTitleInput = findViewById(R.id.etBookTitleInput);
        Button btnStartReadingBook = findViewById(R.id.btnStartReadingBook);
        Button btnBackFromBook = findViewById(R.id.btnBackFromBook);

        ThemeHelper.Palette palette = ThemeHelper.getPalette(this);
        ThemeHelper.applyWindowAndSurface(this, scrollRootBook);
        ThemeHelper.styleHeader(this, layoutBookHeaderCard);
        btnStartReadingBook.setBackground(ThemeHelper.createButtonDrawable(this, palette.primary));

        // Ekran açılır açılmaz "Hangi kitabı okuyacaksın" sesli anonsu yapılır
        Intent askIntent = new Intent(this, TimerForegroundService.class);
        askIntent.setAction(TimerForegroundService.ACTION_SPEAK_CUSTOM);
        askIntent.putExtra(TimerForegroundService.EXTRA_CUSTOM_SPEECH, "Hangi kitabı okuyacaksın");
        startService(askIntent);

        btnStartReadingBook.setOnClickListener(v -> {
            String bookName = etBookTitleInput.getText().toString().trim();
            if (bookName.isEmpty()) {
                Toast.makeText(BookReadActivity.this, "Lütfen okuyacağın kitabın ismini yaz 📖", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent startBookIntent = new Intent(BookReadActivity.this, TimerForegroundService.class);
            startBookIntent.setAction(TimerForegroundService.ACTION_START_BOOK_SESSION);
            startBookIntent.putExtra(TimerForegroundService.EXTRA_SUBJECT, bookName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startBookIntent);
            } else {
                startService(startBookIntent);
            }

            Intent timerIntent = new Intent(BookReadActivity.this, TimerActivity.class);
            timerIntent.putExtra(TimerForegroundService.EXTRA_MODE, StudyDatabaseHelper.MODE_BOOK);
            timerIntent.putExtra(TimerForegroundService.EXTRA_SUBJECT, bookName);
            startActivity(timerIntent);
            finish();
        });

        btnBackFromBook.setOnClickListener(v -> finish());
    }
}
