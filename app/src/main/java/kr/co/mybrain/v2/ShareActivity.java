package kr.co.mybrain.v2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.time.ZoneId;
import java.util.ArrayList;

import kr.co.mybrain.v2.assistant.KoreanNaturalLanguageParser;
import kr.co.mybrain.v2.assistant.ParsedWorkItem;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.data.WorkItemRepository;
import kr.co.mybrain.v2.ui.WorkItemEditorDialog;

/** 다른 앱에서 공유된 텍스트·URL·파일을 MyBrain 항목으로 변환합니다. */
public final class ShareActivity extends AppCompatActivity {
    private WorkItemRepository repository;
    private ParsedWorkItem parsedItem;
    private String sharedSource;

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
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (text != null && !text.toString().trim().isEmpty()) {
                openSharedText(text.toString().trim());
            } else if (stream != null) {
                openSharedFiles(singleton(stream));
            } else {
                Toast.makeText(this, "공유된 내용을 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams == null || streams.isEmpty()) finish(); else openSharedFiles(streams);
        } else finish();
    }

    private void openSharedText(String text) {
        sharedSource = text;
        parsedItem = KoreanNaturalLanguageParser.parse(text, ZoneId.systemDefault());
        parsedItem.sourceText = text;
        checkDuplicateThenEdit();
    }

    private void openSharedFiles(ArrayList<Uri> streams) {
        StringBuilder source = new StringBuilder("공유 파일");
        for (Uri uri : streams) source.append('\n').append(uri);
        sharedSource = source.toString();
        parsedItem = new ParsedWorkItem();
        parsedItem.type = WorkItemEntity.TYPE_MEMO;
        parsedItem.title = streams.size() == 1 ? "공유 파일" : "공유 파일 " + streams.size() + "개";
        parsedItem.sourceText = sharedSource;
        parsedItem.confidence = 1f;
        parsedItem.reminderExplicitlyDisabled = true;
        checkDuplicateThenEdit();
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
        WorkItemEditorDialog dialog = new WorkItemEditorDialog(parsedItem, item -> {
            repository.insert(item.toEntity(), id -> runOnUiThread(() -> {
                Toast.makeText(this, "공유 내용을 저장했습니다.", Toast.LENGTH_SHORT).show();
                finish();
            }));
        });
        dialog.show(getSupportFragmentManager(), "shared_item_editor");
    }
}
