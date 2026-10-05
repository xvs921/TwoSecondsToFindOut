package com.example.twosecondstofindout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_RESTORE = 2;
    private static final int REQUEST_ADD_QUESTIONS = 3;

    private Button ButtonStart;
    private Button ButtonContinue;
    private Button ButtonStatistics;
    private Button ButtonQuestions;
    private Button ButtonBackup;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        ButtonContinue.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, QuestionActivity.class);
            startActivity(intent);
            finish();
        });
        ButtonStatistics.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, StatisticsActivity.class)));
        ButtonQuestions.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, QuestionsActivity.class)));
        ButtonBackup.setOnClickListener(view -> showBackupDialog());
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
        ButtonContinue = findViewById(R.id.ButtonContinue);
        ButtonStatistics = findViewById(R.id.ButtonStatistics);
        ButtonQuestions = findViewById(R.id.ButtonQuestions);
        ButtonBackup = findViewById(R.id.ButtonBackup);
        database = new Database(this);
        // a game that was left before the end can be continued
        boolean canContinue = new GameState(this).isInProgress() && database.countPlayers() > 0;
        ButtonContinue.setVisibility(canContinue ? View.VISIBLE : View.GONE);
    }

    //
    // BACKUP: the file is picked by the user, so it can go to Google Drive, Downloads, etc.
    //

    private void showBackupDialog() {
        String[] options = {"Mentés fájlba", "Visszaállítás fájlból", "Kérdések hozzáadása fájlból"};
        new AlertDialog.Builder(this)
                .setTitle("Mentés és visszaállítás")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        intent.putExtra(Intent.EXTRA_TITLE, "itt-van-a-nyelvemen-" + date + ".json");
                        startActivityForResult(intent, REQUEST_EXPORT);
                    } else if (which == 1) {
                        new AlertDialog.Builder(this)
                                .setTitle("Visszaállítás")
                                .setMessage("Az összes kérdés és a statisztika lecserélődik a mentésben lévőkre.")
                                .setPositiveButton("Tovább", (d, w) -> openBackupFile(REQUEST_RESTORE))
                                .setNegativeButton("Mégse", null)
                                .show();
                    } else {
                        openBackupFile(REQUEST_ADD_QUESTIONS);
                    }
                })
                .show();
    }

    private void openBackupFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // json files are not always marked as json, e.g. in Downloads
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT) {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    output.write(database.exportJson().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Mentve", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_RESTORE) {
                int count = database.importJson(readFile(uri), true);
                Toast.makeText(this, "Visszaállítva: " + count + " kérdés", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_ADD_QUESTIONS) {
                int count = database.importJson(readFile(uri), false);
                Toast.makeText(this, count + " új kérdés hozzáadva", Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Ez nem egy mentésfájl", Toast.LENGTH_LONG).show();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, "Nem sikerült: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readFile(Uri uri) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }
}
