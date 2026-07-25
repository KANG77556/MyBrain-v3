package kr.co.mybrain.ai;

import android.app.Activity;
import android.graphics.Rect;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.util.List;

/**
 * 앱 전체 화면에서 입력칸 밖을 누르면 소프트 키보드를 닫는 Window.Callback 래퍼입니다.
 *
 * 입력칸 내부 터치는 그대로 유지하고, 버튼·빈 공간·카드 등 입력칸 외부의 터치만
 * 감지하여 현재 포커스를 해제합니다. 기존 Activity의 Window.Callback 동작은
 * 모두 원래 콜백으로 전달합니다.
 */
final class KeyboardDismissWindowCallback implements Window.Callback {
    private final Activity activity;
    private final Window.Callback delegate;

    KeyboardDismissWindowCallback(Activity activity, Window.Callback delegate) {
        this.activity = activity;
        this.delegate = delegate;
    }

    /** 이미 공통 키보드 닫기 콜백이 설치됐는지 확인합니다. */
    static boolean isInstalled(Window.Callback callback) {
        return callback instanceof KeyboardDismissWindowCallback;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            hideKeyboardWhenTouchingOutside(event);
        }
        return delegate.dispatchTouchEvent(event);
    }

    /** 현재 EditText 바깥을 누른 경우에만 포커스와 키보드를 함께 닫습니다. */
    private void hideKeyboardWhenTouchingOutside(MotionEvent event) {
        View focused = activity.getCurrentFocus();
        if (!(focused instanceof EditText)) return;

        Rect inputArea = new Rect();
        boolean visible = focused.getGlobalVisibleRect(inputArea);
        if (visible && inputArea.contains((int) event.getRawX(), (int) event.getRawY())) return;

        focused.clearFocus();
        InputMethodManager manager = (InputMethodManager)
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (manager != null && focused.getWindowToken() != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) { return delegate.dispatchKeyEvent(event); }
    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) { return delegate.dispatchKeyShortcutEvent(event); }
    @Override public boolean dispatchTrackballEvent(MotionEvent event) { return delegate.dispatchTrackballEvent(event); }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return delegate.dispatchGenericMotionEvent(event); }
    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return delegate.dispatchPopulateAccessibilityEvent(event);
    }
    @Override public View onCreatePanelView(int featureId) { return delegate.onCreatePanelView(featureId); }
    @Override public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return delegate.onCreatePanelMenu(featureId, menu);
    }
    @Override public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return delegate.onPreparePanel(featureId, view, menu);
    }
    @Override public boolean onMenuOpened(int featureId, Menu menu) {
        return delegate.onMenuOpened(featureId, menu);
    }
    @Override public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return delegate.onMenuItemSelected(featureId, item);
    }
    @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        delegate.onWindowAttributesChanged(attrs);
    }
    @Override public void onContentChanged() { delegate.onContentChanged(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { delegate.onWindowFocusChanged(hasFocus); }
    @Override public void onAttachedToWindow() { delegate.onAttachedToWindow(); }
    @Override public void onDetachedFromWindow() { delegate.onDetachedFromWindow(); }
    @Override public void onPanelClosed(int featureId, Menu menu) { delegate.onPanelClosed(featureId, menu); }
    @Override public boolean onSearchRequested() { return delegate.onSearchRequested(); }
    @Override public boolean onSearchRequested(SearchEvent searchEvent) {
        return delegate.onSearchRequested(searchEvent);
    }
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return delegate.onWindowStartingActionMode(callback);
    }
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return delegate.onWindowStartingActionMode(callback, type);
    }
    @Override public void onActionModeStarted(ActionMode mode) { delegate.onActionModeStarted(mode); }
    @Override public void onActionModeFinished(ActionMode mode) { delegate.onActionModeFinished(mode); }
    @Override public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        delegate.onProvideKeyboardShortcuts(data, menu, deviceId);
    }
    @Override public void onPointerCaptureChanged(boolean hasCapture) {
        delegate.onPointerCaptureChanged(hasCapture);
    }
}
