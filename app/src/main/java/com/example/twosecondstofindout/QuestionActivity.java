package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

public class QuestionActivity extends AppCompatActivity {

    private TextView Question;
    private TextView Answer;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        init();
        Intent intent = getIntent();
        int topic = intent.getIntExtra("theme",1);
        kerdes(topic);
    }
    private void init() {
        Question = findViewById(R.id.Question);
        Answer = findViewById(R.id.Answer);
        database = new Database(this);
    }

    public void kerdes(int topic)
    {
        Question.setText(String.valueOf(topic));
        database.selectQuestionId(topic);
        /*StringBuffer stringBuffer=new StringBuffer();
        String asd=eredmeny.toString();
        Question.setText(asd);*/
    }
}