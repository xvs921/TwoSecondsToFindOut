package com.example.twosecondstofindout;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// Every test starts with an empty database that holds the questions of res/raw/questions.txt
@RunWith(RobolectricTestRunner.class)
public class DatabaseTest {

    // a topic that has no questions in questions.txt, for the tests' own questions
    private static final int TEST_TOPIC = 9;

    private Context context;
    private Database database;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        database = new Database(context);
    }

    @After
    public void tearDown() {
        database.close();
    }

    //
    // QUESTIONS
    //

    @Test
    public void loadsTwoHundredQuestionsPerTopicFromFile() {
        assertEquals(7, Database.TOPICS.length);
        for (int topic = 1; topic <= Database.TOPICS.length; topic++) {
            assertEquals(Database.TOPICS[topic - 1], 200, countQuestions(topic));
        }
    }

    @Test
    public void questionOfTheFileCanBeInMoreTopics() {
        int water = findQuestionId("Mi a víz kémiai képlete?");
        assertArrayEquals(new int[]{2, 7}, database.selectQuestionTopics(water));
    }

    @Test
    public void questionInMoreTopicsIsListedInEach() {
        database.saveQuestion(-1, new int[]{TEST_TOPIC, TEST_TOPIC + 1}, "Közös?", "Igen");
        assertEquals(1, countQuestions(TEST_TOPIC));
        assertEquals(1, countQuestions(TEST_TOPIC + 1));
    }

    @Test
    public void usedQuestionIsNotDrawnInItsOtherTopicEither() {
        database.saveQuestion(-1, new int[]{TEST_TOPIC, TEST_TOPIC + 1}, "Közös?", "Igen");
        database.saveQuestion(-1, new int[]{TEST_TOPIC + 1}, "Csak a másikban?", "Igen");
        int shared = drawQuestion(TEST_TOPIC);
        assertTrue(shared != drawQuestion(TEST_TOPIC + 1));
    }

    @Test
    public void editingTopicsMovesTheQuestion() {
        int id = addTestQuestion("Áthelyezett?", "Igen");
        database.saveQuestion(id, new int[]{TEST_TOPIC + 1}, "Áthelyezett?", "Igen");
        assertEquals(0, countQuestions(TEST_TOPIC));
        assertArrayEquals(new int[]{TEST_TOPIC + 1}, database.selectQuestionTopics(id));
    }

    @Test
    public void randomQuestionDoesNotRepeatUntilTopicIsUsedUp() {
        database.saveQuestion(-1, new int[]{TEST_TOPIC}, "Q1", "A1");
        database.saveQuestion(-1, new int[]{TEST_TOPIC}, "Q2", "A2");
        database.saveQuestion(-1, new int[]{TEST_TOPIC}, "Q3", "A3");

        Set<Integer> drawn = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            drawn.add(drawQuestion(TEST_TOPIC));
        }
        assertEquals(3, drawn.size());

        // all three used: the topic starts over instead of running out
        assertTrue(drawn.contains(drawQuestion(TEST_TOPIC)));
    }

    @Test
    public void randomQuestionOfEmptyTopicIsNull() {
        assertNull(database.selectRandomQuestion(TEST_TOPIC));
    }

    @Test
    public void questionCanBeAddedEditedAndDeleted() {
        database.saveQuestion(-1, new int[]{TEST_TOPIC}, "  Mi a kérdés?  ", " Válasz ");
        Cursor questions = database.selectQuestions(TEST_TOPIC);
        assertTrue(questions.moveToFirst());
        int id = questions.getInt(0);
        assertEquals("Mi a kérdés?", questions.getString(1));
        assertEquals("Válasz", questions.getString(2));
        questions.close();

        database.saveQuestion(id, new int[]{TEST_TOPIC}, "Új kérdés", "Új válasz");
        Cursor edited = database.selectQuestion(id);
        assertTrue(edited.moveToFirst());
        assertEquals("Új kérdés", edited.getString(0));
        assertEquals("Új válasz", edited.getString(1));
        edited.close();

        database.deleteQuestion(id);
        assertEquals(0, countQuestions(TEST_TOPIC));
        assertEquals(0, database.selectQuestionTopics(id).length);
    }

    //
    // PLAYERS AND TURNS
    //

    @Test
    public void emptyNamesAreSkippedAndApostropheWorks() {
        database.insertPlayersForNewGame(new String[]{"O'Brien", "", "   ", null}, new int[]{1, 2, 3, 2});
        assertEquals(1, database.countPlayers());
        Cursor players = database.selectPlayers();
        assertTrue(players.moveToFirst());
        assertEquals("O'Brien", players.getString(0));
        assertEquals(1, players.getInt(1));
        players.close();
    }

    @Test
    public void playersTakeTurnsAndSikerGivesAPoint() {
        startGame("Anna", "Béla");
        int anna = nextPlayerId();
        database.savePlayerAnswer(anna, 1, true);

        int bela = nextPlayerId();
        assertTrue(anna != bela);
        database.savePlayerAnswer(bela, 2, false);

        assertEquals(anna, nextPlayerId());
        assertArrayEquals(new int[]{1, 1}, database.selectActiveAnsweredRange());
        Cursor scoreboard = database.selectScoreboard();
        assertTrue(scoreboard.moveToFirst());
        assertEquals("Anna", scoreboard.getString(0));
        assertEquals(1, scoreboard.getInt(1));
        scoreboard.close();
    }

    @Test
    public void undoRestoresScoreTurnAndQuestion() {
        startGame("Anna", "Béla");
        int anna = nextPlayerId();
        database.savePlayerAnswer(anna, 42, true);

        assertEquals(42, database.undoLastAnswer());
        assertEquals(anna, nextPlayerId());
        assertFalse(database.hasAnswers());
        assertEquals(0, database.selectTotals()[1]);
        Cursor scoreboard = database.selectScoreboard();
        assertTrue(scoreboard.moveToFirst());
        assertEquals(0, scoreboard.getInt(1));
        scoreboard.close();

        assertEquals(-1, database.undoLastAnswer());
    }

    @Test
    public void newGameWithSamePlayersResetsScores() {
        startGame("Anna", "Béla");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.resetScores();
        assertFalse(database.hasAnswers());
        assertArrayEquals(new int[]{0, 0}, database.selectActiveAnsweredRange());
        // the statistics are kept
        assertEquals(1, database.selectTotals()[1]);
    }

    //
    // TIE-BREAK
    //

    @Test
    public void tieBreakKeepsOnlyTheLeaders() {
        startGame("Anna", "Béla", "Cili");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.savePlayerAnswer(nextPlayerId(), 2, true);
        database.savePlayerAnswer(nextPlayerId(), 3, false);

        assertEquals(2, database.keepLeadersActive());
        assertEquals("Anna, Béla", database.selectActivePlayerNames());
    }

    @Test
    public void tieBreakEndsWithOneLeader() {
        startGame("Anna", "Béla");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.savePlayerAnswer(nextPlayerId(), 2, false);
        assertEquals(1, database.keepLeadersActive());
    }

    @Test
    public void undoAfterTieBreakBringsBackTheDroppedPlayer() {
        startGame("Anna", "Béla", "Cili");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.savePlayerAnswer(nextPlayerId(), 2, true);
        int cili = nextPlayerId();
        database.savePlayerAnswer(cili, 3, false);
        database.keepLeadersActive();

        database.undoLastAnswer();
        assertEquals("Anna, Béla, Cili", database.selectActivePlayerNames());
        assertEquals(cili, nextPlayerId());
    }

    @Test
    public void keepLeadersActiveTwiceDropsNobodyMore() {
        startGame("Anna", "Béla", "Cili");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.savePlayerAnswer(nextPlayerId(), 2, true);
        database.savePlayerAnswer(nextPlayerId(), 3, false);
        database.keepLeadersActive();
        // e.g. the game was continued at the end of a round
        assertEquals(2, database.keepLeadersActive());

        database.undoLastAnswer();
        assertEquals("Anna, Béla, Cili", database.selectActivePlayerNames());
    }

    //
    // STATISTICS
    //

    @Test
    public void statisticsCountGamesAnswersAndWins() {
        startGame("Anna", "Béla");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.savePlayerAnswer(nextPlayerId(), 2, false);
        database.saveGameResult();

        assertArrayEquals(new int[]{1, 2, 1}, database.selectTotals());
        Cursor players = database.selectPlayerStatistics();
        assertTrue(players.moveToFirst());
        assertEquals("Anna", players.getString(0));
        assertEquals(1, players.getInt(1));
        assertEquals(1, players.getInt(2));
        assertEquals(1, players.getInt(3));
        players.close();
    }

    @Test
    public void undoOnResultsScreenRemovesTheWin() {
        startGame("Anna");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.saveGameResult();
        database.undoLastAnswer();
        database.deleteLastGameResult();
        assertArrayEquals(new int[]{0, 0, 0}, database.selectTotals());
    }

    @Test
    public void hardestQuestionsNeedTwoAnswers() {
        int question = addTestQuestion("Nehéz?", "Igen");
        startGame("Anna", "Béla");
        database.savePlayerAnswer(nextPlayerId(), question, false);
        assertFalse(hardestContains("Nehéz?"));

        database.savePlayerAnswer(nextPlayerId(), question, false);
        assertTrue(hardestContains("Nehéz?"));
    }

    @Test
    public void statisticsCanBeDeleted() {
        startGame("Anna");
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        database.saveGameResult();
        database.deleteStatistics();
        assertArrayEquals(new int[]{0, 0, 0}, database.selectTotals());
    }

    //
    // BACKUP
    //

    @Test
    public void restoreBringsBackQuestionsAndStatistics() throws JSONException {
        int question = addTestQuestion("Saját kérdés?", "Saját válasz");
        startGame("Anna");
        database.savePlayerAnswer(nextPlayerId(), question, true);
        database.saveGameResult();
        int questionsBefore = countAllQuestions();
        String backup = database.exportJson();

        database.deleteQuestion(question);
        database.deleteStatistics();
        int restored = database.importJson(backup, true);

        assertEquals(questionsBefore, restored);
        assertEquals(questionsBefore, countAllQuestions());
        assertArrayEquals(new int[]{2, 7}, database.selectQuestionTopics(findQuestionId("Mi a víz kémiai képlete?")));
        assertArrayEquals(new int[]{1, 1, 1}, database.selectTotals());
        // the statistics still point to the same question
        assertTrue(hardestOrAnyHistoryFor(question));
        Cursor restoredQuestion = database.selectQuestion(question);
        assertTrue(restoredQuestion.moveToFirst());
        assertEquals("Saját kérdés?", restoredQuestion.getString(0));
        restoredQuestion.close();
    }

    @Test
    public void addingQuestionsFromFileSkipsExistingOnes() throws JSONException {
        int question = addTestQuestion("Barátom kérdése?", "Igen");
        String backup = database.exportJson();
        assertEquals(0, database.importJson(backup, false));

        database.deleteQuestion(question);
        assertEquals(1, database.importJson(backup, false));
        assertEquals(1, countQuestions(TEST_TOPIC));
    }

    @Test
    public void addingQuestionsFromFileAddsMissingTopics() throws JSONException {
        int question = addTestQuestion("Kétkategóriás?", "Igen");
        database.saveQuestion(question, new int[]{TEST_TOPIC, TEST_TOPIC + 1}, "Kétkategóriás?", "Igen");
        String backup = database.exportJson();
        database.saveQuestion(question, new int[]{TEST_TOPIC}, "Kétkategóriás?", "Igen");

        assertEquals(0, database.importJson(backup, false));
        assertArrayEquals(new int[]{TEST_TOPIC, TEST_TOPIC + 1}, database.selectQuestionTopics(question));
    }

    @Test
    public void oldBackupWithOneTopicCanBeRestored() throws JSONException {
        String backup = "{\"app\": \"TwoSecondsToFindOut\", \"version\": 1, \"questions\": ["
                + "{\"id\": 1, \"topic\": 2, \"question\": \"Régi mentés?\", \"answer\": \"Igen\"}]}";
        assertEquals(1, database.importJson(backup, true));
        assertArrayEquals(new int[]{2}, database.selectQuestionTopics(1));
        assertEquals(1, countAllQuestions());
    }

    @Test
    public void foreignFileIsRejected() {
        assertImportFails("nem json");
        assertImportFails("{\"app\": \"Valami más\", \"version\": 1, \"questions\": []}");
        assertImportFails("{\"app\": \"TwoSecondsToFindOut\", \"version\": 99, \"questions\": []}");
    }

    @Test
    public void brokenBackupChangesNothing() throws JSONException {
        JSONObject backup = new JSONObject(database.exportJson());
        JSONArray questions = backup.getJSONArray("questions");
        questions.getJSONObject(5).remove("answer");

        assertImportFails(backup.toString());
        assertEquals(200, countQuestions(1));
    }

    //
    // MIGRATION
    //

    @Test
    public void upgradeFromVersion4KeepsQuestionsAndAddsStatistics() {
        database.close();
        context.deleteDatabase(Database.DATABASE_NAME);
        File file = context.getDatabasePath(Database.DATABASE_NAME);
        file.getParentFile().mkdirs();
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("CREATE TABLE questions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0)");
        old.execSQL("CREATE TABLE players(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), topic INTEGER DEFAULT 2, answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
        old.execSQL("CREATE TABLE answers(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, question_id INTEGER NOT NULL, points INTEGER NOT NULL, deactivated TEXT DEFAULT '')");
        old.execSQL("INSERT INTO questions(topic, question, answer) VALUES (9, 'Régi saját kérdés?', 'Igen')");
        old.execSQL("INSERT INTO players(name) VALUES ('Anna')");
        old.setVersion(4);
        old.close();

        database = new Database(context);
        assertEquals(1, countQuestions(TEST_TOPIC));
        // the new questions of the file are added too
        assertEquals(200, countQuestions(1));
        assertEquals(1, database.countPlayers());
        database.savePlayerAnswer(nextPlayerId(), 1, true);
        assertArrayEquals(new int[]{0, 1, 1}, database.selectTotals());
        assertEquals(1, database.undoLastAnswer());
    }

    @Test
    public void upgradeFromVersion5KeepsOwnQuestionsAndAddsTopics() {
        database.close();
        context.deleteDatabase(Database.DATABASE_NAME);
        File file = context.getDatabasePath(Database.DATABASE_NAME);
        file.getParentFile().mkdirs();
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null);
        old.execSQL("CREATE TABLE questions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0)");
        old.execSQL("CREATE TABLE players(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), topic INTEGER DEFAULT 2, answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
        old.execSQL("CREATE TABLE answers(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, question_id INTEGER NOT NULL, points INTEGER NOT NULL, deactivated TEXT DEFAULT '', history_id INTEGER DEFAULT 0)");
        old.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_name VARCHAR(200) NOT NULL, topic INTEGER NOT NULL, question_id INTEGER NOT NULL, success INTEGER NOT NULL, played_at INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, winner VARCHAR(200) NOT NULL, played_at INTEGER NOT NULL)");
        // a question of the old file with one topic, and an own question
        old.execSQL("INSERT INTO questions(topic, question, answer) VALUES (2, 'Mi a víz kémiai képlete?', 'H2O')");
        // a question of the old file that was reworded since
        old.execSQL("INSERT INTO questions(topic, question, answer) VALUES (3, 'Melyik a Biblia leghosszabb könyve?', 'Zsoltárok')");
        old.execSQL("INSERT INTO questions(topic, question, answer) VALUES (9, 'Saját kérdés?', 'Igen')");
        old.execSQL("INSERT INTO games(winner, played_at) VALUES ('Anna', 1)");
        old.setVersion(5);
        old.close();

        database = new Database(context);
        assertEquals(1, countQuestions(TEST_TOPIC));
        // the old question got its new topic and is not duplicated
        assertArrayEquals(new int[]{2, 7}, database.selectQuestionTopics(findQuestionId("Mi a víz kémiai képlete?")));
        // the reworded question is renamed, not added again
        assertArrayEquals(new int[]{3}, database.selectQuestionTopics(findQuestionId("Melyik bibliai könyvnek van a legtöbb fejezete?")));
        for (int topic = 1; topic <= Database.TOPICS.length; topic++) {
            assertEquals(200, countQuestions(topic));
        }
        assertEquals(1, database.selectTotals()[0]);
    }

    //
    // QUESTIONS MARKED AS WRONG
    //

    @Test
    public void questionCanBeFlaggedAndUnflagged() {
        int question = addTestQuestion("Rossz kérdés?", "Rossz válasz");
        assertFalse(database.isQuestionFlagged(question));

        database.setQuestionFlagged(question, true);
        assertTrue(database.isQuestionFlagged(question));
        Cursor flagged = database.selectFlaggedQuestions();
        assertEquals(1, flagged.getCount());
        assertTrue(flagged.moveToFirst());
        assertEquals(question, flagged.getInt(0));
        assertEquals("Rossz kérdés?", flagged.getString(1));
        // the list shows a flag, the editor gets the plain text
        assertEquals("⚑ Rossz kérdés?", flagged.getString(3));
        flagged.close();

        database.setQuestionFlagged(question, false);
        assertFalse(database.isQuestionFlagged(question));
        Cursor none = database.selectFlaggedQuestions();
        assertEquals(0, none.getCount());
        none.close();
    }

    @Test
    public void topicListShowsFlagOnlyOnFlaggedQuestions() {
        int flaggedQuestion = addTestQuestion("Jelölt?", "Igen");
        addTestQuestion("Jó kérdés?", "Igen");
        database.setQuestionFlagged(flaggedQuestion, true);

        Cursor questions = database.selectQuestions(TEST_TOPIC);
        Set<String> shown = new HashSet<>();
        while (questions.moveToNext()) {
            shown.add(questions.getString(3));
        }
        questions.close();
        assertTrue(shown.contains("⚑ Jelölt?"));
        assertTrue(shown.contains("Jó kérdés?"));
    }

    @Test
    public void restoreKeepsFlags() throws JSONException {
        int question = addTestQuestion("Jelölt kérdés?", "Válasz");
        database.setQuestionFlagged(question, true);
        String backup = database.exportJson();

        database.setQuestionFlagged(question, false);
        database.importJson(backup, true);

        assertTrue(database.isQuestionFlagged(question));
    }

    //
    // TEAMS
    //

    @Test
    public void teamMembersAreSavedAndAnswerInTurns() {
        database.insertPlayersForNewGame(new String[]{"Pirosak", "Kékek"}, new int[]{1, 1},
                new String[]{" Anna, Béla ,, Cili ", null}, new int[]{2000, 2000});

        Cursor players = database.selectPlayers();
        assertTrue(players.moveToFirst());
        assertEquals("Anna, Béla ,, Cili", players.getString(2));
        assertTrue(players.moveToNext());
        assertEquals("", players.getString(2));
        players.close();

        Cursor next = database.selectNextPlayer();
        assertTrue(next.moveToFirst());
        assertEquals("Pirosak", next.getString(1));
        String members = next.getString(4);
        next.close();
        assertEquals("Anna", Database.memberOnTurn(members, 0));
        assertEquals("Béla", Database.memberOnTurn(members, 1));
        assertEquals("Cili", Database.memberOnTurn(members, 2));
        assertEquals("Anna", Database.memberOnTurn(members, 3));
    }

    @Test
    public void noMemberOnTurnWithoutTeam() {
        assertEquals("", Database.memberOnTurn("", 0));
        assertEquals("", Database.memberOnTurn(null, 5));
        assertEquals("", Database.memberOnTurn(" , ", 1));
    }

    //
    // STREAK BONUS
    //

    @Test
    public void everyThirdCorrectAnswerInARowGivesABonus() {
        startGame("Anna");
        int anna = nextPlayerId();
        int question = drawQuestion(1);
        assertEquals(1, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(1, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(2, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(1, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(1, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(2, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(8, points("Anna"));
        // the statistics count answers, not points
        assertArrayEquals(new int[]{0, 6, 6}, database.selectTotals());
    }

    @Test
    public void lateAnswerBreaksTheStreak() {
        startGame("Anna");
        int anna = nextPlayerId();
        int question = drawQuestion(1);
        database.savePlayerAnswer(anna, question, true, true);
        database.savePlayerAnswer(anna, question, true, true);
        assertEquals(0, database.savePlayerAnswer(anna, question, false, true));
        assertEquals(1, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(1, database.savePlayerAnswer(anna, question, true, true));
        assertEquals(2, database.savePlayerAnswer(anna, question, true, true));
    }

    @Test
    public void streakIsCountedPerPlayer() {
        startGame("Anna", "Béla");
        int question = drawQuestion(1);
        int anna = nextPlayerId();
        database.savePlayerAnswer(anna, question, true, true);
        int bela = nextPlayerId();
        database.savePlayerAnswer(bela, question, false, true);
        database.savePlayerAnswer(anna, question, true, true);
        database.savePlayerAnswer(bela, question, true, true);
        assertEquals(2, database.savePlayerAnswer(anna, question, true, true));
    }

    @Test
    public void undoTakesBackTheBonus() {
        startGame("Anna");
        int anna = nextPlayerId();
        int question = drawQuestion(1);
        database.savePlayerAnswer(anna, question, true, true);
        database.savePlayerAnswer(anna, question, true, true);
        database.savePlayerAnswer(anna, question, true, true);
        assertEquals(4, points("Anna"));

        database.undoLastAnswer();

        assertEquals(2, points("Anna"));
        // answered again, the bonus comes again
        assertEquals(2, database.savePlayerAnswer(anna, question, true, true));
    }

    @Test
    public void noBonusWhenTurnedOff() {
        startGame("Anna");
        int anna = nextPlayerId();
        int question = drawQuestion(1);
        for (int i = 0; i < 3; i++) {
            assertEquals(1, database.savePlayerAnswer(anna, question, true, false));
        }
        assertEquals(3, points("Anna"));
    }

    //
    // TIME TO ANSWER
    //

    @Test
    public void everyPlayerHasTheirOwnTime() {
        database.insertPlayersForNewGame(new String[]{"Kicsi", "Nagy"}, new int[]{1, 2},
                new String[]{"", ""}, new int[]{4000, 1500});

        Cursor players = database.selectPlayers();
        assertTrue(players.moveToFirst());
        assertEquals(4000, players.getInt(3));
        assertTrue(players.moveToNext());
        assertEquals(1500, players.getInt(3));
        players.close();

        Cursor next = database.selectNextPlayer();
        assertTrue(next.moveToFirst());
        assertEquals("Kicsi", next.getString(1));
        assertEquals(4000, next.getInt(5));
        next.close();
    }

    @Test
    public void playersWithoutTimeGetTwoSeconds() {
        startGame("Anna");
        Cursor next = database.selectNextPlayer();
        assertTrue(next.moveToFirst());
        assertEquals(2000, next.getInt(5));
        next.close();
    }

    @Test
    public void secondsAreShownTheHungarianWay() {
        assertEquals("2 mp", PlayersActivity.formatSeconds(2000));
        assertEquals("1,5 mp", PlayersActivity.formatSeconds(1500));
        assertEquals("2,5 mp", PlayersActivity.formatSeconds(2500));
    }

    //
    // UPGRADE
    //

    @Test
    public void upgradeFromVersion6KeepsDataAndAddsColumns() {
        database.close();
        context.deleteDatabase(Database.DATABASE_NAME);
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(Database.DATABASE_NAME), null);
        old.execSQL("CREATE TABLE questions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0)");
        old.execSQL("CREATE TABLE players(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), topic INTEGER DEFAULT 2, answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
        old.execSQL("CREATE TABLE answers(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, question_id INTEGER NOT NULL, points INTEGER NOT NULL, deactivated TEXT DEFAULT '', history_id INTEGER DEFAULT 0)");
        old.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_name VARCHAR(200) NOT NULL, topic INTEGER NOT NULL, question_id INTEGER NOT NULL, success INTEGER NOT NULL, played_at INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, winner VARCHAR(200) NOT NULL, played_at INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE question_topics(question_id INTEGER NOT NULL, topic INTEGER NOT NULL, PRIMARY KEY (question_id, topic))");
        old.execSQL("INSERT INTO questions(id, topic, question, answer) VALUES (1, 9, 'Régi saját kérdés?', 'Régi válasz')");
        old.execSQL("INSERT INTO question_topics(question_id, topic) VALUES (1, 9)");
        old.execSQL("INSERT INTO players(name, topic) VALUES ('Anna', 1)");
        old.setVersion(6);
        old.close();

        database = new Database(context);

        assertFalse(database.isQuestionFlagged(1));
        database.setQuestionFlagged(1, true);
        assertTrue(database.isQuestionFlagged(1));
        Cursor players = database.selectPlayers();
        assertTrue(players.moveToFirst());
        assertEquals("Anna", players.getString(0));
        assertEquals("", players.getString(2));
        assertEquals(Database.DEFAULT_ANSWER_MS, players.getInt(3));
        players.close();
    }

    //
    // HELPERS
    //

    private void startGame(String... names) {
        int[] topics = new int[names.length];
        java.util.Arrays.fill(topics, 1);
        database.insertPlayersForNewGame(names, topics);
    }

    private int points(String name) {
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT points FROM " + Database.TABLE_PLAYERS + " WHERE name = ?", new String[]{name});
        assertTrue(name, cursor.moveToFirst());
        int points = cursor.getInt(0);
        cursor.close();
        return points;
    }

    private int nextPlayerId() {
        Cursor player = database.selectNextPlayer();
        assertTrue(player.moveToFirst());
        int id = player.getInt(0);
        player.close();
        return id;
    }

    private int drawQuestion(int topic) {
        Cursor question = database.selectRandomQuestion(topic);
        assertTrue(question.moveToFirst());
        int id = question.getInt(0);
        question.close();
        database.markQuestionUsed(id);
        return id;
    }

    private int addTestQuestion(String question, String answer) {
        database.saveQuestion(-1, new int[]{TEST_TOPIC}, question, answer);
        Cursor questions = database.selectQuestions(TEST_TOPIC);
        assertTrue(questions.moveToFirst());
        int id = questions.getInt(0);
        questions.close();
        return id;
    }

    private int findQuestionId(String question) {
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id FROM " + Database.TABLE_QUESTIONS + " WHERE question = ?", new String[]{question});
        assertTrue(question, cursor.moveToFirst());
        int id = cursor.getInt(0);
        cursor.close();
        return id;
    }

    private int countAllQuestions() {
        Cursor cursor = database.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + Database.TABLE_QUESTIONS, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    private int countQuestions(int topic) {
        Cursor questions = database.selectQuestions(topic);
        int count = questions.getCount();
        questions.close();
        return count;
    }

    private boolean hardestContains(String question) {
        Cursor hardest = database.selectHardestQuestions(10);
        boolean found = false;
        while (hardest.moveToNext()) {
            found |= question.equals(hardest.getString(0));
        }
        hardest.close();
        return found;
    }

    private boolean hardestOrAnyHistoryFor(int questionId) {
        Cursor history = database.getReadableDatabase().rawQuery(
                "SELECT 1 FROM " + Database.TABLE_HISTORY + " WHERE question_id = ?", new String[]{String.valueOf(questionId)});
        boolean found = history.moveToFirst();
        history.close();
        return found;
    }

    private void assertImportFails(String json) {
        try {
            database.importJson(json, true);
            fail("import should fail: " + json);
        } catch (JSONException expected) {
            // expected
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        org.junit.Assert.assertArrayEquals(expected, actual);
    }
}
