package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ResultActivity extends AppCompatActivity {

    private TextView Scoreboard;
    private Button ButtonNewGame;
    private Button ButtonMainMenu;
    private Button ButtonUndoResult;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        init();
        ButtonNewGame.setOnClickListener(view -> {
            Intent intent = new Intent(ResultActivity.this, RoundActivity.class);
            startActivity(intent);
            finish();
        });
        // a mistake on the last question can still be fixed, the game goes on from there
        ButtonUndoResult.setOnClickListener(view -> {
            int questionId = database.undoLastAnswer();
            if (questionId < 0) {
                return;
            }
            new GameState(ResultActivity.this).setInProgress(true);
            Intent intent = new Intent(ResultActivity.this, QuestionActivity.class);
            intent.putExtra("questionId", questionId);
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
        ButtonUndoResult = findViewById(R.id.ButtonUndoResult);
        database = new Database(this);

        Scoreboard.setText(database.selectScoreboardText());
        ButtonUndoResult.setVisibility(database.hasAnswers() ? View.VISIBLE : View.GONE);
    }
}
