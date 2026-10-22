package com.example.twosecondstofindout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.core.view.ViewCompat;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PlayersActivity extends AppCompatActivity {

    private static final String[] LEVELS = new String[Database.TOPIC_ORDER.length];
    static {
        for (int i = 0; i < LEVELS.length; i++) {
            LEVELS[i] = Database.TOPICS[Database.TOPIC_ORDER[i] - 1];
        }
    }
    // at most 12 people play, alone or together in the teams
    static final int MAX_PEOPLE = 12;
    private static final int MAX_PLAYERS = MAX_PEOPLE;
    // a new team starts with this many member rows
    private static final int NEW_TEAM_MEMBERS = 2;
    // the times to answer a player can get
    private static final int[] ANSWER_MS = {1000, 1500, 2000, 2500, 3000, 4000, 5000};

    private EditText[] Players;
    private Spinner[] PlayerLevels;
    private Spinner[] PlayerTimes;
    private LinearLayout[] MemberLists;
    private TextView[] NewMemberButtons;
    private View[] RemoveButtons;
    private View[] PlayerRows;
    private TextView PlayersTitle;
    private TextView PlayersHint;
    private Button ButtonModeSingle;
    private Button ButtonModeTeams;
    private Button ButtonNewPlayer;
    private Button ButtonBackPlayers;
    private Button ButtonNextPlayers;
    private ArrayAdapter<String> levelAdapter;
    private ArrayAdapter<String> timeAdapter;
    private Database database;
    // in a team game every row is a team, and its members answer in turns
    private boolean teams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.setUp(this);
        setContentView(R.layout.activity_players);
        init(savedInstanceState);
        // the previous screen was closed: the back gesture goes to the main menu like Vissza, instead of closing the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ButtonBackPlayers.performClick();
            }
        });
        ButtonModeSingle.setOnClickListener(view -> setTeams(false));
        ButtonModeTeams.setOnClickListener(view -> setTeams(true));
        ButtonNewPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (View row : PlayerRows) {
                    if (row.getVisibility() == View.GONE) {
                        row.setVisibility(View.VISIBLE);
                        break;
                    }
                }
                updateAddButtons();
            }
        });

        ButtonBackPlayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(PlayersActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        ButtonNextPlayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Database.Player> players = readPlayers();
                if (!teams) {
                    for (Database.Player player : players) {
                        player.members.clear();
                    }
                }
                if (countPeople(players) > MAX_PEOPLE) {
                    Toast.makeText(PlayersActivity.this, "Legfeljebb " + MAX_PEOPLE + " játékos lehet, a csapatokban együtt is.", Toast.LENGTH_LONG).show();
                    return;
                }
                if (teams && !everyTeamHasMembers(players)) {
                    Toast.makeText(PlayersActivity.this, "Adj meg tagokat minden csapatnak!", Toast.LENGTH_LONG).show();
                    return;
                }
                if (teams && !sameTeamSizes(players)) {
                    Toast.makeText(PlayersActivity.this, "Minden csapatban ugyanannyi ember legyen!", Toast.LENGTH_LONG).show();
                    return;
                }
                String duplicate = findDuplicateName(players);
                if (duplicate != null) {
                    Toast.makeText(PlayersActivity.this, "„" + duplicate + "” kétszer szerepel. Adj mindenkinek más nevet, különben a statisztikában összekeverednek!", Toast.LENGTH_LONG).show();
                    return;
                }
                database.insertPlayersForNewGame(players);
                if(database.countPlayers() == 0){
                    Toast.makeText(PlayersActivity.this, teams ? "Adj meg legalább egy csapatot!" : "Adj meg legalább egy játékost!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(PlayersActivity.this, RoundActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

    private static final String STATE_TEAMS = "teams";
    private static final String STATE_ROWS = "rows";

    // The rows have the same view ids, so Android cannot keep what was typed in them when the
    // screen is made again (e.g. the phone was turned): the rows are saved here instead.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<Bundle> rows = new ArrayList<>();
        for (int i = 0; i < PlayerRows.length && PlayerRows[i].getVisibility() == View.VISIBLE; i++) {
            rows.add(toBundle(readRow(i)));
        }
        outState.putParcelableArrayList(STATE_ROWS, rows);
        outState.putBoolean(STATE_TEAMS, teams);
    }

    static Bundle toBundle(Database.Player player) {
        Bundle bundle = new Bundle();
        bundle.putString("name", player.name);
        bundle.putInt("topic", player.topic);
        bundle.putInt("answerMs", player.answerMs);
        ArrayList<Bundle> members = new ArrayList<>();
        for (Database.Member member : player.members) {
            Bundle memberBundle = new Bundle();
            memberBundle.putString("name", member.name);
            memberBundle.putInt("topic", member.topic);
            memberBundle.putInt("answerMs", member.answerMs);
            members.add(memberBundle);
        }
        bundle.putParcelableArrayList("members", members);
        return bundle;
    }

    static Database.Player fromBundle(Bundle bundle) {
        Database.Player player = new Database.Player(bundle.getString("name", ""), bundle.getInt("topic"), bundle.getInt("answerMs"));
        ArrayList<Bundle> members = BundleCompat.getParcelableArrayList(bundle, "members", Bundle.class);
        if (members != null) {
            for (Bundle member : members) {
                player.members.add(new Database.Member(member.getString("name", ""), member.getInt("topic"), member.getInt("answerMs")));
            }
        }
        return player;
    }

    @SuppressLint("SetTextI18n")
    private void init(Bundle savedInstanceState) {
        LinearLayout playerList = findViewById(R.id.PlayerList);
        Players = new EditText[MAX_PLAYERS];
        PlayerLevels = new Spinner[MAX_PLAYERS];
        PlayerTimes = new Spinner[MAX_PLAYERS];
        MemberLists = new LinearLayout[MAX_PLAYERS];
        NewMemberButtons = new TextView[MAX_PLAYERS];
        RemoveButtons = new View[MAX_PLAYERS];
        PlayerRows = new View[MAX_PLAYERS];
        levelAdapter = new ArrayAdapter<>(this, R.layout.spinner_level, LEVELS);
        levelAdapter.setDropDownViewResource(R.layout.spinner_level_dropdown);
        String[] times = new String[ANSWER_MS.length];
        for (int i = 0; i < times.length; i++) {
            times[i] = formatSeconds(ANSWER_MS[i]);
        }
        timeAdapter = new ArrayAdapter<>(this, R.layout.spinner_level, times);
        timeAdapter.setDropDownViewResource(R.layout.spinner_level_dropdown);
        for (int i = 0; i < MAX_PLAYERS; i++) {
            View row = getLayoutInflater().inflate(R.layout.item_player, playerList, false);
            TextView number = row.findViewById(R.id.PlayerNumber);
            number.setText((i + 1) + ".");
            PlayerRows[i] = row;
            // the rows are saved by onSaveInstanceState
            row.setSaveFromParentEnabled(false);
            Players[i] = row.findViewById(R.id.PlayerName);
            PlayerLevels[i] = row.findViewById(R.id.PlayerLevel);
            PlayerLevels[i].setAdapter(levelAdapter);
            PlayerLevels[i].setSelection(1);
            PlayerTimes[i] = row.findViewById(R.id.PlayerTime);
            PlayerTimes[i].setAdapter(timeAdapter);
            PlayerTimes[i].setSelection(timeIndex(Database.DEFAULT_ANSWER_MS));
            MemberLists[i] = row.findViewById(R.id.MemberList);
            NewMemberButtons[i] = row.findViewById(R.id.ButtonNewMember);
            final int index = i;
            NewMemberButtons[i].setOnClickListener(view -> {
                addMemberRow(index, null);
                updateAddButtons();
            });
            // the row can be moved by long pressing its number
            number.setOnLongClickListener(view -> ViewCompat.startDragAndDrop(row,
                    ClipData.newPlainText("", ""), new View.DragShadowBuilder(row), index, 0));
            row.setOnDragListener((view, event) -> onRowDrag(index, event));
            RemoveButtons[i] = row.findViewById(R.id.PlayerRemove);
            RemoveButtons[i].setOnClickListener(view -> removeRow(index));
            playerList.addView(row);
        }
        PlayersTitle = findViewById(R.id.PlayersTitle);
        PlayersHint = findViewById(R.id.PlayersHint);
        ButtonModeSingle = findViewById(R.id.ButtonModeSingle);
        ButtonModeTeams = findViewById(R.id.ButtonModeTeams);
        database = new Database(this);

        // the players of the last game are filled in, at least two rows are shown.
        // When the screen is made again, the rows as they were.
        List<Database.Player> lastPlayers = new ArrayList<>();
        ArrayList<Bundle> savedRows = savedInstanceState == null ? null : BundleCompat.getParcelableArrayList(savedInstanceState, STATE_ROWS, Bundle.class);
        if (savedRows != null) {
            for (Bundle row : savedRows) {
                lastPlayers.add(fromBundle(row));
            }
        } else {
            lastPlayers = database.selectPlayers();
        }
        boolean lastGameHadTeams = false;
        for (int row = 0; row < lastPlayers.size() && row < Players.length; row++) {
            writeRow(row, lastPlayers.get(row));
            lastGameHadTeams |= !lastPlayers.get(row).members.isEmpty();
        }
        if (savedRows != null) {
            lastGameHadTeams = savedInstanceState.getBoolean(STATE_TEAMS);
        }
        for (int i = Math.max(2, lastPlayers.size()); i < PlayerRows.length; i++) {
            PlayerRows[i].setVisibility(View.GONE);
        }

        ButtonNewPlayer = findViewById(R.id.ButtonNewPlayer);
        ButtonBackPlayers = findViewById(R.id.ButtonBackPlayers);
        ButtonNextPlayers = findViewById(R.id.ButtonNextPlayers);
        setTeams(lastGameHadTeams);
    }

    //
    // MOVING THE ROWS
    //
    // the dragged row is dropped on another one, and takes its place
    private boolean onRowDrag(int index, DragEvent event) {
        if (!(event.getLocalState() instanceof Integer)) {
            return false;
        }
        View row = PlayerRows[index];
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                row.setAlpha(0.5f);
                break;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                row.setAlpha(1f);
                break;
            case DragEvent.ACTION_DROP:
                row.setAlpha(1f);
                moveRow((Integer) event.getLocalState(), index);
                break;
        }
        return true;
    }

    private void moveRow(int from, int to) {
        if (from == to) {
            return;
        }
        List<Database.Player> rows = new ArrayList<>();
        for (int i = 0; i < PlayerRows.length && PlayerRows[i].getVisibility() == View.VISIBLE; i++) {
            rows.add(readRow(i));
        }
        if (from >= rows.size() || to >= rows.size()) {
            return;
        }
        rows.add(to, rows.remove(from));
        for (int i = 0; i < rows.size(); i++) {
            writeRow(i, rows.get(i));
        }
        setTeams(teams);
    }

    // the rows below move up, the last one is emptied and hidden
    private void removeRow(int index) {
        List<Database.Player> rows = new ArrayList<>();
        for (int i = 0; i < PlayerRows.length && PlayerRows[i].getVisibility() == View.VISIBLE; i++) {
            rows.add(readRow(i));
        }
        if (rows.size() <= 1 || index >= rows.size()) {
            return;
        }
        rows.remove(index);
        for (int i = 0; i < rows.size(); i++) {
            writeRow(i, rows.get(i));
        }
        int last = rows.size();
        writeRow(last, new Database.Player("", 2, Database.DEFAULT_ANSWER_MS));
        PlayerRows[last].setVisibility(View.GONE);
        setTeams(teams);
    }

    // a member is dragged by their handle and dropped on another member of the same team
    private boolean onMemberDrag(View member, DragEvent event) {
        if (!(event.getLocalState() instanceof View)) {
            return false;
        }
        View dragged = (View) event.getLocalState();
        LinearLayout team = (LinearLayout) member.getParent();
        if (team == null || dragged.getParent() != team) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                member.setAlpha(0.5f);
                break;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                member.setAlpha(1f);
                break;
            case DragEvent.ACTION_DROP:
                member.setAlpha(1f);
                int to = team.indexOfChild(member);
                team.removeView(dragged);
                team.addView(dragged, to);
                break;
        }
        return true;
    }

    //
    // READING AND FILLING THE ROWS
    //
    // the row as it is on the screen, with the empty member rows too
    private Database.Player readRow(int index) {
        Database.Player player = new Database.Player(Players[index].getText().toString(),
                Database.TOPIC_ORDER[PlayerLevels[index].getSelectedItemPosition()], ANSWER_MS[PlayerTimes[index].getSelectedItemPosition()]);
        for (int i = 0; i < MemberLists[index].getChildCount(); i++) {
            View member = MemberLists[index].getChildAt(i);
            player.members.add(new Database.Member(
                    ((EditText) member.findViewById(R.id.MemberName)).getText().toString(),
                    Database.TOPIC_ORDER[((Spinner) member.findViewById(R.id.MemberLevel)).getSelectedItemPosition()],
                    ANSWER_MS[((Spinner) member.findViewById(R.id.MemberTime)).getSelectedItemPosition()]));
        }
        return player;
    }

    private void writeRow(int index, Database.Player player) {
        Players[index].setText(player.name);
        PlayerLevels[index].setSelection(Database.topicPosition(player.topic));
        PlayerTimes[index].setSelection(timeIndex(player.answerMs));
        MemberLists[index].removeAllViews();
        for (Database.Member member : player.members) {
            addMemberRow(index, member);
        }
    }

    // the visible rows, a team takes the level and time of its first member
    private List<Database.Player> readPlayers() {
        List<Database.Player> players = new ArrayList<>();
        for (int i = 0; i < PlayerRows.length; i++) {
            if (PlayerRows[i].getVisibility() != View.VISIBLE) {
                continue;
            }
            Database.Player player = readRow(i);
            List<Database.Member> named = new ArrayList<>();
            for (Database.Member member : player.members) {
                if (!member.name.trim().isEmpty()) {
                    named.add(member);
                }
            }
            player.members.clear();
            player.members.addAll(named);
            if (teams && !named.isEmpty()) {
                player.topic = named.get(0).topic;
                player.answerMs = named.get(0).answerMs;
            }
            players.add(player);
        }
        return players;
    }

    // member: null for an empty row with the level and time of the team
    private void addMemberRow(int index, Database.Member member) {
        View row = getLayoutInflater().inflate(R.layout.item_member, MemberLists[index], false);
        Spinner level = row.findViewById(R.id.MemberLevel);
        level.setAdapter(levelAdapter);
        Spinner time = row.findViewById(R.id.MemberTime);
        time.setAdapter(timeAdapter);
        if (member == null) {
            level.setSelection(PlayerLevels[index].getSelectedItemPosition());
            time.setSelection(PlayerTimes[index].getSelectedItemPosition());
        } else {
            ((EditText) row.findViewById(R.id.MemberName)).setText(member.name);
            level.setSelection(Database.topicPosition(member.topic));
            time.setSelection(timeIndex(member.answerMs));
        }
        row.findViewById(R.id.MemberRemove).setOnClickListener(view -> {
            MemberLists[index].removeView(row);
            updateAddButtons();
        });
        row.findViewById(R.id.MemberHandle).setOnLongClickListener(view -> ViewCompat.startDragAndDrop(row,
                ClipData.newPlainText("", ""), new View.DragShadowBuilder(row), row, 0));
        row.setOnDragListener((view, event) -> onMemberDrag(row, event));
        MemberLists[index].addView(row);
    }

    // the chosen mode is filled, the other one is outlined
    @SuppressLint("SetTextI18n")
    private void setTeams(boolean teams) {
        this.teams = teams;
        showSelected(ButtonModeTeams, teams);
        showSelected(ButtonModeSingle, !teams);
        PlayersTitle.setText(teams ? "Csapatok" : "Játékosok");
        PlayersHint.setText(teams
                ? "Add meg a csapatokat abban a sorrendben, ahogy ültök, és a tagokat szinttel és idővel! Egy körben minden tag kap egy kérdést, ezért minden csapatban ugyanannyian legyenek. A sorszámot vagy a ≡ jelet hosszan nyomva áthúzhatod a sort."
                : "Add meg a neveket abban a sorrendben, ahogy ültök, és válassz mindenkinek szintet és időt! A sorszámot hosszan nyomva áthúzhatod a sort.");
        ButtonNewPlayer.setText(teams ? "+ Új csapat" : "+ Új játékos");
        for (int i = 0; i < Players.length; i++) {
            Players[i].setHint(teams ? "Csapat neve" : "Név");
            // in a team game every member has their own level and time
            PlayerLevels[i].setVisibility(teams ? View.GONE : View.VISIBLE);
            PlayerTimes[i].setVisibility(teams ? View.GONE : View.VISIBLE);
            MemberLists[i].setVisibility(teams ? View.VISIBLE : View.GONE);
            while (teams && MemberLists[i].getChildCount() < NEW_TEAM_MEMBERS) {
                addMemberRow(i, null);
            }
        }
        updateAddButtons();
    }

    private void showSelected(Button button, boolean selected) {
        button.setBackgroundResource(selected ? R.drawable.btn_primary : R.drawable.btn_secondary);
        button.setTextColor(ContextCompat.getColorStateList(this,
                selected ? R.color.onPrimary : R.color.text_secondary_button));
    }

    // the named teams have at least one named member
    static boolean everyTeamHasMembers(List<Database.Player> players) {
        for (Database.Player player : players) {
            if (!player.name.trim().isEmpty() && player.members.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // every team has the same number of members, so everyone answers the same number of questions
    static boolean sameTeamSizes(List<Database.Player> players) {
        int size = -1;
        for (Database.Player player : players) {
            if (player.name.trim().isEmpty()) {
                continue;
            }
            if (size >= 0 && player.members.size() != size) {
                return false;
            }
            size = player.members.size();
        }
        return true;
    }

    // e.g. 1500 -> "1,5 mp"
    static String formatSeconds(int ms) {
        String seconds = ms % 1000 == 0 ? String.valueOf(ms / 1000) : (ms / 1000) + "," + (ms % 1000) / 100;
        return seconds + " mp";
    }

    // the closest of the times that can be chosen
    private static int timeIndex(int ms) {
        int best = 0;
        for (int i = 1; i < ANSWER_MS.length; i++) {
            if (Math.abs(ANSWER_MS[i] - ms) < Math.abs(ANSWER_MS[best] - ms)) {
                best = i;
            }
        }
        return best;
    }

    // At most 12 people: 12 players, or 12 member rows in the shown teams together.
    // A new team comes with its empty member rows.
    private void updateAddButtons() {
        int people = 0;
        int shownRows = 0;
        View nextRow = null;
        int nextRowPeople = 1;
        for (int i = 0; i < PlayerRows.length; i++) {
            if (PlayerRows[i].getVisibility() == View.VISIBLE) {
                people += teams ? MemberLists[i].getChildCount() : 1;
                shownRows++;
            } else if (nextRow == null) {
                nextRow = PlayerRows[i];
                nextRowPeople = teams ? Math.max(1, MemberLists[i].getChildCount()) : 1;
            }
        }
        ButtonNewPlayer.setVisibility(nextRow != null && people + nextRowPeople <= MAX_PEOPLE ? View.VISIBLE : View.GONE);
        for (TextView button : NewMemberButtons) {
            button.setVisibility(teams && people < MAX_PEOPLE ? View.VISIBLE : View.GONE);
        }
        // the last row stays
        for (View button : RemoveButtons) {
            button.setVisibility(shownRows > 1 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // The statistics and the streaks are kept by name: two players, two teams or two team members
    // with the same name (also with other capitals) would be one person. Returns that name, or null.
    static String findDuplicateName(List<Database.Player> players) {
        Set<String> names = new HashSet<>();
        Set<String> members = new HashSet<>();
        Locale hungarian = new Locale("hu", "HU");
        for (Database.Player player : players) {
            String name = player.name.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!names.add(name.toLowerCase(hungarian))) {
                return name;
            }
            for (Database.Member member : player.members) {
                String memberName = member.name.trim();
                if (!memberName.isEmpty() && !members.add(memberName.toLowerCase(hungarian))) {
                    return memberName;
                }
            }
        }
        return null;
    }

    // the people who play: the players, or the members of the teams
    static int countPeople(List<Database.Player> players) {
        int people = 0;
        for (Database.Player player : players) {
            if (!player.name.trim().isEmpty()) {
                people += player.members.isEmpty() ? 1 : player.members.size();
            }
        }
        return people;
    }
}
