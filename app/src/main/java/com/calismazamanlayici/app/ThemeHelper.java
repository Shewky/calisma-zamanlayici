package com.calismazamanlayici.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * Kullanıcının seçtiği temaya göre (Mavi, Pembe, Yeşil, Mor, Turuncu)
 * ekran başlıklarını, butonları ve durum çubuğunu dinamik olarak renklendirir.
 */
public class ThemeHelper {

    public static final String THEME_BLUE = "BLUE";     // Erkek varsayılanı: Mavi tonları
    public static final String THEME_PINK = "PINK";     // Kız varsayılanı: Pembe tonları
    public static final String THEME_GREEN = "GREEN";   // Doğa Yeşili tonları
    public static final String THEME_PURPLE = "PURPLE"; // Galaksi Moru tonları
    public static final String THEME_ORANGE = "ORANGE"; // Enerjik Turuncu tonları

    public static class Palette {
        public final int primary;
        public final int primaryDark;
        public final int secondary;
        public final int accent;
        public final int surfaceBg;

        public Palette(int primary, int primaryDark, int secondary, int accent, int surfaceBg) {
            this.primary = primary;
            this.primaryDark = primaryDark;
            this.secondary = secondary;
            this.accent = accent;
            this.surfaceBg = surfaceBg;
        }
    }

    public static Palette getPalette(Context context) {
        String theme = StudyDatabaseHelper.getThemeKey(context);
        if (THEME_PINK.equals(theme)) {
            return new Palette(0xFFEC407A, 0xFFC2185B, 0xFFAB47BC, 0xFF26A69A, 0xFFFFF5F8);
        } else if (THEME_GREEN.equals(theme)) {
            return new Palette(0xFF2E7D32, 0xFF1B5E20, 0xFF00897B, 0xFFF57C00, 0xFFF1F8E9);
        } else if (THEME_PURPLE.equals(theme)) {
            return new Palette(0xFF673AB7, 0xFF4527A0, 0xFF3949AB, 0xFF00ACC1, 0xFFF3E5F5);
        } else if (THEME_ORANGE.equals(theme)) {
            return new Palette(0xFFEF6C00, 0xFFE65100, 0xFFF57C00, 0xFF00897B, 0xFFFFF8E1);
        } else {
            // Varsayılan Mavi Tonları (THEME_BLUE)
            return new Palette(0xFF1E88E5, 0xFF1565C0, 0xFF039BE5, 0xFF00897B, 0xFFF0F7FF);
        }
    }

    public static void applyWindowAndSurface(Activity activity, View rootScroll) {
        Palette p = getPalette(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = activity.getWindow();
            if (w != null) {
                w.setStatusBarColor(p.primaryDark);
            }
        }
        if (rootScroll != null) {
            rootScroll.setBackgroundColor(p.surfaceBg);
        }
    }

    public static GradientDrawable createHeaderCardDrawable(Context context) {
        Palette p = getPalette(context);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{p.primary, p.secondary}
        );
        float radius = 22f * context.getResources().getDisplayMetrics().density;
        gd.setCornerRadius(radius);
        return gd;
    }

    public static GradientDrawable createButtonDrawable(Context context, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        float radius = 18f * context.getResources().getDisplayMetrics().density;
        gd.setCornerRadius(radius);
        return gd;
    }

    public static GradientDrawable createTimerCircleDrawable(Context context, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(0xFFFFFFFF);
        int strokeWidth = (int) (8f * context.getResources().getDisplayMetrics().density);
        gd.setStroke(strokeWidth, strokeColor);
        return gd;
    }

    public static void styleHeader(Context context, View headerView) {
        if (headerView != null) {
            headerView.setBackground(createHeaderCardDrawable(context));
        }
    }

    public static void stylePrimaryButton(Context context, View btn) {
        if (btn != null) {
            btn.setBackground(createButtonDrawable(context, getPalette(context).primary));
        }
    }

    public static void styleSecondaryButton(Context context, View btn) {
        if (btn != null) {
            btn.setBackground(createButtonDrawable(context, getPalette(context).secondary));
        }
    }

    public static void styleAccentButton(Context context, View btn) {
        if (btn != null) {
            btn.setBackground(createButtonDrawable(context, getPalette(context).accent));
        }
    }
}
