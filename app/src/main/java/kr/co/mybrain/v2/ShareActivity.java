package kr.co.mybrain.v2;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.share.SharedDocumentTextExtractor;
import kr.co.mybrain.v2.ui.WorkItemEditorDialog;

/** 다른 앱에서 공유된 텍스트·URL·이미지·PDF를 MyBrain 항목으로 변환합니다. */
public final class ShareActivity extends AppCompatActivity {
    private WorkItemRepository repository;
    private ParsedWorkItem parsedItem;
    private String sharedSource;
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = WorkItemRepository.getInstance(this);
        handleShare(getIntent());
    }

    private void handleShare(Intent intent) {
        if (intent == null) { finish(); return; }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            Uri stream = readSingleStream(intent);
            if (text != null && !text.toString().trim().isEmpty()) {
                openSharedText(text.toString().trim());
            } else if (stream != null) {
                openSharedFiles(singleton(stream));
            } else {
                Toast.makeText(this, "공유된 내용을 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = readMultipleStreams(intent);
            if (streams.isEmpty()) finish(); else openSharedFiles(streams);
        } else {
            finish();
        }
    }

    private Uri readSingleStream(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        Parcelable value = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        return value instanceof Uri ? (Uri) value : null;
    }

    private ArrayList<Uri> readMultipleStreams(Intent intent) {
        ArrayList<Uri> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ArrayList<Uri> values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            if (values != null) result.addAll(values);
            return result;
        }
        ArrayList<? extends Parcelable> values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (values != null) {
            for (Parcelable value : values) if (value instanceof Uri) result.add((Uri) value);
        }
        return result;
    }

    private void openSharedText(String text) {
        sharedSource = text;
        parsedItem = KoreanNaturalLanguageParser.parse(text, ZoneId.systemDefault());
        parsedItem.sourceText = text;
        checkDuplicateThenEdit();
    }

    private void openSharedFiles(ArrayList<Uri> streams) {
        showProgress("이미지·PDF에서 글자를 읽고 있습니다.");
        SharedDocumentTextExtractor extractor = new SharedDocumentTextExtractor(this);
        extractor.extract(streams, (extractedText, errors) -> runOnUiThread(() -> {
            hideProgress();
            String attachmentInfo = attachmentInfo(streams);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                parsedItem = new ParsedWorkItem();
                parsedItem.type = WorkItemEntity.TYPE_MEMO;
                parsedItem.title = streams.size() == 1 ? "공유 파일" : "공유 파일 " + streams.size() + "개";
                parsedItem.reminderExplicitlyDisabled = true;
                parsedItem.confidence = errors.isEmpty() ? 0.8f : 0.5f;
                sharedSource = attachmentInfo + errorInfo(errors);
            } else {
                parsedItem = KoreanNaturalLanguageParser.parse(extractedText, ZoneId.systemDefault());
                sharedSource = extractedText + "\n\n" + attachmentInfo + errorInfo(errors);
            }
            parsedItem.sourceText = sharedSource;
            if (!errors.isEmpty()) {
                Toast.makeText(this, "일부 파일은 글자를 읽지 못해 첨부 정보만 보존했습니다.", Toast.LENGTH_LONG).show();
            }
            checkDuplicateThenEdit();
        }));
    }

    private String attachmentInfo(List<Uri> streams) {
        StringBuilder source = new StringBuilder("[원본 첨부]");
        for (Uri uri : streams) source.append('\n').append(uri);
        return source.toString();
    }

    private String errorInfo(List<String> errors) {
        if (errors == null || errors.isEmpty()) return "";
        StringBuilder value = new StringBuilder("\n\n[인식 오류]");
        for (String error : errors) value.append('\n').append(error);
        return value.toString();
    }

    private ArrayList<Uri> singleton(Uri uri) {
        ArrayList<Uri> result = new ArrayList<>();
        result.add(uri);
        return result;
    }

    private void checkDuplicateThenEdit() {
        repository.findDuplicate(sharedSource, duplicate -> runOnUiThread(() -> {
            if (duplicate == null) openEditor();
            else new AlertDialog.Builder(this)
                    .setTitle("이미 저장된 내용")
                    .setMessage("같은 내용이 이미 MyBrain에 있습니다. 그래도 새로 저장하시겠습니까?")
                    .setNegativeButton("취소", (dialog, which) -> finish())
                    .setPositiveButton("계속", (dialog, which) -> openEditor())
                    .setOnCancelListener(dialog -> finish())
                    .show();
        }));
    }

    private void openEditor() {
        WorkItemEditorDialog dialog = new WorkItemEditorDialog(parsedItem, item ->
                repository.insert(item.toEntity(), id -> runOnUiThread(() -> {
                    Toast.makeText(this, "공유 내용을 저장했습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                })));
        dialog.show(getSupportFragmentManager(), "shared_item_editor");
    }

    private void showProgress(String message) {
        progressDialog = new AlertDialog.Builder(this)
                .setTitle("문서 분석")
                .setMessage(message)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
    }

    @Override
    protected void onDestroy() {
        hideProgress();
        super.onDestroy();
    }
}
