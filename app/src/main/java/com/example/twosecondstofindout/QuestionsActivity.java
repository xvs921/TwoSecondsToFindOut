package com.example.twosecondstofindout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

// Lists the questions of a topic, new ones can be added, existing ones edited or deleted
public class QuestionsActivity extends AppCompatActivity {

    private Button ButtonBackQuestions;
    private Button ButtonNewQuestion;
    private Button[] TopicButtons;
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
        ButtonNewQuestion.setOnClickListener(view -> showEditDialog(-1, "", ""));
        for (int i = 0; i < TopicButtons.length; i++) {
            int selectedTopic = i + 1;
            TopicButtons[i].setOnClickListener(view -> selectTopic(selectedTopic));
        }
        QuestionList.setOnItemClickListener((parent, view, position, id) -> {
            Cursor row = (Cursor) adapter.getItem(position);
            showEditDialog((int) id, row.getString(1), row.getString(2));
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
        TopicButtons = new Button[]{findViewById(R.id.ButtonTopic1), findViewById(R.id.ButtonTopic2), findViewById(R.id.ButtonTopic3)};
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

    // questionId -1: new question
    private void showEditDialog(int questionId, String question, String answer) {
        View form = getLayoutInflater().inflate(R.layout.dialog_question, null);
        EditText questionInput = form.findViewById(R.id.EditQuestion);
        EditText answerInput = form.findViewById(R.id.EditAnswer);
        Spinner topicInput = form.findViewById(R.id.EditTopic);
        questionInput.setText(question);
        answerInput.setText(answer);
        ArrayAdapter<String> topicAdapter = new ArrayAdapter<>(this, R.layout.spinner_level, Database.TOPICS);
        topicAdapter.setDropDownViewResource(R.layout.spinner_level_dropdown);
        topicInput.setAdapter(topicAdapter);
        topicInput.setSelection(topic - 1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(questionId < 0 ? "Új kérdés" : "Kérdés szerkesztése")
                .setView(form)
                .setPositiveButton("Mentés", null)
                .setNegativeButton("Mégse", null);
        if (questionId >= 0) {
            builder.setNeutralButton("Törlés", (dialog, which) -> confirmDelete(questionId));
        }
        AlertDialog dialog = builder.show();
        // the dialog stays open while a field is empty
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String newQuestion = questionInput.getText().toString().trim();
            String newAnswer = answerInput.getText().toString().trim();
            if (newQuestion.isEmpty() || newAnswer.isEmpty()) {
                Toast.makeText(this, "Add meg a kérdést és a választ is!", Toast.LENGTH_SHORT).show();
                return;
            }
            database.saveQuestion(questionId, topicInput.getSelectedItemPosition() + 1, newQuestion, newAnswer);
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
