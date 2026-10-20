package com.example.twosecondstofindout;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Database extends SQLiteOpenHelper
{
    public static final String DATABASE_NAME = "twoSecondsGame";
    public static final String TABLE_QUESTIONS = "questions";
    public static final String TABLE_PLAYERS = "players";
    public static final String TABLE_ANSWERS = "answers";
    public static final String TABLE_HISTORY = "history";
    public static final String TABLE_GAMES = "games";
    public static final String TABLE_FLASH_HISTORY = "flash_history";
    public static final String TABLE_QUESTION_TOPICS = "question_topics";
    public static final String TABLE_TEAM_MEMBERS = "team_members";

    public static final String COL_1 = "id";
    public static final String COL_2 = "topic";
    public static final String COL_3 = "question";
    public static final String COL_4 = "answer";
    public static final String COL_5 = "used";


    public static final String COL_6 = "id";
    public static final String COL_7 = "name";
    public static final String COL_8 = "points";
    public static final String COL_9 = "topic";

    // the time to answer, unless a player gets more or less
    public static final int DEFAULT_ANSWER_MS = 2000;
    // a bonus point for every 3rd correct answer in a row, when the streak bonus is on
    public static final int STREAK_LENGTH = 3;

    // the kinds of the entries of the undo log: a Siker / Késő, a flash round, a pass
    public static final int ANSWER = 0;
    public static final int FLASH = 1;
    public static final int PASS = 2;

    // the topic ids are the position + 1: 1: Gyerek, 2: Felnőtt, 3: Bibliai, 4: Sport, ...
    public static final String[] TOPICS = {"Gyerek", "Felnőtt", "Bibliai", "Sport", "Földrajz", "Történelem", "Tudomány"};
    // the topic ids in the order they are listed on the screens, the Bibliai is the last
    public static final int[] TOPIC_ORDER = {1, 2, 4, 5, 6, 7, 3};

    // the position of the topic in TOPIC_ORDER
    public static int topicPosition(int topic)
    {
        for (int i = 0; i < TOPIC_ORDER.length; i++)
        {
            if (TOPIC_ORDER[i] == topic)
            {
                return i;
            }
        }
        return 0;
    }

    private final Context context;

    public Database(Context context)
    {
        super(context, DATABASE_NAME, null, 14);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_QUESTIONS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topic INTEGER NOT NULL, question VARCHAR(350) NOT NULL, answer VARCHAR(200) NOT NULL, used INTEGER DEFAULT 0, flagged INTEGER DEFAULT 0)");
        // in team games a player is a team, its members are in team_members (members: only before version 9)
        db.execSQL("CREATE TABLE IF NOT EXISTS "+ TABLE_PLAYERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(200), topic INTEGER DEFAULT 2, answered INTEGER DEFAULT 0, points INTEGER DEFAULT 0, active INTEGER DEFAULT 1, members TEXT DEFAULT '', answer_ms INTEGER DEFAULT " + DEFAULT_ANSWER_MS + ")");
        createTeamMembersTable(db);
        // every Siker / Késő decision, so the last ones can be undone. deactivated: comma separated
        // ids of the players who dropped out of the tie-break right after this answer
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ANSWERS + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, question_id INTEGER NOT NULL, points INTEGER NOT NULL, deactivated TEXT DEFAULT '', history_id INTEGER DEFAULT 0)");
        createStatisticsTables(db);
        addTeamStatisticsColumns(db);
        addFlashPassAndRecordColumns(db);
        createFlashStatistics(db);
        addFlashMembersColumn(db);
        addFlashStreakColumn(db);
        addTeamStreakColumn(db);
        createQuestionTopicsTable(db);
        mergeQuestionsFromFile(db);
    }

    // The members of the teams in the order they answer, every member with their own level and time
    private void createTeamMembersTable(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TEAM_MEMBERS + "(player_id INTEGER NOT NULL, position INTEGER NOT NULL, name VARCHAR(200) NOT NULL, topic INTEGER NOT NULL, answer_ms INTEGER NOT NULL, PRIMARY KEY (player_id, position))");
    }

    // answers.kind: what the undo log entry is (ANSWER, FLASH, PASS), players.passes: the passes left,
    // history.streak: correct answers in a row of the person in the game with this one,
    // history.elapsed_ms: the time until Siker, 0 if not known
    private void addFlashPassAndRecordColumns(SQLiteDatabase db)
    {
        db.execSQL("ALTER TABLE " + TABLE_ANSWERS + " ADD COLUMN kind INTEGER DEFAULT " + ANSWER);
        db.execSQL("ALTER TABLE " + TABLE_PLAYERS + " ADD COLUMN passes INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN streak INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN elapsed_ms INTEGER DEFAULT 0");
    }

    // Every flash question for everybody who got it: name is a person, or a team if team is 1 (in a team
    // game the team gets the point), success is 1 for the one who answered first. flash_id is the entry
    // of the undo log. games.flash_only is 1 for the „Bárki válaszolhat” games.
    private void createFlashStatistics(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FLASH_HISTORY + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, flash_id INTEGER NOT NULL, name VARCHAR(200) NOT NULL, team INTEGER NOT NULL, question_id INTEGER NOT NULL, success INTEGER NOT NULL, played_at INTEGER NOT NULL)");
        db.execSQL("ALTER TABLE " + TABLE_GAMES + " ADD COLUMN flash_only INTEGER DEFAULT 0");
    }

    // flash_history.members: the members of the team who got the flash question, comma separated
    private void addFlashMembersColumn(SQLiteDatabase db)
    {
        db.execSQL("ALTER TABLE " + TABLE_FLASH_HISTORY + " ADD COLUMN members TEXT DEFAULT ''");
    }

    // flash_history.streak: the flash questions in a row the player or team answered first with this
    // one, in the game; only in the row of the one who answered first, 0 in the others
    private void addFlashStreakColumn(SQLiteDatabase db)
    {
        db.execSQL("ALTER TABLE " + TABLE_FLASH_HISTORY + " ADD COLUMN streak INTEGER DEFAULT 0");
    }

    // history.team_streak: in a team game the correct answers in a row of the team with this one
    // (the streak bonus counts these), 0 in a game without teams
    private void addTeamStreakColumn(SQLiteDatabase db)
    {
        db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN team_streak INTEGER DEFAULT 0");
    }

    // history.player_name is the team member who answered and history.team their team,
    // games.team is 1 if the winner is a team
    private void addTeamStatisticsColumns(SQLiteDatabase db)
    {
        db.execSQL("ALTER TABLE " + TABLE_HISTORY + " ADD COLUMN team VARCHAR(200) DEFAULT ''");
        db.execSQL("ALTER TABLE " + TABLE_GAMES + " ADD COLUMN team INTEGER DEFAULT 0");
    }

    // A question can belong to more topics, e.g. Felnőtt and Tudomány.
    // questions.topic is only kept as the first topic of the question.
    private void createQuestionTopicsTable(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_QUESTION_TOPICS + "(question_id INTEGER NOT NULL, topic INTEGER NOT NULL, PRIMARY KEY (question_id, topic))");
    }

    // Kept across games for the statistics: every answer and every winner
    private void createStatisticsTables(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_name VARCHAR(200) NOT NULL, topic INTEGER NOT NULL, question_id INTEGER NOT NULL, success INTEGER NOT NULL, played_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_GAMES + "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, winner VARCHAR(200) NOT NULL, played_at INTEGER NOT NULL)");
    }

    // the index of the first line of res/raw/questions.txt that was added in version 11
    private static final int FIRST_LINE_OF_VERSION_11 = 1306;

    // Loads res/raw/questions.txt, one question per line: topics<TAB>question<TAB>answer,
    // where topics is a comma separated list, e.g. 2,7. Questions that are already there
    // (same text) only get the missing topics, so this can run again after an update.
    private void mergeQuestionsFromFile(SQLiteDatabase db)
    {
        mergeQuestionsFromFile(db, 0);
    }

    // The lines before firstNewLine were loaded already: they only give their topics to the
    // questions that are still there, so the deleted or reworded questions do not come back.
    private void mergeQuestionsFromFile(SQLiteDatabase db, int firstNewLine)
    {
        db.beginTransaction();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.questions), StandardCharsets.UTF_8)))
        {
            String line;
            for (int lineIndex = 0; (line = reader.readLine()) != null; lineIndex++)
            {
                String[] parts = line.split("\t");
                if (parts.length != 3)
                {
                    continue;
                }
                String[] topicTexts = parts[0].split(",");
                int[] topics = new int[topicTexts.length];
                for (int i = 0; i < topicTexts.length; i++)
                {
                    topics[i] = Integer.parseInt(topicTexts[i].trim());
                }
                if (lineIndex >= firstNewLine)
                {
                    addOrLinkQuestion(db, topics, parts[1].trim(), parts[2].trim());
                }
                else
                {
                    long questionId = findQuestion(db, parts[1].trim());
                    if (questionId >= 0)
                    {
                        linkTopics(db, questionId, topics);
                    }
                }
            }
            db.setTransactionSuccessful();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not load questions", e);
        }
        finally
        {
            db.endTransaction();
        }
    }

    // Adds the question, or only its missing topics if a question with the same text exists.
    // Returns true if a new question was added.
    private boolean addOrLinkQuestion(SQLiteDatabase db, int[] topics, String question, String answer)
    {
        long questionId = findQuestion(db, question);
        boolean added = questionId < 0;
        if (added)
        {
            ContentValues values = new ContentValues();
            values.put(COL_2, topics[0]);
            values.put(COL_3, question);
            values.put(COL_4, answer);
            questionId = db.insertOrThrow(TABLE_QUESTIONS, null, values);
        }
        linkTopics(db, questionId, topics);
        return added;
    }

    private void renameQuestion(SQLiteDatabase db, String oldText, String newText)
    {
        db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET question = ? WHERE question = ?", new Object[]{newText, oldText});
    }

    // a reworded question that has a new answer too
    private void renameQuestion(SQLiteDatabase db, String oldText, String newText, String newAnswer)
    {
        db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET question = ?, answer = ? WHERE question = ?", new Object[]{newText, newAnswer, oldText});
    }

    private long findQuestion(SQLiteDatabase db, String question)
    {
        Cursor cursor = db.rawQuery("SELECT id FROM " + TABLE_QUESTIONS + " WHERE question = ?", new String[]{question});
        long id = cursor.moveToFirst() ? cursor.getLong(0) : -1;
        cursor.close();
        return id;
    }

    private void linkTopics(SQLiteDatabase db, long questionId, int[] topics)
    {
        for (int topic : topics)
        {
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_QUESTION_TOPICS + "(question_id, topic) VALUES (?, ?)", new Object[]{questionId, topic});
        }
    }

    // From version 4 on the data is migrated step by step, so own questions and statistics are kept.
    // Add a new step here for every new version instead of dropping the tables.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4)
        {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ANSWERS);
            onCreate(db);
            return;
        }
        if (oldVersion < 5)
        {
            createStatisticsTables(db);
            db.execSQL("ALTER TABLE " + TABLE_ANSWERS + " ADD COLUMN history_id INTEGER DEFAULT 0");
        }
        if (oldVersion < 6)
        {
            // more topics per question, and the new questions and topics of the file
            createQuestionTopicsTable(db);
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_QUESTION_TOPICS + "(question_id, topic) SELECT id, topic FROM " + TABLE_QUESTIONS);
            // questions of the old file that were reworded to be unambiguous, so they are not added twice
            renameQuestion(db, "Mi Magyarország leghosszabb folyója?", "Melyik folyó szakasza a leghosszabb Magyarország területén?");
            renameQuestion(db, "Ki építette fel a templomot Jeruzsálemben?", "Ki építtette az első templomot Jeruzsálemben?");
            renameQuestion(db, "Melyik a Biblia leghosszabb könyve?", "Melyik bibliai könyvnek van a legtöbb fejezete?");
            mergeQuestionsFromFile(db);
        }
        if (oldVersion < 7)
        {
            // questions marked as wrong during the game, and team members
            db.execSQL("ALTER TABLE " + TABLE_QUESTIONS + " ADD COLUMN flagged INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_PLAYERS + " ADD COLUMN members TEXT DEFAULT ''");
        }
        if (oldVersion < 8)
        {
            // every player can have their own time to answer, e.g. more for children
            db.execSQL("ALTER TABLE " + TABLE_PLAYERS + " ADD COLUMN answer_ms INTEGER DEFAULT " + DEFAULT_ANSWER_MS);
        }
        if (oldVersion < 9)
        {
            // every team member gets their own level and time, and their own statistics
            createTeamMembersTable(db);
            addTeamStatisticsColumns(db);
            Cursor teams = db.rawQuery("SELECT id, topic, answer_ms, members FROM " + TABLE_PLAYERS, null);
            while (teams.moveToNext())
            {
                List<String> names = splitNames(teams.getString(3));
                for (int i = 0; i < names.size(); i++)
                {
                    insertMember(db, teams.getInt(0), i, new Member(names.get(i), teams.getInt(1), teams.getInt(2)));
                }
            }
            teams.close();
        }
        if (oldVersion < 10)
        {
            // flash rounds, passes and records
            addFlashPassAndRecordColumns(db);
        }
        if (oldVersion < 11)
        {
            // questions of the old file that were reworded to be unambiguous, some with a new answer
            renameQuestion(db, "Hány lába van a polipnak?", "Hány karja van a polipnak?");
            renameQuestion(db, "Kb. hány nap alatt kerüli meg a Hold a Földet?", "Kb. hány nap alatt kerüli meg a Hold a Földet a csillagokhoz képest?");
            renameQuestion(db, "Melyik vírus okozta a 2020-ban kitört világjárványt?", "Melyik vírus okozta a 2020-as világjárványt?");
            renameQuestion(db, "Hány színe van a szivárványnak?", "Milyen színű a szivárvány legkülső sávja?", "Piros");
            renameQuestion(db, "Miből lesz a pillangó?", "Mi lesz a hernyóból?", "Lepke (pillangó)");
            renameQuestion(db, "Ki hozza a karácsonyi ajándékot a magyar hagyomány szerint?", "Mit teszünk ki az ablakba december 5-én este, hogy a Mikulás megtöltse?", "Csizmát");
            renameQuestion(db, "Melyik alakzatnak nincs sarka?", "Hány sarka van a körnek?", "Egy sincs");
            renameQuestion(db, "Melyik ország határos Magyarországgal nyugaton?", "Melyik német nyelvű ország határos Magyarországgal nyugaton?");
            renameQuestion(db, "Melyik ország határos Magyarországgal keleten?", "Melyik keleti szomszédunkhoz tartozik ma Erdély?", "Romániához");
            renameQuestion(db, "Hány törzs vett részt a honfoglalásban a hagyomány szerint?", "Hány vezér kötött vérszerződést a hagyomány szerint?");
            renameQuestion(db, "Melyik eseménnyel kezdődött a francia forradalom?", "Melyik párizsi erődbörtön ostromára emlékeznek a franciák július 14-én?", "A Bastille");
            // the new questions of the file, and the Felnőtt level gets every Sport, Földrajz,
            // Történelem and Tudomány question, the own ones too
            mergeQuestionsFromFile(db, FIRST_LINE_OF_VERSION_11);
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_QUESTION_TOPICS + "(question_id, topic) SELECT question_id, 2 FROM "
                    + TABLE_QUESTION_TOPICS + " WHERE topic IN (4, 5, 6, 7)");
        }
        if (oldVersion < 12)
        {
            // statistics of the flash questions and of the „Bárki válaszolhat” games, the streaks of the teams
            createFlashStatistics(db);
            addTeamStreakColumn(db);
        }
        if (oldVersion < 13)
        {
            // the members of the teams in the flash statistics
            addFlashMembersColumn(db);
        }
        if (oldVersion < 14)
        {
            // streaks and the streak bonus in the „Bárki válaszolhat” games
            addFlashStreakColumn(db);
        }
    }

    // Returns id, question, answer of a random question not used yet. When every
    // question of the topic was used, the topic starts over. Null if the topic is empty.
    public Cursor selectRandomQuestion(int topic)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {String.valueOf(topic)};
        String query = "SELECT q.id, q.question, q.answer FROM " + TABLE_QUESTIONS + " q JOIN " + TABLE_QUESTION_TOPICS + " t ON t.question_id = q.id "
                + "WHERE t.topic = ? AND q.used = 0 ORDER BY RANDOM() LIMIT 1";
        Cursor question = db.rawQuery(query, args);
        if (question.getCount() == 0)
        {
            question.close();
            db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET used = 0 WHERE id IN (SELECT question_id FROM " + TABLE_QUESTION_TOPICS + " WHERE topic = ?)", args);
            question = db.rawQuery(query, args);
        }
        if (question.getCount() == 0)
        {
            question.close();
            return null;
        }
        return question;
    }

    public void markQuestionUsed(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET used = 1 WHERE id = ?", new Object[]{questionId});
    }

    // The first player of the game, e.g. "Pirosak · Anna" in a team game
    public String selectFirstPlayerName()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor first = db.rawQuery("SELECT id, name FROM " + TABLE_PLAYERS + " ORDER BY id LIMIT 1", null);
        String name = "";
        if (first.moveToFirst())
        {
            Member member = selectMemberOnTurn(first.getInt(0), 0);
            name = member == null ? first.getString(1) : first.getString(1) + " · " + member.name;
        }
        first.close();
        return name;
    }

    // Without a game master the one who asks first: {asker, their team or empty}, or null
    public String[] selectFirstAsker()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor first = db.rawQuery("SELECT id FROM " + TABLE_PLAYERS + " ORDER BY id LIMIT 1", null);
        String[] asker = first.moveToFirst() ? selectAsker(first.getInt(0), 0) : null;
        first.close();
        return asker;
    }

    // Returns question, answer of the given question
    public Cursor selectQuestion(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT question, answer FROM " + TABLE_QUESTIONS + " WHERE id = ?", new String[]{String.valueOf(questionId)});
    }

    // One player, or a team with its members
    public static class Player
    {
        public String name;
        public int topic;
        public int answerMs;
        public final List<Member> members = new ArrayList<>();

        public Player(String name, int topic, int answerMs)
        {
            this.name = name;
            this.topic = topic;
            this.answerMs = answerMs;
        }
    }

    public static class Member
    {
        public String name;
        public int topic;
        public int answerMs;

        public Member(String name, int topic, int answerMs)
        {
            this.name = name;
            this.topic = topic;
            this.answerMs = answerMs;
        }
    }

    // The players of the last game, with the team members
    public List<Player> selectPlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        List<Player> players = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT id, name, topic, answer_ms FROM " + TABLE_PLAYERS + " ORDER BY id", null);
        while (cursor.moveToNext())
        {
            Player player = new Player(cursor.getString(1), cursor.getInt(2), cursor.getInt(3));
            player.members.addAll(selectMembers(db, cursor.getInt(0)));
            players.add(player);
        }
        cursor.close();
        return players;
    }

    private List<Member> selectMembers(SQLiteDatabase db, int playerId)
    {
        List<Member> members = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT name, topic, answer_ms FROM " + TABLE_TEAM_MEMBERS + " WHERE player_id = ? ORDER BY position",
                new String[]{String.valueOf(playerId)});
        while (cursor.moveToNext())
        {
            members.add(new Member(cursor.getString(0), cursor.getInt(1), cursor.getInt(2)));
        }
        cursor.close();
        return members;
    }

    private static void insertMember(SQLiteDatabase db, long playerId, int position, Member member)
    {
        ContentValues values = new ContentValues();
        values.put("player_id", playerId);
        values.put("position", position);
        values.put("name", member.name);
        values.put("topic", member.topic);
        values.put("answer_ms", member.answerMs);
        db.insert(TABLE_TEAM_MEMBERS, null, values);
    }

    // The active player with the fewest answered questions is next: id, name, answered, topic, answer_ms
    public Cursor selectNextPlayer()
    {
        return selectNextPlayer(false);
    }

    // playersAsk: the asker of the round answers last in the round, asked by the next asker.
    // In a team game every member answers in a round, the members of the teams take turns.
    public Cursor selectNextPlayer(boolean playersAsk)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        if (!playersAsk)
        {
            return db.rawQuery("SELECT id, name, answered, topic, answer_ms FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY answered, id LIMIT 1", null);
        }
        int perRound = answersPerRound();
        int lastId = -1;
        int[] ids = selectActivePlayerIds();
        if (ids.length > 0)
        {
            lastId = ids[askerIndex(ids.length, selectActiveAnsweredRange()[0] / perRound)];
        }
        return db.rawQuery("SELECT id, name, answered, topic, answer_ms FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY answered / ?, id = ?, answered, id LIMIT 1",
                new String[]{String.valueOf(perRound), String.valueOf(lastId)});
    }

    // How many questions every player answers in a round: in a team game every member answers one
    public int answersPerRound()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor size = db.rawQuery("SELECT MAX(size) FROM (SELECT COUNT(*) AS size FROM " + TABLE_TEAM_MEMBERS + " t JOIN " + TABLE_PLAYERS + " p ON p.id = t.player_id GROUP BY t.player_id)", null);
        int result = size.moveToFirst() ? Math.max(1, size.getInt(0)) : 1;
        size.close();
        return result;
    }

    private int[] selectActivePlayerIds()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor players = db.rawQuery("SELECT id FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY id", null);
        int[] ids = new int[players.getCount()];
        for (int i = 0; players.moveToNext(); i++)
        {
            ids[i] = players.getInt(0);
        }
        players.close();
        return ids;
    }

    // Without a game master one player asks everybody in a round: the last one in the 1st round,
    // the one before in the 2nd round, and so on. The position of the asker of the given round.
    static int askerIndex(int players, int round)
    {
        return ((players - 1 - round) % players + players) % players;
    }

    // The team member whose turn it is: they answer in turns, one question each.
    // null if the player is not a team.
    public Member selectMemberOnTurn(int playerId, int answered)
    {
        return memberOnTurn(selectMembers(this.getWritableDatabase(), playerId), answered);
    }

    private static Member memberOnTurn(List<Member> members, int answered)
    {
        return members.isEmpty() ? null : members.get(answered % members.size());
    }

    // the names in a comma separated list, without the empty ones (members before version 9)
    private static List<String> splitNames(String names)
    {
        List<String> result = new ArrayList<>();
        if (names != null)
        {
            for (String name : names.split(","))
            {
                if (!name.trim().isEmpty())
                {
                    result.add(name.trim());
                }
            }
        }
        return result;
    }

    // Without a game master the asker of the round asks everybody else, and the asker
    // themselves is asked by the asker of the next round. In a team game one member of the
    // asker team asks the whole round, the members take turns from one asking to the next.
    // answered: how many questions the given player answered so far.
    // {asker, their team or empty}, or null if there is nobody else to ask.
    public String[] selectAsker(int playerId, int answered)
    {
        int round = answered / answersPerRound();
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor players = db.rawQuery("SELECT id, name FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY id", null);
        int count = players.getCount();
        String[] asker = null;
        if (count > 1)
        {
            players.moveToPosition(askerIndex(count, round));
            if (players.getInt(0) == playerId)
            {
                round++;
                players.moveToPosition(askerIndex(count, round));
            }
            // every team asks once in every <count> rounds
            Member member = memberOnTurn(selectMembers(db, players.getInt(0)), round / count);
            asker = member == null ? new String[]{players.getString(1), ""} : new String[]{member.name, players.getString(1)};
        }
        players.close();
        return asker;
    }

    // {fewest, most} answered questions among the active players. When they are equal, a round is over.
    public int[] selectActiveAnsweredRange()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor range = db.rawQuery("SELECT MIN(answered), MAX(answered) FROM " + TABLE_PLAYERS + " WHERE active = 1", null);
        int[] result = range.moveToFirst() ? new int[]{range.getInt(0), range.getInt(1)} : new int[]{0, 0};
        range.close();
        return result;
    }

    public String selectActivePlayerNames()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor names = db.rawQuery("SELECT name FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY id", null);
        StringBuilder result = new StringBuilder();
        while (names.moveToNext())
        {
            if (result.length() > 0)
            {
                result.append(", ");
            }
            result.append(names.getString(0));
        }
        names.close();
        return result.toString();
    }

    public int countPlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAYERS, null);
        int result = count.moveToFirst() ? count.getInt(0) : 0;
        count.close();
        return result;
    }

    public void savePlayerAnswer(int playerId, int questionId, boolean success)
    {
        savePlayerAnswer(playerId, questionId, success, false);
    }

    public int savePlayerAnswer(int playerId, int questionId, boolean success, boolean streakBonus)
    {
        return savePlayerAnswer(playerId, questionId, success, streakBonus, 0);
    }

    // streakBonus: every 3rd correct answer in a row is worth 2 points.
    // elapsedMs: the time from the start of the timer until Siker, for the records (0 if not known).
    // Returns the points given. The undo log keeps them, so an undo takes back the bonus too.
    public int savePlayerAnswer(int playerId, int questionId, boolean success, boolean streakBonus, long elapsedMs)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        int points = success ? 1 : 0;
        // the correct answers in a row of the player or team with this one
        int inARow = success ? correctInARow(db, playerId) + 1 : 0;
        if (success && streakBonus && inARow % STREAK_LENGTH == 0)
        {
            points = 2;
        }
        long historyId = 0;
        Cursor player = db.rawQuery("SELECT name, topic, answered FROM " + TABLE_PLAYERS + " WHERE id = ?", new String[]{String.valueOf(playerId)});
        if (player.moveToFirst())
        {
            // in a team game the statistics are kept for the member who answered too
            Member member = memberOnTurn(selectMembers(db, playerId), player.getInt(2));
            ContentValues history = new ContentValues();
            history.put("player_name", member == null ? player.getString(0) : member.name);
            history.put("team", member == null ? "" : player.getString(0));
            history.put("topic", member == null ? player.getInt(1) : member.topic);
            history.put("question_id", questionId);
            // a correct answer, whatever bonus it got
            history.put("success", success ? 1 : 0);
            history.put("streak", success ? personInARow(db, member == null ? player.getString(0) : member.name) + 1 : 0);
            history.put("team_streak", member == null ? 0 : inARow);
            history.put("elapsed_ms", success ? elapsedMs : 0);
            history.put("played_at", System.currentTimeMillis());
            historyId = db.insert(TABLE_HISTORY, null, history);
        }
        player.close();
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = answered + 1, points = points + ? WHERE id = ?", new Object[]{points, playerId});

        ContentValues values = new ContentValues();
        values.put("player_id", playerId);
        values.put("question_id", questionId);
        values.put("points", points);
        values.put("history_id", historyId);
        db.insert(TABLE_ANSWERS, null, values);
        return points;
    }

    // the correct answers of the person in this game since their last Késő, also in a team
    private int personInARow(SQLiteDatabase db, String name)
    {
        Cursor answers = db.rawQuery("SELECT h.streak FROM " + TABLE_ANSWERS + " a JOIN " + TABLE_HISTORY + " h ON h.id = a.history_id "
                + "WHERE h.player_name = ? ORDER BY a.id DESC LIMIT 1", new String[]{name});
        int streak = answers.moveToFirst() ? answers.getInt(0) : 0;
        answers.close();
        return streak;
    }

    // the correct answers of the player in this game since their last Késő
    private int correctInARow(SQLiteDatabase db, int playerId)
    {
        Cursor answers = db.rawQuery("SELECT points FROM " + TABLE_ANSWERS + " WHERE player_id = ? AND kind = " + ANSWER + " ORDER BY id DESC", new String[]{String.valueOf(playerId)});
        int count = 0;
        while (answers.moveToNext() && answers.getInt(0) > 0)
        {
            count++;
        }
        answers.close();
        return count;
    }

    // A flash question of a „Bárki válaszolhat” game: the question for everybody, the one who answered first gets a point.
    // playerId: 0 if nobody answered in time. It is in the undo log, and in the flash statistics,
    // not in the answered questions.
    public void saveFlashAnswer(int playerId, int questionId)
    {
        saveFlashAnswer(playerId, questionId, -1, false);
    }

    public void saveFlashAnswer(int playerId, int questionId, int askerId)
    {
        saveFlashAnswer(playerId, questionId, askerId, false);
    }

    // askerId: the player who asked and saw the answer, so they did not get the question; -1 if nobody.
    // streakBonus: every 3rd flash question in a row answered first is worth 2 points; the questions
    // the player did not get (they asked) do not break the streak. Returns the points given.
    public int saveFlashAnswer(int playerId, int questionId, int askerId, boolean streakBonus)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        int inARow = playerId > 0 ? flashInARow(db, playerId) + 1 : 0;
        int points = playerId > 0 ? 1 : 0;
        if (streakBonus && inARow > 0 && inARow % STREAK_LENGTH == 0)
        {
            points = 2;
        }
        db.beginTransaction();
        try
        {
            db.execSQL("UPDATE " + TABLE_PLAYERS + " SET points = points + ? WHERE id = ?", new Object[]{points, playerId});
            long flashId = insertLogEntry(db, playerId, questionId, points, FLASH);
            db.execSQL("INSERT INTO " + TABLE_FLASH_HISTORY + "(flash_id, name, team, members, question_id, success, played_at) "
                    + "SELECT ?, p.name, EXISTS (SELECT 1 FROM " + TABLE_TEAM_MEMBERS + " t WHERE t.player_id = p.id), "
                    + "IFNULL((SELECT GROUP_CONCAT(t.name, ', ') FROM " + TABLE_TEAM_MEMBERS + " t WHERE t.player_id = p.id), ''), ?, p.id = ?, ? "
                    + "FROM " + TABLE_PLAYERS + " p WHERE p.active = 1 AND p.id != ?",
                    new Object[]{flashId, questionId, playerId, System.currentTimeMillis(), askerId});
            db.execSQL("UPDATE " + TABLE_FLASH_HISTORY + " SET streak = ? WHERE flash_id = ? AND success = 1", new Object[]{inARow, flashId});
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
        return points;
    }

    // the flash questions of this game the player or team got and answered first, since the last one they missed
    private int flashInARow(SQLiteDatabase db, int playerId)
    {
        Cursor rows = db.rawQuery("SELECT f.success FROM " + TABLE_FLASH_HISTORY + " f JOIN " + TABLE_ANSWERS + " a ON a.id = f.flash_id "
                + "JOIN " + TABLE_PLAYERS + " p ON p.id = ? WHERE f.name = p.name "
                + "AND f.team = EXISTS (SELECT 1 FROM " + TABLE_TEAM_MEMBERS + " t WHERE t.player_id = p.id) ORDER BY f.id DESC",
                new String[]{String.valueOf(playerId)});
        int count = 0;
        while (rows.moveToNext() && rows.getInt(0) == 1)
        {
            count++;
        }
        rows.close();
        return count;
    }

    // The player passes the question and gets another one, if they have a pass left.
    // Returns false if they have none.
    public boolean usePass(int playerId, int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        if (selectPassesLeft(playerId) <= 0)
        {
            return false;
        }
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET passes = passes - 1 WHERE id = ?", new Object[]{playerId});
        insertLogEntry(db, playerId, questionId, 0, PASS);
        return true;
    }

    public int selectPassesLeft(int playerId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor passes = db.rawQuery("SELECT passes FROM " + TABLE_PLAYERS + " WHERE id = ?", new String[]{String.valueOf(playerId)});
        int result = passes.moveToFirst() ? passes.getInt(0) : 0;
        passes.close();
        return result;
    }

    private static long insertLogEntry(SQLiteDatabase db, int playerId, int questionId, int points, int kind)
    {
        ContentValues values = new ContentValues();
        values.put("player_id", playerId);
        values.put("question_id", questionId);
        values.put("points", points);
        values.put("kind", kind);
        return db.insert(TABLE_ANSWERS, null, values);
    }

    // The most points of the active players, to see if the target was reached
    public int selectMostActivePoints()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor most = db.rawQuery("SELECT IFNULL(MAX(points), 0) FROM " + TABLE_PLAYERS + " WHERE active = 1", null);
        int result = most.moveToFirst() ? most.getInt(0) : 0;
        most.close();
        return result;
    }

    // true if some players dropped out in a tie-break
    public boolean hasInactivePlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor inactive = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAYERS + " WHERE active = 0", null);
        boolean result = inactive.moveToFirst() && inactive.getInt(0) > 0;
        inactive.close();
        return result;
    }

    // the topic of a flash question: the level of one of the active players or team members
    public int selectRandomActiveTopic()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor topic = db.rawQuery("SELECT topic FROM (SELECT p.topic FROM " + TABLE_PLAYERS + " p WHERE p.active = 1 AND NOT EXISTS (SELECT 1 FROM " + TABLE_TEAM_MEMBERS + " t WHERE t.player_id = p.id) "
                + "UNION ALL SELECT t.topic FROM " + TABLE_TEAM_MEMBERS + " t JOIN " + TABLE_PLAYERS + " p ON p.id = t.player_id WHERE p.active = 1) ORDER BY RANDOM() LIMIT 1", null);
        int result = topic.moveToFirst() ? topic.getInt(0) : 2;
        topic.close();
        return result;
    }

    // id, name of the active players, for the flash round
    public Cursor selectActivePlayers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT id, name FROM " + TABLE_PLAYERS + " WHERE active = 1 ORDER BY id", null);
    }

    // the flash questions of this game so far
    public int countFlashAnswers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ANSWERS + " WHERE kind = " + FLASH, null);
        int result = count.moveToFirst() ? count.getInt(0) : 0;
        count.close();
        return result;
    }

    // Only flash questions, without a game master: the phone goes on with every question,
    // backwards from the last player, and the one who asks does not answer that question.
    // In a round there is a question for every player, so everybody asks once in a round.
    // question: the number of the question in the game, from 0.
    // {asker, their team or empty, the id of the player or team}, or null with less than 2 players.
    public String[] selectFlashAsker(int question)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor players = db.rawQuery("SELECT id, name FROM " + TABLE_PLAYERS + " ORDER BY id", null);
        int count = players.getCount();
        String[] asker = null;
        if (count > 1)
        {
            players.moveToPosition(askerIndex(count, question));
            int playerId = players.getInt(0);
            Member member = memberOnTurn(selectMembers(db, playerId), question / count);
            asker = member == null
                    ? new String[]{players.getString(1), "", String.valueOf(playerId)}
                    : new String[]{member.name, players.getString(1), String.valueOf(playerId)};
        }
        players.close();
        return asker;
    }

    // ANSWER, FLASH or PASS: what the next undo takes back, -1 if nothing
    public int selectLastAnswerKind()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor last = db.rawQuery("SELECT kind FROM " + TABLE_ANSWERS + " ORDER BY id DESC LIMIT 1", null);
        int result = last.moveToFirst() ? last.getInt(0) : -1;
        last.close();
        return result;
    }

    // Only the active players with the most points stay active (tie-break).
    // Returns how many players are still active.
    public int keepLeadersActive()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor losers = db.rawQuery("SELECT id FROM " + TABLE_PLAYERS + " WHERE active = 1 AND points < (SELECT MAX(points) FROM " + TABLE_PLAYERS + " WHERE active = 1)", null);
        StringBuilder ids = new StringBuilder();
        while (losers.moveToNext())
        {
            ids.append(",").append(losers.getInt(0));
        }
        losers.close();
        if (ids.length() > 0)
        {
            String idList = ids.substring(1);
            db.execSQL("UPDATE " + TABLE_PLAYERS + " SET active = 0 WHERE id IN (" + idList + ")");
            // remember it on the last answer, so undoing that answer brings these players back
            db.execSQL("UPDATE " + TABLE_ANSWERS + " SET deactivated = deactivated || ? WHERE id = (SELECT MAX(id) FROM " + TABLE_ANSWERS + ")", new Object[]{ids.toString()});
        }
        Cursor active = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PLAYERS + " WHERE active = 1", null);
        int result = active.moveToFirst() ? active.getInt(0) : 0;
        active.close();
        return result;
    }

    public boolean hasAnswers()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor count = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ANSWERS, null);
        boolean result = count.moveToFirst() && count.getInt(0) > 0;
        count.close();
        return result;
    }

    // Takes back the last Siker / Késő, flash round or pass (see selectLastAnswerKind).
    // Returns the id of its question, or -1 if there was nothing to undo.
    public int undoLastAnswer()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor last = db.rawQuery("SELECT id, player_id, question_id, points, deactivated, history_id, kind FROM " + TABLE_ANSWERS + " ORDER BY id DESC LIMIT 1", null);
        if (!last.moveToFirst())
        {
            last.close();
            return -1;
        }
        int answerId = last.getInt(0);
        int playerId = last.getInt(1);
        int questionId = last.getInt(2);
        int points = last.getInt(3);
        String deactivated = last.getString(4);
        long historyId = last.getLong(5);
        int kind = last.getInt(6);
        last.close();

        db.execSQL("DELETE FROM " + TABLE_HISTORY + " WHERE id = ?", new Object[]{historyId});
        db.execSQL("DELETE FROM " + TABLE_FLASH_HISTORY + " WHERE flash_id = ?", new Object[]{answerId});
        if (deactivated != null && deactivated.length() > 1)
        {
            db.execSQL("UPDATE " + TABLE_PLAYERS + " SET active = 1 WHERE id IN (" + deactivated.substring(1) + ")");
        }
        // a flash round or a pass did not count as the player's turn
        int answered = kind == ANSWER ? 1 : 0;
        int passes = kind == PASS ? 1 : 0;
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = answered - ?, points = points - ?, passes = passes + ? WHERE id = ?", new Object[]{answered, points, passes, playerId});
        db.execSQL("DELETE FROM " + TABLE_ANSWERS + " WHERE id = ?", new Object[]{answerId});
        return questionId;
    }

    // name, points ordered by points
    public Cursor selectScoreboard()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT name, points FROM " + TABLE_PLAYERS + " ORDER BY points DESC, id", null);
    }

    public String selectScoreboardText()
    {
        Cursor scoreboard = selectScoreboard();
        StringBuilder result = new StringBuilder();
        int place = 1;
        while (scoreboard.moveToNext())
        {
            String[] medals = {"🥇", "🥈", "🥉"};
            result.append(place <= 3 ? medals[place - 1] : place + ".").append("  ")
                    .append(scoreboard.getString(0)).append(" - ")
                    .append(scoreboard.getInt(1)).append(" pont\n");
            place++;
        }
        scoreboard.close();
        return result.toString();
    }

    // Same players, new game
    public void resetScores()
    {
        resetScores(0);
    }

    // passesPerPerson: how many passes everybody gets, a team gets them for every member
    public void resetScores(int passesPerPerson)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_PLAYERS + " SET answered = 0, points = 0, active = 1, passes = ? * MAX(1, (SELECT COUNT(*) FROM " + TABLE_TEAM_MEMBERS + " t WHERE t.player_id = " + TABLE_PLAYERS + ".id))",
                new Object[]{passesPerPerson});
        db.execSQL("DELETE FROM " + TABLE_ANSWERS);
    }

    public void insertPlayersForNewGame(String[] players, int[] topics)
    {
        int[] answerMs = new int[players.length];
        java.util.Arrays.fill(answerMs, DEFAULT_ANSWER_MS);
        insertPlayersForNewGame(players, topics, new String[players.length], answerMs);
    }

    // members: the comma separated team members of each player, null or empty if not a team.
    // They get the level and time of their team. answerMs: the time of each player to answer.
    public void insertPlayersForNewGame(String[] players, int[] topics, String[] members, int[] answerMs)
    {
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < players.length; i++)
        {
            Player player = new Player(players[i], topics[i], answerMs[i]);
            for (String name : splitNames(members[i]))
            {
                player.members.add(new Member(name, topics[i], answerMs[i]));
            }
            list.add(player);
        }
        insertPlayersForNewGame(list);
    }

    // Players without a name and team members without a name are left out
    public void insertPlayersForNewGame(List<Player> players)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PLAYERS);
        db.execSQL("DELETE FROM " + TABLE_ANSWERS);
        db.execSQL("DELETE FROM " + TABLE_TEAM_MEMBERS);
        for (Player player : players)
        {
            if (player.name == null || player.name.trim().isEmpty())
            {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(COL_7, player.name.trim());
            values.put(COL_9, player.topic);
            values.put("answer_ms", player.answerMs);
            long playerId = db.insert(TABLE_PLAYERS, null, values);
            int position = 0;
            for (Member member : player.members)
            {
                if (member.name != null && !member.name.trim().isEmpty())
                {
                    insertMember(db, playerId, position++, new Member(member.name.trim(), member.topic, member.answerMs));
                }
            }
        }
    }

    //
    // QUESTION EDITOR
    //

    // _id, question, answer, shown of a topic, for the question list.
    // shown is the question with a flag in front of it if it was marked as wrong.
    public Cursor selectQuestions(int topic)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT q.id AS _id, q.question, q.answer, " + SHOWN_QUESTION + " FROM " + TABLE_QUESTIONS + " q JOIN " + TABLE_QUESTION_TOPICS + " t ON t.question_id = q.id "
                + "WHERE t.topic = ? ORDER BY q.id DESC", new String[]{String.valueOf(topic)});
    }

    private static final String SHOWN_QUESTION = "CASE WHEN q.flagged = 1 THEN '⚑ ' || q.question ELSE q.question END AS shown";

    // _id, question, answer, shown of the questions marked as wrong, of every topic
    public Cursor selectFlaggedQuestions()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT q.id AS _id, q.question, q.answer, " + SHOWN_QUESTION + " FROM " + TABLE_QUESTIONS + " q WHERE q.flagged = 1 ORDER BY q.id DESC", null);
    }

    // marked during the game when the question or its answer is wrong, to be fixed in the editor
    public void setQuestionFlagged(int questionId, boolean flagged)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_QUESTIONS + " SET flagged = ? WHERE id = ?", new Object[]{flagged ? 1 : 0, questionId});
    }

    public boolean isQuestionFlagged(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT flagged FROM " + TABLE_QUESTIONS + " WHERE id = ?", new String[]{String.valueOf(questionId)});
        boolean flagged = cursor.moveToFirst() && cursor.getInt(0) == 1;
        cursor.close();
        return flagged;
    }

    public int[] selectQuestionTopics(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT topic FROM " + TABLE_QUESTION_TOPICS + " WHERE question_id = ? ORDER BY topic", new String[]{String.valueOf(questionId)});
        int[] topics = new int[cursor.getCount()];
        for (int i = 0; cursor.moveToNext(); i++)
        {
            topics[i] = cursor.getInt(0);
        }
        cursor.close();
        return topics;
    }

    // questionId -1: new question. topics must not be empty.
    public void saveQuestion(int questionId, int[] topics, String question, String answer)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_2, topics[0]);
        values.put(COL_3, question.trim());
        values.put(COL_4, answer.trim());
        db.beginTransaction();
        try
        {
            long id = questionId;
            if (questionId < 0)
            {
                id = db.insertOrThrow(TABLE_QUESTIONS, null, values);
            }
            else
            {
                db.update(TABLE_QUESTIONS, values, "id = ?", new String[]{String.valueOf(questionId)});
                db.delete(TABLE_QUESTION_TOPICS, "question_id = ?", new String[]{String.valueOf(questionId)});
            }
            linkTopics(db, id, topics);
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    public void deleteQuestion(int questionId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_QUESTION_TOPICS, "question_id = ?", new String[]{String.valueOf(questionId)});
        db.delete(TABLE_QUESTIONS, "id = ?", new String[]{String.valueOf(questionId)});
    }

    //
    // STATISTICS
    //

    // Called when a game is over, the winner is the first on the scoreboard
    public void saveGameResult()
    {
        saveGameResult(false);
    }

    // flashOnly: a „Bárki válaszolhat” game
    public void saveGameResult(boolean flashOnly)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor winner = db.rawQuery("SELECT name, EXISTS (SELECT 1 FROM " + TABLE_TEAM_MEMBERS + " t WHERE t.player_id = p.id) FROM " + TABLE_PLAYERS + " p ORDER BY points DESC, active DESC, id LIMIT 1", null);
        if (winner.moveToFirst())
        {
            ContentValues values = new ContentValues();
            values.put("winner", winner.getString(0));
            values.put("team", winner.getInt(1));
            values.put("flash_only", flashOnly ? 1 : 0);
            values.put("played_at", System.currentTimeMillis());
            db.insert(TABLE_GAMES, null, values);
        }
        winner.close();
    }

    // When the last answer of a finished game is undone, the game is not over any more
    public void deleteLastGameResult()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_GAMES + " WHERE id = (SELECT MAX(id) FROM " + TABLE_GAMES + ")");
    }

    // {finished games, answered questions, successful answers}
    public int[] selectTotals()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor totals = db.rawQuery("SELECT (SELECT COUNT(*) FROM " + TABLE_GAMES + "), COUNT(*), IFNULL(SUM(success), 0) FROM " + TABLE_HISTORY, null);
        int[] result = totals.moveToFirst() ? new int[]{totals.getInt(0), totals.getInt(1), totals.getInt(2)} : new int[]{0, 0, 0};
        totals.close();
        return result;
    }

    // STANDARD GAMES: name, answered, successful, wins, streaks ordered by wins and success rate.
    // A streak is every 3rd correct answer in a row, like the streak bonus, also when it is off.
    // Every person, also the answers they gave in a team; wins only of their own games.
    // The winners are added too: before version 12 a standard game could be won with the points
    // of flash rounds, without an answered question.
    public Cursor selectPlayerStatistics()
    {
        return selectStatistics("SELECT player_name AS name, 1 AS answered, success, " + isStreak("streak") + " AS streaks FROM " + TABLE_HISTORY, 0, 0);
    }

    // The same for the teams: name, answered, successful, wins, streaks of the team
    public Cursor selectTeamStatistics()
    {
        return selectStatistics("SELECT team AS name, 1 AS answered, success, " + isStreak("team_streak") + " AS streaks FROM " + TABLE_HISTORY + " WHERE team != ''", 1, 0);
    }

    // The members of the team who played in it, in the order they first played, e.g. "Anna, Béla".
    // flash: in the „Bárki válaszolhat” games, otherwise in the standard games.
    public String selectTeamMembers(String team, boolean flash)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor rows = flash
                ? db.rawQuery("SELECT members FROM " + TABLE_FLASH_HISTORY + " WHERE team = 1 AND name = ? AND members != '' ORDER BY id", new String[]{team})
                : db.rawQuery("SELECT player_name FROM " + TABLE_HISTORY + " WHERE team = ? GROUP BY player_name ORDER BY MIN(id)", new String[]{team});
        java.util.Set<String> members = new java.util.LinkedHashSet<>();
        while (rows.moveToNext())
        {
            members.addAll(splitNames(rows.getString(0)));
        }
        rows.close();
        return String.join(", ", members);
    }

    // 1 if the answer is the 3rd, 6th, … correct one in a row
    private static String isStreak(String column)
    {
        return "(" + column + " > 0 AND " + column + " % " + STREAK_LENGTH + " = 0)";
    }

    // „BÁRKI VÁLASZOLHAT” GAMES: name, flash questions got, answered first, wins, streaks.
    // In a team game the team got the question.
    public Cursor selectFlashPlayerStatistics()
    {
        return selectStatistics("SELECT name, 1 AS answered, success, " + isStreak("streak") + " AS streaks FROM " + TABLE_FLASH_HISTORY + " WHERE team = 0", 0, 1);
    }

    public Cursor selectFlashTeamStatistics()
    {
        return selectStatistics("SELECT name, 1 AS answered, success, " + isStreak("streak") + " AS streaks FROM " + TABLE_FLASH_HISTORY + " WHERE team = 1", 1, 1);
    }

    // rows: name, answered (1), success, streaks (1 or 0). team: 1 for the team wins, 0 for the own wins of a person.
    // flashOnly: 1 for the wins of the „Bárki válaszolhat” games, 0 for the standard games.
    private Cursor selectStatistics(String rows, int team, int flashOnly)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String winners = "SELECT winner AS name, 0 AS answered, 0 AS success, 0 AS streaks FROM " + TABLE_GAMES
                + " WHERE team = " + team + " AND flash_only = " + flashOnly;
        return db.rawQuery("SELECT r.name, SUM(r.answered), SUM(r.success), "
                + "(SELECT COUNT(*) FROM " + TABLE_GAMES + " g WHERE g.winner = r.name AND g.team = " + team + " AND g.flash_only = " + flashOnly + ") AS wins, SUM(r.streaks) "
                + "FROM (" + rows + " UNION ALL " + winners + ") r GROUP BY r.name "
                + "ORDER BY wins DESC, SUM(r.success) * 1.0 / SUM(r.answered) DESC", null);
    }

    // RECORDS: {name, value} or null if there is none yet

    // the most correct answers in a row of a person in one game
    public String[] selectLongestStreak()
    {
        return selectRecord("SELECT player_name, MAX(streak) FROM " + TABLE_HISTORY + " WHERE streak > 0 GROUP BY player_name ORDER BY MAX(streak) DESC, MIN(id) LIMIT 1");
    }

    // the most flash questions in a row answered first in a „Bárki válaszolhat” game, by a person or a team
    public String[] selectLongestFlashStreak()
    {
        return selectRecord("SELECT name, MAX(streak) FROM " + TABLE_FLASH_HISTORY + " WHERE streak > 0 GROUP BY name, team ORDER BY MAX(streak) DESC, MIN(id) LIMIT 1");
    }

    // the fastest correct answer, in milliseconds until Siker was pressed
    public String[] selectFastestAnswer()
    {
        return selectRecord("SELECT player_name, elapsed_ms FROM " + TABLE_HISTORY + " WHERE success = 1 AND elapsed_ms > 0 ORDER BY elapsed_ms, id LIMIT 1");
    }

    // the most games won in a row by the same player or team
    public String[] selectMostWinsInARow()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor games = db.rawQuery("SELECT winner FROM " + TABLE_GAMES + " ORDER BY id", null);
        String best = null;
        int bestCount = 0;
        String previous = null;
        int count = 0;
        while (games.moveToNext())
        {
            String winner = games.getString(0);
            count = winner.equals(previous) ? count + 1 : 1;
            previous = winner;
            if (count > bestCount)
            {
                best = winner;
                bestCount = count;
            }
        }
        games.close();
        return best == null ? null : new String[]{best, String.valueOf(bestCount)};
    }

    private String[] selectRecord(String query)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor record = db.rawQuery(query, null);
        String[] result = record.moveToFirst() ? new String[]{record.getString(0), String.valueOf(record.getLong(1))} : null;
        record.close();
        return result;
    }

    // THE GAME THAT IS OVER (its answers are kept until the next game): {title, name, value} for
    // the fastest answer, the longest streak and the most correct answers of a person, and the
    // longest flash streak. Only the ones that happened.
    public List<String[]> selectGameHighlights()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        List<String[]> highlights = new ArrayList<>();
        String answers = TABLE_ANSWERS + " a JOIN " + TABLE_HISTORY + " h ON h.id = a.history_id";
        String[] fastest = selectRecord("SELECT h.player_name, h.elapsed_ms FROM " + answers
                + " WHERE h.success = 1 AND h.elapsed_ms > 0 ORDER BY h.elapsed_ms, h.id LIMIT 1");
        if (fastest != null)
        {
            highlights.add(new String[]{"⚡ Leggyorsabb válasz", fastest[0], formatMillis(Long.parseLong(fastest[1])) + " mp"});
        }
        String[] streak = selectRecord("SELECT h.player_name, MAX(h.streak) FROM " + answers
                + " GROUP BY h.player_name HAVING MAX(h.streak) >= 2 ORDER BY MAX(h.streak) DESC, MIN(h.id) LIMIT 1");
        if (streak != null)
        {
            highlights.add(new String[]{"🔥 Leghosszabb sorozat", streak[0], streak[1] + " találat"});
        }
        // only if one person has the most, at least 2
        String[] most = selectRecord("SELECT h.player_name, SUM(h.success) FROM " + answers
                + " GROUP BY h.player_name HAVING SUM(h.success) >= 2 ORDER BY SUM(h.success) DESC, MIN(h.id) LIMIT 1");
        String[] second = selectRecord("SELECT h.player_name, SUM(h.success) FROM " + answers
                + " GROUP BY h.player_name ORDER BY SUM(h.success) DESC, MIN(h.id) LIMIT 1 OFFSET 1");
        if (most != null && (second == null || !second[1].equals(most[1])))
        {
            highlights.add(new String[]{"🎯 Legtöbb találat", most[0], most[1] + " találat"});
        }
        String[] flashStreak = selectRecord("SELECT f.name, MAX(f.streak) FROM " + TABLE_FLASH_HISTORY + " f JOIN " + TABLE_ANSWERS
                + " a ON a.id = f.flash_id WHERE f.streak >= 2 GROUP BY f.name, f.team ORDER BY MAX(f.streak) DESC, MIN(f.id) LIMIT 1");
        if (flashStreak != null)
        {
            highlights.add(new String[]{"🔥 Leghosszabb villámsorozat", flashStreak[0], flashStreak[1] + " elsőként"});
        }
        return highlights;
    }

    // e.g. 412 → "0,412", 1500 → "1,500"
    public static String formatMillis(long ms)
    {
        return String.format(java.util.Locale.ROOT, "%d,%03d", ms / 1000, ms % 1000);
    }

    // question, answer, asked, successful of the questions that were missed the most (asked at least twice)
    public Cursor selectHardestQuestions(int limit)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery("SELECT q.question, q.answer, COUNT(*), SUM(h.success) FROM " + TABLE_HISTORY + " h "
                + "JOIN " + TABLE_QUESTIONS + " q ON q.id = h.question_id "
                + "GROUP BY h.question_id HAVING COUNT(*) >= 2 "
                + "ORDER BY SUM(h.success) * 1.0 / COUNT(*), COUNT(*) DESC LIMIT ?", new String[]{String.valueOf(limit)});
    }

    public void deleteStatistics()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_HISTORY);
        db.execSQL("DELETE FROM " + TABLE_GAMES);
        db.execSQL("DELETE FROM " + TABLE_FLASH_HISTORY);
        db.execSQL("UPDATE " + TABLE_ANSWERS + " SET history_id = 0");
    }

    //
    // BACKUP
    //

    // 1: one topic per question ("topic"), 2: more topics per question ("topics"),
    // 3: the team of the answers and of the winners, 4: streak and time of the answers for the records,
    // 5: the flash questions and the „Bárki válaszolhat” games, the streaks of the teams
    public static final int BACKUP_VERSION = 5;

    // Every question (own ones too) and the statistics as JSON
    public String exportJson() throws JSONException
    {
        return exportJson(true);
    }

    // statistics: false for a file with only the questions, e.g. to share them with friends.
    // Such a file cannot be restored, only its questions can be added.
    public String exportJson(boolean statistics) throws JSONException
    {
        SQLiteDatabase db = this.getWritableDatabase();
        JSONObject backup = new JSONObject();
        backup.put("app", "TwoSecondsToFindOut");
        backup.put("version", BACKUP_VERSION);
        backup.put("exported_at", System.currentTimeMillis());
        JSONArray questions = exportTable(db, "SELECT id, question, answer, flagged FROM " + TABLE_QUESTIONS + " ORDER BY id");
        for (int i = 0; i < questions.length(); i++)
        {
            JSONObject question = questions.getJSONObject(i);
            JSONArray topics = new JSONArray();
            for (int topic : selectQuestionTopics(question.getInt("id")))
            {
                topics.put(topic);
            }
            question.put("topics", topics);
        }
        backup.put("questions", questions);
        backup.put("statistics", statistics);
        if (!statistics)
        {
            return backup.toString(2);
        }
        backup.put("history", exportTable(db, "SELECT id, player_name, team, topic, question_id, success, streak, team_streak, elapsed_ms, played_at FROM " + TABLE_HISTORY + " ORDER BY id"));
        backup.put("games", exportTable(db, "SELECT id, winner, team, flash_only, played_at FROM " + TABLE_GAMES + " ORDER BY id"));
        backup.put("flash_history", exportTable(db, "SELECT id, flash_id, name, team, members, question_id, success, streak, played_at FROM " + TABLE_FLASH_HISTORY + " ORDER BY id"));
        return backup.toString(2);
    }

    private JSONArray exportTable(SQLiteDatabase db, String query) throws JSONException
    {
        JSONArray rows = new JSONArray();
        Cursor cursor = db.rawQuery(query, null);
        while (cursor.moveToNext())
        {
            JSONObject row = new JSONObject();
            for (int i = 0; i < cursor.getColumnCount(); i++)
            {
                if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER)
                {
                    row.put(cursor.getColumnName(i), cursor.getLong(i));
                }
                else
                {
                    row.put(cursor.getColumnName(i), cursor.getString(i));
                }
            }
            rows.put(row);
        }
        cursor.close();
        return rows;
    }

    // true if the backup has statistics too, so it can replace everything (backups before version 5 always have).
    // Throws JSONException if the file is not a backup of this app.
    public static boolean backupHasStatistics(String json) throws JSONException
    {
        JSONObject backup = new JSONObject(json);
        if (!"TwoSecondsToFindOut".equals(backup.optString("app")) || backup.getInt("version") > BACKUP_VERSION)
        {
            throw new JSONException("Not a backup of this app");
        }
        return backup.optBoolean("statistics", true) && backup.has("history");
    }

    // A file with only the questions cannot replace everything: the statistics would be lost
    public static class QuestionsOnlyException extends JSONException
    {
        public QuestionsOnlyException()
        {
            super("The file has only questions");
        }
    }

    // replace: everything is replaced by the backup (restore).
    // otherwise only the questions that are not there yet are added (e.g. questions from a friend).
    // Returns the number of questions added. Throws JSONException if the file is not a backup of this app.
    public int importJson(String json, boolean replace) throws JSONException
    {
        JSONObject backup = new JSONObject(json);
        if (!"TwoSecondsToFindOut".equals(backup.optString("app")) || backup.getInt("version") > BACKUP_VERSION)
        {
            throw new JSONException("Not a backup of this app");
        }
        if (replace && !backup.optBoolean("statistics", true))
        {
            throw new QuestionsOnlyException();
        }
        JSONArray questions = backup.getJSONArray("questions");
        SQLiteDatabase db = this.getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try
        {
            if (replace)
            {
                db.execSQL("DELETE FROM " + TABLE_QUESTIONS);
                db.execSQL("DELETE FROM " + TABLE_QUESTION_TOPICS);
                db.execSQL("DELETE FROM " + TABLE_HISTORY);
                db.execSQL("DELETE FROM " + TABLE_GAMES);
                db.execSQL("DELETE FROM " + TABLE_FLASH_HISTORY);
                // the undo log points to the old questions and statistics
                db.execSQL("DELETE FROM " + TABLE_ANSWERS);
            }
            for (int i = 0; i < questions.length(); i++)
            {
                JSONObject question = questions.getJSONObject(i);
                int[] topics = readTopics(question);
                String text = question.getString("question");
                String answer = question.getString("answer");
                if (!replace)
                {
                    // an existing question only gets the missing topics
                    if (addOrLinkQuestion(db, topics, text, answer))
                    {
                        added++;
                    }
                    continue;
                }
                ContentValues values = new ContentValues();
                // the statistics refer to the question ids
                values.put(COL_1, question.getLong("id"));
                values.put(COL_2, topics[0]);
                values.put(COL_3, text);
                values.put(COL_4, answer);
                values.put("flagged", question.optInt("flagged", 0) == 1 ? 1 : 0);
                db.insertOrThrow(TABLE_QUESTIONS, null, values);
                linkTopics(db, question.getLong("id"), topics);
                added++;
            }
            if (replace)
            {
                importTable(db, TABLE_HISTORY, backup.optJSONArray("history"),
                        new String[]{"id", "player_name", "topic", "question_id", "success", "played_at"}, new String[]{"team", "streak", "team_streak", "elapsed_ms"});
                importTable(db, TABLE_GAMES, backup.optJSONArray("games"),
                        new String[]{"id", "winner", "played_at"}, new String[]{"team", "flash_only"});
                importTable(db, TABLE_FLASH_HISTORY, backup.optJSONArray("flash_history"),
                        new String[]{"id", "flash_id", "name", "team", "question_id", "success", "played_at"}, new String[]{"members", "streak"});
            }
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
        return added;
    }

    // version 1 backups have one "topic", version 2 backups a "topics" list
    private int[] readTopics(JSONObject question) throws JSONException
    {
        JSONArray list = question.optJSONArray("topics");
        if (list == null)
        {
            return new int[]{question.getInt("topic")};
        }
        if (list.length() == 0)
        {
            throw new JSONException("Question without topic");
        }
        int[] topics = new int[list.length()];
        for (int i = 0; i < list.length(); i++)
        {
            topics[i] = list.getInt(i);
        }
        return topics;
    }

    // only the given columns are read, so a broken or forged file cannot reach other columns.
    // optional: columns that older backups do not have
    private void importTable(SQLiteDatabase db, String table, JSONArray rows, String[] columns, String[] optional) throws JSONException
    {
        if (rows == null)
        {
            return;
        }
        for (int i = 0; i < rows.length(); i++)
        {
            JSONObject row = rows.getJSONObject(i);
            ContentValues values = new ContentValues();
            for (String name : optional)
            {
                if (row.has(name))
                {
                    putValue(values, name, row.get(name));
                }
            }
            for (String name : columns)
            {
                putValue(values, name, row.get(name));
            }
            db.insertOrThrow(table, null, values);
        }
    }

    private static void putValue(ContentValues values, String name, Object value)
    {
        if (value instanceof Number)
        {
            values.put(name, ((Number) value).longValue());
        }
        else
        {
            values.put(name, String.valueOf(value));
        }
    }
}
