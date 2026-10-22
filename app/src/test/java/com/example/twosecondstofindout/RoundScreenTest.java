package com.example.twosecondstofindout;

import android.widget.EditText;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertEquals;

// The settings of the game with a fresh database and two players
@RunWith(RobolectricTestRunner.class)
public class RoundScreenTest {

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
    public void theChoicesStayWhenThePhoneIsTurned() {
        ActivityController<RoundActivity> controller = Robolectric.buildActivity(RoundActivity.class).setup();
        controller.get().findViewById(R.id.ButtonGoalPoints).performClick();
        ((EditText) controller.get().findViewById(R.id.RoundNumber)).setText("15");
        controller.get().findViewById(R.id.ButtonReaderPhone).performClick();
        controller.recreate();
        RoundActivity activity = controller.get();
        assertEquals("Hány pontig?", ((TextView) activity.findViewById(R.id.RoundText)).getText().toString());
        assertEquals("15", ((EditText) activity.findViewById(R.id.RoundNumber)).getText().toString());
        // the game starts with the choices made before turning
        activity.findViewById(R.id.ButtonNextRound).performClick();
        GameState gameState = new GameState(activity);
        assertEquals(true, gameState.isByPoints());
        assertEquals(15, gameState.getTargetPoints());
        assertEquals(true, gameState.isPhoneReading());
    }
}
