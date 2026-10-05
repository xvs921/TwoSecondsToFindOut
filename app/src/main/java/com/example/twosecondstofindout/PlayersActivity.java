package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class PlayersActivity extends AppCompatActivity {

    private static final String[] LEVELS = Database.TOPICS;
    // there are at most 6 players or teams
    private static final int MAX_PLAYERS = 6;
    // the times to answer a player can get
    private static final int[] ANSWER_MS = {1000, 1500, 2000, 2500, 3000, 4000, 5000};

    private EditText[] Players;
    private EditText[] PlayerMembers;
    private Spinner[] PlayerLevels;
    private Spinner[] PlayerTimes;
    private View[] PlayerRows;
    private TextView PlayersTitle;
    private TextView PlayersHint;
    private Button ButtonModeSingle;
    private Button ButtonModeTeams;
    private Button ButtonNewPlayer;
    private Button ButtonBackPlayers;
    private Button ButtonNextPlayers;
    private Database database;
    // in a team game every row is a team, and its members answer in turns
    private boolean teams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_players);
        init();
        ButtonModeSingle.setOnClickListener(view -> setTeams(false));
        ButtonModeTeams.setOnClickListener(view -> setTeams(true));
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
                String[] members = new String[Players.length];
                int[] answerMs = new int[Players.length];
                for (int i = 0; i < Players.length; i++) {
                    boolean visible = PlayerRows[i].getVisibility() == View.VISIBLE;
                    names[i] = visible ? Players[i].getText().toString() : "";
                    // the topic ids are 1: Gyerek, 2: Felnőtt, 3: Bibliai
                    topics[i] = PlayerLevels[i].getSelectedItemPosition() + 1;
                    members[i] = visible && teams ? PlayerMembers[i].getText().toString() : "";
                    answerMs[i] = ANSWER_MS[PlayerTimes[i].getSelectedItemPosition()];
                }
                database.insertPlayersForNewGame(names, topics, members, answerMs);
                if(database.countPlayers() == 0){
                    Toast.makeText(PlayersActivity.this, teams ? "Adj meg legalább egy csapatot!" : "Adj meg legalább egy játékost!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(PlayersActivity.this, RoundActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    @SuppressLint("SetTextI18n")
    private void init() {
        LinearLayout playerList = findViewById(R.id.PlayerList);
        Players = new EditText[MAX_PLAYERS];
        PlayerMembers = new EditText[MAX_PLAYERS];
        PlayerLevels = new Spinner[MAX_PLAYERS];
        PlayerTimes = new Spinner[MAX_PLAYERS];
        PlayerRows = new View[MAX_PLAYERS];
        ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(this, R.layout.spinner_level, LEVELS);
        levelAdapter.setDropDownViewResource(R.layout.spinner_level_dropdown);
        String[] times = new String[ANSWER_MS.length];
        for (int i = 0; i < times.length; i++) {
            times[i] = formatSeconds(ANSWER_MS[i]);
        }
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(this, R.layout.spinner_level, times);
        timeAdapter.setDropDownViewResource(R.layout.spinner_level_dropdown);
        for (int i = 0; i < MAX_PLAYERS; i++) {
            View row = getLayoutInflater().inflate(R.layout.item_player, playerList, false);
            ((TextView) row.findViewById(R.id.PlayerNumber)).setText((i + 1) + ".");
            PlayerRows[i] = row;
            Players[i] = row.findViewById(R.id.PlayerName);
            PlayerMembers[i] = row.findViewById(R.id.PlayerMembers);
            PlayerLevels[i] = row.findViewById(R.id.PlayerLevel);
            PlayerLevels[i].setAdapter(levelAdapter);
            PlayerLevels[i].setSelection(1);
            PlayerTimes[i] = row.findViewById(R.id.PlayerTime);
            PlayerTimes[i].setAdapter(timeAdapter);
            PlayerTimes[i].setSelection(timeIndex(Database.DEFAULT_ANSWER_MS));
            playerList.addView(row);
        }
        PlayersTitle = findViewById(R.id.PlayersTitle);
        PlayersHint = findViewById(R.id.PlayersHint);
        ButtonModeSingle = findViewById(R.id.ButtonModeSingle);
        ButtonModeTeams = findViewById(R.id.ButtonModeTeams);
        database = new Database(this);

        // the players of the last game are filled in, at least two rows are shown
        Cursor lastPlayers = database.selectPlayers();
        int row = 0;
        boolean lastGameHadTeams = false;
        while (lastPlayers.moveToNext() && row < Players.length) {
            Players[row].setText(lastPlayers.getString(0));
            PlayerLevels[row].setSelection(lastPlayers.getInt(1) - 1);
            String members = lastPlayers.getString(2);
            PlayerMembers[row].setText(members);
            PlayerTimes[row].setSelection(timeIndex(lastPlayers.getInt(3)));
            lastGameHadTeams |= members != null && !members.trim().isEmpty();
            row++;
        }
        lastPlayers.close();
        for (int i = Math.max(2, row); i < PlayerRows.length; i++) {
            PlayerRows[i].setVisibility(View.GONE);
        }

        ButtonNewPlayer = findViewById(R.id.ButtonNewPlayer);
        ButtonBackPlayers = findViewById(R.id.ButtonBackPlayers);
        ButtonNextPlayers = findViewById(R.id.ButtonNextPlayers);
        setTeams(lastGameHadTeams);
        updateNewPlayerButton();
    }

    // the chosen mode is filled, the other one is outlined
    @SuppressLint("SetTextI18n")
    private void setTeams(boolean teams) {
        this.teams = teams;
        showSelected(ButtonModeTeams, teams);
        showSelected(ButtonModeSingle, !teams);
        PlayersTitle.setText(teams ? "Csapatok" : "Játékosok");
        PlayersHint.setText(teams
                ? "Add meg a csapatokat, a tagjaikat, a szintet és az időt! A tagok felváltva válaszolnak."
                : "Add meg a neveket, és válassz mindenkinek szintet és időt!");
        ButtonNewPlayer.setText(teams ? "+ Új csapat" : "+ Új játékos");
        for (int i = 0; i < Players.length; i++) {
            Players[i].setHint(teams ? "Csapat neve" : "Név");
            PlayerMembers[i].setVisibility(teams ? View.VISIBLE : View.GONE);
        }
    }

    private void showSelected(Button button, boolean selected) {
        button.setBackgroundResource(selected ? R.drawable.btn_primary : R.drawable.btn_secondary);
        button.setTextColor(ContextCompat.getColorStateList(this,
                selected ? R.color.onPrimary : R.color.text_secondary_button));
    }

    // e.g. 1500 -> "1,5 mp"
    static String formatSeconds(int ms) {
        String seconds = ms % 1000 == 0 ? String.valueOf(ms / 1000) : (ms / 1000) + "," + (ms % 1000) / 100;
        return seconds + " mp";
    }

    // the closest of the times that can be chosen
    private static int timeIndex(int ms) {
        int best = 0;
        for (int i = 1; i < ANSWER_MS.length; i++) {
            if (Math.abs(ANSWER_MS[i] - ms) < Math.abs(ANSWER_MS[best] - ms)) {
                best = i;
            }
        }
        return best;
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
