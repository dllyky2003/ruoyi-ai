package com.ruoyi.ems.room;


import com.ruoyi.ems.npc.TeamSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeamSizeTest {

    @Test
    void fromCodeValid() {
        assertEquals(TeamSize.TEAM_3MAN, TeamSize.fromCode("3MAN"));
        assertEquals(TeamSize.TEAM_4MAN, TeamSize.fromCode("4MAN"));
        assertEquals(TeamSize.TEAM_5MAN, TeamSize.fromCode("5MAN"));
    }

    @Test
    void fromCodeCaseInsensitive() {
        assertEquals(TeamSize.TEAM_3MAN, TeamSize.fromCode("3man"));
        assertEquals(TeamSize.TEAM_4MAN, TeamSize.fromCode("4man"));
    }

    @Test
    void fromCodeInvalid() {
        assertNull(TeamSize.fromCode(null));
        assertNull(TeamSize.fromCode(""));
        assertNull(TeamSize.fromCode("  "));
        assertNull(TeamSize.fromCode("6MAN"));
        assertNull(TeamSize.fromCode("ABC"));
    }

    @Test
    void fromValueValid() {
        assertEquals(TeamSize.TEAM_3MAN, TeamSize.fromValue(3));
        assertEquals(TeamSize.TEAM_4MAN, TeamSize.fromValue(4));
        assertEquals(TeamSize.TEAM_5MAN, TeamSize.fromValue(5));
    }

    @Test
    void fromValueInvalid() {
        assertNull(TeamSize.fromValue(null));
        assertNull(TeamSize.fromValue(0));
        assertNull(TeamSize.fromValue(6));
        assertNull(TeamSize.fromValue(-1));
    }

    @Test
    void defaultSizeIs3Man() {
        assertEquals(TeamSize.TEAM_3MAN, TeamSize.defaultSize());
    }

    @Test
    void codeAndValueConsistent() {
        for (TeamSize t : TeamSize.values()) {
            assertEquals(t, TeamSize.fromCode(t.getCode()));
            assertEquals(t, TeamSize.fromValue(t.getValue()));
        }
    }

    @Test
    void valueMatchesCodePrefix() {
        // "3MAN" 的 value 应该是 3
        assertEquals(3, TeamSize.TEAM_3MAN.getValue());
        assertEquals(4, TeamSize.TEAM_4MAN.getValue());
        assertEquals(5, TeamSize.TEAM_5MAN.getValue());
    }
}
