package com.example.twosecondstofindout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class QuestionActivity extends AppCompatActivity {

    private TextView CurrentPlayer;
    private TextView Question;
    private TextView Answer;
    private TextView TimerText;
    private Button ButtonStartStop;
    private boolean timerStarted;
    private Database database;

    private Button ButtonOk;
    private Button ButtonNotOk;
    private Button ButtonSkip;
    private Button ButtonUndo;
    private View QuestionCard;
    private View TimerArea;
    private View RoundArea;
    private View AnswerButtons;
    private TextView RoundTitle;
    private TextView RoundStandings;
    private Button ButtonNextRound;
    private ToneGenerator toneGenerator;

    private final Handler handler = new Handler(Looper.getMainLooper());
    // elapsed time in milliseconds, measured from the moment Start was pressed
    private long time = 0;
    private long startTime;
    private int defaultTimerColor;

    private GameState gameState;
    private int rounds;
    private int currentPlayerId;
    private int currentTopic;
    private int currentQuestionId;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_question);
        getOnBackPressedDispatcher().addCallback(this, backToMainMenu);
        init();
        rounds = gameState.getRounds();
        // after an undo on the results screen the undone question comes back
        int undoneQuestionId = getIntent().getIntExtra("questionId", -1);
        if (undoneQuestionId >= 0) {
            showTurn(undoneQuestionId);
        } else {
            nextTurn();
        }
        ButtonStartStop.setOnClickListener(view -> {
            startTimer();
        });
        ButtonOk.setOnClickListener(view -> {
            if(timerStarted){
                stopTimer();
                database.savePlayerAnswer(currentPlayerId, currentQuestionId, true);
                nextTurn();
            }
        });
        // takes back the last Siker / Késő, the player gets the same question again
        ButtonUndo.setOnClickListener(view -> {
            stopTimer();
            int questionId = database.undoLastAnswer();
            if (questionId >= 0) {
                showTurn(questionId);
            }
        });
        // a new question for the same player, without scoring
        ButtonSkip.setOnClickListener(view -> {
            stopTimer();
            kerdes(currentTopic);
        });
        ButtonNextRound.setOnClickListener(view -> showTurn(-1));
        ButtonNotOk.setOnClickListener(view -> {
            if(timerStarted){
                stopTimer();
                database.savePlayerAnswer(currentPlayerId, currentQuestionId, false);
                nextTurn();
            }
        });
    }

    // the game is saved, it can be continued from the main menu
    private final OnBackPressedCallback backToMainMenu = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            Intent intent = new Intent(QuestionActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Choreographer.getInstance().removeFrameCallback(timerFrame);
        handler.removeCallbacks(timeUp);
        toneGenerator.release();
    }

    @SuppressLint("SetTextI18n")
    private void init() {
        CurrentPlayer = findViewById(R.id.CurrentPlayer);
        Question = findViewById(R.id.Question);
        Answer = findViewById(R.id.Answer);
        TimerText = findViewById(R.id.Timer);
        ButtonStartStop = findViewById(R.id.ButtonStartStop);
        ButtonOk = findViewById(R.id.ButtonOk);
        ButtonNotOk = findViewById(R.id.ButtonNotOk);
        ButtonSkip = findViewById(R.id.ButtonSkip);
        ButtonUndo = findViewById(R.id.ButtonUndo);
        QuestionCard = findViewById(R.id.QuestionCard);
        TimerArea = findViewById(R.id.TimerArea);
        RoundArea = findViewById(R.id.RoundArea);
        AnswerButtons = findViewById(R.id.AnswerButtons);
        RoundTitle = findViewById(R.id.RoundTitle);
        RoundStandings = findViewById(R.id.RoundStandings);
        ButtonNextRound = findViewById(R.id.ButtonNextRound);
        gameState = new GameState(this);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);
        database = new Database(this);
        defaultTimerColor = TimerText.getCurrentTextColor();
        timerStarted = false;
    }

    // Called after every Siker / Késő. When every active player answered the same number
    // of questions a round is over: the standings are shown, and after the last round the
    // players with the most points play tie-break rounds until there is one winner.
    private void nextTurn()
    {
        int[] answeredRange = database.selectActiveAnsweredRange();
        boolean roundOver = answeredRange[0] == answeredRange[1] && answeredRange[0] > 0;
        if (!roundOver)
        {
            showTurn(-1);
            return;
        }
        if (answeredRange[0] < rounds)
        {
            showRoundStandings(answeredRange[0] + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
        showRoundStandings("Holtverseny!", "Szétszavazó kör: " + database.selectActivePlayerNames() + "\n\n" + database.selectScoreboardText());
    }

    // the standings take the place of the timer, the game buttons are hidden until Tovább
    private void showRoundStandings(String title, String standings)
    {
        RoundTitle.setText(title);
        RoundStandings.setText(standings.trim());
        setRoundAreaVisible(true);
    }

    private void setRoundAreaVisible(boolean visible)
    {
        RoundArea.setVisibility(visible ? View.VISIBLE : View.GONE);
        TimerArea.setVisibility(visible ? View.GONE : View.VISIBLE);
        AnswerButtons.setVisibility(visible ? View.GONE : View.VISIBLE);
        // the last player and question stay readable, but faded
        CurrentPlayer.setAlpha(visible ? 0.35f : 1f);
        QuestionCard.setAlpha(visible ? 0.35f : 1f);
    }

    // Shows the next player with a new question, or with the given one after an undo
    @SuppressLint("SetTextI18n")
    private void showTurn(int questionId)
    {
        setRoundAreaVisible(false);
        Cursor player = database.selectNextPlayer();
        if (!player.moveToFirst())
        {
            player.close();
            finishGame();
            return;
        }
        currentPlayerId = player.getInt(0);
        int answered = player.getInt(2);
        if (answered >= rounds)
        {
            CurrentPlayer.setText(player.getString(1) + " (szétszavazó)");
        }
        else
        {
            CurrentPlayer.setText(player.getString(1) + " (" + (answered + 1) + ". kör / " + rounds + ")");
        }
        currentTopic = player.getInt(3);
        player.close();
        // the undone question may have been deleted in the meantime
        if (questionId < 0 || !showQuestion(questionId))
        {
            kerdes(currentTopic);
        }
        ButtonUndo.setEnabled(database.hasAnswers());
    }

    private void finishGame()
    {
        database.saveGameResult();
        gameState.setInProgress(false);
        Intent intent = new Intent(QuestionActivity.this, ResultActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean showQuestion(int questionId)
    {
        Cursor question = database.selectQuestion(questionId);
        boolean found = question.moveToFirst();
        if (found)
        {
            currentQuestionId = questionId;
            Question.setText(question.getString(0));
            Answer.setText(question.getString(1));
        }
        question.close();
        return found;
    }

    @SuppressLint("SetTextI18n")
    public void kerdes(int topic)
    {
        Cursor question = database.selectRandomQuestion(topic);
        if (question == null || !question.moveToFirst())
        {
            if (question != null)
            {
                question.close();
            }
            Question.setText("Ebben a témában nincs kérdés.");
            Answer.setText("");
            ButtonStartStop.setVisibility(View.INVISIBLE);
            return;
        }
        currentQuestionId = question.getInt(0);
        database.markQuestionUsed(currentQuestionId);
        Question.setText(question.getString(1));
        Answer.setText(question.getString(2));
        question.close();
    }

    //
    // TIMER FUNCTIONS
    //
    // redraws the timer on every frame of the screen, so the milliseconds keep running smoothly
    private final Choreographer.FrameCallback timerFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            time = SystemClock.elapsedRealtime() - startTime;
            TimerText.setText(getTimerText());
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    // the player has 2 seconds to answer, the game master decides Siker / Késő
    private final Runnable timeUp = () -> {
        TimerText.setTextColor(ContextCompat.getColor(QuestionActivity.this, R.color.danger));
        // beep once, so the game master can watch the player
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 400);
    };

    public void startTimer()
    {
        if(timerStarted){
            return;
        }
        timerStarted = true;
        time = 0;
        startTime = SystemClock.elapsedRealtime();
        ButtonStartStop.setVisibility(View.INVISIBLE);
        Choreographer.getInstance().postFrameCallback(timerFrame);
        handler.postDelayed(timeUp, 2000);
    }

    @SuppressLint("SetTextI18n")
    private void stopTimer()
    {
        Choreographer.getInstance().removeFrameCallback(timerFrame);
        handler.removeCallbacks(timeUp);
        timerStarted = false;
        time = 0;
        TimerText.setText(getTimerText());
        TimerText.setTextColor(defaultTimerColor);
        ButtonStartStop.setVisibility(View.VISIBLE);
    }

    // seconds and milliseconds, e.g. 1,123
    @SuppressLint("DefaultLocale")
    private String getTimerText(){
        return String.format("%d,%03d", time / 1000, time % 1000);
    }
}
