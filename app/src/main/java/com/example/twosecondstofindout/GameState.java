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

    private final SharedPreferences prefs;

    public GameState(Context context)
    {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void start(int rounds)
    {
        prefs.edit().putInt(KEY_ROUNDS, rounds).putBoolean(KEY_IN_PROGRESS, true).apply();
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
}
