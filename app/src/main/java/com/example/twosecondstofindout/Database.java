package com.example.twosecondstofindout;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Database extends SQLiteOpenHelper
{
    public static final String DATABASE_NAME = "twoSecondsGame";
    public static final String TABLE_QUESTIONS = "questions";
    public static final String TABLE_PLAYERS = "players";
    public static final String TABLE_ANSWERS = "answers";
    public static final String TABLE_HISTORY = "history";
    public static final String TABLE_GAMES = "games";
    public static final String TABLE_QUESTION_TOPICS = "question_topics";

    public static final String COL_1 = "id";
    public static final String COL_2 = "topic";
    public static final String COL_3 = "question";
    public static final String COL_4 = "answer";
    public static final String COL_5 = "used";


    public static final String COL_6 = "id";
    public static final String COL_7 = "name";
    public static final String COL_8 = "points";
    public static final String COL_9 = "topic";

    // the topic ids are the position + 1: 1: Gyerek, 2: Felnőtt, 3: Bibliai, 4: Sport, ...
    public static final String[] TOPICS = {"Gyerek", "Felnőtt", "Bibliai", "Sport", "Földrajz", "Történelem", "Tudomány"};

    private final Context context;

    public Database(Context context)
    {
        super(context, DATABASE_NAME, null, 6);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_QUESTIONS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_PLAYERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), topic INTEGER DEFAULT 2, answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
        // every Siker / Késő decision, so the last ones can be undone. deactivated: comma separated
        // ids of the players who dropped out of the tie-break right after this answer
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ANSWERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, question_id INTEGER NOT NULL, points INTEGER NOT NULL, deactivated TEXT DEFAULT '', history_id INTEGER DEFAULT 0)");
        createStatisticsTables(db);
        createQuestionTopicsTable(db);
        mergeQuestionsFromFile(db);
    }

    // A question can belong to more topics, e.g. Felnőtt and Tudomány.
    // questions.topic is only kept as the first topic of the question.
    private void createQuestionTopicsTable(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_QUESTION_TOPICS + "(question_id INTEGER NOT NULL, topic INTEGER NOT NULL, PRIMARY KEY (question_id, topic))");
    }

    // Kept across games for the statistics: every answer and every winner
    private void createStatisticsTables(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_name VARCHAR(200) NOT NULL, topic INTEGER NOT NULL, question_id INTEGER NOT NULL, success INTEGER NOT NULL, played_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_GAMES + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, winner VARCHAR(200) NOT NULL, played_at INTEGER NOT NULL)");
    }

    // Loads res/raw/questions.txt, one question per line: topics<TAB>question<TAB>answer,
    // where topics is a comma separated list, e.g. 2,7. Questions that are already there
    // (same text) only get the missing topics, so this can run again after an update.
    private void mergeQuestionsFromFile(SQLiteDatabase db)
    {
        db.beginTransaction();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.questions), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\t");
                if (parts.length != 3)
                {
                    continue;
                }
                String[] topicTexts = parts[0].split(",");
                int[] topics = new int[topicTexts.length];
                for (int i = 0; i < topicTexts.length; i++)
                {
                    topics[i] = Integer.parseInt(topicTexts[i].trim());
                }
                addOrLinkQuestion(db, topics, parts[1].trim(), parts[2].trim());
            }
            db.setTransactionSuccessful();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not load questions", e);
        }
        finally
        {
            db.endTransaction();
        }
    }

    // Adds the question, or only its missing topics if a question with the same text exists.
    // Returns true if a new question was added.
    private boolean addOrLinkQuestion(SQLiteDatabase db, int[] topics, String question, String answer)
    {
        long questionId = findQuestion(db, question);
        boolean added = questionId < 0;
        if (added)
        {
            ContentValues values = new ContentValues();
            values.put(COL_2, topics[0]);
            values.put(COL_3, question);
            values.put(COL_4, answer);
            questionId = db.insertOrThrow(TABLE_QUESTIONS, null, values);
        }
        linkTopics(db, questionId, topics);
        return added;
    }

    private void renameQuestion(SQLiteDatabase db, String oldText, String newText)
    {
        db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET question = ? WHERE question = ?", new Object[]{newText, oldText});
    }

    private long findQuestion(SQLiteDatabase db, String question)
    {
        Cursor cursor = db.rawQuery("SELECT id FROM " + TABLE_QUESTIONS + " WHERE question = ?", new String[]{question});
        long id = cursor.moveToFirst() ? cursor.getLong(0) : -1;
        cursor.close();
        return id;
    }

    private void linkTopics(SQLiteDatabase db, long questionId, int[] topics)
    {
        for (int topic : topics)
        {
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_QUESTION_TOPICS + "(question_id, topic) VALUES (?, ?)", new Object[]{questionId, topic});
        }
    }

    // From version 4 on the data is migrated step by step, so own questions and statistics are kept.
    // Add a new step here for every new version instead of dropping the tables.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4)
        {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANSWERS);
            onCreate(db);
            return;
        }
        if (oldVersion < 5)
        {
            createStatisticsTables(db);
            db.execSQL("ALTER TABLE " + TABLE_ANSWERS + " ADD COLUMN history_id INTEGER DEFAULT 0");
        }
        if (oldVersion < 6)
        {
            // more topics per question, and the new questions and topics of the file
            createQuestionTopicsTable(db);
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_QUESTION_TOPICS + "(question_id, topic) SELECT id, topic FROM " + TABLE_QUESTIONS);
            // questions of the old file that were reworded to be unambiguous, so they are not added twice
            renameQuestion(db, "Mi Magyarország leghosszabb folyója?", "Melyik folyó szakasza a leghosszabb Magyarország területén?");
            renameQuestion(db, "Ki építette fel a templomot Jeruzsálemben?", "Ki építtette az első templomot Jeruzsálemben?");
            renameQuestion(db, "Melyik a Biblia leghosszabb könyve?", "Melyik bibliai könyvnek van a legtöbb fejezete?");
            mergeQuestionsFromFile(db);
        }
    }

    // Returns id, question, answer of a random question not used yet. When every
    // question of the topic was used, the topic starts over. Null if the topic is empty.
    public Cursor selectRandomQuestion(int topic)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {String.valueOf(topic)};
        String query = "SELECT q.id, q.question, q.answer FROM " + TABLE_QUESTIONS + " q JOIN " + TABLE_QUESTION_TOPICS + " t ON t.question_id = q.id "
                + "WHERE t.topic = ? AND q.used = 0 ORDER BY RANDOM() LIMIT 1";
        Cursor question = db.rawQuery(query, args);
        if (question.getCount() == 0)
        {
            question.close();
            db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET used = 0 WHERE id IN (SELECT question_id FROM " + TABLE_QUESTION_TOPICS + " WHERE topic = ?)", args);
            question = db.rawQuery(query, args);
        }
        if (question.getCount() == 0)
        {
            question.close();
            return null;
        }
        return question;
    }

    public void markQuestionUsed(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET used = 1 WHERE id = ?", new Object[]{questionId});
    }

    public Cursor selectFirstPlayerName()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor firstName = db.rawQuery("SELECT name FROM " + TABLE_PLAYERS + " ORDER BY id LIMIT 1", null);
        return firstName;
    }

    // Returns question, answer of the given question
    public Cursor selectQuestion(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT question, answer FROM " + TABLE_QUESTIONS + " WHERE id = ?", new String[]{String.valueOf(questionId)});
    }

    // The players of the last game: name, topic
    public Cursor selectPlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT name, topic FROM " + TABLE_PLAYERS + " ORDER BY id", null);
    }

    // The active player with the fewest answered questions is next: id, name, answered, topic
    public Cursor selectNextPlayer()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT id, name, answered, topic FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY answered, id LIMIT 1", null);
    }

    // {fewest, most} answered questions among the active players. When they are equal, a round is over.
    public int[] selectActiveAnsweredRange()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor range = db.rawQuery("SELECT MIN(answered), MAX(answered) FROM " + TABLE_PLAYERS + " WHERE active = 1", null);
        int[] result = range.moveToFirst() ? new int[]{range.getInt(0), range.getInt(1)} : new int[]{0, 0};
        range.close();
        return result;
    }

    public String selectActivePlayerNames()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor names = db.rawQuery("SELECT name FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY id", null);
        StringBuilder result = new StringBuilder();
        while (names.moveToNext())
        {
            if (result.length() > 0)
            {
                result.append(", ");
            }
            result.append(names.getString(0));
        }
        names.close();
        return result.toString();
    }

    public int countPlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAYERS, null);
        int result = count.moveToFirst() ? count.getInt(0) : 0;
        count.close();
        return result;
    }

    public void savePlayerAnswer(int playerId, int questionId, boolean success)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        int points = success ? 1 : 0;
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = answered + 1, points = points + ? WHERE id = ?", new Object[]{points, playerId});

        long historyId = 0;
        Cursor player = db.rawQuery("SELECT name, topic FROM " + TABLE_PLAYERS + " WHERE id = ?", new String[]{String.valueOf(playerId)});
        if (player.moveToFirst())
        {
            ContentValues history = new ContentValues();
            history.put("player_name", player.getString(0));
            history.put("topic", player.getInt(1));
            history.put("question_id", questionId);
            history.put("success", points);
            history.put("played_at", System.currentTimeMillis());
            historyId = db.insert(TABLE_HISTORY, null, history);
        }
        player.close();

        ContentValues values = new ContentValues();
        values.put("player_id", playerId);
        values.put("question_id", questionId);
        values.put("points", points);
        values.put("history_id", historyId);
        db.insert(TABLE_ANSWERS, null, values);
    }

    // Only the active players with the most points stay active (tie-break).
    // Returns how many players are still active.
    public int keepLeadersActive()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor losers = db.rawQuery("SELECT id FROM " + TABLE_PLAYERS + " WHERE active = 1 AND points < (SELECT MAX(points) FROM " + TABLE_PLAYERS + " WHERE active = 1)", null);
        StringBuilder ids = new StringBuilder();
        while (losers.moveToNext())
        {
            ids.append(",").append(losers.getInt(0));
        }
        losers.close();
        if (ids.length() > 0)
        {
            String idList = ids.substring(1);
            db.execSQL("UPDATE " + TABLE_PLAYERS + " SET active = 0 WHERE id IN (" + idList + ")");
            // remember it on the last answer, so undoing that answer brings these players back
            db.execSQL("UPDATE " + TABLE_ANSWERS + " SET deactivated = deactivated || ? WHERE id = (SELECT MAX(id) FROM " + TABLE_ANSWERS + ")", new Object[]{ids.toString()});
        }
        Cursor active = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAYERS + " WHERE active = 1", null);
        int result = active.moveToFirst() ? active.getInt(0) : 0;
        active.close();
        return result;
    }

    public boolean hasAnswers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ANSWERS, null);
        boolean result = count.moveToFirst() && count.getInt(0) > 0;
        count.close();
        return result;
    }

    // Takes back the last Siker / Késő. Returns the id of its question, or -1 if there was nothing to undo.
    public int undoLastAnswer()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor last = db.rawQuery("SELECT id, player_id, question_id, points, deactivated, history_id FROM " + TABLE_ANSWERS + " ORDER BY id DESC LIMIT 1", null);
        if (!last.moveToFirst())
        {
            last.close();
            return -1;
        }
        int answerId = last.getInt(0);
        int playerId = last.getInt(1);
        int questionId = last.getInt(2);
        int points = last.getInt(3);
        String deactivated = last.getString(4);
        long historyId = last.getLong(5);
        last.close();

        db.execSQL("DELETE FROM " + TABLE_HISTORY + " WHERE id = ?", new Object[]{historyId});
        if (deactivated != null && deactivated.length() > 1)
        {
            db.execSQL("UPDATE " + TABLE_PLAYERS + " SET active = 1 WHERE id IN (" + deactivated.substring(1) + ")");
        }
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = answered - 1, points = points - ? WHERE id = ?", new Object[]{points, playerId});
        db.execSQL("DELETE FROM " + TABLE_ANSWERS + " WHERE id = ?", new Object[]{answerId});
        return questionId;
    }

    // name, points ordered by points
    public Cursor selectScoreboard()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT name, points FROM " + TABLE_PLAYERS + " ORDER BY points DESC, id", null);
    }

    public String selectScoreboardText()
    {
        Cursor scoreboard = selectScoreboard();
        StringBuilder result = new StringBuilder();
        int place = 1;
        while (scoreboard.moveToNext())
        {
            String[] medals = {"🥇", "🥈", "🥉"};
            result.append(place <= 3 ? medals[place - 1] : place + ".").append("  ")
                    .append(scoreboard.getString(0)).append(" - ")
                    .append(scoreboard.getInt(1)).append(" pont\n");
            place++;
        }
        scoreboard.close();
        return result.toString();
    }

    // Same players, new game
    public void resetScores()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = 0, points = 0, active = 1");
        db.execSQL("DELETE FROM " + TABLE_ANSWERS);
    }

    public void insertPlayersForNewGame(String[] players, int[] topics)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PLAYERS);
        db.execSQL("DELETE FROM " + TABLE_ANSWERS);
        for (int i = 0; i < players.length; i++)
        {
            String player = players[i];
            if (player != null && !player.trim().isEmpty())
            {
                ContentValues values = new ContentValues();
                values.put(COL_7, player.trim());
                values.put(COL_9, topics[i]);
                db.insert(TABLE_PLAYERS, null, values);
            }
        }
    }

    //
    // QUESTION EDITOR
    //

    // _id, question, answer of a topic, for the question list
    public Cursor selectQuestions(int topic)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT q.id AS _id, q.question, q.answer FROM " + TABLE_QUESTIONS + " q JOIN " + TABLE_QUESTION_TOPICS + " t ON t.question_id = q.id "
                + "WHERE t.topic = ? ORDER BY q.id DESC", new String[]{String.valueOf(topic)});
    }

    public int[] selectQuestionTopics(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT topic FROM " + TABLE_QUESTION_TOPICS + " WHERE question_id = ? ORDER BY topic", new String[]{String.valueOf(questionId)});
        int[] topics = new int[cursor.getCount()];
        for (int i = 0; cursor.moveToNext(); i++)
        {
            topics[i] = cursor.getInt(0);
        }
        cursor.close();
        return topics;
    }

    // questionId -1: new question. topics must not be empty.
    public void saveQuestion(int questionId, int[] topics, String question, String answer)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_2, topics[0]);
        values.put(COL_3, question.trim());
        values.put(COL_4, answer.trim());
        db.beginTransaction();
        try
        {
            long id = questionId;
            if (questionId < 0)
            {
                id = db.insertOrThrow(TABLE_QUESTIONS, null, values);
            }
            else
            {
                db.update(TABLE_QUESTIONS, values, "id = ?", new String[]{String.valueOf(questionId)});
                db.delete(TABLE_QUESTION_TOPICS, "question_id = ?", new String[]{String.valueOf(questionId)});
            }
            linkTopics(db, id, topics);
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    public void deleteQuestion(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_QUESTION_TOPICS, "question_id = ?", new String[]{String.valueOf(questionId)});
        db.delete(TABLE_QUESTIONS, "id = ?", new String[]{String.valueOf(questionId)});
    }

    //
    // STATISTICS
    //

    // Called when a game is over, the winner is the first on the scoreboard
    public void saveGameResult()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor winner = db.rawQuery("SELECT name FROM " + TABLE_PLAYERS + " ORDER BY points DESC, active DESC, id LIMIT 1", null);
        if (winner.moveToFirst())
        {
            ContentValues values = new ContentValues();
            values.put("winner", winner.getString(0));
            values.put("played_at", System.currentTimeMillis());
            db.insert(TABLE_GAMES, null, values);
        }
        winner.close();
    }

    // When the last answer of a finished game is undone, the game is not over any more
    public void deleteLastGameResult()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_GAMES + " WHERE id = (SELECT MAX(id) FROM " + TABLE_GAMES + ")");
    }

    // {finished games, answered questions, successful answers}
    public int[] selectTotals()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor totals = db.rawQuery("SELECT (SELECT COUNT(*) FROM " + TABLE_GAMES + "), COUNT(*), IFNULL(SUM(success), 0) FROM " + TABLE_HISTORY, null);
        int[] result = totals.moveToFirst() ? new int[]{totals.getInt(0), totals.getInt(1), totals.getInt(2)} : new int[]{0, 0, 0};
        totals.close();
        return result;
    }

    // name, answered, successful, wins ordered by wins and success rate
    public Cursor selectPlayerStatistics()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT h.player_name, COUNT(*), SUM(h.success), "
                + "(SELECT COUNT(*) FROM " + TABLE_GAMES + " g WHERE g.winner = h.player_name) AS wins "
                + "FROM " + TABLE_HISTORY + " h GROUP BY h.player_name "
                + "ORDER BY wins DESC, SUM(h.success) * 1.0 / COUNT(*) DESC", null);
    }

    // question, answer, asked, successful of the questions that were missed the most (asked at least twice)
    public Cursor selectHardestQuestions(int limit)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT q.question, q.answer, COUNT(*), SUM(h.success) FROM " + TABLE_HISTORY + " h "
                + "JOIN " + TABLE_QUESTIONS + " q ON q.id = h.question_id "
                + "GROUP BY h.question_id HAVING COUNT(*) >= 2 "
                + "ORDER BY SUM(h.success) * 1.0 / COUNT(*), COUNT(*) DESC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    public void deleteStatistics()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_HISTORY);
        db.execSQL("DELETE FROM " + TABLE_GAMES);
        db.execSQL("UPDATE " + TABLE_ANSWERS + " SET history_id = 0");
    }

    //
    // BACKUP
    //

    // 1: one topic per question ("topic"), 2: more topics per question ("topics")
    public static final int BACKUP_VERSION = 2;

    // Every question (own ones too) and the statistics as JSON
    public String exportJson() throws JSONException
    {
        SQLiteDatabase db = this.getWritableDatabase();
        JSONObject backup = new JSONObject();
        backup.put("app", "TwoSecondsToFindOut");
        backup.put("version", BACKUP_VERSION);
        backup.put("exported_at", System.currentTimeMillis());
        JSONArray questions = exportTable(db, "SELECT id, question, answer FROM " + TABLE_QUESTIONS + " ORDER BY id");
        for (int i = 0; i < questions.length(); i++)
        {
            JSONObject question = questions.getJSONObject(i);
            JSONArray topics = new JSONArray();
            for (int topic : selectQuestionTopics(question.getInt("id")))
            {
                topics.put(topic);
            }
            question.put("topics", topics);
        }
        backup.put("questions", questions);
        backup.put("history", exportTable(db, "SELECT id, player_name, topic, question_id, success, played_at FROM " + TABLE_HISTORY + " ORDER BY id"));
        backup.put("games", exportTable(db, "SELECT id, winner, played_at FROM " + TABLE_GAMES + " ORDER BY id"));
        return backup.toString(2);
    }

    private JSONArray exportTable(SQLiteDatabase db, String query) throws JSONException
    {
        JSONArray rows = new JSONArray();
        Cursor cursor = db.rawQuery(query, null);
        while (cursor.moveToNext())
        {
            JSONObject row = new JSONObject();
            for (int i = 0; i < cursor.getColumnCount(); i++)
            {
                if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER)
                {
                    row.put(cursor.getColumnName(i), cursor.getLong(i));
                }
                else
                {
                    row.put(cursor.getColumnName(i), cursor.getString(i));
                }
            }
            rows.put(row);
        }
        cursor.close();
        return rows;
    }

    // replace: everything is replaced by the backup (restore).
    // otherwise only the questions that are not there yet are added (e.g. questions from a friend).
    // Returns the number of questions added. Throws JSONException if the file is not a backup of this app.
    public int importJson(String json, boolean replace) throws JSONException
    {
        JSONObject backup = new JSONObject(json);
        if (!"TwoSecondsToFindOut".equals(backup.optString("app")) || backup.getInt("version") > BACKUP_VERSION)
        {
            throw new JSONException("Not a backup of this app");
        }
        JSONArray questions = backup.getJSONArray("questions");
        SQLiteDatabase db = this.getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try
        {
            if (replace)
            {
                db.execSQL("DELETE FROM " + TABLE_QUESTIONS);
                db.execSQL("DELETE FROM " + TABLE_QUESTION_TOPICS);
                db.execSQL("DELETE FROM " + TABLE_HISTORY);
                db.execSQL("DELETE FROM " + TABLE_GAMES);
                // the undo log points to the old questions and statistics
                db.execSQL("DELETE FROM " + TABLE_ANSWERS);
            }
            for (int i = 0; i < questions.length(); i++)
            {
                JSONObject question = questions.getJSONObject(i);
                int[] topics = readTopics(question);
                String text = question.getString("question");
                String answer = question.getString("answer");
                if (!replace)
                {
                    // an existing question only gets the missing topics
                    if (addOrLinkQuestion(db, topics, text, answer))
                    {
                        added++;
                    }
                    continue;
                }
                ContentValues values = new ContentValues();
                // the statistics refer to the question ids
                values.put(COL_1, question.getLong("id"));
                values.put(COL_2, topics[0]);
                values.put(COL_3, text);
                values.put(COL_4, answer);
                db.insertOrThrow(TABLE_QUESTIONS, null, values);
                linkTopics(db, question.getLong("id"), topics);
                added++;
            }
            if (replace)
            {
                importTable(db, TABLE_HISTORY, backup.optJSONArray("history"),
                        new String[]{"id", "player_name", "topic", "question_id", "success", "played_at"});
                importTable(db, TABLE_GAMES, backup.optJSONArray("games"),
                        new String[]{"id", "winner", "played_at"});
            }
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
        return added;
    }

    // version 1 backups have one "topic", version 2 backups a "topics" list
    private int[] readTopics(JSONObject question) throws JSONException
    {
        JSONArray list = question.optJSONArray("topics");
        if (list == null)
        {
            return new int[]{question.getInt("topic")};
        }
        if (list.length() == 0)
        {
            throw new JSONException("Question without topic");
        }
        int[] topics = new int[list.length()];
        for (int i = 0; i < list.length(); i++)
        {
            topics[i] = list.getInt(i);
        }
        return topics;
    }

    // only the given columns are read, so a broken or forged file cannot reach other columns
    private void importTable(SQLiteDatabase db, String table, JSONArray rows, String[] columns) throws JSONException
    {
        if (rows == null)
        {
            return;
        }
        for (int i = 0; i < rows.length(); i++)
        {
            JSONObject row = rows.getJSONObject(i);
            ContentValues values = new ContentValues();
            for (String name : columns)
            {
                Object value = row.get(name);
                if (value instanceof Number)
                {
                    values.put(name, ((Number) value).longValue());
                }
                else
                {
                    values.put(name, String.valueOf(value));
                }
            }
            db.insertOrThrow(table, null, values);
        }
    }
}
