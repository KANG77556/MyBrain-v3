package kr.co.mybrain.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 메시지를 기기 내부에서 분석해 일정·할 일·메모로 저장하는 단일 화면 앱입니다.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "mybrain_data";
    private static final String KEY_ITEMS = "items";
    private final List<Item> items = new ArrayList<>();
    private LinearLayout listArea;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadItems();
        buildScreen();
        receiveSharedText(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        receiveSharedText(intent);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView title = new TextView(this);
        title.setText("MyBrain AI");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(20, 45, 80));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, fullWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("메시지를 일정·할 일·메모로 자동 정리합니다");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle, fullWrap());

        summary = new TextView(this);
        summary.setTextSize(17);
        summary.setTextColor(Color.rgb(29, 99, 216));
        summary.setPadding(dp(14), dp(14), dp(14), dp(14));
        summary.setBackgroundColor(Color.WHITE);
        root.addView(summary, fullWrap());

        Button addButton = new Button(this);
        addButton.setText("메시지 붙여넣기 및 분석");
        addButton.setAllCaps(false);
        addButton.setOnClickListener(v -> showInputDialog("") );
        LinearLayout.LayoutParams buttonParams = fullWrap();
        buttonParams.setMargins(0, dp(14), 0, dp(10));
        root.addView(addButton, buttonParams);

        TextView listTitle = new TextView(this);
        listTitle.setText("정리된 항목");
        listTitle.setTextSize(21);
        listTitle.setTextColor(Color.rgb(20, 45, 80));
        listTitle.setPadding(0, dp(8), 0, dp(8));
        root.addView(listTitle, fullWrap());

        ScrollView scroll = new ScrollView(this);
        listArea = new LinearLayout(this);
        listArea.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listArea, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        setContentView(root);
        refreshList();
    }

    private void showInputDialog(String preset) {
        EditText input = new EditText(this);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setHint("예: 내일 오후 2시 교무실에서 회의가 있습니다.\n금요일까지 자율점검표를 제출해 주세요.");
        input.setText(preset);
        input.setSelection(input.getText().length());

        int pad = dp(18);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("메시지 분석")
                .setView(holder)
                .setNegativeButton("취소", null)
                .setPositiveButton("분석", (dialog, which) -> analyzeAndConfirm(input.getText().toString()))
                .show();
    }

    private void analyzeAndConfirm(String raw) {
        String text = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            Toast.makeText(this, "분석할 메시지를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Analysis analysis = analyze(text);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        EditText type = field("분류: 일정 / 할 일 / 메모", analysis.type);
        EditText itemTitle = field("제목", analysis.title);
        EditText date = field("날짜", analysis.date);
        EditText time = field("시간", analysis.time);
        form.addView(type);
        form.addView(itemTitle);
        form.addView(date);
        form.addView(time);

        new AlertDialog.Builder(this)
                .setTitle("분석 결과 확인")
                .setMessage("내용을 확인하고 필요하면 수정하세요.")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    Item item = new Item();
                    item.type = normalizeType(type.getText().toString());
                    item.title = itemTitle.getText().toString().trim();
                    item.date = date.getText().toString().trim();
                    item.time = time.getText().toString().trim();
                    item.original = text;
                    items.add(0, item);
                    saveItems();
                    refreshList();
                })
                .show();
    }

    private EditText field(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setSingleLine(true);
        return editText;
    }

    private Analysis analyze(String text) {
        Analysis result = new Analysis();
        result.date = extractDate(text);
        result.time = extractTime(text);

        String[] taskWords = {"제출", "작성", "확인", "준비", "보내", "전달", "신청", "완료", "처리", "연락"};
        String[] scheduleWords = {"회의", "연수", "수업", "상담", "면담", "행사", "출장", "약속", "모임", "방문"};

        boolean task = containsAny(text, taskWords) || text.contains("까지") || text.contains("마감");
        boolean schedule = containsAny(text, scheduleWords) || (!result.date.isEmpty() && !result.time.isEmpty());
        result.type = task ? "할 일" : (schedule ? "일정" : "메모");

        String title = text.replaceAll("[.!?]", "").trim();
        if (title.length() > 36) title = title.substring(0, 36) + "…";
        result.title = title;
        return result;
    }

    private String extractDate(String text) {
        Calendar cal = Calendar.getInstance();
        if (text.contains("모레")) cal.add(Calendar.DAY_OF_MONTH, 2);
        else if (text.contains("내일")) cal.add(Calendar.DAY_OF_MONTH, 1);
        else if (text.contains("오늘")) return formatDate(cal.getTime());
        else {
            Matcher full = Pattern.compile("(20\\d{2})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
            if (full.find()) return String.format(Locale.KOREA, "%s-%02d-%02d", full.group(1), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
            Matcher shortDate = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
            if (shortDate.find()) {
                int month = Integer.parseInt(shortDate.group(1));
                int day = Integer.parseInt(shortDate.group(2));
                int year = cal.get(Calendar.YEAR);
                Calendar candidate = Calendar.getInstance();
                candidate.set(year, month - 1, day, 0, 0, 0);
                if (candidate.before(cal)) year++;
                return String.format(Locale.KOREA, "%04d-%02d-%02d", year, month, day);
            }
            return "";
        }
        return formatDate(cal.getTime());
    }

    private String extractTime(String text) {
        Matcher matcher = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?").matcher(text);
        if (!matcher.find()) return "";
        int hour = Integer.parseInt(matcher.group(2));
        int minute = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        if ("오후".equals(matcher.group(1)) && hour < 12) hour += 12;
        if ("오전".equals(matcher.group(1)) && hour == 12) hour = 0;
        return String.format(Locale.KOREA, "%02d:%02d", hour, minute);
    }

    private boolean containsAny(String text, String[] words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private String normalizeType(String value) {
        String text = value == null ? "" : value.trim();
        if (text.contains("할")) return "할 일";
        if (text.contains("일정")) return "일정";
        return "메모";
    }

    private void refreshList() {
        if (listArea == null) return;
        listArea.removeAllViews();
        int schedules = 0, tasks = 0, memos = 0;
        for (Item item : items) {
            if ("일정".equals(item.type)) schedules++;
            else if ("할 일".equals(item.type)) tasks++;
            else memos++;
        }
        summary.setText("일정 " + schedules + "개   ·   할 일 " + tasks + "개   ·   메모 " + memos + "개");

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("아직 정리된 항목이 없습니다.\n위 버튼을 눌러 메시지를 입력하세요.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(16);
            empty.setPadding(dp(12), dp(40), dp(12), dp(40));
            listArea.addView(empty, fullWrap());
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            Item item = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(Color.WHITE);

            TextView header = new TextView(this);
            header.setText("[" + item.type + "] " + item.title);
            header.setTextSize(17);
            header.setTextColor(Color.rgb(20, 45, 80));
            card.addView(header, fullWrap());

            String info = joinInfo(item.date, item.time);
            if (!info.isEmpty()) {
                TextView meta = new TextView(this);
                meta.setText(info);
                meta.setTextColor(Color.rgb(29, 99, 216));
                meta.setPadding(0, dp(5), 0, dp(5));
                card.addView(meta, fullWrap());
            }

            TextView original = new TextView(this);
            original.setText(item.original);
            original.setTextColor(Color.DKGRAY);
            card.addView(original, fullWrap());

            Button delete = new Button(this);
            delete.setText("삭제");
            delete.setAllCaps(false);
            delete.setOnClickListener(v -> {
                items.remove(index);
                saveItems();
                refreshList();
            });
            card.addView(delete, fullWrap());

            LinearLayout.LayoutParams params = fullWrap();
            params.setMargins(0, 0, 0, dp(10));
            listArea.addView(card, params);
        }
    }

    private String joinInfo(String date, String time) {
        if (date.isEmpty()) return time;
        if (time.isEmpty()) return date;
        return date + " · " + time;
    }

    private void receiveSharedText(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.trim().isEmpty()) showInputDialog(shared);
        }
    }

    private void saveItems() {
        StringBuilder out = new StringBuilder();
        for (Item item : items) {
            if (out.length() > 0) out.append("\n");
            out.append(escape(item.type)).append("\t")
                    .append(escape(item.title)).append("\t")
                    .append(escape(item.date)).append("\t")
                    .append(escape(item.time)).append("\t")
                    .append(escape(item.original));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_ITEMS, out.toString()).apply();
    }

    private void loadItems() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_ITEMS, "");
        if (raw == null || raw.isEmpty()) return;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 5) continue;
            Item item = new Item();
            item.type = unescape(parts[0]);
            item.title = unescape(parts[1]);
            item.date = unescape(parts[2]);
            item.time = unescape(parts[3]);
            item.original = unescape(parts[4]);
            items.add(item);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A");
    }

    private String unescape(String value) {
        return value.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%");
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(date);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static class Analysis {
        String type = "메모";
        String title = "";
        String date = "";
        String time = "";
    }

    private static class Item {
        String type = "메모";
        String title = "";
        String date = "";
        String time = "";
        String original = "";
    }
}
