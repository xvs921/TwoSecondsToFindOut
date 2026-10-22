package com.example.twosecondstofindout;

import android.widget.LinearLayout;
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

// The question editor with a fresh database
@RunWith(RobolectricTestRunner.class)
public class QuestionsScreenTest {

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
    public void theTopicStaysWhenThePhoneIsTurned() {
        database.setQuestionFlagged(database.findQuestionId("Mi a víz kémiai képlete?"), true);
        ActivityController<QuestionsActivity> controller = Robolectric.buildActivity(QuestionsActivity.class).setup();
        // the last topic button lists the questions marked as wrong
        LinearLayout buttons = controller.get().findViewById(R.id.TopicButtons);
        buttons.getChildAt(buttons.getChildCount() - 1).performClick();
        controller.recreate();
        assertEquals("1 hibásnak jelölt kérdés", ((TextView) controller.get().findViewById(R.id.QuestionCount)).getText().toString());
    }
}
