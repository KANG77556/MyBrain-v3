package kr.co.mybrain.v2.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import kr.co.mybrain.v2.AdaptiveMainActivity;
import kr.co.mybrain.v2.CalendarActivity;
import kr.co.mybrain.v2.MainActivity;
import kr.co.mybrain.v2.WorkItemListActivity;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.SaveIntegrityPolicy;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;

/**
 * 검증된 홈 런처를 변경하지 않고 저장 버튼에 DB 재조회·중복 차단·날짜 이동을 추가합니다.
 * 보조 연결에 실패하면 기존 화면 동작을 그대로 유지합니다.
 */
public final class SaveIntegrityController {
    private static final Map<Activity, Session> SESSIONS = new HashMap<>();
    private static boolean installed;

    private SaveIntegrityController() {}

    public static synchronized void install(Application application) {
        if (installed) return;
        installed = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}

            @Override public void onActivityResumed(Activity activity) {
                if (!(activity instanceof AdaptiveMainActivity)) return;
                activity.getWindow().getDecorView().postDelayed(() -> attach(activity), 340L);
            }

            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

            @Override public void onActivityDestroyed(Activity activity) {
                SESSIONS.remove(activity);
            }
        });
    }

    private static void attach(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed() || SESSIONS.containsKey(activity)) return;
        Session session = new Session((AdaptiveMainActivity) activity);
        if (session.attach()) SESSIONS.put(activity, session);
    }

    private static final class Session {
        private final AdaptiveMainActivity activity;
        private Button saveButton;
        private TextView statusText;
        private EditText inputText;
        private WorkItemRepository repository;
        private boolean saving;

        Session(AdaptiveMainActivity activity) {
            this.activity = activity;
        }

        boolean attach() {
            try {
                saveButton = readMainField("saveButton", Button.class);
                statusText = readMainField("statusText", TextView.class);
                inputText = readMainField("inputText", EditText.class);
                repository = readMainField("repository", WorkItemRepository.class);
                if (saveButton == null || statusText == null || repository == null) return false;
                saveButton.setOnClickListener(view -> saveVerified());
                saveButton.setContentDescription("분석 결과를 확인하고 저장");
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void saveVerified() {
            if (saving || activity.isFinishing() || activity.isDestroyed()) return;
            try {
                if (readMainBoolean("analysisRunning")) {
                    Toast.makeText(activity, "정밀 분석이 끝난 후 저장하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                stopVoiceIfNeeded();
                ParsedWorkItem parsed = readMainField("parsedItem", ParsedWorkItem.class);
                if (parsed == null) {
                    invokeMainVoid("analyzeInput", new Class<?>[]{boolean.class}, true);
                    return;
                }
                if (SaveIntegrityPolicy.requiresDate(parsed.type, parsed.startAt)) {
                    showMissingDateDialog();
                    return;
                }

                WorkItemEntity entity = parsed.toEntity();
                saving = true;
                saveButton.setEnabled(false);
                saveButton.setAlpha(.65f);
                saveButton.setText("저장 확인 중…");
                statusText.setText("데이터베이스에 저장하고 다시 확인하는 중입니다…");

                repository.insertVerified(entity, result -> activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    saving = false;
                    if (result == null || (!result.success && !result.duplicate)) {
                        restoreSaveButton(true);
                        statusText.setText("저장 실패 · 입력 내용은 유지했습니다. 다시 시도하세요.");
                        showFailureDialog();
                        return;
                    }
                    if (result.duplicate) {
                        restoreSaveButton(true);
                        statusText.setText("중복 저장 차단 · 같은 항목이 이미 저장되어 있습니다.");
                        showResultDialog(result.item, true);
                        return;
                    }

                    try {
                        invokeMainVoid("clearInput", new Class<?>[0]);
                    } catch (Throwable ignored) {
                        // 화면 초기화 실패가 이미 확인된 DB 저장을 실패로 바꾸지는 않습니다.
                    }
                    restoreSaveButton(false);
                    statusText.setText("저장 확인 완료 · 데이터베이스에서 다시 확인했습니다.");
                    showResultDialog(result.item, false);
                }));
            } catch (Throwable error) {
                saving = false;
                restoreSaveButton(true);
                statusText.setText("저장 준비 중 문제가 발생했습니다. 입력 내용은 유지했습니다.");
                showFailureDialog();
            }
        }

        private void showMissingDateDialog() {
            new AlertDialog.Builder(activity)
                    .setTitle("일정 날짜가 없습니다")
                    .setMessage("날짜가 없는 일정은 캘린더에 표시되지 않습니다. 날짜와 시간을 확인한 뒤 저장하세요.")
                    .setPositiveButton("확인·수정", (dialog, which) -> {
                        try {
                            invokeMainVoid("openEditor", new Class<?>[0]);
                        } catch (Throwable ignored) {
                            Toast.makeText(activity, "결과 확인·수정 버튼을 눌러 날짜를 지정하세요.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("취소", null)
                    .show();
        }

        private void showFailureDialog() {
            new AlertDialog.Builder(activity)
                    .setTitle("저장하지 못했습니다")
                    .setMessage("입력 내용은 그대로 남아 있습니다. 저장 공간을 확인한 뒤 다시 시도하세요.")
                    .setPositiveButton("다시 시도", (dialog, which) -> saveVerified())
                    .setNegativeButton("닫기", null)
                    .show();
        }

        private void showResultDialog(WorkItemEntity item, boolean duplicate) {
            if (item == null) return;
            String message = duplicate
                    ? "같은 항목이 이미 저장되어 있어 새 항목을 만들지 않았습니다."
                    : "‘" + safeTitle(item.title) + "’을 저장하고 데이터베이스에서 다시 확인했습니다.";
            if (item.startAt != null) message += "\n\n" + formatDateTime(item.startAt);

            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle(duplicate ? "이미 저장된 항목" : "저장 완료")
                    .setMessage(message)
                    .setNegativeButton(duplicate ? "닫기" : "계속 입력", (dialog, which) -> {
                        if (!duplicate) focusInput();
                    });

            if (SaveIntegrityPolicy.opensCalendar(item.type, item.startAt)) {
                builder.setPositiveButton("저장한 날짜", (dialog, which) ->
                                activity.startActivity(CalendarActivity.focusIntent(activity, item)))
                        .setNeutralButton("저장 목록", (dialog, which) ->
                                activity.startActivity(new Intent(activity, WorkItemListActivity.class)));
            } else {
                builder.setPositiveButton("저장 목록", (dialog, which) ->
                                activity.startActivity(new Intent(activity, WorkItemListActivity.class)))
                        .setNeutralButton("오늘 보기", (dialog, which) ->
                                activity.startActivity(new Intent(activity, CalendarActivity.class)));
            }
            builder.show();
        }

        private void focusInput() {
            if (inputText == null) return;
            inputText.requestFocus();
            InputMethodManager manager = (InputMethodManager)
                    activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT);
        }

        private void restoreSaveButton(boolean enabled) {
            if (saveButton == null) return;
            saveButton.setText("저장");
            saveButton.setEnabled(enabled);
            saveButton.setAlpha(enabled ? 1f : .45f);
        }

        private void stopVoiceIfNeeded() {
            try {
                if (!readMainBoolean("listening")) return;
                Object recognizer = readRawMainField("speechRecognizer");
                if (recognizer == null) return;
                Method stop = recognizer.getClass().getMethod("stop");
                stop.invoke(recognizer);
            } catch (Throwable ignored) {
                // 음성 종료 실패가 저장을 막지는 않습니다.
            }
        }

        private String safeTitle(String value) {
            String title = SaveIntegrityPolicy.normalizedTitle(value);
            return title.length() <= 42 ? title : title.substring(0, 42) + "…";
        }

        private String formatDateTime(long millis) {
            return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREA));
        }

        private boolean readMainBoolean(String name) throws Exception {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(activity);
        }

        private Object readRawMainField(String name) throws Exception {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        }

        private <T> T readMainField(String name, Class<T> type) throws Exception {
            Object value = readRawMainField(name);
            return type.isInstance(value) ? type.cast(value) : null;
        }

        private void invokeMainVoid(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
            Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(activity, args);
        }
    }
}
