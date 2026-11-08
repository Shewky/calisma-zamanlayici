package com.calismazamanlayici.veli;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * Veli uygulaması için Gümüş Tonları dahil 8 farklı renk teması sunar.
 */
public class VeliThemeHelper {

    public static final String THEME_NAVY_EMERALD = "NAVY_EMERALD";
    public static final String THEME_SILVER = "SILVER";
    public static final String THEME_ANTHRACITE = "ANTHRACITE";
    public static final String THEME_FOREST = "FOREST";
    public static final String THEME_BURGUNDY = "BURGUNDY";
    public static final String THEME_ESPRESSO = "ESPRESSO";
    public static final String THEME_ROYAL_INDIGO = "ROYAL_INDIGO";
    public static final String THEME_SLATE_STEEL = "SLATE_STEEL";

    public static final String[] ALL_THEME_KEYS = new String[]{
            THEME_NAVY_EMERALD,
            THEME_SILVER,
            THEME_ANTHRACITE,
            THEME_FOREST,
            THEME_BURGUNDY,
            THEME_ESPRESSO,
            THEME_ROYAL_INDIGO,
            THEME_SLATE_STEEL
    };

    public static final String[] ALL_THEME_TITLES = new String[]{
            "👔 Klasik Lacivert & Zümrüt (Varsayılan)",
            "🥈 Gümüş Tonları (Metalik Gümüş)",
            "🌑 Antrasit & Karbon Gri",
            "🌲 Koyu Orman Yeşili & Haki",
            "🍷 Bordo & Mürdüm Klasik",
            "☕ Kahve & Bronz Toprak",
            "🏛️ Kraliyet İndigo & Gece Mavisi",
            "🪨 Arduvaz & Çelik Mavisi"
    };

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
        String theme = VeliStorage.getThemeKey(context);
        if (THEME_SILVER.equals(theme)) {
            // Gümüş Tonları (Parlak Metalik Gümüş / Platin Gri)
            return new Palette(0xFF78909C, 0xFF546E7A, 0xFF90A4AE, 0xFF607D8B, 0xFFF4F6F8);
        } else if (THEME_ANTHRACITE.equals(theme)) {
            return new Palette(0xFF212121, 0xFF000000, 0xFF424242, 0xFF616161, 0xFFE5E7EB);
        } else if (THEME_FOREST.equals(theme)) {
            return new Palette(0xFF1B5E20, 0xFF0D3B10, 0xFF2E7D32, 0xFF558B2F, 0xFFF1F8E9);
        } else if (THEME_BURGUNDY.equals(theme)) {
            return new Palette(0xFF7B1FA2, 0xFF4A148C, 0xFF880E4F, 0xFFAD1457, 0xFFFCE4EC);
        } else if (THEME_ESPRESSO.equals(theme)) {
            return new Palette(0xFF4E342E, 0xFF3E2723, 0xFF6D4C41, 0xFF8D6E63, 0xFFEFEBE9);
        } else if (THEME_ROYAL_INDIGO.equals(theme)) {
            return new Palette(0xFF1A237E, 0xFF0D1247, 0xFF283593, 0xFF0277BD, 0xFFE8EAF6);
        } else if (THEME_SLATE_STEEL.equals(theme)) {
            return new Palette(0xFF334155, 0xFF1E293B, 0xFF475569, 0xFF0369A1, 0xFFF1F5F9);
        } else {
            return new Palette(0xFF0D47A1, 0xFF002171, 0xFF1565C0, 0xFF00897B, 0xFFF1F5F9);
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

    public static GradientDrawable createHeaderDrawable(Context context) {
        Palette p = getPalette(context);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{p.primary, p.secondary}
        );
        float radius = 20f * context.getResources().getDisplayMetrics().density;
        gd.setCornerRadius(radius);
        return gd;
    }

    public static GradientDrawable createButtonDrawable(Context context, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        float radius = 16f * context.getResources().getDisplayMetrics().density;
        gd.setCornerRadius(radius);
        return gd;
    }
}
