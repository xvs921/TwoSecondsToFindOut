package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class PlayersActivity extends AppCompatActivity {

    private EditText Player1;
    private EditText Player2;
    private EditText Player3;
    private EditText Player4;
    private EditText Player5;
    private EditText Player6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_players);
        init();

    }

    private void init() {
        Player1 = findViewById(R.id.Player1);
        Player2 = findViewById(R.id.Player2);
        Player3 = findViewById(R.id.Player3);
        Player4 = findViewById(R.id.Player4);
        Player5 = findViewById(R.id.Player5);
        Player6 = findViewById(R.id.Player6);
        Player3.setVisibility(View.GONE);
        Player4.setVisibility(View.GONE);
        Player5.setVisibility(View.GONE);
        Player6.setVisibility(View.GONE);
        /*EditText.setVisibility(View.GONE); //For hiding
        Button.setVisibility(View.VISIBLE); //For showing*/
    }
}