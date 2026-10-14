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
    private static final String KEY_PLAYERS_ASK = "playersAsk";
    private static final String KEY_BY_POINTS = "byPoints";
    private static final String KEY_TARGET_POINTS = "targetPoints";
    private static final String KEY_FLASH = "flash";
    private static final String KEY_FLASH_PENDING = "flashPending";
    private static final String KEY_PASSES = "passes";
    private static final String KEY_FLASH_ONLY = "flashOnly";

    // the game is played until somebody has this many points, unless it is set otherwise
    public static final int DEFAULT_TARGET_POINTS = 10;

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
                .putBoolean(KEY_FLASH_PENDING, false)
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

    // there is no game master, the players ask each other and pass the phone on, kept for the next games too
    public void setPlayersAsk(boolean playersAsk)
    {
        prefs.edit().putBoolean(KEY_PLAYERS_ASK, playersAsk).apply();
    }

    public boolean isPlayersAsk()
    {
        return prefs.getBoolean(KEY_PLAYERS_ASK, false);
    }

    // byPoints: the game goes on until somebody has targetPoints at the end of a round,
    // otherwise it is over after the rounds. Both numbers are kept for the next games.
    public void setGoal(boolean byPoints, int targetPoints)
    {
        prefs.edit().putBoolean(KEY_BY_POINTS, byPoints).putInt(KEY_TARGET_POINTS, targetPoints).apply();
    }

    public boolean isByPoints()
    {
        return prefs.getBoolean(KEY_BY_POINTS, false);
    }

    public int getTargetPoints()
    {
        return prefs.getInt(KEY_TARGET_POINTS, DEFAULT_TARGET_POINTS);
    }

    // a question for everybody after every round, kept for the next games too
    public void setFlash(boolean flash)
    {
        prefs.edit().putBoolean(KEY_FLASH, flash).apply();
    }

    public boolean isFlash()
    {
        return prefs.getBoolean(KEY_FLASH, false);
    }

    // the flash round is the next, so it comes back when the game is continued
    public void setFlashPending(boolean flashPending)
    {
        prefs.edit().putBoolean(KEY_FLASH_PENDING, flashPending).apply();
    }

    public boolean isFlashPending()
    {
        return prefs.getBoolean(KEY_FLASH_PENDING, false);
    }

    // how many passes everybody gets in a game (0: none), kept for the next games too
    public void setPasses(int passes)
    {
        prefs.edit().putInt(KEY_PASSES, passes).apply();
    }

    public int getPasses()
    {
        return prefs.getInt(KEY_PASSES, 0);
    }

    // every question is a flash question: whoever answers first gets the point, kept for the next games too
    public void setFlashOnly(boolean flashOnly)
    {
        prefs.edit().putBoolean(KEY_FLASH_ONLY, flashOnly).apply();
    }

    public boolean isFlashOnly()
    {
        return prefs.getBoolean(KEY_FLASH_ONLY, false);
    }
}
