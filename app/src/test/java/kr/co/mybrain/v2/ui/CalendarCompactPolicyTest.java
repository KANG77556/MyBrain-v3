package kr.co.mybrain.v2.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CalendarCompactPolicyTest {
    @Test public void phoneStartsCollapsed() {
        assertTrue(CalendarCompactPolicy.startCollapsed(false, false));
    }

    @Test public void tabletCanStartExpanded() {
        assertFalse(CalendarCompactPolicy.startCollapsed(true, false));
    }

    @Test public void focusedItemStartsCollapsed() {
        assertTrue(CalendarCompactPolicy.startCollapsed(true, true));
    }

    @Test public void labelsExplainAction() {
        assertEquals("달력 접기", CalendarCompactPolicy.toggleLabel(true, "2026년 7월 27일"));
        assertEquals("달력 펼치기 · 2026년 7월 27일", CalendarCompactPolicy.toggleLabel(false, "2026년 7월 27일"));
    }

    @Test public void touchHeightChanges() {
        assertEquals(52, CalendarCompactPolicy.toggleHeightDp(true));
        assertEquals(48, CalendarCompactPolicy.toggleHeightDp(false));
    }
}