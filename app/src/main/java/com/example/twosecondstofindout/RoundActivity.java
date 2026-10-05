package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class RoundActivity extends AppCompatActivity {
    private Button ButtonBackRound;
    private Button ButtonNextRound;
    private TextView FirstPlayer;
    private EditText RoundNumber;
    private Button ButtonRoundMinus;
    private Button ButtonRoundPlus;
    private Button ButtonReaderMaster;
    private Button ButtonReaderPhone;
    private Button ButtonSignalBeep;
    private Button ButtonSignalVibrate;
    private Button ButtonStreakBonus;
    private Database database;
    private GameState gameState;
    private boolean phoneReads;
    private boolean beep;
    private boolean vibrate;
    private boolean streakBonus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
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
        ButtonRoundMinus.setOnClickListener(view -> RoundNumber.setText(String.valueOf(Math.max(1, getRoundNumber() - 1))));
        ButtonRoundPlus.setOnClickListener(view -> RoundNumber.setText(String.valueOf(getRoundNumber() + 1)));
        ButtonReaderMaster.setOnClickListener(view -> setPhoneReads(false));
        ButtonReaderPhone.setOnClickListener(view -> setPhoneReads(true));
        ButtonSignalBeep.setOnClickListener(view -> setSignals(!beep, vibrate));
        ButtonSignalVibrate.setOnClickListener(view -> setSignals(beep, !vibrate));
        ButtonStreakBonus.setOnClickListener(view -> setStreakBonus(!streakBonus));
        ButtonNextRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                database.resetScores();
                gameState.start(getRoundNumber(), phoneReads);
                gameState.setSignals(beep, vibrate);
                gameState.setStreakBonus(streakBonus);
                Intent intentStart = new Intent(RoundActivity.this, QuestionActivity.class);
                startActivity(intentStart);
                finish();
            }
        });
    }
    private void init() {
        ButtonBackRound = findViewById(R.id.ButtonBackRound);
        ButtonNextRound = findViewById(R.id.ButtonNextRound);
        FirstPlayer = findViewById(R.id.FirstPlayer);
        RoundNumber = findViewById(R.id.RoundNumber);
        ButtonRoundMinus = findViewById(R.id.ButtonRoundMinus);
        ButtonRoundPlus = findViewById(R.id.ButtonRoundPlus);
        ButtonReaderMaster = findViewById(R.id.ButtonReaderMaster);
        ButtonReaderPhone = findViewById(R.id.ButtonReaderPhone);
        ButtonSignalBeep = findViewById(R.id.ButtonSignalBeep);
        ButtonSignalVibrate = findViewById(R.id.ButtonSignalVibrate);
        ButtonStreakBonus = findViewById(R.id.ButtonStreakBonus);
        database = new Database(this);
        gameState = new GameState(this);
        setPhoneReads(gameState.isPhoneReading());
        setSignals(gameState.isBeeping(), gameState.isVibrating());
        setStreakBonus(gameState.isStreakBonus());
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
        if (firstPlayerName != null)
        {
            firstPlayerName.close();
        }

    }

    // the chosen reader is filled, the other one is outlined
    private void setPhoneReads(boolean phoneReads) {
        this.phoneReads = phoneReads;
        showSelected(ButtonReaderPhone, phoneReads);
        showSelected(ButtonReaderMaster, !phoneReads);
    }

    private void setSignals(boolean beep, boolean vibrate) {
        this.beep = beep;
        this.vibrate = vibrate;
        showSelected(ButtonSignalBeep, beep);
        showSelected(ButtonSignalVibrate, vibrate);
    }

    private void setStreakBonus(boolean streakBonus) {
        this.streakBonus = streakBonus;
        showSelected(ButtonStreakBonus, streakBonus);
    }

    private void showSelected(Button button, boolean selected) {
        button.setBackgroundResource(selected ? R.drawable.btn_primary : R.drawable.btn_secondary);
        button.setTextColor(ContextCompat.getColorStateList(this,
                selected ? R.color.onPrimary : R.color.text_secondary_button));
    }

    private int getRoundNumber() {
        try {
            return Math.max(1, Integer.parseInt(RoundNumber.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
