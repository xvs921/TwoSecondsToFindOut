package com.example.twosecondstofindout;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayersActivityTest {

    @Test
    public void teamsOfTheSameSizeAreAccepted() {
        assertTrue(PlayersActivity.sameTeamSizes(List.of(team("Pirosak", "Anna", "Béla"), team("Kékek", "Cili", "Dani"))));
    }

    @Test
    public void teamsOfDifferentSizesAreRejected() {
        assertFalse(PlayersActivity.sameTeamSizes(List.of(team("Pirosak", "Anna", "Béla"), team("Kékek", "Cili"))));
    }

    @Test
    public void emptyRowsAreNotTeams() {
        assertTrue(PlayersActivity.sameTeamSizes(List.of(team("Pirosak", "Anna"), team(" ", "Béla", "Cili"), team("Kékek", "Dani"))));
        assertTrue(PlayersActivity.everyTeamHasMembers(List.of(team("Pirosak", "Anna"), team(""))));
    }

    @Test
    public void aTeamWithoutMembersIsRejected() {
        assertFalse(PlayersActivity.everyTeamHasMembers(List.of(team("Pirosak", "Anna"), team("Kékek"))));
    }

    @Test
    public void thePeopleAreCountedInTheTeams() {
        assertEquals(5, PlayersActivity.countPeople(List.of(team("Pirosak", "Anna", "Béla"), team("Kékek", "Cili", "Dani"), team("Dani"), team(""))));
        assertTrue(PlayersActivity.countPeople(List.of(
                team("A", "1", "2", "3", "4"), team("B", "5", "6", "7", "8"), team("C", "9", "10", "11", "12", "13"))) > PlayersActivity.MAX_PEOPLE);
    }

    private static Database.Player team(String name, String... members) {
        Database.Player team = new Database.Player(name, 2, Database.DEFAULT_ANSWER_MS);
        for (String member : members) {
            team.members.add(new Database.Member(member, 2, Database.DEFAULT_ANSWER_MS));
        }
        return team;
    }
}
