package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button ButtonStart;
    private Button ButtonContinue;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        ButtonContinue.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, QuestionActivity.class);
            startActivity(intent);
            finish();
        });
        ButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentStart = new Intent(MainActivity.this, PlayersActivity.class);
                startActivity(intentStart);
                finish();
            }
        });
    }

    private void init() {
        ButtonStart = findViewById(R.id.ButtonStart);
        ButtonContinue = findViewById(R.id.ButtonContinue);
        database = new Database(this);
        // a game that was left before the end can be continued
        boolean canContinue = new GameState(this).isInProgress() && database.countPlayers() > 0;
        ButtonContinue.setVisibility(canContinue ? View.VISIBLE : View.GONE);
    }
}