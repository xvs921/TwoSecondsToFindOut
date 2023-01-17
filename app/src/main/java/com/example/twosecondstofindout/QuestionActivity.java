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

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        init();
        Intent intent = getIntent();
        int topic = intent.getIntExtra("theme",1);
        kerdes(topic);
        timer = new Timer();
        ButtonStartStop.setOnClickListener(view -> {
            startStop();
        });
        ButtonNotOk.setOnClickListener(view -> {
            startStop();
            kerdes(topic);
        });
    }
    @SuppressLint("SetTextI18n")
    private void init() {
        Question = findViewById(R.id.Question);
        Answer = findViewById(R.id.Answer);
        TimerText = findViewById(R.id.Timer);
        ButtonStartStop = findViewById(R.id.ButtonStartStop);
        ButtonOk = findViewById(R.id.ButtonOk);
        ButtonNotOk = findViewById(R.id.ButtonNotOk);
        database = new Database(this);
        ButtonStartStop.setText("Start");
        timerStarted = false;
    }

    public void kerdes(int topic)
    {
        Cursor dbQuestionId = database.selectQuestionId(topic);
        StringBuilder stringBufferQId=new StringBuilder();
        if (dbQuestionId != null && dbQuestionId.getCount() > 0)
        {
            while (dbQuestionId.moveToNext())
            {
                stringBufferQId.append(dbQuestionId.getString(0));
            }
        }

        int questionId = Integer.parseInt(stringBufferQId.toString());
        //Question.setText(String.valueOf(questionId));  string value of
        Cursor questionText = database.selectQuestionText(questionId);
        StringBuilder stringBufferQ=new StringBuilder();
        if (questionText != null && questionText.getCount() > 0)
        {
            while (questionText.moveToNext())
            {
                stringBufferQ.append(questionText.getString(0));
            }
            Question.setText(stringBufferQ.toString());
        }

        Cursor answerText = database.selectAnswerText(questionId);
        StringBuilder stringBufferA=new StringBuilder();
        if (answerText != null && answerText.getCount() > 0)
        {
            while (answerText.moveToNext())
            {
                stringBufferA.append(answerText.getString(0));
            }
            Answer.setText(stringBufferA.toString());
        }
    }

    //
    // BUTTON CLICK EVENTS
    //
    private void startStop(){
        if(timerTask != null){
            timerTask.cancel();
            time = 0;
            if(ButtonOk.getVisibility() == View.INVISIBLE){
                ButtonOk.setVisibility(View.VISIBLE);
            }
        }
        timerStarted = !timerStarted;
        if(timerStarted){
            ButtonStartStop.setVisibility(View.INVISIBLE);
            startTimer();
        } else{
            ButtonStartStop.setVisibility(View.VISIBLE);
            TimerText.setText("00 : 00 : 00");
        }
    }


    //
    // TIMER FUNCTIONS
    //
    public void startTimer()
    {
        timerTask = new TimerTask() {
            @Override
            public void run () {
                runOnUiThread(() -> {
                    time++;
                    TimerText.setText(getTimerText());
                    if(time >= 4){
                        TimerText.setTextColor(Color.parseColor("#ff0000"));
                    }
                    if(time >= 6){
                        if(ButtonOk.getVisibility() == View.VISIBLE){
                            ButtonOk.setVisibility(View.INVISIBLE);
                        }
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 1000);
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