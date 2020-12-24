package com.example.twosecondstofindout;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button ButtonStart;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        ButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentStart = new Intent(MainActivity.this, PlayersActivity.class);
                startActivity(intentStart);
                finish();
            }
        });
    }

    private void init() {
        ButtonStart = findViewById(R.id.ButtonStart);
        database = new Database(this);
    }
}