package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 음성·OCR 분석 결과를 기존 WorkItemEditorActivity의 초안 저장 형식으로 전달합니다.
 * 기존 작성 초안이 있으면 사용자 확인 없이 덮어쓰지 않습니다.
 */
public final class QuickInputPrefill {
    private static final String DRAFT_PREFS = "mybrain_editor_draft";

    private QuickInputPrefill() { }

    public static void openEditor(Activity activity, AiAnalysisResult result) {
        SharedPreferences draft = activity.getSharedPreferences(DRAFT_PREFS, Activity.MODE_PRIVATE);
        if (draft.getBoolean("exists", false)) {
            new AlertDialog.Builder(activity)
                    .setTitle("작성 중인 초안이 있습니다")
                    .setMessage("기존 초안을 새 음성·OCR 결과로 교체할까요? 기존 초안은 복구할 수 없습니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("새 결과로 교체", (dialog, which) -> {
                        writeDraft(draft, result);
                        launchEditor(activity, result);
                    })
                    .show();
            return;
        }
        writeDraft(draft, result);
        launchEditor(activity, result);
    }

    private static void writeDraft(SharedPreferences draft, AiAnalysisResult result) {
        AiAnalysisResult safe = result == null ? new AiAnalysisResult() : result;
        draft.edit()
                .clear()
                .putBoolean("exists", true)
                .putString("type", emptyDefault(safe.type, "메모"))
                .putString("title", safe(safe.title))
                .putString("date", safe(safe.date))
                .putString("time", safe(safe.time))
                .putString("content", safe(safe.content))
                .putInt("reminder", -1)
                .putString("repeat", emptyDefault(safe.repeatType, "NONE"))
                .putString("repeat_end", "")
                .apply();
    }

    private static void launchEditor(Activity activity, AiAnalysisResult result) {
        Intent intent = new Intent(activity, WorkItemEditorActivity.class);
        intent.putExtra(WorkItemEditorActivity.EXTRA_INDEX, -1);
        intent.putExtra(WorkItemEditorActivity.EXTRA_TYPE, emptyDefault(result.type, "메모"));
        intent.putExtra(WorkItemEditorActivity.EXTRA_DATE, safe(result.date));
        activity.startActivity(intent);
        activity.finish();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyDefault(String value, String fallback) {
        String safe = safe(value).trim();
        return safe.isEmpty() ? fallback : safe;
    }
}
