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
    public void playersAskIsKept() {
        assertFalse(new GameState(context).isPlayersAsk());
        new GameState(context).setPlayersAsk(true);
        assertTrue(new GameState(context).isPlayersAsk());
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

    @Test
    public void beepAndVibrateByDefaultAndChoiceIsKept() {
        GameState gameState = new GameState(context);
        assertTrue(gameState.isBeeping());
        assertTrue(gameState.isVibrating());

        gameState.setSignals(false, true);

        GameState later = new GameState(context);
        assertFalse(later.isBeeping());
        assertTrue(later.isVibrating());
    }

    @Test
    public void roundsFlashAndPassesByDefault() {
        GameState gameState = new GameState(context);
        assertFalse(gameState.isByPoints());
        assertEquals(GameState.DEFAULT_TARGET_POINTS, gameState.getTargetPoints());
        assertFalse(gameState.isFlash());
        assertEquals(0, gameState.getPasses());
    }

    @Test
    public void goalFlashAndPassesAreKept() {
        GameState gameState = new GameState(context);
        gameState.setGoal(true, 15);
        gameState.setFlash(true);
        gameState.setPasses(2);

        GameState later = new GameState(context);
        assertTrue(later.isByPoints());
        assertEquals(15, later.getTargetPoints());
        assertTrue(later.isFlash());
        assertEquals(2, later.getPasses());
    }

    @Test
    public void aNewGameDoesNotStartWithAFlashRound() {
        GameState gameState = new GameState(context);
        gameState.setFlashPending(true);
        gameState.start(3, true);
        assertFalse(new GameState(context).isFlashPending());
    }

    @Test
    public void anybodyCanAnswerIsKept() {
        assertFalse(new GameState(context).isFlashOnly());
        new GameState(context).setFlashOnly(true);
        assertTrue(new GameState(context).isFlashOnly());
    }
}
