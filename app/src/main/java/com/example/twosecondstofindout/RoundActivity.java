package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class RoundActivity extends AppCompatActivity {
    private Button ButtonBackRound;
    private Button ButtonNextRound;
    private TextView FirstPlayer;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_round);
        init();
        ButtonBackRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentStart = new Intent(RoundActivity.this, PlayersActivity.class);
                startActivity(intentStart);
                finish();
            }
        });
        ButtonNextRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentStart = new Intent(RoundActivity.this, DifficultActivity.class);
                startActivity(intentStart);
                finish();
            }
        });
    }
    private void init() {
        ButtonBackRound = findViewById(R.id.ButtonBackRound);
        ButtonNextRound = findViewById(R.id.ButtonNextRound);
        FirstPlayer = findViewById(R.id.FirstPlayer);
        database = new Database(this);
        String firstPlayerText = FirstPlayer.getText().toString();

        Cursor firstPlayerName = database.selectFirstPlayerName();
        StringBuffer stringBuffer = new StringBuffer();
        if (firstPlayerName != null && firstPlayerName.getCount() > 0)
        {
            while (firstPlayerName.moveToNext())
            {
                stringBuffer.append(firstPlayerName.getString(0));
            }
            FirstPlayer.setText(firstPlayerText + stringBuffer.toString());
        }

    }
}