package kr.co.mybrain.ai;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBrain AI 1.8.4 실기기 UI 안정화 화면입니다.
 *
 * 1.8.3의 기능과 저장 구조는 그대로 사용하고, 화면이 그려진 뒤 실제 기기 폭과
 * 글자 확대 비율을 기준으로 간격·글자·터치 영역을 자동 보정합니다.
 */
public class WorkspaceActivityV3 extends WorkspaceActivityV2 {

    private View rootView;
    private boolean patchScheduled;
    private int lastChildSignature = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(android.R.id.content);
        installResponsiveWatcher();
        schedulePatch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        schedulePatch();
    }

    /** 화면 전환으로 내부 View가 다시 만들어질 때마다 한 번만 보정 작업을 예약합니다. */
    private void installResponsiveWatcher() {
        if (rootView == null) return;
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int signature = createChildSignature(rootView);
                        if (signature != lastChildSignature) {
                            lastChildSignature = signature;
                            schedulePatch();
                        }
                    }
                });
    }

    /** 여러 번 발생하는 레이아웃 이벤트를 1회의 보정 작업으로 합칩니다. */
    private void schedulePatch() {
        if (rootView == null || patchScheduled) return;
        patchScheduled = true;
        rootView.postDelayed(() -> {
            patchScheduled = false;
            applyResponsivePatch();
        }, 80L);
    }

    /** 현재 화면의 자식 수와 텍스트를 이용해 화면 전환 여부를 가볍게 판단합니다. */
    private int createChildSignature(View view) {
        int result = view.getClass().getName().hashCode();
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            result = 31 * result + (value == null ? 0 : value.toString().hashCode());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            result = 31 * result + group.getChildCount();
            for (int i = 0; i < group.getChildCount(); i++) {
                result = 31 * result + createChildSignature(group.getChildAt(i));
            }
        }
        return result;
    }

    /** 실기기 폭·글자 크기·방향에 맞춰 화면 요소를 보정합니다. */
    private void applyResponsivePatch() {
        if (rootView == null) return;

        float widthDp = widthDp();
        float fontScale = getResources().getConfiguration().fontScale;
        boolean compact = widthDp < 390f || fontScale > 1.12f;
        boolean veryCompact = widthDp < 360f || fontScale > 1.28f;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        List<View> all = new ArrayList<>();
        collectViews(rootView, all);

        patchHeader(all, compact);
        patchModeSwitcher(all, compact);
        patchNavigation(all, compact, veryCompact);
        patchWeekCards(all, compact, veryCompact);
        patchAgendaCards(all, compact);
        patchFloatingButton(all, compact, landscape);
        patchBottomNavigation(all, compact, veryCompact, landscape);
        patchScrollPadding(all, landscape);
    }

    /** 상단 제목과 설정 버튼을 작은 화면에서도 같은 기준선에 맞춥니다. */
    private void patchHeader(List<View> all, boolean compact) {
        for (View view : all) {
            if (!(view instanceof TextView)) continue;
            TextView text = (TextView) view;
            String value = safeText(text);

            if (("MyBrain AI".equals(value) || "홈".equals(value) || "할 일".equals(value)
                    || "일정".equals(value) || "메모".equals(value) || "달력".equals(value))
                    && text.getParent() instanceof LinearLayout
                    && ((LinearLayout) text.getParent()).getChildCount() == 2) {
                text.setTextSize(compact ? 23f : 25f);
                text.setGravity(Gravity.CENTER_VERTICAL);
            }

            if ("⚙".equals(value) && view instanceof Button) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = dp(compact ? 44 : 48);
                params.height = dp(compact ? 44 : 48);
                view.setLayoutParams(params);
                text.setTextSize(compact ? 17f : 18f);
                view.setMinimumWidth(0);
                view.setMinimumHeight(0);
            }
        }
    }

    /** 월간·주간·일간 선택 영역의 높이와 글자 크기를 보정합니다. */
    private void patchModeSwitcher(List<View> all, boolean compact) {
        for (View view : all) {
            if (!(view instanceof Button)) continue;
            Button button = (Button) view;
            String value = safeText(button);
            if (!("월간".equals(value) || "주간".equals(value) || "일간".equals(value))) continue;

            button.setTextSize(compact ? 15f : 16f);
            button.setMinimumHeight(0);
            button.setMinimumWidth(0);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (params != null) {
                params.height = dp(compact ? 46 : 50);
                button.setLayoutParams(params);
            }
        }
    }

    /** 이전·오늘·다음 버튼과 가운데 기간 문구가 잘리지 않도록 폭을 조정합니다. */
    private void patchNavigation(List<View> all, boolean compact, boolean veryCompact) {
        for (View view : all) {
            if (!(view instanceof TextView)) continue;
            TextView text = (TextView) view;
            String value = safeText(text);

            if ("‹".equals(value) || "›".equals(value)) {
                text.setTextSize(compact ? 22f : 24f);
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params != null) {
                    params.width = dp(veryCompact ? 44 : 50);
                    params.height = dp(46);
                    view.setLayoutParams(params);
                }
                view.setMinimumWidth(0);
                view.setMinimumHeight(0);
            } else if ("오늘".equals(value) && view instanceof Button) {
                text.setTextSize(compact ? 13f : 15f);
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params != null) {
                    params.width = dp(veryCompact ? 54 : 64);
                    params.height = dp(42);
                    view.setLayoutParams(params);
                }
                view.setMinimumWidth(0);
                view.setMinimumHeight(0);
            } else if (value.contains("~") && (value.contains("월") || value.contains("일"))) {
                text.setTextSize(veryCompact ? 15f : (compact ? 16f : 18f));
                text.setSingleLine(true);
                text.setGravity(Gravity.CENTER);
            }
        }
    }

    /** 주간 7개 날짜 카드를 기기 폭에 맞춰 압축하고 모든 카드의 높이를 통일합니다. */
    private void patchWeekCards(List<View> all, boolean compact, boolean veryCompact) {
        for (View view : all) {
            if (!(view instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) view;
            if (!isWeekCardRow(row)) continue;

            row.setPadding(0, dp(2), 0, dp(4));
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                if (!(child instanceof TextView)) continue;
                TextView chip = (TextView) child;
                chip.setTextSize(veryCompact ? 10f : (compact ? 11f : 12f));
                chip.setGravity(Gravity.CENTER);
                chip.setIncludeFontPadding(false);
                chip.setMinWidth(0);
                chip.setMinimumWidth(0);
                chip.setMinimumHeight(0);
                chip.setPadding(dp(1), dp(5), dp(1), dp(4));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, dp(veryCompact ? 78 : (compact ? 84 : 92)), 1f);
                params.setMargins(dp(1), dp(2), dp(1), dp(2));
                chip.setLayoutParams(params);
            }
        }
    }

    /** 날짜 카드 행인지 텍스트 줄 구조로 판별합니다. */
    private boolean isWeekCardRow(LinearLayout row) {
        if (row.getOrientation() != LinearLayout.HORIZONTAL || row.getChildCount() != 7) return false;
        int matches = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (!(child instanceof TextView)) return false;
            String value = safeText((TextView) child);
            if (value.contains("\n") && value.contains("/")) matches++;
        }
        return matches == 7;
    }

    /** 일정 카드의 최소 높이와 내용 간격을 줄여 더 많은 업무를 한 화면에 표시합니다. */
    private void patchAgendaCards(List<View> all, boolean compact) {
        for (View view : all) {
            if (!(view instanceof LinearLayout)) continue;
            LinearLayout group = (LinearLayout) view;
            if (!looksLikeAgendaCard(group)) continue;

            group.setPadding(dp(12), dp(compact ? 11 : 13), dp(12), dp(compact ? 11 : 13));
            group.setElevation(dp(1));
            group.setMinimumHeight(dp(compact ? 82 : 92));
        }
    }

    /** 왼쪽 색상선과 텍스트 영역이 있는 일정 카드인지 판별합니다. */
    private boolean looksLikeAgendaCard(LinearLayout group) {
        if (group.getOrientation() != LinearLayout.HORIZONTAL || group.getChildCount() < 2) return false;
        View first = group.getChildAt(0);
        View second = group.getChildAt(1);
        return !(first instanceof TextView) && second instanceof LinearLayout
                && first.getLayoutParams() != null && first.getLayoutParams().width <= dp(8);
    }

    /** 플로팅 추가 버튼을 콘텐츠와 하단 메뉴 사이에 안정적으로 배치합니다. */
    private void patchFloatingButton(List<View> all, boolean compact, boolean landscape) {
        for (View view : all) {
            if (!(view instanceof Button) || !"＋".equals(safeText((TextView) view))) continue;
            Button button = (Button) view;
            button.setTextSize(compact ? 27f : 30f);
            button.setMinimumWidth(0);
            button.setMinimumHeight(0);
            button.setContentDescription("새 항목 추가");

            ViewGroup.LayoutParams raw = view.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
                int size = dp(compact ? 60 : 64);
                params.width = size;
                params.height = size;
                params.gravity = Gravity.END | Gravity.BOTTOM;
                params.setMargins(0, 0, dp(18), dp(landscape ? 12 : 18));
                button.setLayoutParams(params);
            }
        }
    }

    /** 하단 메뉴를 64~68dp로 줄이되 터치 영역은 48dp 이상 유지합니다. */
    private void patchBottomNavigation(List<View> all, boolean compact,
                                       boolean veryCompact, boolean landscape) {
        for (View view : all) {
            if (!(view instanceof LinearLayout)) continue;
            LinearLayout bar = (LinearLayout) view;
            if (!isBottomNavigation(bar)) continue;

            bar.setPadding(dp(4), dp(3), dp(4), dp(3));
            bar.setElevation(dp(4));
            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                if (!(child instanceof TextView)) continue;
                TextView tab = (TextView) child;
                tab.setTextSize(veryCompact ? 10.5f : (compact ? 11f : 12f));
                tab.setGravity(Gravity.CENTER);
                tab.setIncludeFontPadding(false);
                tab.setPadding(0, dp(3), 0, dp(2));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, dp(landscape ? 58 : (compact ? 64 : 68)), 1f);
                params.setMargins(dp(1), 0, dp(1), 0);
                tab.setLayoutParams(params);
            }
        }
    }

    /** 홈·할 일·일정·메모·달력 5개가 들어 있는 하단 메뉴인지 판별합니다. */
    private boolean isBottomNavigation(LinearLayout bar) {
        if (bar.getOrientation() != LinearLayout.HORIZONTAL || bar.getChildCount() != 5) return false;
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < bar.getChildCount(); i++) {
            if (!(bar.getChildAt(i) instanceof TextView)) return false;
            values.append(safeText((TextView) bar.getChildAt(i))).append('|');
        }
        return values.toString().contains("홈") && values.toString().contains("할 일")
                && values.toString().contains("일정") && values.toString().contains("메모")
                && values.toString().contains("달력");
    }

    /** 스크롤 마지막 항목이 플로팅 버튼에 가리지 않도록 충분한 하단 공간을 확보합니다. */
    private void patchScrollPadding(List<View> all, boolean landscape) {
        for (View view : all) {
            if (!(view instanceof ScrollView) || ((ScrollView) view).getChildCount() == 0) continue;
            View child = ((ScrollView) view).getChildAt(0);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout content = (LinearLayout) child;
            int targetBottom = dp(landscape ? 78 : 104);
            if (content.getPaddingBottom() < targetBottom) {
                content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                        content.getPaddingRight(), targetBottom);
            }
        }
    }

    private void collectViews(View view, List<View> output) {
        output.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectViews(group.getChildAt(i), output);
        }
    }

    private String safeText(TextView view) {
        CharSequence value = view.getText();
        return value == null ? "" : value.toString().trim();
    }

    private float widthDp() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.widthPixels / metrics.density;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
