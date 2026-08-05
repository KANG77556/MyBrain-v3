from pathlib import Path
import re

main = Path('app/src/main/java/com/brainassistant/app/MainActivity.kt')
s = main.read_text()

# 필요한 UI 및 시스템 바 처리 import
s = s.replace('import android.graphics.Color\n', 'import android.graphics.Color\nimport android.graphics.Typeface\nimport android.graphics.drawable.GradientDrawable\n')
if 'import androidx.core.view.ViewCompat' not in s:
    s = s.replace('import androidx.core.content.ContextCompat\n', 'import androidx.core.content.ContextCompat\nimport androidx.core.view.ViewCompat\nimport androidx.core.view.WindowInsetsCompat\n')

# setContentView에 하단 시스템 내비게이션 안전 영역 적용
match = re.search(r'setContentView\((\w+)\)', s)
if not match:
    raise SystemExit('setContentView target missing')
root_name = match.group(1)
needle = match.group(0)
replacement = needle + f'''\n        ViewCompat.setOnApplyWindowInsetsListener({root_name}) {{ view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navigation.bottom)
            insets
        }}
        window.navigationBarColor = Color.parseColor("#0D0F17")'''
s = s.replace(needle, replacement, 1)

# Kotlin 함수 범위를 찾아 본문 교체
def replace_function_body(source: str, signature: str, new_body: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise SystemExit(f'{signature} missing')
    brace = source.find('{', start)
    depth = 0
    end = None
    for i in range(brace, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise SystemExit(f'{signature} closing brace missing')
    return source[:brace+1] + '\n' + new_body + '\n    ' + source[end:]

# 기존 showHome에서 콘텐츠 호스트 변수명 자동 탐색
home_start = s.find('private fun showHome()')
if home_start < 0:
    raise SystemExit('showHome missing')
home_brace = s.find('{', home_start)
host_match = re.search(r'(\w+)\.removeAllViews\(\)', s[home_brace:home_brace+1800])
if not host_match:
    raise SystemExit('home content host missing')
host = host_match.group(1)

home_body = f'''        {host}.removeAllViews()
        val items = loadItems()
        val schedules = items.filter {{ it.type == "일정" && !it.completed }}
        val tasks = items.filter {{ it.type == "할 일" && !it.completed }}
        val memos = items.filter {{ it.type == "메모" }}

        fun rounded(fill: String, radius: Float = 22f, stroke: String? = null): GradientDrawable =
            GradientDrawable().apply {{
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(radius.toInt()).toFloat()
                setColor(Color.parseColor(fill))
                if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
            }}

        fun label(value: String, size: Float, colorValue: String = "#F7F7FB", bold: Boolean = false) =
            TextView(this).apply {{
                text = value
                textSize = size
                setTextColor(Color.parseColor(colorValue))
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }}

        fun quickCard(icon: String, title: String, description: String, action: () -> Unit): LinearLayout =
            LinearLayout(this).apply {{
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = rounded("#1A1E2A", 20f, "#2A3040")
                isClickable = true
                isFocusable = true
                setOnClickListener {{ action() }}
                addView(label(icon, 25f, "#8D97FF"), LinearLayout.LayoutParams(-1, dp(34)))
                addView(label(title, 16f, "#FFFFFF", true))
                addView(label(description, 12f, "#A9AFBD").apply {{ setPadding(0, dp(4), 0, 0) }})
            }}

        val page = LinearLayout(this).apply {{
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(Color.parseColor("#0D0F17"))
        }}

        page.addView(label("안녕하세요! 👋", 16f, "#7E89FF", true))
        page.addView(label("오늘도 차분하게\n정리해 볼까요?", 31f, "#FFFFFF", true).apply {{ setPadding(0, dp(5), 0, dp(18)) }})

        val briefing = LinearLayout(this).apply {{
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded("#4652D8", 24f)
        }}
        briefing.addView(label("오늘의 브리핑", 19f, "#FFFFFF", true))
        val stats = LinearLayout(this).apply {{ orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }}
        listOf("📅\n${{schedules.size}}\n일정", "✓\n${{tasks.size}}\n할 일", "📝\n${{memos.size}}\n메모").forEach {{ textValue ->
            stats.addView(label(textValue, 15f, "#FFFFFF", true).apply {{ gravity = Gravity.CENTER }}, LinearLayout.LayoutParams(0, dp(92), 1f))
        }}
        briefing.addView(stats)
        page.addView(briefing, LinearLayout.LayoutParams(-1, -2).apply {{ bottomMargin = dp(22) }})

        page.addView(label("빠른 실행", 20f, "#FFFFFF", true).apply {{ setPadding(0, 0, 0, dp(12)) }})
        val quickGrid = LinearLayout(this).apply {{ orientation = LinearLayout.VERTICAL }}
        val firstRow = LinearLayout(this).apply {{ orientation = LinearLayout.HORIZONTAL }}
        firstRow.addView(quickCard("🎤", "음성 기록", "말하면 자동 분류") {{ startVoiceInput() }}, LinearLayout.LayoutParams(0, dp(126), 1f).apply {{ marginEnd = dp(6) }})
        firstRow.addView(quickCard("✎", "빠른 메모", "놓치기 전에 기록") {{ showItemEditor("메모") }}, LinearLayout.LayoutParams(0, dp(126), 1f).apply {{ marginStart = dp(6) }})
        val secondRow = LinearLayout(this).apply {{ orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }}
        secondRow.addView(quickCard("▣", "일정 추가", "시간과 장소 등록") {{ showItemEditor("일정") }}, LinearLayout.LayoutParams(0, dp(126), 1f).apply {{ marginEnd = dp(6) }})
        secondRow.addView(quickCard("✓", "할 일 추가", "우선순위와 마감") {{ showItemEditor("할 일") }}, LinearLayout.LayoutParams(0, dp(126), 1f).apply {{ marginStart = dp(6) }})
        quickGrid.addView(firstRow)
        quickGrid.addView(secondRow)
        page.addView(quickGrid, LinearLayout.LayoutParams(-1, -2).apply {{ bottomMargin = dp(24) }})

        page.addView(label("예정된 일정", 20f, "#FFFFFF", true).apply {{ setPadding(0, 0, 0, dp(10)) }})
        val nextSchedule = schedules.filter {{ it.dueAt <= 0L || it.dueAt >= System.currentTimeMillis() }}.minByOrNull {{ if (it.dueAt > 0L) it.dueAt else Long.MAX_VALUE }}
        val scheduleCard = LinearLayout(this).apply {{
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded("#181C27", 20f, "#292E3B")
            isClickable = true
            setOnClickListener {{ showPlanner() }}
        }}
        if (nextSchedule == null) {{
            scheduleCard.addView(label("등록된 일정이 없습니다", 16f, "#B4BAC8", true))
            scheduleCard.addView(label("일정 추가를 눌러 새 일정을 만들어 보세요.", 13f, "#858C9B").apply {{ setPadding(0, dp(5), 0, 0) }})
        }} else {{
            scheduleCard.addView(label(nextSchedule.title, 18f, "#FFFFFF", true))
            val timeText = if (nextSchedule.dueAt > 0L) formatDateTime(nextSchedule.dueAt) else nextSchedule.due
            scheduleCard.addView(label(timeText, 14f, "#9DA6FF").apply {{ setPadding(0, dp(6), 0, 0) }})
            if (nextSchedule.details.isNotBlank()) scheduleCard.addView(label(nextSchedule.details.take(70), 13f, "#A9AFBD").apply {{ setPadding(0, dp(5), 0, 0) }})
        }}
        page.addView(scheduleCard, LinearLayout.LayoutParams(-1, -2).apply {{ bottomMargin = dp(22) }})

        page.addView(label("미완료 할 일", 20f, "#FFFFFF", true).apply {{ setPadding(0, 0, 0, dp(10)) }})
        val nextTask = tasks.firstOrNull()
        val taskCard = LinearLayout(this).apply {{
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded("#181C27", 20f, "#292E3B")
            isClickable = true
            setOnClickListener {{ showPlanner() }}
        }}
        if (nextTask == null) {{
            taskCard.addView(label("모든 할 일을 완료했습니다", 16f, "#B4BAC8", true))
        }} else {{
            taskCard.addView(label("□  ${{nextTask.title}}", 17f, "#FFFFFF", true))
            val dueText = if (nextTask.dueAt > 0L) formatDateTime(nextTask.dueAt) else nextTask.due
            if (dueText.isNotBlank()) taskCard.addView(label(dueText, 13f, "#C18BFF").apply {{ setPadding(dp(25), dp(6), 0, 0) }})
        }}
        page.addView(taskCard)

        val scroll = ScrollView(this).apply {{
            isFillViewport = true
            clipToPadding = false
            addView(page, ScrollView.LayoutParams(-1, -2))
        }}
        {host}.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))'''

s = replace_function_body(s, 'private fun showHome()', home_body)

# 버전 갱신
gradle = Path('app/build.gradle.kts')
g = gradle.read_text().replace('versionCode = 7', 'versionCode = 8').replace('versionName = "0.6.0"', 'versionName = "0.7.0"')
gradle.write_text(g)

main.write_text(s)
