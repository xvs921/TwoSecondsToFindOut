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
import android.util.TypedValue;
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
    // a flash question: a question for everybody, the one who answers first gets a point
    private boolean flashTurn;
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
        if (savedInstanceState != null) {
            // the screen was made again (e.g. the phone was turned): the same question stays,
            // the phone is not passed on again
            announcedAsker = savedInstanceState.getString(STATE_ANNOUNCED_ASKER);
            if (savedInstanceState.getBoolean(STATE_ROUND_OVER)) {
                // the standings are shown again
                if (flashOnly) {
                    nextFlash();
                } else {
                    nextTurn();
                }
            } else if (flashOnly) {
                showFlash(savedInstanceState.getInt(STATE_QUESTION_ID, -1));
            } else {
                showTurn(savedInstanceState.getInt(STATE_QUESTION_ID, -1));
            }
        } else if (flashOnly) {
            if (undoneQuestionId >= 0) {
                showFlash(undoneQuestionId);
            } else {
                nextFlash();
            }
        } else if (undoneQuestionId >= 0) {
            showTurn(undoneQuestionId);
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
        // takes back the last Siker / Késő, flash question or pass, its question comes back
        ButtonUndo.setOnClickListener(view -> {
            stopTimer();
            int questionId = database.undoLastAnswer();
            if (questionId < 0) {
                return;
            }
            if (flashOnly) {
                showFlash(questionId);
            } else {
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
            if (flashOnly) {
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

    private static final String STATE_QUESTION_ID = "questionId";
    private static final String STATE_ROUND_OVER = "roundOver";
    private static final String STATE_ANNOUNCED_ASKER = "announcedAsker";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_QUESTION_ID, currentQuestionId);
        outState.putBoolean(STATE_ROUND_OVER, RoundArea.getVisibility() == View.VISIBLE);
        outState.putString(STATE_ANNOUNCED_ASKER, announcedAsker);
    }

    // e.g. a phone call or another app: the reading and the timer stop, Start can be pressed again later
    @Override
    protected void onStop() {
        super.onStop();
        stopTimer();
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Choreographer.getInstance().removeFrameCallback(timerFrame);
        handler.removeCallbacks(timeUp);
        if (toneGenerator != null) {
            toneGenerator.release();
        }
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
        // long Hungarian words are broken at a syllable with a hyphen, not anywhere
        Question.setTextLocale(new Locale("hu", "HU"));
        Answer.setTextLocale(new Locale("hu", "HU"));
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
        // with many flash buttons there is less room: the timer gets smaller instead of being cut off
        TimerArea.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> fitTimer());
        RoundArea = findViewById(R.id.RoundArea);
        AnswerButtons = findViewById(R.id.AnswerButtons);
        RoundTitle = findViewById(R.id.RoundTitle);
        RoundStandings = findViewById(R.id.RoundStandings);
        ButtonNextRound = findViewById(R.id.ButtonNextRound);
        gameState = new GameState(this);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);
        } catch (RuntimeException e) {
            // some phones cannot make a tone generator (e.g. too many sounds are open), the game goes on without the beep
            toneGenerator = null;
        }
        database = new Database(this);
        defaultTimerColor = TimerText.getCurrentTextColor();
        timerStarted = false;
        // otherwise the game master reads the question and presses Start
        askerChecks = gameState.isPhoneReading();
        if (gameState.isPhoneReading()) {
            textToSpeech = new TextToSpeech(this, this::onTextToSpeechReady);
        }
    }

    // every question starts with the normal size, fitTimer makes a long one smaller if there is no room
    private void setQuestionText(String text) {
        Question.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        Question.setText(text);
    }

    // The timer circle is at most 200 dp, and as big as the room above the Start button allows.
    // With little room (many flash buttons) the timer and Start are next to each other.
    private void fitTimer() {
        float density = getResources().getDisplayMetrics().density;
        int button = Math.max(ButtonStartStop.getHeight(), ButtonRepeat.getHeight());
        int height = TimerArea.getHeight();
        int room = height - button - Math.round(36 * density);
        boolean sideBySide = room < Math.round(96 * density);
        // even next to each other there is no room: a long question with many players, the question gets smaller
        if (sideBySide && height < button + Math.round(8 * density)
                && Question.getTextSize() > TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 21, getResources().getDisplayMetrics())) {
            Question.post(() -> Question.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20));
            return;
        }
        int size = sideBySide
                ? Math.max(Math.round(48 * density), Math.min(Math.round(96 * density), height - Math.round(8 * density)))
                : Math.min(Math.round(200 * density), room);
        android.view.ViewGroup.LayoutParams params = TimerText.getLayoutParams();
        int orientation = sideBySide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        if (params.height == size && ((LinearLayout) TimerArea).getOrientation() == orientation) {
            return;
        }
        // not during the layout pass that called this
        TimerText.post(() -> {
            ((LinearLayout) TimerArea).setOrientation(orientation);
            params.width = size;
            params.height = size;
            TimerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(16f, 42f * size / (200 * density)));
            TimerText.setLayoutParams(params);
            int gap = Math.round(20 * density);
            for (Button start : new Button[]{ButtonStartStop, ButtonRepeat}) {
                LinearLayout.LayoutParams startParams = (LinearLayout.LayoutParams) start.getLayoutParams();
                startParams.setMargins(sideBySide ? gap : 0, sideBySide ? 0 : gap, 0, 0);
                startParams.width = Math.round((sideBySide ? 160 : 200) * density);
                start.setLayoutParams(startParams);
            }
        });
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
            showRoundStandings(finishedRounds + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
        // also when nobody dropped out, e.g. everybody has the same points
        database.startTieBreak();
        showRoundStandings("Holtverseny!", "Szétszavazó kör: " + database.selectActivePlayerNames() + "\n\n" + database.selectScoreboardText());
    }

    // Only flash questions: a round has a question for every player or team. After a round the
    // standings are shown, and the game is over like with the turns: after the rounds, or when
    // somebody has the target points. The leaders play a tie-break that decides their places one by one.
    private void nextFlash()
    {
        // the one who answers first is placed and drops out, until one is left: 2 tied players
        // need 1 question, 3 need 2, and so on (a question that nobody answered does not count)
        if (database.isTieBreak())
        {
            if (database.countActivePlayers() <= 1)
            {
                finishGame();
            }
            else if (database.isTieBreakStarting())
            {
                showFlashTieBreakStandings();
            }
            else
            {
                showFlash(-1);
            }
            return;
        }
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
            showRoundStandings(finishedRounds + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
        database.startTieBreak();
        showFlashTieBreakStandings();
    }

    private void showFlashTieBreakStandings()
    {
        showRoundStandings("Holtverseny!", "Szétszavazó: " + database.selectActivePlayerNames()
                + "\nAki elsőként jól válaszol, az kerül előre, a többiek folytatják.\n\n" + database.selectScoreboardText());
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
        // the last player and question stay readable, but faded
        CurrentPlayer.setAlpha(visible ? 0.35f : 1f);
        Asker.setAlpha(visible ? 0.35f : 1f);
        QuestionCard.setAlpha(visible ? 0.35f : 1f);
    }

    // Shows the next player with a new question, or with the given one after an undo
    @SuppressLint("SetTextI18n")
    private void showTurn(int questionId)
    {
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
        // a game saved before the tie-break was marked is in it after its rounds too
        if (database.isTieBreak() || (!byPoints && round >= rounds))
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
        setRoundAreaVisible(false);
        int asked = database.countFlashAnswers();
        boolean tieBreak = database.isTieBreak();
        flashAskerId = -1;
        String[] flashAsker = gameState.isPlayersAsk() ? database.selectFlashAsker(asked, tieBreak) : null;
        if (flashAsker != null) {
            flashAskerId = Integer.parseInt(flashAsker[2]);
        }
        int perRound = Math.max(1, database.countPlayers());
        int round = asked / perRound;
        boolean byPoints = gameState.isByPoints();
        String progress = tieBreak ? "szétszavazó"
                : byPoints ? (round + 1) + ". kör · " + gameState.getTargetPoints() + " pontig"
                : (round + 1) + ". kör / " + rounds;
        CurrentPlayer.setText("⚡ Bárki válaszolhat! (" + progress + ")");
        currentAnswerer = "";
        announcement = "Bárki válaszolhat!";
        currentAnswerMs = Database.DEFAULT_ANSWER_MS;
        // without a game master the one with the phone asks, the asker of the next turn
        currentAsker = flashAsker == null ? null : new String[]{flashAsker[0], flashAsker[1]};
        showFlashButtons(true);
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
        // three in a row, four when there are many players, so the timer still fits above them
        int perRow = players.getCount() > 6 ? 4 : 3;
        while (players.moveToNext()) {
            if (row == null || row.getChildCount() == perRow) {
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Math.round(48 * getResources().getDisplayMetrics().density), 1);
        params.setMargins(margin, margin, margin, margin);
        button.setLayoutParams(params);
        button.setOnClickListener(view -> {
            // like Siker, only after the question was read and the timer started
            if (!timerStarted) {
                return;
            }
            stopTimer();
            if (database.isTieBreak()) {
                int points = database.saveFlashTieBreakAnswer(playerId, currentQuestionId, flashAskerId);
                if (playerId > 0) {
                    Toast.makeText(this, "🏅 " + name + " előrébb végzett: +" + points + " pont", Toast.LENGTH_SHORT).show();
                }
                nextFlash();
                return;
            }
            int points = database.saveFlashAnswer(playerId, currentQuestionId, flashAskerId, gameState.isStreakBonus());
            if (points > 1) {
                Toast.makeText(this, "🔥 " + name + ": " + Database.STREAK_LENGTH + " egymás után! +1 bónuszpont", Toast.LENGTH_LONG).show();
            } else if (playerId > 0) {
                Toast.makeText(this, "⚡ +1 pont: " + name, Toast.LENGTH_SHORT).show();
            }
            nextFlash();
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
        database.saveGameResult(flashOnly);
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
            setQuestionText(question.getString(0));
            hideAnswer(question.getString(1));
            showFlag();
            showRepeatButton(false);
        }
        question.close();
        return found;
    }

    @SuppressLint("SetTextI18n")
    public void kerdes(int topic)
    {
        Cursor question = database.selectRandomQuestion(topic);
        if (question == null)
        {
            // e.g. every question of the level was deleted: a question of another level, so the game goes on
            question = database.selectRandomQuestionOfAnyTopic();
            if (question != null)
            {
                Toast.makeText(this, "Ezen a szinten nincs kérdés, ez egy másik szintről jön.", Toast.LENGTH_SHORT).show();
            }
        }
        if (question == null || !question.moveToFirst())
        {
            if (question != null)
            {
                question.close();
            }
            setQuestionText("Nincs egy kérdés sem. Adj hozzá kérdéseket a Kérdések menüben!");
            hideAnswer("");
            ButtonStartStop.setVisibility(View.INVISIBLE);
            FlagQuestion.setVisibility(View.GONE);
            return;
        }
        currentQuestionId = question.getInt(0);
        database.markQuestionUsed(currentQuestionId);
        setQuestionText(question.getString(1));
        hideAnswer(question.getString(2));
        question.close();
        showFlag();
        // Start comes back if it was hidden because there was no question
        showRepeatButton(false);
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
        if (gameState.isBeeping() && toneGenerator != null) {
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
