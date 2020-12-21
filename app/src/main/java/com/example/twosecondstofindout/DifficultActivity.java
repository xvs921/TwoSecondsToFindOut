package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class DifficultActivity extends AppCompatActivity {

    private Button ButtonDifficultChild;
    private Button ButtonDifficultAdult;
    private Button ButtonDifficultBible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.difficult_activity);
        init();
        ButtonDifficultChild.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DifficultActivity.this, QuestionActivity.class);
                intent.putExtra("theme",1);
                startActivity(intent);
                finish();
            }
        });
        ButtonDifficultAdult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DifficultActivity.this, QuestionActivity.class);
                intent.putExtra("theme",2);
                startActivity(intent);
                finish();
            }
        });
        ButtonDifficultBible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DifficultActivity.this, QuestionActivity.class);
                intent.putExtra("theme",3);
                startActivity(intent);
                finish();
            }
        });
    }

    private void init() {
        ButtonDifficultChild = findViewById(R.id.ButtonDifficultChild);
        ButtonDifficultAdult = findViewById(R.id.ButtonDifficultAdult);
        ButtonDifficultBible = findViewById(R.id.ButtonDifficultBible);
    }
}