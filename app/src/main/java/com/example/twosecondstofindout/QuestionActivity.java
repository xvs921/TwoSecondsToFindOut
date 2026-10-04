package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
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

    private java.util.Timer timer;
    private TimerTask timerTask;
    private double time = 0.0;
    private int defaultTimerColor;

    private int rounds;
    private int currentPlayerId;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        init();
        Intent intent = getIntent();
        rounds = intent.getIntExtra("rounds",1);
        timer = new Timer();
        nextTurn();
        ButtonStartStop.setOnClickListener(view -> {
            startTimer();
        });
        ButtonOk.setOnClickListener(view -> {
            if(timerStarted){
                stopTimer();
                database.savePlayerAnswer(currentPlayerId, true);
                nextTurn();
            }
        });
        ButtonNotOk.setOnClickListener(view -> {
            if(timerStarted){
                stopTimer();
                database.savePlayerAnswer(currentPlayerId, false);
                nextTurn();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
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
        database = new Database(this);
        ButtonStartStop.setText("Start");
        defaultTimerColor = TimerText.getCurrentTextColor();
        timerStarted = false;
    }

    // The player with the fewest answers is next. When everyone answered
    // in every round, the game is over and the results are shown.
    @SuppressLint("SetTextI18n")
    private void nextTurn()
    {
        Cursor player = database.selectNextPlayer();
        if (player == null || !player.moveToFirst() || player.getInt(2) >= rounds)
        {
            if (player != null)
            {
                player.close();
            }
            showResults();
            return;
        }
        currentPlayerId = player.getInt(0);
        CurrentPlayer.setText(player.getString(1) + " (" + (player.getInt(2) + 1) + ". kör / " + rounds + ")");
        int topic = player.getInt(3);
        player.close();
        kerdes(topic);
    }

    private void showResults()
    {
        Intent intent = new Intent(QuestionActivity.this, ResultActivity.class);
        startActivity(intent);
        finish();
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
        database.markQuestionUsed(question.getInt(0));
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
        ButtonStartStop.setVisibility(View.INVISIBLE);
        timerTask = new TimerTask() {
            @Override
            public void run () {
                runOnUiThread(() -> {
                    if(!timerStarted){
                        return;
                    }
                    TimerText.setText(getTimerText());
                    // the player has 2 seconds to answer, the game master decides Siker / Késő
                    if(time >= 2){
                        TimerText.setTextColor(Color.parseColor("#ff0000"));
                    }
                    time++;
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 1000);
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
        TimerText.setText("00 : 00 : 00");
        TimerText.setTextColor(defaultTimerColor);
        ButtonStartStop.setVisibility(View.VISIBLE);
    }

    private String getTimerText(){
        int rounded = (int)Math.round(time);
        int seconds = ((rounded % 86400)) % 3600 % 60;
        int minutes = ((rounded % 86400)) % 3600 / 60;
        int hours = ((rounded % 86400)) / 3600;

        return formatTime(seconds, minutes, hours);
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(int s, int m, int h){
        return String.format("%02d", h) + " : " + String.format("%02d", m) + " : " + String.format("%02d", s);
    }
}