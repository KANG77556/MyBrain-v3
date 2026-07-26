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

/** 앱 화면 안에서 한국어 음성을 연속으로 받아 적는 엔진입니다. */
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
    private String lastFinal = "";
    private long lastFinalAt;

    public ContinuousSpeechRecognizer(Context context, Listener listener) {
        this.listener = listener;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
        recognizer.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.KOREA.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
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
        try { recognizer.stopListening(); } catch (Exception ignored) { }
        listener.onListeningStateChanged(false);
    }

    public void clearText() {
        committed.setLength(0);
        lastFinal = "";
        lastFinalAt = 0L;
        listener.onPartialText("", "");
    }

    public String getCommittedText() { return committed.toString().trim(); }

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
        String cleaned = normalize(value);
        if (cleaned.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (cleaned.equals(lastFinal) && now - lastFinalAt < 4000L) return;

        String existing = normalize(committed.toString());
        if (!existing.isEmpty()) {
            if (existing.equals(cleaned) || existing.endsWith(" " + cleaned)) return;
            if (cleaned.startsWith(existing) && cleaned.length() > existing.length()) {
                committed.setLength(0);
                committed.append(cleaned);
                lastFinal = cleaned;
                lastFinalAt = now;
                return;
            }
        }

        if (committed.length() > 0) committed.append(' ');
        committed.append(cleaned);
        lastFinal = cleaned;
        lastFinalAt = now;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
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
        scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 900L : 350L);
    }

    @Override
    public void onResults(Bundle results) {
        if (!requested || destroyed) return;
        appendCommitted(firstResult(results, SpeechRecognizer.RESULTS_RECOGNITION));
        listener.onFinalText(getCommittedText());
        scheduleRestart(250L);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (!requested || destroyed) return;
        String partial = normalize(firstResult(partialResults, SpeechRecognizer.RESULTS_RECOGNITION));
        if (partial.equals(lastFinal)) partial = "";
        listener.onPartialText(getCommittedText(), partial);
    }

    @Override public void onEvent(int eventType, Bundle params) { }
}
