package com.example.twosecondstofindout;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * From Android 15 apps are drawn behind the status and navigation bars, so every screen
 * pads its content by the bars (and by the keyboard while it is open).
 */
final class SystemBars {

    private SystemBars() {
    }

    /** Call in onCreate, before setContentView. */
    static void setUp(ComponentActivity activity) {
        // light or dark bar icons, following the dark mode of the phone
        EdgeToEdge.enable(activity);

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
