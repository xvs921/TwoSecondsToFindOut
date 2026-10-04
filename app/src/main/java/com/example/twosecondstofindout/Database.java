package com.example.twosecondstofindout;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper
{
    public static final String DATABASE_NAME = "twoSecondsGame";
    public static final String TABLE_QUESTIONS = "questions";
    public static final String TABLE_PLAYERS = "players";

    public static final String COL_1 = "id";
    public static final String COL_2 = "topic";
    public static final String COL_3 = "question";
    public static final String COL_4 = "answer";
    public static final String COL_5 = "used";


    public static final String COL_6 = "id";
    public static final String COL_7 = "name";
    public static final String COL_8 = "points";


    public Database(Context context)
    {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_QUESTIONS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0)");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Mi Magyarország fővárosa?','Budapest')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Mi Spanyolország fővárosa?','Madrid')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Mi Anglia fővárosa?','London')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Hány éves kortól számít felnőttnek valaki Magyarországon?','18')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Mi Magyarország leghosszabb folyója?','Duna')");

        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (2,'Mikor kezdődött az első világháború?','1914')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (2,'Hány állmból áll az USA?','50')");

        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (3,'Mi Isten neve?','Jehova')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (3,'Ki volt Dávid hűséges barátja, Saul fia?','Jonatán')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (3,'Ki volt az első ember?','Ádám')");
        db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_PLAYERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYERS);
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

    // The player with the fewest answered questions is next: id, name, answered
    public Cursor selectNextPlayer()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT id, name, answered FROM " + TABLE_PLAYERS + " ORDER BY answered, id LIMIT 1", null);
    }

    public int countPlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAYERS, null);
        int result = count.moveToFirst() ? count.getInt(0) : 0;
        count.close();
        return result;
    }

    public void savePlayerAnswer(int playerId, boolean success)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = answered + 1, points = points + ? WHERE id = ?", new Object[]{success ? 1 : 0, playerId});
    }

    // name, points ordered by points
    public Cursor selectScoreboard()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT name, points FROM " + TABLE_PLAYERS + " ORDER BY points DESC, id", null);
    }

    public void resetScores()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = 0, points = 0");
    }

    public void insertPlayersForNewGame(String... players)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PLAYERS);
        for (String player : players)
        {
            if (player != null && !player.trim().isEmpty())
            {
                ContentValues values = new ContentValues();
                values.put(COL_7, player.trim());
                db.insert(TABLE_PLAYERS, null, values);
            }
        }
    }


}
