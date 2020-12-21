package com.example.twosecondstofindout;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper
{
    public static final String DATABASE_NAME = "twoseconds.db";
    public static final String TABLE_NAME = "kerdesek";

    public static final String COL_1 = "id";
    public static final String COL_2 = "temakor_id";
    public static final String COL_3 = "kerdes";
    public static final String COL_4 = "valasz";

    public Database(Context context)
    {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, temakor INTEGER NOT NULL, kerdes VARCHAR(350) NOT NULL, valasz VARCHAR(200) NOT NULL)");
        db.execSQL("INSERT INTO " + TABLE_NAME + "(temakor, kerdes, valasz) VALUES (1,'asd','1')");
        db.execSQL("INSERT INTO " + TABLE_NAME + "(temakor, kerdes, valasz) VALUES (1,'asd','2')");
        db.execSQL("INSERT INTO " + TABLE_NAME + "(temakor, kerdes, valasz) VALUES (1,'asd','3')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    }

    public Cursor selectTeljesNev(int temakor)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor eredmeny = db.rawQuery("SELECT id FROM " + TABLE_NAME + " WHERE temakor_id = '" + temakor + "' ORDER BY RAND() LIMIT 1", null);
        return eredmeny;
    }

}
