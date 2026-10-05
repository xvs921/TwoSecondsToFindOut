package com.example.twosecondstofindout;

import android.graphics.Color;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * From Android 15 apps are drawn behind the status and navigation bars, so every screen
 * pads its content by the bars (and by the keyboard while it is open).
 */
final class SystemBars {

    // dark scrim behind the bars on Android versions that cannot draw dark bar icons
    private static final int DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b);

    private SystemBars() {
    }

    /** Call in onCreate, before setContentView. */
    static void setUp(ComponentActivity activity) {
        SystemBarStyle light = SystemBarStyle.light(Color.TRANSPARENT, DARK_SCRIM);
        EdgeToEdge.enable(activity, light, light);

        View content = activity.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
