package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
        Cursor dbQuestionId = database.selectQuestionId(topic);
        StringBuffer stringBufferQId=new StringBuffer();
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
        StringBuffer stringBufferQ=new StringBuffer();
        if (questionText != null && questionText.getCount() > 0)
        {
            while (questionText.moveToNext())
            {
                stringBufferQ.append(questionText.getString(0));
            }
            Question.setText(stringBufferQ.toString());
        }

        Cursor answerText = database.selectAnswerText(questionId);
        StringBuffer stringBufferA=new StringBuffer();
        if (answerText != null && answerText.getCount() > 0)
        {
            while (answerText.moveToNext())
            {
                stringBufferA.append(answerText.getString(0));
            }
            Answer.setText(stringBufferA.toString());
        }
    }
}