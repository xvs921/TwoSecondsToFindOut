package com.example.twosecondstofindout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// Lists the questions of a topic, new ones can be added, existing ones edited or deleted
public class QuestionsActivity extends AppCompatActivity {

    private Button ButtonBackQuestions;
    private Button ButtonNewQuestion;
    private Button[] TopicButtons;
    private LinearLayout TopicButtonRow;
    private TextView QuestionCount;
    private ListView QuestionList;
    private SimpleCursorAdapter adapter;
    private Database database;
    private int topic = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questions);
        init();
        ButtonBackQuestions.setOnClickListener(view -> finish());
        ButtonNewQuestion.setOnClickListener(view -> showEditDialog(-1, "", "", new int[]{topic}));
        for (int i = 0; i < TopicButtons.length; i++) {
            int selectedTopic = i + 1;
            TopicButtons[i].setOnClickListener(view -> selectTopic(selectedTopic));
        }
        QuestionList.setOnItemClickListener((parent, view, position, id) -> {
            Cursor row = (Cursor) adapter.getItem(position);
            showEditDialog((int) id, row.getString(1), row.getString(2), database.selectQuestionTopics((int) id));
        });
        selectTopic(1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Cursor cursor = adapter.getCursor();
        if (cursor != null) {
            cursor.close();
        }
    }

    private void init() {
        ButtonBackQuestions = findViewById(R.id.ButtonBackQuestions);
        ButtonNewQuestion = findViewById(R.id.ButtonNewQuestion);
        TopicButtonRow = findViewById(R.id.TopicButtons);
        TopicButtons = new Button[Database.TOPICS.length];
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < TopicButtons.length; i++) {
            Button button = new Button(this, null, 0, R.style.ButtonSmall);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Math.round(48 * getResources().getDisplayMetrics().density));
            params.setMarginEnd(gap);
            button.setLayoutParams(params);
            button.setText(Database.TOPICS[i]);
            TopicButtonRow.addView(button);
            TopicButtons[i] = button;
        }
        QuestionCount = findViewById(R.id.QuestionCount);
        QuestionList = findViewById(R.id.QuestionList);
        database = new Database(this);
        adapter = new SimpleCursorAdapter(this, R.layout.item_question, null,
                new String[]{"question", "answer"}, new int[]{R.id.ItemQuestion, R.id.ItemAnswer}, 0);
        QuestionList.setAdapter(adapter);
    }

    // the selected topic button is filled, the others are outlined
    private void selectTopic(int selectedTopic) {
        topic = selectedTopic;
        for (int i = 0; i < TopicButtons.length; i++) {
            boolean selected = i + 1 == topic;
            TopicButtons[i].setBackgroundResource(selected ? R.drawable.btn_primary : R.drawable.btn_secondary);
            TopicButtons[i].setTextColor(ContextCompat.getColor(this, selected ? R.color.surface : R.color.colorPrimary));
        }
        reload();
    }

    @SuppressLint("SetTextI18n")
    private void reload() {
        Cursor old = adapter.swapCursor(database.selectQuestions(topic));
        if (old != null) {
            old.close();
        }
        QuestionCount.setText(adapter.getCount() + " kérdés");
    }

    // questionId -1: new question. A question can be in more topics.
    private void showEditDialog(int questionId, String question, String answer, int[] topics) {
        View form = getLayoutInflater().inflate(R.layout.dialog_question, null);
        EditText questionInput = form.findViewById(R.id.EditQuestion);
        EditText answerInput = form.findViewById(R.id.EditAnswer);
        LinearLayout topicInputs = form.findViewById(R.id.EditTopics);
        questionInput.setText(question);
        answerInput.setText(answer);
        CheckBox[] topicBoxes = new CheckBox[Database.TOPICS.length];
        for (int i = 0; i < topicBoxes.length; i++) {
            CheckBox box = new CheckBox(this);
            box.setText(Database.TOPICS[i]);
            box.setTextSize(18);
            for (int selected : topics) {
                box.setChecked(box.isChecked() || selected == i + 1);
            }
            topicInputs.addView(box);
            topicBoxes[i] = box;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(questionId < 0 ? "Új kérdés" : "Kérdés szerkesztése")
                .setView(form)
                .setPositiveButton("Mentés", null)
                .setNegativeButton("Mégse", null);
        if (questionId >= 0) {
            builder.setNeutralButton("Törlés", (dialog, which) -> confirmDelete(questionId));
        }
        AlertDialog dialog = builder.show();
        // the dialog stays open while something is missing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String newQuestion = questionInput.getText().toString().trim();
            String newAnswer = answerInput.getText().toString().trim();
            List<Integer> checked = new ArrayList<>();
            for (int i = 0; i < topicBoxes.length; i++) {
                if (topicBoxes[i].isChecked()) {
                    checked.add(i + 1);
                }
            }
            if (newQuestion.isEmpty() || newAnswer.isEmpty() || checked.isEmpty()) {
                Toast.makeText(this, "Add meg a kérdést, a választ és legalább egy kategóriát!", Toast.LENGTH_SHORT).show();
                return;
            }
            int[] newTopics = new int[checked.size()];
            for (int i = 0; i < newTopics.length; i++) {
                newTopics[i] = checked.get(i);
            }
            database.saveQuestion(questionId, newTopics, newQuestion, newAnswer);
            dialog.dismiss();
            reload();
        });
    }

    private void confirmDelete(int questionId) {
        new AlertDialog.Builder(this)
                .setTitle("Törlöd a kérdést?")
                .setPositiveButton("Törlés", (dialog, which) -> {
                    database.deleteQuestion(questionId);
                    reload();
                })
                .setNegativeButton("Mégse", null)
                .show();
    }
}
