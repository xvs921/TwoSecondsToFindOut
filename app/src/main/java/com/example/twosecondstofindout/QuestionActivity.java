package com.example.twosecondstofindout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

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
    private ToneGenerator toneGenerator;

    private java.util.Timer timer;
    private TimerTask timerTask;
    // elapsed time in milliseconds, measured from the moment Start was pressed
    private long time = 0;
    private long startTime;
    private boolean beeped;
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
        setContentView(R.layout.activity_question);
        init();
        rounds = gameState.getRounds();
        timer = new Timer();
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
        ButtonNotOk.setOnClickListener(view -> {
            if(timerStarted){
                stopTimer();
                database.savePlayerAnswer(currentPlayerId, currentQuestionId, false);
                nextTurn();
            }
        });
    }

    // the game is saved, it can be continued from the main menu
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(QuestionActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
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
            showRoundDialog(answeredRange[0] + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
        showRoundDialog("Holtverseny!", "Szétszavazó kör: " + database.selectActivePlayerNames() + "\n\n" + database.selectScoreboardText());
    }

    private void showRoundDialog(String title, String message)
    {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Tovább", (dialog, which) -> showTurn(-1))
                .show();
    }

    // Shows the next player with a new question, or with the given one after an undo
    @SuppressLint("SetTextI18n")
    private void showTurn(int questionId)
    {
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
    public void startTimer()
    {
        if(timerStarted){
            return;
        }
        timerStarted = true;
        time = 0;
        beeped = false;
        startTime = SystemClock.elapsedRealtime();
        ButtonStartStop.setVisibility(View.INVISIBLE);
        timerTask = new TimerTask() {
            @Override
            public void run () {
                runOnUiThread(() -> {
                    if(!timerStarted){
                        return;
                    }
                    time = SystemClock.elapsedRealtime() - startTime;
                    TimerText.setText(getTimerText());
                    // the player has 2 seconds to answer, the game master decides Siker / Késő
                    if(time >= 2000){
                        TimerText.setTextColor(ContextCompat.getColor(QuestionActivity.this, R.color.danger));
                        // beep once, so the game master can watch the player
                        if(!beeped){
                            beeped = true;
                            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 400);
                        }
                    }
                });
            }
        };
        // refreshed often, so the milliseconds keep running smoothly
        timer.scheduleAtFixedRate(timerTask, 0, 25);
    }

    @SuppressLint("SetTextI18n")
    private void stopTimer()
    {
        if(timerTask != null){
            timerTask.cancel();
            timerTask = null;
        }
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
