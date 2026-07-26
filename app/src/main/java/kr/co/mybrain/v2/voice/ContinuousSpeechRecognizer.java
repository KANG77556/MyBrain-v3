package kr.co.mybrain.v2.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 앱 화면을 벗어나지 않고 음성을 연속으로 받아 적는 엔진입니다.
 * Android 음성인식 세션이 종료되면 사용자가 중지하기 전까지 자동으로 다시 시작합니다.
 */
public final class ContinuousSpeechRecognizer implements RecognitionListener {

    public interface Listener {
        void onListeningStateChanged(boolean listening);
        void onPartialText(String committedText, String partialText);
        void onFinalText(String committedText);
        void onRecoverableError(String message);
    }

    private final SpeechRecognizer recognizer;
    private final Intent recognizerIntent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final StringBuilder committed = new StringBuilder();

    private boolean requested;
    private boolean destroyed;
    private boolean restartScheduled;

    public ContinuousSpeechRecognizer(Context context, Listener listener) {
        this.listener = listener;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
        recognizer.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
    }

    public void start() {
        if (destroyed || requested) return;
        requested = true;
        listener.onListeningStateChanged(true);
        startSession();
    }

    public void stop() {
        requested = false;
        restartScheduled = false;
        mainHandler.removeCallbacksAndMessages(null);
        try {
            recognizer.stopListening();
        } catch (Exception ignored) { }
        listener.onListeningStateChanged(false);
    }

    public void clearText() {
        committed.setLength(0);
        listener.onPartialText("", "");
    }

    public String getCommittedText() {
        return committed.toString().trim();
    }

    public void destroy() {
        destroyed = true;
        requested = false;
        mainHandler.removeCallbacksAndMessages(null);
        recognizer.cancel();
        recognizer.destroy();
    }

    private void startSession() {
        if (!requested || destroyed) return;
        restartScheduled = false;
        try {
            recognizer.cancel();
            recognizer.startListening(recognizerIntent);
        } catch (Exception error) {
            listener.onRecoverableError("음성인식을 다시 준비하고 있습니다.");
            scheduleRestart(700L);
        }
    }

    private void scheduleRestart(long delayMillis) {
        if (!requested || destroyed || restartScheduled) return;
        restartScheduled = true;
        mainHandler.postDelayed(this::startSession, delayMillis);
    }

    private void appendCommitted(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) return;
        if (committed.length() > 0) committed.append(' ');
        committed.append(cleaned);
    }

    private String firstResult(Bundle results, String key) {
        if (results == null) return "";
        ArrayList<String> values = results.getStringArrayList(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }

    @Override
    public void onError(int error) {
        if (!requested || destroyed) return;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            requested = false;
            listener.onListeningStateChanged(false);
            listener.onRecoverableError("마이크 권한이 없어 음성인식을 중지했습니다.");
            return;
        }
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            listener.onRecoverableError("음성 연결을 다시 시도합니다.");
        }
        scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 900L : 300L);
    }

    @Override
    public void onResults(Bundle results) {
        String value = firstResult(results, SpeechRecognizer.RESULTS_RECOGNITION);
        appendCommitted(value);
        listener.onFinalText(getCommittedText());
        scheduleRestart(200L);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        listener.onPartialText(getCommittedText(), firstResult(partialResults, SpeechRecognizer.RESULTS_RECOGNITION));
    }

    @Override public void onEvent(int eventType, Bundle params) { }
}