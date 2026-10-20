package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class RoundActivity extends AppCompatActivity {
    private Button ButtonBackRound;
    private Button ButtonNextRound;
    private TextView FirstPlayer;
    private TextView RoundTotal;
    private EditText RoundNumber;
    private Button ButtonRoundMinus;
    private Button ButtonRoundPlus;
    private Button ButtonReaderMaster;
    private Button ButtonReaderPhone;
    private Button ButtonAskerMaster;
    private Button ButtonAskerPlayers;
    private TextView AskerHint;
    private Button ButtonSignalBeep;
    private Button ButtonSignalVibrate;
    private Button ButtonStreakBonus;
    private Button ButtonGoalRounds;
    private Button ButtonGoalPoints;
    private TextView RoundText;
    private Button[] PassButtons;
    private Button ButtonAnswerOne;
    private Button ButtonAnswerAnybody;
    private TextView AnswerHint;
    // hidden when anybody can answer: only for the questions of one player
    private View[] TurnSettings;
    private Database database;
    private GameState gameState;
    private boolean phoneReads;
    private boolean playersAsk;
    private boolean beep;
    private boolean vibrate;
    private boolean streakBonus;
    // the game is played until somebody has the target points, otherwise for the rounds
    private boolean byPoints;
    // the number of the other goal, while the number field shows one of them
    private int otherGoalNumber;
    private int passes;
    // anybody can answer: only flash questions, whoever answers first gets the point
    private boolean flashOnly;
    private String[] firstFlashAsker;
    // the first player, and the first asker when the players ask each other
    private String firstPlayer;
    private String[] firstAsker;
    private int playerCount;
    private int answersPerRound;

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
        RoundNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                showRoundTotal();
            }
        });
        ButtonReaderMaster.setOnClickListener(view -> setPhoneReads(false));
        ButtonReaderPhone.setOnClickListener(view -> setPhoneReads(true));
        ButtonAskerMaster.setOnClickListener(view -> setPlayersAsk(false));
        ButtonAskerPlayers.setOnClickListener(view -> setPlayersAsk(true));
        ButtonSignalBeep.setOnClickListener(view -> setSignals(!beep, vibrate));
        ButtonSignalVibrate.setOnClickListener(view -> setSignals(beep, !vibrate));
        ButtonStreakBonus.setOnClickListener(view -> setStreakBonus(!streakBonus));
        ButtonGoalRounds.setOnClickListener(view -> setByPoints(false));
        ButtonGoalPoints.setOnClickListener(view -> setByPoints(true));
        ButtonAnswerOne.setOnClickListener(view -> setFlashOnly(false));
        ButtonAnswerAnybody.setOnClickListener(view -> setFlashOnly(true));
        for (int i = 0; i < PassButtons.length; i++) {
            final int count = i;
            PassButtons[i].setOnClickListener(view -> setPasses(count));
        }
        ButtonNextRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // alone you would see the answer
                if (playersAsk && playerCount < 2) {
                    Toast.makeText(RoundActivity.this, "Egymást kérdezni legalább két játékossal vagy csapattal lehet!", Toast.LENGTH_LONG).show();
                    return;
                }
                database.resetScores(flashOnly ? 0 : passes);
                gameState.setFlashOnly(flashOnly);
                gameState.start(byPoints ? otherGoalNumber : getRoundNumber(), phoneReads);
                gameState.setGoal(byPoints, byPoints ? getRoundNumber() : otherGoalNumber);
                gameState.setPasses(passes);
                gameState.setSignals(beep, vibrate);
                gameState.setStreakBonus(streakBonus);
                gameState.setPlayersAsk(playersAsk);
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
        RoundTotal = findViewById(R.id.RoundTotal);
        RoundNumber = findViewById(R.id.RoundNumber);
        ButtonRoundMinus = findViewById(R.id.ButtonRoundMinus);
        ButtonRoundPlus = findViewById(R.id.ButtonRoundPlus);
        ButtonReaderMaster = findViewById(R.id.ButtonReaderMaster);
        ButtonReaderPhone = findViewById(R.id.ButtonReaderPhone);
        ButtonAskerMaster = findViewById(R.id.ButtonAskerMaster);
        ButtonAskerPlayers = findViewById(R.id.ButtonAskerPlayers);
        AskerHint = findViewById(R.id.AskerHint);
        ButtonSignalBeep = findViewById(R.id.ButtonSignalBeep);
        ButtonSignalVibrate = findViewById(R.id.ButtonSignalVibrate);
        ButtonStreakBonus = findViewById(R.id.ButtonStreakBonus);
        ButtonGoalRounds = findViewById(R.id.ButtonGoalRounds);
        ButtonGoalPoints = findViewById(R.id.ButtonGoalPoints);
        RoundText = findViewById(R.id.RoundText);
        PassButtons = new Button[]{findViewById(R.id.ButtonPass0), findViewById(R.id.ButtonPass1), findViewById(R.id.ButtonPass2)};
        ButtonAnswerOne = findViewById(R.id.ButtonAnswerOne);
        ButtonAnswerAnybody = findViewById(R.id.ButtonAnswerAnybody);
        AnswerHint = findViewById(R.id.AnswerHint);
        // the streak bonus is also for the „Bárki válaszolhat” games
        TurnSettings = new View[]{findViewById(R.id.PassTitle), findViewById(R.id.PassRow), findViewById(R.id.PassHint)};
        database = new Database(this);
        gameState = new GameState(this);
        firstPlayer = database.selectFirstPlayerName();
        firstAsker = database.selectFirstAsker();
        firstFlashAsker = database.selectFlashAsker(0);
        playerCount = database.countPlayers();
        answersPerRound = database.answersPerRound();
        setPhoneReads(gameState.isPhoneReading());
        setPlayersAsk(gameState.isPlayersAsk());
        setSignals(gameState.isBeeping(), gameState.isVibrating());
        setStreakBonus(gameState.isStreakBonus());
        setPasses(Math.min(gameState.getPasses(), PassButtons.length - 1));
        // the number field shows the rounds first (as in the last game), the target points are the other number
        RoundNumber.setText(String.valueOf(Math.max(1, gameState.getRounds())));
        otherGoalNumber = gameState.getTargetPoints();
        setByPoints(gameState.isByPoints());
        setFlashOnly(gameState.isFlashOnly());
    }

    // without a game master the phone goes to the first asker before the game starts
    @SuppressLint("SetTextI18n")
    private void showFirstPlayer() {
        if (flashOnly) {
            if (playersAsk && firstFlashAsker != null) {
                String team = firstFlashAsker[1].isEmpty() ? "" : " (" + firstFlashAsker[1] + ")";
                FirstPlayer.setText("Az első kérdező: " + firstFlashAsker[0] + team + "\nNála legyen a telefon! Az első kérdésre nem válaszol.");
            } else {
                FirstPlayer.setText("Az első kérdés bárkié!");
            }
        } else if (playersAsk && firstAsker != null) {
            String team = firstAsker[1].isEmpty() ? "" : " (" + firstAsker[1] + ")";
            FirstPlayer.setText("Az első kérdező: " + firstAsker[0] + team + "\nNála legyen a telefon!");
        } else {
            FirstPlayer.setText("Az első játékos: " + firstPlayer);
        }
    }

    // the number field shows the rounds or the target points, the other number is kept
    @SuppressLint("SetTextI18n")
    private void setByPoints(boolean byPoints) {
        if (byPoints != this.byPoints) {
            int shown = getRoundNumber();
            RoundNumber.setText(String.valueOf(otherGoalNumber));
            otherGoalNumber = shown;
        }
        this.byPoints = byPoints;
        showSelected(ButtonGoalPoints, byPoints);
        showSelected(ButtonGoalRounds, !byPoints);
        RoundText.setText(byPoints ? "Hány pontig?" : "Hány kör legyen?");
        showRoundTotal();
    }

    private void setPasses(int passes) {
        this.passes = passes;
        for (int i = 0; i < PassButtons.length; i++) {
            showSelected(PassButtons[i], i == passes);
        }
    }

    // e.g. "Mindenki 3 kérdést kap, összesen 9." In a team game every member answers in a round.
    @SuppressLint("SetTextI18n")
    private void showRoundTotal() {
        if (flashOnly && !byPoints) {
            RoundTotal.setText("Egy körben annyi kérdés van, ahány " + (answersPerRound > 1 ? "csapat" : "játékos") + ": összesen " + getRoundNumber() * playerCount + " kérdés.");
            return;
        }
        if (byPoints) {
            RoundTotal.setText("Mindig egy teljes kör végén nézzük meg: az nyer, akinek ekkor legalább " + getRoundNumber() + " pontja van, és a legtöbb.");
            return;
        }
        int perPerson = getRoundNumber();
        int total = getRoundNumber() * answersPerRound * playerCount;
        RoundTotal.setText((answersPerRound > 1 ? "Minden tag " : "Mindenki ") + perPerson + " kérdést kap, összesen " + total + ".");
    }

    // the chosen reader is filled, the other one is outlined
    private void setPhoneReads(boolean phoneReads) {
        this.phoneReads = phoneReads;
        showSelected(ButtonReaderPhone, phoneReads);
        showSelected(ButtonReaderMaster, !phoneReads);
    }

    // without a game master the player before the one on turn asks, and the phone goes round
    @SuppressLint("SetTextI18n")
    private void setPlayersAsk(boolean playersAsk) {
        this.playersAsk = playersAsk;
        showSelected(ButtonAskerPlayers, playersAsk);
        showSelected(ButtonAskerMaster, !playersAsk);
        showFirstPlayer();
        showAskerHint();
    }

    @SuppressLint("SetTextI18n")
    private void showAskerHint() {
        if (!playersAsk) {
            AskerHint.setText("A játékmesternél van a telefon, ő kérdez mindenkit.");
        } else if (flashOnly) {
            AskerHint.setText("Minden kérdés után a telefon a következő játékoshoz kerül. Aki kérdez, az arra a kérdésre nem válaszol, így egy körben mindenki egyszer marad ki.");
        } else {
            AskerHint.setText("Az első körben az utolsó játékos kérdez végig mindenkit, a másodikban az utolsó előtti, és így tovább. A kérdezőt a következő kör kérdezője kérdezi.");
        }
    }

    // anybody can answer: every question is a flash question, the settings of the turns are hidden
    @SuppressLint("SetTextI18n")
    private void setFlashOnly(boolean flashOnly) {
        this.flashOnly = flashOnly;
        showSelected(ButtonAnswerAnybody, flashOnly);
        showSelected(ButtonAnswerOne, !flashOnly);
        AnswerHint.setText(flashOnly
                ? "Minden kérdés mindenkié: aki elsőként rávágja, pontot kap."
                : "Mindig egy játékos kap kérdést, sorban egymás után.");
        for (View setting : TurnSettings) {
            setting.setVisibility(flashOnly ? View.GONE : View.VISIBLE);
        }
        showFirstPlayer();
        showAskerHint();
        showRoundTotal();
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
