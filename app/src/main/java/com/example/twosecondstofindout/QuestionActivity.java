package com.example.twosecondstofindout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Choreographer;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class QuestionActivity extends AppCompatActivity {

    private TextView CurrentPlayer;
    private TextView Asker;
    private TextView Question;
    private TextView Answer;
    // the answer is hidden until it is tapped, or until the phone has read the question
    private String currentAnswerText = "";
    private int defaultAnswerColor;
    private TextView TimerText;
    private Button ButtonStartStop;
    private Button ButtonRepeat;
    private TextView FlagQuestion;
    private boolean timerStarted;
    private Database database;

    private Button ButtonOk;
    private Button ButtonNotOk;
    private Button ButtonSkip;
    private Button ButtonUndo;
    private Button ButtonPass;
    private View OkButtons;
    private LinearLayout FlashButtons;
    private View QuestionCard;
    private View TimerArea;
    private View RoundArea;
    private View AnswerButtons;
    private TextView RoundTitle;
    private TextView RoundStandings;
    private Button ButtonNextRound;
    private ToneGenerator toneGenerator;
    // e.g. the Samsung engine has no Hungarian voice, the Google one usually has
    private static final String GOOGLE_TEXT_TO_SPEECH = "com.google.android.tts";
    private TextToSpeech textToSpeech;
    private boolean triedGoogleTextToSpeech;
    // false until a Hungarian voice is ready, until then the button is a plain Start
    private boolean canReadAloud;
    // the question being read aloud, the timer starts when it is finished
    private String readingId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    // elapsed time in milliseconds, measured from the moment Start was pressed
    private long time = 0;
    private long startTime;
    private int defaultTimerColor;

    private GameState gameState;
    private int rounds;
    // in a team game every member answers in a round
    private int answersPerRound;
    private int currentPlayerId;
    private int currentTopic;
    private int currentQuestionId;
    // the time to answer of the current player
    private int currentAnswerMs = Database.DEFAULT_ANSWER_MS;
    // who answers now: the player, or the team member on turn
    private String currentAnswerer = "";
    // without a game master: {the player who holds the phone now, their team or empty}, null if none
    private String[] currentAsker;
    // the phone is only announced when it goes to another player
    private String announcedAsker;
    // the phone reads the questions, so the one holding it only checks the answer
    private boolean askerChecks;
    // what the phone says before the question, e.g. "Anna következik!"
    private String announcement = "";
    // a flash round: a question for everybody, the one who answers first gets a point
    private boolean flashTurn;
    // after the standings of the round comes a flash round
    private boolean flashNext;
    // anybody can answer: every question is a flash question
    private boolean flashOnly;
    // without a game master in a flash only game: the player or team with the phone does not answer
    private int flashAskerId = -1;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_question);
        getOnBackPressedDispatcher().addCallback(this, backToMainMenu);
        init();
        rounds = gameState.getRounds();
        answersPerRound = database.answersPerRound();
        // after an undo on the results screen the undone question comes back
        int undoneQuestionId = getIntent().getIntExtra("questionId", -1);
        flashOnly = gameState.isFlashOnly();
        if (flashOnly) {
            if (undoneQuestionId >= 0) {
                showFlash(undoneQuestionId);
            } else {
                nextFlash();
            }
        } else if (undoneQuestionId >= 0) {
            showTurn(undoneQuestionId);
        } else if (gameState.isFlashPending()) {
            showFlash(-1);
        } else {
            nextTurn();
        }
        ButtonStartStop.setOnClickListener(view -> begin());
        // the player did not hear the question: the phone reads it again,
        // or the game master reads it again and presses Start
        ButtonRepeat.setOnClickListener(view -> {
            stopTimer();
            if (canReadAloud) {
                readQuestion(false);
            }
        });
        FlagQuestion.setOnClickListener(view -> {
            database.setQuestionFlagged(currentQuestionId, !database.isQuestionFlagged(currentQuestionId));
            showFlag();
        });
        ButtonOk.setOnClickListener(view -> {
            if(timerStarted){
                long elapsed = SystemClock.elapsedRealtime() - startTime;
                stopTimer();
                int points = database.savePlayerAnswer(currentPlayerId, currentQuestionId, true, gameState.isStreakBonus(), elapsed);
                if (points > 1) {
                    Toast.makeText(this, "🔥 " + Database.STREAK_LENGTH + " találat egymás után: +1 bónuszpont!", Toast.LENGTH_LONG).show();
                }
                nextTurn();
            }
        });
        // takes back the last Siker / Késő, flash round or pass, its question comes back
        ButtonUndo.setOnClickListener(view -> {
            stopTimer();
            int kind = database.selectLastAnswerKind();
            int questionId = database.undoLastAnswer();
            if (questionId < 0) {
                return;
            }
            if (kind == Database.FLASH) {
                showFlash(questionId);
            } else {
                // the round of the flash round is not over any more
                gameState.setFlashPending(false);
                showTurn(questionId);
            }
        });
        // the player passes, and gets another question without losing a point
        ButtonPass.setOnClickListener(view -> {
            stopTimer();
            if (database.usePass(currentPlayerId, currentQuestionId)) {
                kerdes(currentTopic);
                showPassButton();
                ButtonUndo.setEnabled(true);
            }
        });
        // a new question for the same player, without scoring
        ButtonSkip.setOnClickListener(view -> {
            stopTimer();
            kerdes(currentTopic);
        });
        ButtonNextRound.setOnClickListener(view -> {
            if (flashNext) {
                showFlash(-1);
            } else {
                showTurn(-1);
            }
        });
        ButtonNotOk.setOnClickListener(view -> {
            if(timerStarted){
                stopTimer();
                database.savePlayerAnswer(currentPlayerId, currentQuestionId, false);
                nextTurn();
            }
        });
    }

    // the game is saved, it can be continued from the main menu
    private final OnBackPressedCallback backToMainMenu = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            Intent intent = new Intent(QuestionActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Choreographer.getInstance().removeFrameCallback(timerFrame);
        handler.removeCallbacks(timeUp);
        toneGenerator.release();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
    }

    @SuppressLint("SetTextI18n")
    private void init() {
        CurrentPlayer = findViewById(R.id.CurrentPlayer);
        Asker = findViewById(R.id.Asker);
        Question = findViewById(R.id.Question);
        Answer = findViewById(R.id.Answer);
        defaultAnswerColor = Answer.getCurrentTextColor();
        Answer.setOnClickListener(view -> showAnswer());
        TimerText = findViewById(R.id.Timer);
        ButtonStartStop = findViewById(R.id.ButtonStartStop);
        ButtonRepeat = findViewById(R.id.ButtonRepeat);
        FlagQuestion = findViewById(R.id.FlagQuestion);
        ButtonOk = findViewById(R.id.ButtonOk);
        ButtonNotOk = findViewById(R.id.ButtonNotOk);
        ButtonSkip = findViewById(R.id.ButtonSkip);
        ButtonUndo = findViewById(R.id.ButtonUndo);
        ButtonPass = findViewById(R.id.ButtonPass);
        OkButtons = findViewById(R.id.OkButtons);
        FlashButtons = findViewById(R.id.FlashButtons);
        QuestionCard = findViewById(R.id.QuestionCard);
        TimerArea = findViewById(R.id.TimerArea);
        RoundArea = findViewById(R.id.RoundArea);
        AnswerButtons = findViewById(R.id.AnswerButtons);
        RoundTitle = findViewById(R.id.RoundTitle);
        RoundStandings = findViewById(R.id.RoundStandings);
        ButtonNextRound = findViewById(R.id.ButtonNextRound);
        gameState = new GameState(this);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);
        database = new Database(this);
        defaultTimerColor = TimerText.getCurrentTextColor();
        timerStarted = false;
        // otherwise the game master reads the question and presses Start
        askerChecks = gameState.isPhoneReading();
        if (gameState.isPhoneReading()) {
            textToSpeech = new TextToSpeech(this, this::onTextToSpeechReady);
        }
    }

    //
    // READING THE QUESTION ALOUD
    //
    @SuppressLint("SetTextI18n")
    private void onTextToSpeechReady(int status)
    {
        if (status != TextToSpeech.SUCCESS || !speaksHungarian()) {
            if (!triedGoogleTextToSpeech && hasTextToSpeechEngine(GOOGLE_TEXT_TO_SPEECH)) {
                triedGoogleTextToSpeech = true;
                textToSpeech.shutdown();
                textToSpeech = new TextToSpeech(this, this::onTextToSpeechReady, GOOGLE_TEXT_TO_SPEECH);
            } else {
                Toast.makeText(this, "A telefonon nincs magyar felolvasó hang, " + (gameState.isPlayersAsk() ? "a kérdező" : "a játékmester") + " olvasson fel.", Toast.LENGTH_LONG).show();
                askerChecks = false;
                showAskerText();
            }
            return;
        }
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> readingFinished(utteranceId));
            }

            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> readingFinished(utteranceId));
            }
        });
        canReadAloud = true;
        ButtonStartStop.setText("🔊 Felolvasás");
        // the first turn was shown before the voice was ready
        if (RoundArea.getVisibility() != View.VISIBLE) {
            announceAsker();
        }
    }

    private boolean speaksHungarian()
    {
        int language = textToSpeech.setLanguage(Locale.forLanguageTag("hu-HU"));
        return language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private boolean hasTextToSpeechEngine(String packageName)
    {
        for (TextToSpeech.EngineInfo engine : textToSpeech.getEngines()) {
            if (engine.name.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    // Start, or Felolvasás when the phone reads the question
    private void begin()
    {
        if (canReadAloud) {
            readQuestion(true);
        } else {
            startTimer();
        }
    }

    // Újra takes the place of Start while the question is read or the timer runs
    private void showRepeatButton(boolean running)
    {
        ButtonStartStop.setVisibility(running ? View.GONE : View.VISIBLE);
        ButtonRepeat.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    // announce: first says who answers, e.g. "Anna következik!". Not when the question is read again.
    private void readQuestion(boolean announce)
    {
        if (timerStarted || readingId != null) {
            return;
        }
        readingId = "question-" + currentQuestionId + "-" + SystemClock.elapsedRealtime();
        showRepeatButton(true);
        int queueMode = TextToSpeech.QUEUE_FLUSH;
        if (announce && !announcement.isEmpty()) {
            textToSpeech.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "announce-" + readingId);
            textToSpeech.playSilentUtterance(300, TextToSpeech.QUEUE_ADD, "pause-" + readingId);
            queueMode = TextToSpeech.QUEUE_ADD;
        }
        if (textToSpeech.speak(Question.getText(), queueMode, null, readingId) == TextToSpeech.ERROR) {
            readingId = null;
            showAnswer();
            startTimer();
        }
    }

    // e.g. "Béla ellenőriz, nála legyen a telefon.", so the phone is passed on before Felolvasás.
    // Only when the phone goes to another player.
    private void announceAsker()
    {
        if (canReadAloud && currentAsker != null && !currentAsker[0].equals(announcedAsker) && readingId == null && !timerStarted) {
            announcedAsker = currentAsker[0];
            textToSpeech.speak(currentAsker[0] + " ellenőriz, nála legyen a telefon.", TextToSpeech.QUEUE_FLUSH, null, "asker-" + SystemClock.elapsedRealtime());
        }
    }

    // the timer starts right after the question was read, unless it was interrupted by Kihagyás / Visszavonás
    private void readingFinished(String utteranceId)
    {
        if (!utteranceId.equals(readingId)) {
            return;
        }
        readingId = null;
        // the phone has read the question, now the one with the phone can see the answer
        showAnswer();
        startTimer();
    }

    // Called after every Siker / Késő. When every active player answered the same number
    // of questions (in a team game: every member one) a round is over: the standings are shown, and after the last round the
    // players with the most points play tie-break rounds until there is one winner.
    private void nextTurn()
    {
        flashNext = false;
        int[] answeredRange = database.selectActiveAnsweredRange();
        boolean roundOver = answeredRange[0] == answeredRange[1] && answeredRange[0] > 0 && answeredRange[0] % answersPerRound == 0;
        int finishedRounds = answeredRange[0] / answersPerRound;
        if (!roundOver)
        {
            showTurn(-1);
            return;
        }
        // until somebody has the target points at the end of a round, or for the rounds
        boolean goesOn = gameState.isByPoints()
                ? database.selectMostActivePoints() < gameState.getTargetPoints()
                : finishedRounds < rounds;
        if (goesOn)
        {
            flashNext = gameState.isFlash();
            showRoundStandings(finishedRounds + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
        showRoundStandings("Holtverseny!", "Szétszavazó kör: " + database.selectActivePlayerNames() + "\n\n" + database.selectScoreboardText());
    }

    // Only flash questions: a round has a question for every player or team. After a round the
    // standings are shown, and the game is over like with the turns: after the rounds, or when
    // somebody has the target points. The leaders play tie-break rounds.
    private void nextFlash()
    {
        flashNext = false;
        int asked = database.countFlashAnswers();
        int perRound = Math.max(1, database.countPlayers());
        if (asked == 0 || asked % perRound != 0)
        {
            showFlash(-1);
            return;
        }
        int finishedRounds = asked / perRound;
        boolean goesOn = gameState.isByPoints()
                ? database.selectMostActivePoints() < gameState.getTargetPoints()
                : finishedRounds < rounds;
        if (goesOn)
        {
            flashNext = true;
            showRoundStandings(finishedRounds + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
        flashNext = true;
        showRoundStandings("Holtverseny!", "Szétszavazó kör: " + database.selectActivePlayerNames() + "\n\n" + database.selectScoreboardText());
    }

    // the standings take the place of the timer, the game buttons are hidden until Tovább
    private void showRoundStandings(String title, String standings)
    {
        RoundTitle.setText(title);
        RoundStandings.setText(standings.trim());
        setRoundAreaVisible(true);
    }

    private void setRoundAreaVisible(boolean visible)
    {
        RoundArea.setVisibility(visible ? View.VISIBLE : View.GONE);
        TimerArea.setVisibility(visible ? View.GONE : View.VISIBLE);
        AnswerButtons.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (visible) {
            ButtonNextRound.setText(flashNext && !flashOnly ? "⚡ Jöhet a villámkör" : "Tovább");
        }
        // the last player and question stay readable, but faded
        CurrentPlayer.setAlpha(visible ? 0.35f : 1f);
        Asker.setAlpha(visible ? 0.35f : 1f);
        QuestionCard.setAlpha(visible ? 0.35f : 1f);
    }

    // Shows the next player with a new question, or with the given one after an undo
    @SuppressLint("SetTextI18n")
    private void showTurn(int questionId)
    {
        flashNext = false;
        setRoundAreaVisible(false);
        showFlashButtons(false);
        Cursor player = database.selectNextPlayer(gameState.isPlayersAsk());
        if (!player.moveToFirst())
        {
            player.close();
            finishGame();
            return;
        }
        currentPlayerId = player.getInt(0);
        int answered = player.getInt(2);
        // in a team game the team members answer in turns, each with their own level and time
        Database.Member member = database.selectMemberOnTurn(currentPlayerId, answered);
        String name = member == null ? player.getString(1) : player.getString(1) + " · " + member.name;
        currentAnswerer = member == null ? player.getString(1) : member.name;
        announcement = currentAnswerer + " következik!";
        currentAnswerMs = member == null ? player.getInt(4) : member.answerMs;
        currentTopic = member == null ? player.getInt(3) : member.topic;
        // a time other than the usual 2 seconds is shown, e.g. "Anna ⏱ 4 mp"
        if (currentAnswerMs != Database.DEFAULT_ANSWER_MS)
        {
            name += " ⏱ " + PlayersActivity.formatSeconds(currentAnswerMs);
        }
        int round = answered / answersPerRound;
        boolean byPoints = gameState.isByPoints();
        if (byPoints ? database.hasInactivePlayers() : round >= rounds)
        {
            CurrentPlayer.setText(name + " (szétszavazó)");
        }
        else if (byPoints)
        {
            CurrentPlayer.setText(name + " (" + (round + 1) + ". kör · " + gameState.getTargetPoints() + " pontig)");
        }
        else
        {
            CurrentPlayer.setText(name + " (" + (round + 1) + ". kör / " + rounds + ")");
        }
        player.close();
        showAsker(answered);
        // the undone question may have been deleted in the meantime
        if (questionId < 0 || !showQuestion(questionId))
        {
            kerdes(currentTopic);
        }
        showPassButton();
        ButtonUndo.setEnabled(database.hasAnswers());
    }

    // 🃏 with the passes left, only if passes are on
    @SuppressLint("SetTextI18n")
    private void showPassButton()
    {
        int left = database.selectPassesLeft(currentPlayerId);
        ButtonPass.setVisibility(gameState.getPasses() > 0 && !flashTurn ? View.VISIBLE : View.GONE);
        ButtonPass.setText("🃏\n" + left);
        ButtonPass.setEnabled(left > 0);
    }

    // A question for everybody after a round, the game master chooses who answered first.
    // questionId: the question after an undo, or -1 for a new one.
    @SuppressLint("SetTextI18n")
    private void showFlash(int questionId)
    {
        flashNext = false;
        gameState.setFlashPending(!flashOnly);
        setRoundAreaVisible(false);
        int asked = database.countFlashAnswers();
        flashAskerId = -1;
        String[] flashAsker = flashOnly && gameState.isPlayersAsk() ? database.selectFlashAsker(asked) : null;
        if (flashAsker != null) {
            flashAskerId = Integer.parseInt(flashAsker[2]);
        }
        showFlashButtons(true);
        if (flashOnly) {
            int perRound = Math.max(1, database.countPlayers());
            int round = asked / perRound;
            boolean byPoints = gameState.isByPoints();
            String progress = (byPoints ? database.hasInactivePlayers() : round >= rounds) ? "szétszavazó"
                    : byPoints ? (round + 1) + ". kör · " + gameState.getTargetPoints() + " pontig"
                    : (round + 1) + ". kör / " + rounds;
            CurrentPlayer.setText("⚡ Bárki válaszolhat! (" + progress + ")");
        } else {
            CurrentPlayer.setText("⚡ Villámkör – bárki válaszolhat!");
        }
        currentAnswerer = "";
        announcement = "Villámkör! Bárki válaszolhat.";
        currentAnswerMs = Database.DEFAULT_ANSWER_MS;
        // without a game master the one with the phone asks, the asker of the next turn
        currentAsker = flashAsker == null ? null : new String[]{flashAsker[0], flashAsker[1]};
        if (!flashOnly && gameState.isPlayersAsk()) {
            Cursor next = database.selectNextPlayer(true);
            if (next.moveToFirst()) {
                currentAsker = database.selectAsker(next.getInt(0), next.getInt(2));
            }
            next.close();
        }
        showAskerText();
        announceAsker();
        currentTopic = database.selectRandomActiveTopic();
        if (questionId < 0 || !showQuestion(questionId))
        {
            kerdes(currentTopic);
        }
        ButtonUndo.setEnabled(database.hasAnswers());
    }

    // the players' buttons take the place of Siker / Késő in a flash round
    private void showFlashButtons(boolean flash)
    {
        flashTurn = flash;
        OkButtons.setVisibility(flash ? View.GONE : View.VISIBLE);
        FlashButtons.setVisibility(flash ? View.VISIBLE : View.GONE);
        // the title stays, the buttons are made again
        while (FlashButtons.getChildCount() > 1) {
            FlashButtons.removeViewAt(1);
        }
        if (!flash) {
            return;
        }
        LinearLayout row = null;
        Cursor players = database.selectActivePlayers();
        while (players.moveToNext()) {
            // three in a row
            if (row == null || row.getChildCount() == 3) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                FlashButtons.addView(row);
            }
            Button button = flashButton(players.getString(1), players.getInt(0), false);
            // the one with the phone sees the answer
            if (players.getInt(0) == flashAskerId) {
                button.setEnabled(false);
                button.setAlpha(0.4f);
            }
            row.addView(button);
        }
        players.close();
        LinearLayout nobody = new LinearLayout(this);
        nobody.addView(flashButton("Senki", 0, true));
        FlashButtons.addView(nobody);
    }

    // playerId: who gets the point, 0 for nobody
    private Button flashButton(String name, int playerId, boolean secondary)
    {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMaxLines(1);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setBackgroundResource(secondary ? R.drawable.btn_secondary : R.drawable.btn_success);
        button.setTextColor(ContextCompat.getColorStateList(this, secondary ? R.color.text_secondary_button : R.color.onPrimary));
        int margin = Math.round(4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Math.round(52 * getResources().getDisplayMetrics().density), 1);
        params.setMargins(margin, margin, margin, margin);
        button.setLayoutParams(params);
        button.setOnClickListener(view -> {
            // like Siker, only after the question was read and the timer started
            if (!timerStarted) {
                return;
            }
            stopTimer();
            database.saveFlashAnswer(playerId, currentQuestionId);
            gameState.setFlashPending(false);
            if (playerId > 0) {
                Toast.makeText(this, "⚡ +1 pont: " + name, Toast.LENGTH_SHORT).show();
            }
            if (flashOnly) {
                nextFlash();
            } else {
                showTurn(-1);
            }
        });
        return button;
    }

    // without a game master the asker of the round holds the phone
    private void showAsker(int answered)
    {
        currentAsker = gameState.isPlayersAsk() ? database.selectAsker(currentPlayerId, answered) : null;
        showAskerText();
        announceAsker();
    }

    // e.g. "📱 Béla (Kékek) kérdez", or "ellenőriz" when the phone reads the question
    @SuppressLint("SetTextI18n")
    private void showAskerText()
    {
        if (currentAsker == null) {
            Asker.setVisibility(View.GONE);
            return;
        }
        String team = currentAsker[1].isEmpty() ? "" : " (" + currentAsker[1] + ")";
        Asker.setText("📱 " + currentAsker[0] + team + (askerChecks ? " ellenőriz" : " kérdez") + "\nNála legyen a telefon!"
                + (flashTurn ? " Most ő nem válaszol." : ""));
        Asker.setVisibility(View.VISIBLE);
    }

    private void finishGame()
    {
        database.saveGameResult();
        gameState.setInProgress(false);
        Intent intent = new Intent(QuestionActivity.this, ResultActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean showQuestion(int questionId)
    {
        Cursor question = database.selectQuestion(questionId);
        boolean found = question.moveToFirst();
        if (found)
        {
            currentQuestionId = questionId;
            Question.setText(question.getString(0));
            hideAnswer(question.getString(1));
            showFlag();
        }
        question.close();
        return found;
    }

    @SuppressLint("SetTextI18n")
    public void kerdes(int topic)
    {
        Cursor question = database.selectRandomQuestion(topic);
        if (question == null || !question.moveToFirst())
        {
            if (question != null)
            {
                question.close();
            }
            Question.setText("Ebben a témában nincs kérdés.");
            hideAnswer("");
            ButtonStartStop.setVisibility(View.INVISIBLE);
            FlagQuestion.setVisibility(View.GONE);
            return;
        }
        currentQuestionId = question.getInt(0);
        database.markQuestionUsed(currentQuestionId);
        Question.setText(question.getString(1));
        hideAnswer(question.getString(2));
        question.close();
        showFlag();
    }

    @SuppressLint("SetTextI18n")
    private void showFlag()
    {
        boolean flagged = database.isQuestionFlagged(currentQuestionId);
        FlagQuestion.setVisibility(View.VISIBLE);
        FlagQuestion.setText(flagged ? "⚑ Megjelölve hibásnak" : "⚑ Hibás kérdés?");
        FlagQuestion.setTextColor(ContextCompat.getColor(this, flagged ? R.color.danger : R.color.textSecondary));
    }

    //
    // TIMER FUNCTIONS
    //
    // redraws the timer on every frame of the screen, so the milliseconds keep running smoothly
    private final Choreographer.FrameCallback timerFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            time = SystemClock.elapsedRealtime() - startTime;
            TimerText.setText(getTimerText());
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    // the player has 2 seconds (or their own time) to answer, the game master decides Siker / Késő
    private final Runnable timeUp = () -> {
        TimerText.setTextColor(ContextCompat.getColor(QuestionActivity.this, R.color.danger));
        // beep and/or vibrate once, so the game master can watch the player
        if (gameState.isBeeping()) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 400);
        }
        if (gameState.isVibrating()) {
            vibrate();
        }
    };

    private void vibrate()
    {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibrator = ((VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(400);
        }
    }

    // A new question: its answer is hidden, so the player cannot see it on the screen
    @SuppressLint("SetTextI18n")
    private void hideAnswer(String answer)
    {
        currentAnswerText = answer;
        if (answer.isEmpty()) {
            Answer.setText("");
            return;
        }
        Answer.setText("👁 Koppints a válaszért");
        Answer.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
    }

    private void showAnswer()
    {
        Answer.setText(currentAnswerText);
        Answer.setTextColor(defaultAnswerColor);
    }

    public void startTimer()
    {
        if(timerStarted){
            return;
        }
        timerStarted = true;
        time = 0;
        startTime = SystemClock.elapsedRealtime();
        showRepeatButton(true);
        Choreographer.getInstance().postFrameCallback(timerFrame);
        handler.postDelayed(timeUp, currentAnswerMs);
    }

    @SuppressLint("SetTextI18n")
    private void stopTimer()
    {
        if (readingId != null) {
            readingId = null;
            textToSpeech.stop();
        }
        Choreographer.getInstance().removeFrameCallback(timerFrame);
        handler.removeCallbacks(timeUp);
        timerStarted = false;
        time = 0;
        TimerText.setText(getTimerText());
        TimerText.setTextColor(defaultTimerColor);
        showRepeatButton(false);
    }

    // seconds and milliseconds, e.g. 1,123
    @SuppressLint("DefaultLocale")
    private String getTimerText(){
        return String.format("%d,%03d", time / 1000, time % 1000);
    }
}
