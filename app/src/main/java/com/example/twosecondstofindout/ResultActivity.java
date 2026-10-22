package com.example.twosecondstofindout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

public class ResultActivity extends AppCompatActivity {

    private TextView Scoreboard;
    private Button ButtonNewGame;
    private Button ButtonMainMenu;
    private Button ButtonUndoResult;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_result);
        init();
        // the game screen was closed: the back gesture goes to the main menu, instead of closing the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ButtonMainMenu.performClick();
            }
        });
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
            database.deleteLastGameResult();
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

        // without the empty line after the last player
        Scoreboard.setText(database.selectScoreboardText().trim());
        // the best moments of this game
        StringBuilder highlights = new StringBuilder();
        List<String[]> rows = database.selectGameHighlights();
        for (String[] row : rows) {
            highlights.append(row[0]).append(": ").append(row[1]).append(" – ").append(row[2]).append('\n');
        }
        TextView highlightsText = findViewById(R.id.Highlights);
        highlightsText.setText(highlights.toString().trim());
        int visibility = rows.isEmpty() ? View.GONE : View.VISIBLE;
        highlightsText.setVisibility(visibility);
        findViewById(R.id.HighlightsTitle).setVisibility(visibility);
        ButtonUndoResult.setVisibility(database.hasAnswers() ? View.VISIBLE : View.GONE);
    }
}
