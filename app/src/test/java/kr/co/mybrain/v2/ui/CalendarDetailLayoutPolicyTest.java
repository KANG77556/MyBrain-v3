package kr.co.mybrain.v2.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CalendarDetailLayoutPolicyTest {
    @Test public void phoneCalendarUsesCompactHeight() {
        assertEquals(286, CalendarDetailLayoutPolicy.calendarHeightDp(false));
    }

    @Test public void tabletCalendarUsesLargerHeight() {
        assertEquals(320, CalendarDetailLayoutPolicy.calendarHeightDp(true));
    }

    @Test public void oneHandModeKeepsMoreSpaceBelowLastItem() {
        int normal = CalendarDetailLayoutPolicy.bottomContentPaddingDp(false, 100);
        int oneHand = CalendarDetailLayoutPolicy.bottomContentPaddingDp(true, 100);
        assertTrue(oneHand > normal);
    }

    @Test public void largeTextKeepsAdditionalBottomSpace() {
        int normal = CalendarDetailLayoutPolicy.bottomContentPaddingDp(true, 100);
        int large = CalendarDetailLayoutPolicy.bottomContentPaddingDp(true, 115);
        assertTrue(large > normal);
    }

    @Test public void largeTextUsesLargerDetailOffset() {
        assertTrue(CalendarDetailLayoutPolicy.detailTopOffsetDp(130)
                > CalendarDetailLayoutPolicy.detailTopOffsetDp(100));
    }
}
