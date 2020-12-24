package com.example.twosecondstofindout;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Optional;

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
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Hány éves kortól számít felnőttnek valaki?','18')");
        db.execSQL("INSERT INTO " + TABLE_QUESTIONS + "(topic, question, answer) VALUES (1,'Mi Magyarország leghosszabb folyója?','Duna')");
        db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_PLAYERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
    }

    public Cursor selectQuestionId(int topic)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor questionId = db.rawQuery("SELECT id FROM " + TABLE_QUESTIONS + " WHERE topic = " + topic + " ORDER BY RANDOM() LIMIT 1", null);
        return questionId;
    }

    public Cursor selectQuestionText(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor question = db.rawQuery("SELECT question FROM " + TABLE_QUESTIONS + " WHERE id = " + questionId, null);
        return question;
    }

    public Cursor selectAnswerText(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor answer = db.rawQuery("SELECT answer FROM " + TABLE_QUESTIONS + " WHERE id = " + questionId, null);
        return answer;
    }

    public Cursor selectFirstPlayerName()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor firstName = db.rawQuery("SELECT name FROM " + TABLE_PLAYERS + " ORDER BY id LIMIT 1", null);
        return firstName;
    }

    public void insertPlayersForNewGame(String player1, String player2, String player3, String player4, String player5, String player6)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PLAYERS);
        if(player1 != null  && player1 != ""){
            db.execSQL("INSERT INTO " + TABLE_PLAYERS + "(name) VALUES ('"+ player1 + "');");
        }
        if(player2 != null  && player2 != ""){
            db.execSQL("INSERT INTO " + TABLE_PLAYERS + "(name) VALUES ('"+ player2 + "');");
        }
        if(player3 != null  && player3 != ""){
            db.execSQL("INSERT INTO " + TABLE_PLAYERS + "(name) VALUES ('"+ player3 + "');");
        }
        if(player4 != null  && player4 != ""){
            db.execSQL("INSERT INTO " + TABLE_PLAYERS + "(name) VALUES ('"+ player4 + "');");
        }
        if(player5 != null  && player5 != ""){
            db.execSQL("INSERT INTO " + TABLE_PLAYERS + "(name) VALUES ('"+ player5 + "');");
        }
        if(player6 != null  && player6 != ""){
            db.execSQL("INSERT INTO " + TABLE_PLAYERS + "(name) VALUES ('"+ player6 + "');");
        }
    }


}
