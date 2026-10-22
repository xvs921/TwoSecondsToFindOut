package com.example.twosecondstofindout;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertEquals;

// The players screen with a fresh database
@RunWith(RobolectricTestRunner.class)
public class PlayersScreenTest {

    private Database database;

    @Before
    public void setUp() {
        database = new Database(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void theTypedNamesStayWhenThePhoneIsTurned() {
        database.insertPlayersForNewGame(new String[]{"Anna", "Béla"}, new int[]{1, 2});
        ActivityController<PlayersActivity> controller = Robolectric.buildActivity(PlayersActivity.class).setup();
        playerName(controller.get(), 1).setText("Bence");
        controller.get().findViewById(R.id.ButtonNewPlayer).performClick();
        playerName(controller.get(), 2).setText("Cili");
        controller.recreate();
        assertEquals("Anna", playerName(controller.get(), 0).getText().toString());
        assertEquals("Bence", playerName(controller.get(), 1).getText().toString());
        assertEquals("Cili", playerName(controller.get(), 2).getText().toString());
        assertEquals(View.VISIBLE, row(controller.get(), 2).getVisibility());
        assertEquals(View.GONE, row(controller.get(), 3).getVisibility());
    }

    @Test
    public void theTeamsAndTheirMembersStayWhenThePhoneIsTurned() {
        database.insertPlayersForNewGame(new String[]{"Pirosak", "Kékek"}, new int[]{1, 2}, new String[]{"Anna, Béla", "Cili, Dani"}, new int[]{2000, 2000});
        ActivityController<PlayersActivity> controller = Robolectric.buildActivity(PlayersActivity.class).setup();
        controller.recreate();
        assertEquals("Kékek", playerName(controller.get(), 1).getText().toString());
        LinearLayout members = row(controller.get(), 1).findViewById(R.id.MemberList);
        assertEquals(View.VISIBLE, members.getVisibility());
        assertEquals("Dani", ((EditText) members.getChildAt(1).findViewById(R.id.MemberName)).getText().toString());
    }

    private static View row(PlayersActivity activity, int index) {
        return ((LinearLayout) activity.findViewById(R.id.PlayerList)).getChildAt(index);
    }

    private static EditText playerName(PlayersActivity activity, int index) {
        return row(activity, index).findViewById(R.id.PlayerName);
    }
}
