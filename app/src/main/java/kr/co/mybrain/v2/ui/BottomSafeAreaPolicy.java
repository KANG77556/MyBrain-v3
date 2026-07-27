package kr.co.mybrain.v2.ui;

/** 시스템 내비게이션과 마지막 버튼 사이에 확보할 하단 안전 여백을 계산합니다. */
public final class BottomSafeAreaPolicy {
    private static final int NORMAL_EXTRA_DP = 88;
    private static final int ONE_HAND_EXTRA_DP = 120;

    private BottomSafeAreaPolicy() {}

    public static int requiredBottomDp(int navigationInsetDp, boolean oneHandMode) {
        return Math.max(0, navigationInsetDp)
                + (oneHandMode ? ONE_HAND_EXTRA_DP : NORMAL_EXTRA_DP);
    }
}
