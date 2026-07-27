package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.MainActivity;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/** 검증 저장 직전에 기존 일정과 겹치는 시간을 확인합니다. */
public final class ScheduleConflictController {
    private static boolean installed;

    private ScheduleConflictController() {}

    public static synchronized void install(Application application) {
        if (installed) return;
        installed = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof AdaptiveMainActivity) {
                    activity.getWindow().getDecorView().postDelayed(() -> attach((AdaptiveMainActivity) activity), 620L);
                }
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private static void attach(AdaptiveMainActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Button saveButton = readField(activity, "saveButton", Button.class);
            if (saveButton == null || Boolean.TRUE.equals(saveButton.getTag())) return;
            saveButton.setTag(Boolean.TRUE);
            saveButton.setOnClickListener(v -> checkThenSave(activity));
            saveButton.setContentDescription("일정 충돌을 확인하고 저장");
        } catch (Throwable ignored) {
            // 연결 실패 시 SaveIntegrityController가 설정한 기존 저장 동작을 유지합니다.
        }
    }

    private static void checkThenSave(AdaptiveMainActivity activity) {
        try {
            ParsedWorkItem parsed = readField(activity, "parsedItem", ParsedWorkItem.class);
            if (parsed == null || !WorkItemEntity.TYPE_SCHEDULE.equals(parsed.type) || parsed.startAt == null) {
                invokeVerifiedSave(activity);
                return;
            }
            WorkItemEntity candidate = parsed.toEntity();
            WorkItemRepository repository = readField(activity, "repository", WorkItemRepository.class);
            if (repository == null) {
                invokeVerifiedSave(activity);
                return;
            }
            long from = ScheduleConflictPolicy.queryFrom(candidate);
            long to = ScheduleConflictPolicy.queryTo(candidate);
            repository.getBetween(from, to, items -> activity.runOnUiThread(() -> {
                List<WorkItemEntity> conflicts = ScheduleConflictPolicy.conflicts(candidate, items);
                if (conflicts.isEmpty()) invokeVerifiedSave(activity);
                else showConflictDialog(activity, candidate, conflicts);
            }));
        } catch (Throwable error) {
            invokeVerifiedSave(activity);
        }
    }

    private static void showConflictDialog(AdaptiveMainActivity activity, WorkItemEntity candidate,
                                           List<WorkItemEntity> conflicts) {
        WorkItemEntity first = conflicts.get(0);
        StringBuilder message = new StringBuilder();
        message.append(formatRange(candidate)).append("에 등록하려는 일정이\n기존 일정과 겹칩니다.\n\n");
        int limit = Math.min(3, conflicts.size());
        for (int i = 0; i < limit; i++) {
            WorkItemEntity item = conflicts.get(i);
            message.append("• ").append(safeTitle(item.title)).append("  ").append(formatRange(item)).append("\n");
        }
        if (conflicts.size() > limit) message.append("외 ").append(conflicts.size() - limit).append("개");

        new AlertDialog.Builder(activity)
                .setTitle("일정 시간이 겹칩니다")
                .setMessage(message.toString().trim())
                .setPositiveButton("시간 변경", (dialog, which) -> openEditor(activity))
                .setNeutralButton("그대로 저장", (dialog, which) -> invokeVerifiedSave(activity))
                .setNegativeButton("취소", null)
                .show();
    }

    private static void openEditor(AdaptiveMainActivity activity) {
        try {
            Method method = MainActivity.class.getDeclaredMethod("openEditor");
            method.setAccessible(true);
            method.invoke(activity);
        } catch (Throwable ignored) {
            Toast.makeText(activity, "결과 확인·수정 버튼에서 시간을 변경하세요.", Toast.LENGTH_LONG).show();
        }
    }

    private static void invokeVerifiedSave(AdaptiveMainActivity activity) {
        try {
            Field sessionsField = SaveIntegrityController.class.getDeclaredField("SESSIONS");
            sessionsField.setAccessible(true);
            Object raw = sessionsField.get(null);
            if (raw instanceof Map) {
                Object session = ((Map<?, ?>) raw).get(activity);
                if (session != null) {
                    Method save = session.getClass().getDeclaredMethod("saveVerified");
                    save.setAccessible(true);
                    save.invoke(session);
                    return;
                }
            }
            Method fallback = MainActivity.class.getDeclaredMethod("saveParsedItem");
            fallback.setAccessible(true);
            fallback.invoke(activity);
        } catch (Throwable ignored) {
            Toast.makeText(activity, "저장을 준비하지 못했습니다. 다시 눌러 주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatRange(WorkItemEntity item) {
        if (item == null || item.startAt == null) return "시간 미지정";
        DateTimeFormatter full = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA);
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA);
        String start = Instant.ofEpochMilli(item.startAt).atZone(ZoneId.systemDefault()).format(full);
        long endAt = ScheduleConflictPolicy.endAt(item);
        String end = Instant.ofEpochMilli(endAt).atZone(ZoneId.systemDefault()).format(time);
        return start + "~" + end;
    }

    private static String safeTitle(String title) {
        String value = title == null || title.trim().isEmpty() ? "제목 없는 일정" : title.trim();
        return value.length() <= 24 ? value : value.substring(0, 24) + "…";
    }

    private static <T> T readField(Object target, String name, Class<T> type) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
