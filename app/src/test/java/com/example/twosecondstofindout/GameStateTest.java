package com.example.twosecondstofindout;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Every test starts with empty preferences
@RunWith(RobolectricTestRunner.class)
public class GameStateTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void phoneReadsByDefault() {
        assertTrue(new GameState(context).isPhoneReading());
    }

    @Test
    public void startKeepsRoundsAndReader() {
        new GameState(context).start(3, false);

        // a new instance, like after the app was closed and the game is continued
        GameState gameState = new GameState(context);
        assertEquals(3, gameState.getRounds());
        assertFalse(gameState.isPhoneReading());
        assertTrue(gameState.isInProgress());
    }

    @Test
    public void readerIsRememberedAfterTheGame() {
        GameState gameState = new GameState(context);
        gameState.start(1, false);
        gameState.setInProgress(false);

        assertFalse(new GameState(context).isPhoneReading());
    }
}
