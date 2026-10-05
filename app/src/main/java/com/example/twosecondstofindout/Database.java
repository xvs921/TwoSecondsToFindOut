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

public class Database extends SQLiteOpenHelper
{
    public static final String DATABASE_NAME = "twoSecondsGame";
    public static final String TABLE_QUESTIONS = "questions";
    public static final String TABLE_PLAYERS = "players";
    public static final String TABLE_ANSWERS = "answers";

    public static final String COL_1 = "id";
    public static final String COL_2 = "topic";
    public static final String COL_3 = "question";
    public static final String COL_4 = "answer";
    public static final String COL_5 = "used";


    public static final String COL_6 = "id";
    public static final String COL_7 = "name";
    public static final String COL_8 = "points";
    public static final String COL_9 = "topic";


    private final Context context;

    public Database(Context context)
    {
        super(context, DATABASE_NAME, null, 4);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_QUESTIONS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_PLAYERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), topic INTEGER DEFAULT 2, answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
        // every Siker / Késő decision, so the last ones can be undone. deactivated: comma separated
        // ids of the players who dropped out of the tie-break right after this answer
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ANSWERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, question_id INTEGER NOT NULL, points INTEGER NOT NULL, deactivated TEXT DEFAULT '')");
        insertQuestions(db);
    }

    // Loads the questions from res/raw/questions.txt, one per line: topic<TAB>question<TAB>answer
    private void insertQuestions(SQLiteDatabase db)
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
                ContentValues values = new ContentValues();
                values.put(COL_2, Integer.parseInt(parts[0].trim()));
                values.put(COL_3, parts[1].trim());
                values.put(COL_4, parts[2].trim());
                db.insert(TABLE_QUESTIONS, null, values);
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

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANSWERS);
        onCreate(db);
    }

    // Returns id, question, answer of a random question not used yet. When every
    // question of the topic was used, the topic starts over. Null if the topic is empty.
    public Cursor selectRandomQuestion(int topic)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {String.valueOf(topic)};
        String query = "SELECT id, question, answer FROM " + TABLE_QUESTIONS + " WHERE topic = ? AND used = 0 ORDER BY RANDOM() LIMIT 1";
        Cursor question = db.rawQuery(query, args);
        if (question.getCount() == 0)
        {
            question.close();
            db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET used = 0 WHERE topic = ?", args);
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
        ContentValues values = new ContentValues();
        values.put("player_id", playerId);
        values.put("question_id", questionId);
        values.put("points", points);
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
        Cursor last = db.rawQuery("SELECT id, player_id, question_id, points, deactivated FROM " + TABLE_ANSWERS + " ORDER BY id DESC LIMIT 1", null);
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
        last.close();

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
}
