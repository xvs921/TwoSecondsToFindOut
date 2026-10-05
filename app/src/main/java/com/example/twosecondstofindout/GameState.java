package com.example.twosecondstofindout;

import android.content.Context;
import android.content.SharedPreferences;

// The settings of the running game, so it can be continued after the app was closed.
// The scores themselves are in the database.
public class GameState
{
    private static final String PREFS_NAME = "game";
    private static final String KEY_ROUNDS = "rounds";
    private static final String KEY_IN_PROGRESS = "inProgress";
    private static final String KEY_PHONE_READS = "phoneReads";
    private static final String KEY_BEEP = "beep";
    private static final String KEY_VIBRATE = "vibrate";
    private static final String KEY_STREAK_BONUS = "streakBonus";

    private final SharedPreferences prefs;

    public GameState(Context context)
    {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // phoneReads: the phone reads the questions aloud, otherwise the game master does
    public void start(int rounds, boolean phoneReads)
    {
        prefs.edit()
                .putInt(KEY_ROUNDS, rounds)
                .putBoolean(KEY_PHONE_READS, phoneReads)
                .putBoolean(KEY_IN_PROGRESS, true)
                .apply();
    }

    public void setInProgress(boolean inProgress)
    {
        prefs.edit().putBoolean(KEY_IN_PROGRESS, inProgress).apply();
    }

    public boolean isInProgress()
    {
        return prefs.getBoolean(KEY_IN_PROGRESS, false);
    }

    public int getRounds()
    {
        return prefs.getInt(KEY_ROUNDS, 1);
    }

    // kept after the game, so the next game offers the same choice
    public boolean isPhoneReading()
    {
        return prefs.getBoolean(KEY_PHONE_READS, true);
    }

    // how the phone signals that the 2 seconds are over, kept for the next games too
    public void setSignals(boolean beep, boolean vibrate)
    {
        prefs.edit().putBoolean(KEY_BEEP, beep).putBoolean(KEY_VIBRATE, vibrate).apply();
    }

    public boolean isBeeping()
    {
        return prefs.getBoolean(KEY_BEEP, true);
    }

    public boolean isVibrating()
    {
        return prefs.getBoolean(KEY_VIBRATE, true);
    }

    // every 3rd correct answer in a row is worth an extra point, kept for the next games too
    public void setStreakBonus(boolean streakBonus)
    {
        prefs.edit().putBoolean(KEY_STREAK_BONUS, streakBonus).apply();
    }

    public boolean isStreakBonus()
    {
        return prefs.getBoolean(KEY_STREAK_BONUS, true);
    }
}
