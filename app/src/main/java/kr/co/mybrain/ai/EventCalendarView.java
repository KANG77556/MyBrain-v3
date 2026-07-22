package kr.co.mybrain.ai;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * 일정이 있는 날짜에 색상 점과 건수를 표시하는 전용 월간 달력입니다.
 * 외부 라이브러리를 사용하지 않아 APK 크기와 오류 가능성을 줄입니다.
 */
public class EventCalendarView extends LinearLayout {

    /** 날짜별 표시 정보입니다. */
    public static class EventMarker {
        public final int count;
        public final int color;

        public EventMarker(int count, int color) {
            this.count = count;
            this.color = color;
        }
    }

    /** 날짜별 일정 표시 정보를 제공하는 함수입니다. */
    public interface EventMarkerProvider {
        EventMarker getEventMarker(String date);
    }

    /** 날짜를 눌렀을 때 선택 결과를 전달합니다. */
    public interface OnDateSelectedListener {
        void onDateSelected(String date);
    }

    private final Calendar shownMonth = Calendar.getInstance();
    private final GridLayout dayGrid;
    private final TextView monthTitle;
    private EventMarkerProvider eventMarkerProvider;
    private OnDateSelectedListener dateSelectedListener;
    private String selectedDate;

    public EventCalendarView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(8), dp(4), dp(8));
        shownMonth.set(Calendar.DAY_OF_MONTH, 1);
        selectedDate = format(Calendar.getInstance());

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button previous = new Button(context);
        previous.setText("‹");
        previous.setTextSize(26);
        previous.setAllCaps(false);
        previous.setOnClickListener(v -> moveMonth(-1));
        header.addView(previous, new LayoutParams(dp(58), dp(52)));

        monthTitle = new TextView(context);
        monthTitle.setTextSize(20);
        monthTitle.setTextColor(Color.rgb(25, 25, 25));
        monthTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        header.addView(monthTitle, new LayoutParams(0, dp(52), 1f));

        Button next = new Button(context);
        next.setText("›");
        next.setTextSize(26);
        next.setAllCaps(false);
        next.setOnClickListener(v -> moveMonth(1));
        header.addView(next, new LayoutParams(dp(58), dp(52)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        GridLayout weekdayGrid = new GridLayout(context);
        weekdayGrid.setColumnCount(7);
        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < weekdays.length; i++) {
            TextView day = new TextView(context);
            day.setText(weekdays[i]);
            day.setTextSize(13);
            day.setGravity(Gravity.CENTER);
            day.setTextColor(i == 0 ? Color.rgb(210, 60, 60)
                    : (i == 6 ? Color.rgb(50, 90, 190) : Color.DKGRAY));
            weekdayGrid.addView(day, cellParams(dp(34)));
        }
        addView(weekdayGrid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        dayGrid = new GridLayout(context);
        dayGrid.setColumnCount(7);
        addView(dayGrid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        render();
    }

    public void setEventMarkerProvider(EventMarkerProvider provider) {
        eventMarkerProvider = provider;
        render();
    }

    public void setOnDateSelectedListener(OnDateSelectedListener listener) {
        dateSelectedListener = listener;
    }

    public void setSelectedDate(String date) {
        if (date == null || date.isEmpty()) return;
        selectedDate = date;
        try {
            Calendar value = Calendar.getInstance();
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            parser.setLenient(false);
            value.setTime(parser.parse(date));
            shownMonth.set(Calendar.YEAR, value.get(Calendar.YEAR));
            shownMonth.set(Calendar.MONTH, value.get(Calendar.MONTH));
            shownMonth.set(Calendar.DAY_OF_MONTH, 1);
        } catch (Exception ignored) {
            // 잘못된 날짜는 현재 표시 월을 유지합니다.
        }
        render();
    }

    public String getSelectedDate() {
        return selectedDate;
    }

    public void refreshEvents() {
        render();
    }

    private void moveMonth(int amount) {
        shownMonth.add(Calendar.MONTH, amount);
        shownMonth.set(Calendar.DAY_OF_MONTH, 1);
        render();
    }

    private void render() {
        if (monthTitle == null || dayGrid == null) return;
        monthTitle.setText(String.format(Locale.KOREA, "%d년 %d월",
                shownMonth.get(Calendar.YEAR), shownMonth.get(Calendar.MONTH) + 1));
        dayGrid.removeAllViews();

        Calendar first = (Calendar) shownMonth.clone();
        int emptyCount = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        for (int i = 0; i < emptyCount; i++) {
            dayGrid.addView(new View(getContext()), cellParams(dp(58)));
        }

        int maxDay = shownMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        String today = format(Calendar.getInstance());
        for (int day = 1; day <= maxDay; day++) {
            Calendar date = (Calendar) shownMonth.clone();
            date.set(Calendar.DAY_OF_MONTH, day);
            String value = format(date);
            EventMarker markerInfo = eventMarkerProvider == null
                    ? new EventMarker(0, Color.rgb(29, 99, 216))
                    : eventMarkerProvider.getEventMarker(value);
            if (markerInfo == null) markerInfo = new EventMarker(0, Color.rgb(29, 99, 216));

            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(2), dp(3), dp(2), dp(2));

            TextView number = new TextView(getContext());
            number.setText(String.valueOf(day));
            number.setTextSize(16);
            number.setGravity(Gravity.CENTER);
            number.setTextColor(Color.rgb(35, 35, 35));

            if (value.equals(selectedDate)) {
                number.setTextColor(Color.WHITE);
                number.setBackgroundColor(Color.rgb(29, 99, 216));
                number.setPadding(dp(10), dp(7), dp(10), dp(7));
            } else if (value.equals(today)) {
                number.setTextColor(Color.rgb(29, 99, 216));
                number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
            cell.addView(number, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            TextView marker = new TextView(getContext());
            marker.setGravity(Gravity.CENTER);
            marker.setTextSize(10);
            marker.setTextColor(markerInfo.color);
            marker.setText(markerInfo.count <= 0 ? "" : (markerInfo.count == 1 ? "●" : "● " + markerInfo.count));
            cell.addView(marker, new LayoutParams(LayoutParams.MATCH_PARENT, dp(18)));

            cell.setOnClickListener(v -> {
                selectedDate = value;
                render();
                if (dateSelectedListener != null) dateSelectedListener.onDateSelected(value);
            });
            dayGrid.addView(cell, cellParams(dp(58)));
        }
    }

    private GridLayout.LayoutParams cellParams(int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = height;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        return params;
    }

    private String format(Calendar calendar) {
        return String.format(Locale.KOREA, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
