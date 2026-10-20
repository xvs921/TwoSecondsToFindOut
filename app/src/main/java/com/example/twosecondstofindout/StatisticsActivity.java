package com.example.twosecondstofindout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.database.Cursor;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Statistics of all games: records, totals, players and teams of the standard games and of the
// flash questions, and the questions that were missed the most
public class StatisticsActivity extends AppCompatActivity {

    private TextView StatGames;
    private TextView StatQuestions;
    private TextView StatSuccess;
    private LinearLayout PlayerStatistics;
    private TextView RecordsTitle;
    private LinearLayout Records;
    private TextView TeamStatisticsTitle;
    private LinearLayout TeamStatistics;
    private TextView FlashPlayerStatisticsTitle;
    private LinearLayout FlashPlayerStatistics;
    private TextView FlashTeamStatisticsTitle;
    private LinearLayout FlashTeamStatistics;
    private LinearLayout HardestQuestions;
    private Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_statistics);
        StatGames = findViewById(R.id.StatGames);
        StatQuestions = findViewById(R.id.StatQuestions);
        StatSuccess = findViewById(R.id.StatSuccess);
        PlayerStatistics = findViewById(R.id.PlayerStatistics);
        RecordsTitle = findViewById(R.id.RecordsTitle);
        Records = findViewById(R.id.Records);
        TeamStatisticsTitle = findViewById(R.id.TeamStatisticsTitle);
        TeamStatistics = findViewById(R.id.TeamStatistics);
        FlashPlayerStatisticsTitle = findViewById(R.id.FlashPlayerStatisticsTitle);
        FlashPlayerStatistics = findViewById(R.id.FlashPlayerStatistics);
        FlashTeamStatisticsTitle = findViewById(R.id.FlashTeamStatisticsTitle);
        FlashTeamStatistics = findViewById(R.id.FlashTeamStatistics);
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

        Records.removeAllViews();
        addRecord("🔥 Leghosszabb sorozat", database.selectLongestStreak(), " találat");
        addRecord("🔥 Leghosszabb villámsorozat", database.selectLongestFlashStreak(), " elsőként");
        String[] fastest = database.selectFastestAnswer();
        if (fastest != null) {
            // the time until Siker was pressed, e.g. 0,412 mp
            fastest[1] = Database.formatMillis(Long.parseLong(fastest[1]));
        }
        addRecord("⚡ Leggyorsabb válasz", fastest, " mp");
        addRecord("🏆 Legtöbb győzelem zsinórban", database.selectMostWinsInARow(), "");
        boolean hasRecords = Records.getChildCount() > 0;
        RecordsTitle.setVisibility(hasRecords ? View.VISIBLE : View.GONE);
        Records.setVisibility(hasRecords ? View.VISIBLE : View.GONE);

        // standard games: everybody as if they played alone, also the answers they gave in a team
        if (addStatisticsRows(PlayerStatistics, database.selectPlayerStatistics(), "✓", null) == 0) {
            addRow(PlayerStatistics, "Még nincs lejátszott kérdés.", "");
        }
        showSection(TeamStatisticsTitle, TeamStatistics, database.selectTeamStatistics(), "✓", false);
        // „Bárki válaszolhat” games: flash questions got, answered first, the wins and streaks
        showSection(FlashPlayerStatisticsTitle, FlashPlayerStatistics, database.selectFlashPlayerStatistics(), "⚡", null);
        showSection(FlashTeamStatisticsTitle, FlashTeamStatistics, database.selectFlashTeamStatistics(), "⚡", true);

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

    // record: {name, value}, or null if there is none yet. The name is indented under the title.
    private void addRecord(String title, String[] record, String unit) {
        if (record != null) {
            addRow(Records, twoLines(title, record[0], 32, false), record[1] + unit);
        }
    }

    // a section that is shown only if it has rows
    // flashTeams: null for players, otherwise the members of the teams are shown (of the
    // „Bárki válaszolhat” games if true)
    private void showSection(TextView title, LinearLayout parent, Cursor rows, String mark, Boolean flashTeams) {
        boolean hasRows = addStatisticsRows(parent, rows, mark, flashTeams) > 0;
        title.setVisibility(hasRows ? View.VISIBLE : View.GONE);
        parent.setVisibility(hasRows ? View.VISIBLE : View.GONE);
    }

    // name, questions, successful, wins, streaks; the wins and streaks, and the questions under them.
    // Returns the number of rows.
    private int addStatisticsRows(LinearLayout parent, Cursor rows, String mark, Boolean flashTeams) {
        parent.removeAllViews();
        while (rows.moveToNext()) {
            int answered = rows.getInt(1);
            int successful = rows.getInt(2);
            int wins = rows.getInt(3);
            String name = rows.getString(0);
            String members = flashTeams == null ? "" : database.selectTeamMembers(name, flashTeams);
            addRow(parent, members.isEmpty() ? name : twoLines(name, members, 16, true),
                    "🏆 " + wins + "   🔥 " + rows.getInt(4) + "\n" + mark + " " + successful + "/" + answered + " (" + percent(successful, answered) + ")");
        }
        int count = rows.getCount();
        rows.close();
        return count;
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

    // a second line indented by indentDp under the first one; small: smaller and grey, e.g. the members of a team
    private CharSequence twoLines(String first, String second, int indentDp, boolean small) {
        SpannableString text = new SpannableString(first + "\n" + second);
        int start = first.length() + 1;
        int indent = Math.round(indentDp * getResources().getDisplayMetrics().density);
        text.setSpan(new LeadingMarginSpan.Standard(indent), start, text.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        if (small) {
            text.setSpan(new RelativeSizeSpan(0.85f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.textSecondary)), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private void addRow(LinearLayout parent, CharSequence left, String right) {
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
        rightText.setGravity(Gravity.END);
        rightText.setTextSize(15);
        rightText.setPadding(padding, 0, 0, 0);
        rightText.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));

        row.addView(leftText);
        row.addView(rightText);
        parent.addView(row);
    }
}
