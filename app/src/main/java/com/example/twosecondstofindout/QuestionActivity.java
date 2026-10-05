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
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class QuestionActivity extends AppCompatActivity {

    private TextView CurrentPlayer;
    private TextView Question;
    private TextView Answer;
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
    private int currentPlayerId;
    private int currentTopic;
    private int currentQuestionId;
    // the time to answer of the current player
    private int currentAnswerMs = Database.DEFAULT_ANSWER_MS;
    // who answers now: the player, or the team member on turn
    private String currentAnswerer = "";

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_question);
        getOnBackPressedDispatcher().addCallback(this, backToMainMenu);
        init();
        rounds = gameState.getRounds();
        // after an undo on the results screen the undone question comes back
        int undoneQuestionId = getIntent().getIntExtra("questionId", -1);
        if (undoneQuestionId >= 0) {
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
                stopTimer();
                int points = database.savePlayerAnswer(currentPlayerId, currentQuestionId, true, gameState.isStreakBonus());
                if (points > 1) {
                    Toast.makeText(this, "🔥 " + Database.STREAK_LENGTH + " találat egymás után: +1 bónuszpont!", Toast.LENGTH_LONG).show();
                }
                nextTurn();
            }
        });
        // takes back the last Siker / Késő, the player gets the same question again
        ButtonUndo.setOnClickListener(view -> {
            stopTimer();
            int questionId = database.undoLastAnswer();
            if (questionId >= 0) {
                showTurn(questionId);
            }
        });
        // a new question for the same player, without scoring
        ButtonSkip.setOnClickListener(view -> {
            stopTimer();
            kerdes(currentTopic);
        });
        ButtonNextRound.setOnClickListener(view -> showTurn(-1));
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
        Question = findViewById(R.id.Question);
        Answer = findViewById(R.id.Answer);
        TimerText = findViewById(R.id.Timer);
        ButtonStartStop = findViewById(R.id.ButtonStartStop);
        ButtonRepeat = findViewById(R.id.ButtonRepeat);
        FlagQuestion = findViewById(R.id.FlagQuestion);
        ButtonOk = findViewById(R.id.ButtonOk);
        ButtonNotOk = findViewById(R.id.ButtonNotOk);
        ButtonSkip = findViewById(R.id.ButtonSkip);
        ButtonUndo = findViewById(R.id.ButtonUndo);
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
                Toast.makeText(this, "A telefonon nincs magyar felolvasó hang, a játékmester olvasson fel.", Toast.LENGTH_LONG).show();
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
        if (announce && !currentAnswerer.isEmpty()) {
            textToSpeech.speak(currentAnswerer + " következik!", TextToSpeech.QUEUE_FLUSH, null, "announce-" + readingId);
            textToSpeech.playSilentUtterance(300, TextToSpeech.QUEUE_ADD, "pause-" + readingId);
            queueMode = TextToSpeech.QUEUE_ADD;
        }
        if (textToSpeech.speak(Question.getText(), queueMode, null, readingId) == TextToSpeech.ERROR) {
            readingId = null;
            startTimer();
        }
    }

    // the timer starts right after the question was read, unless it was interrupted by Kihagyás / Visszavonás
    private void readingFinished(String utteranceId)
    {
        if (!utteranceId.equals(readingId)) {
            return;
        }
        readingId = null;
        startTimer();
    }

    // Called after every Siker / Késő. When every active player answered the same number
    // of questions a round is over: the standings are shown, and after the last round the
    // players with the most points play tie-break rounds until there is one winner.
    private void nextTurn()
    {
        int[] answeredRange = database.selectActiveAnsweredRange();
        boolean roundOver = answeredRange[0] == answeredRange[1] && answeredRange[0] > 0;
        if (!roundOver)
        {
            showTurn(-1);
            return;
        }
        if (answeredRange[0] < rounds)
        {
            showRoundStandings(answeredRange[0] + ". kör vége", database.selectScoreboardText());
            return;
        }
        if (database.keepLeadersActive() <= 1)
        {
            finishGame();
            return;
        }
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
        // the last player and question stay readable, but faded
        CurrentPlayer.setAlpha(visible ? 0.35f : 1f);
        QuestionCard.setAlpha(visible ? 0.35f : 1f);
    }

    // Shows the next player with a new question, or with the given one after an undo
    @SuppressLint("SetTextI18n")
    private void showTurn(int questionId)
    {
        setRoundAreaVisible(false);
        Cursor player = database.selectNextPlayer();
        if (!player.moveToFirst())
        {
            player.close();
            finishGame();
            return;
        }
        currentPlayerId = player.getInt(0);
        int answered = player.getInt(2);
        // in a team game the team members answer in turns
        String member = Database.memberOnTurn(player.getString(4), answered);
        String name = member.isEmpty() ? player.getString(1) : player.getString(1) + " · " + member;
        currentAnswerer = member.isEmpty() ? player.getString(1) : member;
        currentAnswerMs = player.getInt(5);
        // a time other than the usual 2 seconds is shown, e.g. "Anna ⏱ 4 mp"
        if (currentAnswerMs != Database.DEFAULT_ANSWER_MS)
        {
            name += " ⏱ " + PlayersActivity.formatSeconds(currentAnswerMs);
        }
        if (answered >= rounds)
        {
            CurrentPlayer.setText(name + " (szétszavazó)");
        }
        else
        {
            CurrentPlayer.setText(name + " (" + (answered + 1) + ". kör / " + rounds + ")");
        }
        currentTopic = player.getInt(3);
        player.close();
        // the undone question may have been deleted in the meantime
        if (questionId < 0 || !showQuestion(questionId))
        {
            kerdes(currentTopic);
        }
        ButtonUndo.setEnabled(database.hasAnswers());
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
            Answer.setText(question.getString(1));
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
            Answer.setText("");
            ButtonStartStop.setVisibility(View.INVISIBLE);
            FlagQuestion.setVisibility(View.GONE);
            return;
        }
        currentQuestionId = question.getInt(0);
        database.markQuestionUsed(currentQuestionId);
        Question.setText(question.getString(1));
        Answer.setText(question.getString(2));
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
