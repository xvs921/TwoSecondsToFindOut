package com.example.twosecondstofindout;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class PlayersActivity extends AppCompatActivity {

    private EditText Player1;
    private EditText Player2;
    private EditText Player3;
    private EditText Player4;
    private EditText Player5;
    private EditText Player6;
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
                if(Player3.getVisibility() == View.INVISIBLE){
                    Player3.setVisibility(View.VISIBLE);
                }
                else if(Player4.getVisibility() == View.INVISIBLE){
                    Player4.setVisibility(View.VISIBLE);
                }
                else if(Player5.getVisibility() == View.INVISIBLE){
                    Player5.setVisibility(View.VISIBLE);
                }
                else if(Player6.getVisibility() == View.INVISIBLE){
                    Player6.setVisibility(View.VISIBLE);
                }
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
                database.insertPlayersForNewGame(Player1.getText().toString(),String.valueOf(Player2.getText()),String.valueOf(Player3.getText()),String.valueOf(Player4.getText()),String.valueOf(Player5.getText()),String.valueOf(Player6.getText()));
                Intent intent = new Intent(PlayersActivity.this, RoundActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    private void init() {
        Player1 = findViewById(R.id.Player1);
        Player2 = findViewById(R.id.Player2);
        Player3 = findViewById(R.id.Player3);
        Player4 = findViewById(R.id.Player4);
        Player5 = findViewById(R.id.Player5);
        Player6 = findViewById(R.id.Player6);
        Player3.setVisibility(View.INVISIBLE);
        Player4.setVisibility(View.INVISIBLE);
        Player5.setVisibility(View.INVISIBLE);
        Player6.setVisibility(View.INVISIBLE);

        ButtonNewPlayer = findViewById(R.id.ButtonNewPlayer);
        ButtonBackPlayers = findViewById(R.id.ButtonBackPlayers);
        ButtonNextPlayers = findViewById(R.id.ButtonNextPlayers);

        database = new Database(this);
    }
}