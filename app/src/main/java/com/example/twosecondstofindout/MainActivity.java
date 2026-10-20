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
    private static final int REQUEST_IMPORT = 2;
    private static final int REQUEST_EXPORT_QUESTIONS = 3;

    private Button ButtonStart;
    private Button ButtonContinue;
    private Button ButtonStatistics;
    private Button ButtonQuestions;
    private Button ButtonBackup;
    private Database database;
    private GameState gameState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
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
        findViewById(R.id.ButtonHelp).setOnClickListener(view -> showHelp());
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
        gameState = new GameState(this);
        // a game that was left before the end can be continued
        boolean canContinue = gameState.isInProgress() && database.countPlayers() > 0;
        ButtonContinue.setVisibility(canContinue ? View.VISIBLE : View.GONE);
        if (!gameState.isHelpShown()) {
            showHelp();
        } else if (gameState.isBackupDue(database.selectTotals()[0])) {
            showBackupReminder();
        }
    }

    private void showHelp() {
        gameState.setHelpShown();
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                // getText keeps the bold headings of the string resource
                .setMessage(getText(R.string.help_text))
                .setPositiveButton("Értem", null)
                .show();
    }

    // every few games: the questions and the statistics are only on this phone
    private void showBackupReminder() {
        int games = database.selectTotals()[0];
        new AlertDialog.Builder(this)
                .setTitle("Mentsük el?")
                .setMessage("Már " + games + " játékot játszottatok. A kérdések és a statisztika csak ezen a telefonon vannak meg: érdemes elmenteni őket egy fájlba, például a Google Drive-ra.")
                .setPositiveButton("Mentés", (dialog, which) -> startExport())
                .setNegativeButton("Később", (dialog, which) -> gameState.setBackupReminded(games))
                .setCancelable(false)
                .show();
    }

    //
    // BACKUP: the file is picked by the user, so it can go to Google Drive, Downloads, etc.
    //

    private void showBackupDialog() {
        String[] options = {"💾 Mentés fájlba", "📂 Betöltés fájlból"};
        new AlertDialog.Builder(this)
                .setTitle("Mentés és betöltés")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        askWhatToSave();
                    } else {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        // json files are not always marked as json, e.g. in Downloads
                        intent.setType("*/*");
                        startActivityForResult(intent, REQUEST_IMPORT);
                    }
                })
                .show();
    }

    // the statistics have the names of the players, so they are not in a file to share
    private void askWhatToSave() {
        String[] options = {"Kérdések és statisztika\n(biztonsági mentés, pl. új telefonhoz)", "Csak a kérdések\n(statisztika nélkül, megosztható)"};
        new AlertDialog.Builder(this)
                .setTitle("Mi kerüljön a fájlba?")
                .setItems(options, (dialog, which) -> startExport(which == 0))
                .show();
    }

    private void startExport() {
        startExport(true);
    }

    // statistics: false saves only the questions
    private void startExport(boolean statistics) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, (statistics ? "vagd-ra-" : "vagd-ra-kerdesek-") + date + ".json");
        startActivityForResult(intent, statistics ? REQUEST_EXPORT : REQUEST_EXPORT_QUESTIONS);
    }

    // A file with statistics: everything can be restored from it, or only its new questions added.
    // A file with only questions: its new questions are added.
    private void askWhatToLoad(String json) {
        String[] options = {"Kérdések és statisztika\n(a mostaniak lecserélődnek)", "Csak az új kérdések\n(a statisztikád marad)"};
        new AlertDialog.Builder(this)
                .setTitle("Ebben a fájlban statisztika is van. Mit töltsünk be?")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("Biztosan lecseréled?")
                                .setMessage("Az összes kérdés és a statisztika lecserélődik a fájlban lévőkre.")
                                .setPositiveButton("Lecserélés", (d, w) -> load(json, true))
                                .setNegativeButton("Mégse", null)
                                .show();
                    } else {
                        load(json, false);
                    }
                })
                .show();
    }

    // replace: everything is replaced, otherwise only the new questions are added
    private void load(String json, boolean replace) {
        try {
            int count = database.importJson(json, replace);
            Toast.makeText(this, replace ? "Betöltve: " + count + " kérdés és a statisztika" : count + " új kérdés hozzáadva", Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Ez nem egy mentésfájl", Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Nem sikerült: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_EXPORT || requestCode == REQUEST_EXPORT_QUESTIONS) {
                boolean statistics = requestCode == REQUEST_EXPORT;
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    output.write(database.exportJson(statistics).getBytes(StandardCharsets.UTF_8));
                }
                if (statistics) {
                    gameState.setBackupReminded(database.selectTotals()[0]);
                }
                Toast.makeText(this, statistics ? "Mentve" : "A kérdések mentve", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_IMPORT) {
                String json = readFile(uri);
                if (Database.backupHasStatistics(json)) {
                    askWhatToLoad(json);
                } else {
                    load(json, false);
                }
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
