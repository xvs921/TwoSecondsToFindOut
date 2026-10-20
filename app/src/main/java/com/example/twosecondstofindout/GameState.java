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
    private static final String KEY_PASSES = "passes";
    private static final String KEY_FLASH_ONLY = "flashOnly";
    private static final String KEY_HELP_SHOWN = "helpShown";
    private static final String KEY_BACKUP_REMINDED_AT = "backupRemindedAt";

    // after this many finished games the main menu asks if everything should be saved to a file
    public static final int GAMES_BETWEEN_BACKUP_REMINDERS = 10;

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

    // the „Hogyan játsszunk?” help is shown by itself only at the first start
    public boolean isHelpShown()
    {
        return prefs.getBoolean(KEY_HELP_SHOWN, false);
    }

    public void setHelpShown()
    {
        prefs.edit().putBoolean(KEY_HELP_SHOWN, true).apply();
    }

    // true if GAMES_BETWEEN_BACKUP_REMINDERS games were finished since the last save or reminder.
    // finishedGames: all the finished games in the statistics.
    public boolean isBackupDue(int finishedGames)
    {
        int remindedAt = prefs.getInt(KEY_BACKUP_REMINDED_AT, 0);
        if (finishedGames < remindedAt)
        {
            // the statistics were deleted, counting starts again
            setBackupReminded(finishedGames);
            return false;
        }
        return finishedGames - remindedAt >= GAMES_BETWEEN_BACKUP_REMINDERS;
    }

    // after a save, or when the user said later
    public void setBackupReminded(int finishedGames)
    {
        prefs.edit().putInt(KEY_BACKUP_REMINDED_AT, finishedGames).apply();
    }
}
