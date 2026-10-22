package com.example.twosecondstofindout;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.ComponentActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

// The screens before them are closed, so the back gesture opens the screen of their Vissza button
@RunWith(RobolectricTestRunner.class)
public class BackGestureTest {

    private Database database;

    @Before
    public void setUp() {
        database = new Database(RuntimeEnvironment.getApplication());
        database.insertPlayersForNewGame(new String[]{"Anna", "Béla"}, new int[]{1, 2});
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void playersGoBackToTheMainMenu() {
        assertGoesBackTo(Robolectric.buildActivity(PlayersActivity.class).setup().get(), MainActivity.class);
    }

    @Test
    public void roundSettingsGoBackToThePlayers() {
        assertGoesBackTo(Robolectric.buildActivity(RoundActivity.class).setup().get(), PlayersActivity.class);
    }

    @Test
    public void resultsGoBackToTheMainMenu() {
        assertGoesBackTo(Robolectric.buildActivity(ResultActivity.class).setup().get(), MainActivity.class);
    }

    private static void assertGoesBackTo(ComponentActivity activity, Class<? extends Activity> expected) {
        activity.getOnBackPressedDispatcher().onBackPressed();
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals(expected.getName(), next.getComponent().getClassName());
        assertTrue(activity.isFinishing());
    }
}
