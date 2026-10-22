package com.example.twosecondstofindout;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

// The game screen with a fresh database and a game of two players, the game master reads the questions
@RunWith(RobolectricTestRunner.class)
public class QuestionActivityTest {

    private Database database;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        database = new Database(context);
        database.insertPlayersForNewGame(new String[]{"Anna", "Béla"}, new int[]{1, 1});
        database.resetScores();
        new GameState(context).start(3, false);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void theQuestionStaysWhenThePhoneIsTurned() {
        ActivityController<QuestionActivity> controller = Robolectric.buildActivity(QuestionActivity.class).setup();
        String question = questionText(controller.get());
        controller.recreate();
        assertEquals(question, questionText(controller.get()));
    }

    @Test
    public void theQuestionOfAnotherLevelIsAskedWhenTheLevelHasNone() {
        database.getWritableDatabase().execSQL("DELETE FROM question_topics WHERE topic = 1");
        ActivityController<QuestionActivity> controller = Robolectric.buildActivity(QuestionActivity.class).setup();
        assertEquals(android.view.View.VISIBLE, controller.get().findViewById(R.id.ButtonStartStop).getVisibility());
    }

    @Test
    public void aFlashTieBreakOfTwoIsDecidedByOneQuestion() {
        new GameState(RuntimeEnvironment.getApplication()).start(1, false);
        new GameState(RuntimeEnvironment.getApplication()).setFlashOnly(true);
        QuestionActivity activity = Robolectric.buildActivity(QuestionActivity.class).setup().get();
        // one round of 2 questions, nobody answers: Anna and Béla are tied
        answerFlash(activity, "Senki");
        answerFlash(activity, "Senki");
        assertEquals("Holtverseny!", ((TextView) activity.findViewById(R.id.RoundTitle)).getText().toString());
        activity.findViewById(R.id.ButtonNextRound).performClick();
        // the first to answer wins, the game is over
        answerFlash(activity, "Béla");
        assertEquals(ResultActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        assertEquals("🥇  Béla - 1 pont\n🥈  Anna - 0 pont", database.selectScoreboardText().trim());
    }

    @Test
    public void theTieBreakIsShownWhenEverybodyIsTiedInAGameToPoints() {
        GameState gameState = new GameState(RuntimeEnvironment.getApplication());
        gameState.setGoal(true, 1);
        QuestionActivity activity = Robolectric.buildActivity(QuestionActivity.class).setup().get();
        // both answer correctly: both have the target points, nobody drops out
        for (int i = 0; i < 2; i++) {
            activity.findViewById(R.id.ButtonStartStop).performClick();
            activity.findViewById(R.id.ButtonOk).performClick();
        }
        assertEquals("Holtverseny!", ((TextView) activity.findViewById(R.id.RoundTitle)).getText().toString());
        activity.findViewById(R.id.ButtonNextRound).performClick();
        assertEquals("Anna (szétszavazó)", ((TextView) activity.findViewById(R.id.CurrentPlayer)).getText().toString());
    }

    // Start, then the button of who answered first
    private static void answerFlash(QuestionActivity activity, String name) {
        activity.findViewById(R.id.ButtonStartStop).performClick();
        Button button = findButton(activity.findViewById(R.id.FlashButtons), name);
        assertNotNull(name, button);
        button.performClick();
    }

    private static Button findButton(View view, String text) {
        if (view instanceof Button && text.equals(((Button) view).getText().toString())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button found = findButton(((ViewGroup) view).getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String questionText(QuestionActivity activity) {
        return ((TextView) activity.findViewById(R.id.Question)).getText().toString();
    }
}
