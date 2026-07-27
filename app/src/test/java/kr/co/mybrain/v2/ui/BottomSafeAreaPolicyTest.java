package kr.co.mybrain.v2.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BottomSafeAreaPolicyTest {
    @Test public void gestureNavigationKeepsReadableBottomSpace() {
        assertEquals(112, BottomSafeAreaPolicy.requiredBottomDp(24, false));
    }

    @Test public void threeButtonNavigationAddsSystemBarAndContentSpace() {
        assertEquals(136, BottomSafeAreaPolicy.requiredBottomDp(48, false));
    }

    @Test public void oneHandModeKeepsMoreRoomForLastAction() {
        assertEquals(168, BottomSafeAreaPolicy.requiredBottomDp(48, true));
    }

    @Test public void missingInsetStillKeepsMinimumSpace() {
        assertEquals(88, BottomSafeAreaPolicy.requiredBottomDp(0, false));
    }

    @Test public void negativeInsetIsIgnored() {
        assertEquals(120, BottomSafeAreaPolicy.requiredBottomDp(-30, true));
    }
}
