package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class ResultActivity extends AppCompatActivity {

    private TextView Scoreboard;
    private Button ButtonNewGame;
    private Button ButtonMainMenu;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        init();
        ButtonNewGame.setOnClickListener(view -> {
            database.resetScores();
            Intent intent = new Intent(ResultActivity.this, RoundActivity.class);
            startActivity(intent);
            finish();
        });
        ButtonMainMenu.setOnClickListener(view -> {
            Intent intent = new Intent(ResultActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void init() {
        Scoreboard = findViewById(R.id.Scoreboard);
        ButtonNewGame = findViewById(R.id.ButtonNewGame);
        ButtonMainMenu = findViewById(R.id.ButtonMainMenu);
        database = new Database(this);

        Cursor scoreboard = database.selectScoreboard();
        StringBuilder stringBuilder = new StringBuilder();
        int place = 1;
        while (scoreboard.moveToNext())
        {
            stringBuilder.append(place).append(". ")
                    .append(scoreboard.getString(0)).append(" - ")
                    .append(scoreboard.getInt(1)).append(" pont\n");
            place++;
        }
        scoreboard.close();
        Scoreboard.setText(stringBuilder.toString());
    }
}
