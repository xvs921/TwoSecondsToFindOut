package com.example.twosecondstofindout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.database.Cursor;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Statistics of all games: totals, players and the questions that were missed the most
public class StatisticsActivity extends AppCompatActivity {

    private TextView StatGames;
    private TextView StatQuestions;
    private TextView StatSuccess;
    private LinearLayout PlayerStatistics;
    private LinearLayout HardestQuestions;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        StatGames = findViewById(R.id.StatGames);
        StatQuestions = findViewById(R.id.StatQuestions);
        StatSuccess = findViewById(R.id.StatSuccess);
        PlayerStatistics = findViewById(R.id.PlayerStatistics);
        HardestQuestions = findViewById(R.id.HardestQuestions);
        database = new Database(this);

        Button ButtonBackStatistics = findViewById(R.id.ButtonBackStatistics);
        Button ButtonDeleteStatistics = findViewById(R.id.ButtonDeleteStatistics);
        ButtonBackStatistics.setOnClickListener(view -> finish());
        ButtonDeleteStatistics.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Törlöd a statisztikát?")
                .setMessage("Minden eddigi játék eredménye törlődik.")
                .setPositiveButton("Törlés", (dialog, which) -> {
                    database.deleteStatistics();
                    load();
                })
                .setNegativeButton("Mégse", null)
                .show());
        load();
    }

    private void load() {
        int[] totals = database.selectTotals();
        StatGames.setText(tile(String.valueOf(totals[0]), "játék"));
        StatQuestions.setText(tile(String.valueOf(totals[1]), "kérdés"));
        StatSuccess.setText(tile(percent(totals[2], totals[1]), "siker"));

        PlayerStatistics.removeAllViews();
        Cursor players = database.selectPlayerStatistics();
        while (players.moveToNext()) {
            int answered = players.getInt(1);
            int successful = players.getInt(2);
            int wins = players.getInt(3);
            addRow(PlayerStatistics, players.getString(0),
                    "🏆 " + wins + "   ✓ " + successful + "/" + answered + " (" + percent(successful, answered) + ")");
        }
        if (players.getCount() == 0) {
            addRow(PlayerStatistics, "Még nincs lejátszott kérdés.", "");
        }
        players.close();

        HardestQuestions.removeAllViews();
        Cursor questions = database.selectHardestQuestions(10);
        while (questions.moveToNext()) {
            addRow(HardestQuestions, questions.getString(0) + "\n" + questions.getString(1),
                    questions.getInt(3) + "/" + questions.getInt(2));
        }
        if (questions.getCount() == 0) {
            addRow(HardestQuestions, "Egy kérdés legalább kétszer kell, hogy elhangozzon.", "");
        }
        questions.close();
    }

    // a big number with a small label under it
    private SpannableString tile(String value, String label) {
        SpannableString text = new SpannableString(value + "\n" + label);
        text.setSpan(new RelativeSizeSpan(2f), 0, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private String percent(int part, int whole) {
        return whole == 0 ? "–" : Math.round(part * 100.0 / whole) + "%";
    }

    private void addRow(LinearLayout parent, String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        row.setPadding(0, padding, 0, padding);

        TextView leftText = new TextView(this);
        leftText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        leftText.setText(left);
        leftText.setTextSize(17);
        leftText.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));

        TextView rightText = new TextView(this);
        rightText.setText(right);
        rightText.setTextSize(15);
        rightText.setPadding(padding, 0, 0, 0);
        rightText.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));

        row.addView(leftText);
        row.addView(rightText);
        parent.addView(row);
    }
}
