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
    private Button ButtonBackDifficult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_difficult);
        init();
        ButtonDifficultChild.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentChild = new Intent(DifficultActivity.this, QuestionActivity.class);
                intentChild.putExtra("theme",1);
                startActivity(intentChild);
                finish();
            }
        });
        ButtonDifficultAdult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentAdult = new Intent(DifficultActivity.this, QuestionActivity.class);
                intentAdult.putExtra("theme",2);
                startActivity(intentAdult);
                finish();
            }
        });
        ButtonDifficultBible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentBible = new Intent(DifficultActivity.this, QuestionActivity.class);
                intentBible.putExtra("theme",3);
                startActivity(intentBible);
                finish();
            }
        });
        ButtonBackDifficult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DifficultActivity.this, RoundActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void init() {
        ButtonDifficultChild = findViewById(R.id.ButtonDifficultChild);
        ButtonDifficultAdult = findViewById(R.id.ButtonDifficultAdult);
        ButtonDifficultBible = findViewById(R.id.ButtonDifficultBible);
        ButtonBackDifficult = findViewById(R.id.ButtonBackDifficult);
    }
}