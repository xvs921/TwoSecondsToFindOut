package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class PlayersActivity extends AppCompatActivity {

    private static final String[] LEVELS = {"Gyerek", "Felnőtt", "Bibliai"};

    private EditText[] Players;
    private Spinner[] PlayerLevels;
    private View[] PlayerRows;
    private Button ButtonNewPlayer;
    private Button ButtonBackPlayers;
    private Button ButtonNextPlayers;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_players);
        init();
        ButtonNewPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (View row : PlayerRows) {
                    if (row.getVisibility() == View.GONE) {
                        row.setVisibility(View.VISIBLE);
                        break;
                    }
                }
                updateNewPlayerButton();
            }
        });

        ButtonBackPlayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(PlayersActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        ButtonNextPlayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String[] names = new String[Players.length];
                int[] topics = new int[Players.length];
                for (int i = 0; i < Players.length; i++) {
                    names[i] = PlayerRows[i].getVisibility() == View.VISIBLE ? Players[i].getText().toString() : "";
                    // the topic ids are 1: Gyerek, 2: Felnőtt, 3: Bibliai
                    topics[i] = PlayerLevels[i].getSelectedItemPosition() + 1;
                }
                database.insertPlayersForNewGame(names, topics);
                if(database.countPlayers() == 0){
                    Toast.makeText(PlayersActivity.this, "Adj meg legalább egy játékost!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(PlayersActivity.this, RoundActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    private void init() {
        Players = new EditText[]{findViewById(R.id.Player1), findViewById(R.id.Player2), findViewById(R.id.Player3),
                findViewById(R.id.Player4), findViewById(R.id.Player5), findViewById(R.id.Player6)};
        PlayerLevels = new Spinner[]{findViewById(R.id.PlayerLevel1), findViewById(R.id.PlayerLevel2), findViewById(R.id.PlayerLevel3),
                findViewById(R.id.PlayerLevel4), findViewById(R.id.PlayerLevel5), findViewById(R.id.PlayerLevel6)};
        PlayerRows = new View[]{findViewById(R.id.PlayerRow1), findViewById(R.id.PlayerRow2), findViewById(R.id.PlayerRow3),
                findViewById(R.id.PlayerRow4), findViewById(R.id.PlayerRow5), findViewById(R.id.PlayerRow6)};

        ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(this, R.layout.spinner_level, LEVELS);
        levelAdapter.setDropDownViewResource(R.layout.spinner_level_dropdown);
        for (Spinner level : PlayerLevels) {
            level.setAdapter(levelAdapter);
            level.setSelection(1);
        }
        database = new Database(this);

        // the players of the last game are filled in, at least two rows are shown
        Cursor lastPlayers = database.selectPlayers();
        int row = 0;
        while (lastPlayers.moveToNext() && row < Players.length) {
            Players[row].setText(lastPlayers.getString(0));
            PlayerLevels[row].setSelection(lastPlayers.getInt(1) - 1);
            row++;
        }
        lastPlayers.close();
        for (int i = Math.max(2, row); i < PlayerRows.length; i++) {
            PlayerRows[i].setVisibility(View.GONE);
        }

        ButtonNewPlayer = findViewById(R.id.ButtonNewPlayer);
        ButtonBackPlayers = findViewById(R.id.ButtonBackPlayers);
        ButtonNextPlayers = findViewById(R.id.ButtonNextPlayers);
        updateNewPlayerButton();
    }

    // there are at most 6 players
    private void updateNewPlayerButton() {
        boolean hasHiddenRow = false;
        for (View row : PlayerRows) {
            if (row.getVisibility() == View.GONE) {
                hasHiddenRow = true;
            }
        }
        ButtonNewPlayer.setVisibility(hasHiddenRow ? View.VISIBLE : View.GONE);
    }
}