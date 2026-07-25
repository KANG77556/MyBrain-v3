from pathlib import Path

main = Path('app/src/main/java/com/brainassistant/app/MainActivity.kt')
s = main.read_text()

s = s.replace('import android.app.AlarmManager\n', 'import android.app.Activity\nimport android.app.AlarmManager\n')
s = s.replace('import android.os.Build\n', 'import android.os.Build\nimport android.speech.RecognizerIntent\n')

s = s.replace('''                    4 -> showPlaceholder("AI 비서", "저장된 일정·할 일·메모를 바탕으로 자연어 작업을 준비합니다.")''', '''                    4 -> showNaturalLanguageInput()''')

s = s.replace('''                "빠른 메모", "음성 기록" -> showItemEditor("메모")''', '''                "빠른 메모" -> showItemEditor("메모")
                "음성 기록" -> startVoiceInput()''')

anchor = '''    private fun showTypePicker() {'''
helpers = r'''
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2104)
            toast("마이크 권한을 허용한 뒤 다시 눌러 주세요.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "일정, 할 일 또는 메모를 말씀해 주세요")
        }
        try {
            startActivityForResult(intent, 2105)
        } catch (_: Exception) {
            toast("이 기기에서 음성 인식을 시작할 수 없습니다.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2105 && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) showNaturalLanguageInput(spoken)
        }
    }

    private fun showNaturalLanguageInput(initialText: String = "") {
        val input = EditText(this).apply {
            hint = "예: 내일 오후 2시 교무회의"
            setText(initialText)
            setSelection(text.length)
            minLines = 2
            gravity = Gravity.TOP
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "말하거나 입력한 문장을 분석해 일정·할 일·메모로 저장합니다."
                textSize = 13f
                setTextColor(color(R.color.text_secondary))
                setPadding(0, 0, 0, dp(10))
            })
            addView(input, margin(height = 100))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("AI 빠른 입력")
            .setView(box)
            .setNegativeButton("취소", null)
            .setNeutralButton("🎤 말하기", null)
            .setPositiveButton("분석", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                startVoiceInput()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    input.error = "내용을 입력하세요"
                    return@setOnClickListener
                }
                dialog.dismiss()
                confirmNaturalItem(text)
            }
        }
        dialog.show()
    }

    private fun confirmNaturalItem(source: String) {
        val item = parseNaturalItem(source)
        val whenText = if (item.dueAt > 0L) formatDateTime(item.dueAt) else "날짜 없음"
        val message = "분류: ${item.type}\n제목: ${item.title}\n시간: $whenText\n\n이 내용으로 저장할까요?"
        AlertDialog.Builder(this)
            .setTitle("분석 결과")
            .setMessage(message)
            .setNegativeButton("다시 입력") { _, _ -> showNaturalLanguageInput(source) }
            .setNeutralButton("직접 수정") { _, _ -> showItemEditor(item.type, item) }
            .setPositiveButton("저장") { _, _ ->
                val all = loadItems().toMutableList()
                all.add(0, item)
                saveItems(all)
                scheduleAlarm(item)
                toast("${item.type}으로 저장했습니다.")
                showHome()
            }
            .show()
    }

    private fun parseNaturalItem(source: String): BrainItem {
        val lower = source.trim()
        val type = when {
            listOf("까지", "제출", "해야", "준비", "완료", "마감", "할 일").any { lower.contains(it) } -> "할 일"
            listOf("회의", "약속", "수업", "상담", "출장", "예약", "일정", "만나").any { lower.contains(it) } || Regex("(오전|오후)?\\s*\\d{1,2}시").containsMatchIn(lower) -> "일정"
            else -> "메모"
        }
        val dueAt = parseKoreanDateTime(lower)
        var title = lower
            .replace(Regex("오늘|내일|모레|다음\\s*주"), "")
            .replace(Regex("(월|화|수|목|금|토|일)요일(까지)?"), "")
            .replace(Regex("(오전|오후)?\\s*\\d{1,2}시(\\s*\\d{1,2}분)?"), "")
            .replace("까지", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (title.isBlank()) title = when (type) { "일정" -> "새 일정"; "할 일" -> "새 할 일"; else -> "음성 메모" }
        val dueText = if (dueAt > 0L) formatDateTime(dueAt) else ""
        val reminder = when (type) { "일정" -> 30; "할 일" -> 0; else -> -1 }
        return BrainItem(
            UUID.randomUUID().toString(), type, title, source, dueText,
            false, System.currentTimeMillis(), dueAt, reminder
        )
    }

    private fun parseKoreanDateTime(text: String): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var hasDate = false
        when {
            text.contains("모레") -> { cal.add(Calendar.DAY_OF_YEAR, 2); hasDate = true }
            text.contains("내일") -> { cal.add(Calendar.DAY_OF_YEAR, 1); hasDate = true }
            text.contains("오늘") -> hasDate = true
        }
        val weekdays = mapOf("일" to Calendar.SUNDAY, "월" to Calendar.MONDAY, "화" to Calendar.TUESDAY, "수" to Calendar.WEDNESDAY, "목" to Calendar.THURSDAY, "금" to Calendar.FRIDAY, "토" to Calendar.SATURDAY)
        val weekdayMatch = Regex("(월|화|수|목|금|토|일)요일").find(text)
        if (weekdayMatch != null) {
            val target = weekdays[weekdayMatch.groupValues[1]] ?: cal.get(Calendar.DAY_OF_WEEK)
            var diff = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (diff == 0) diff = 7
            if (text.contains("다음 주")) diff += 7
            cal.add(Calendar.DAY_OF_YEAR, diff)
            hasDate = true
        }
        val monthDay = Regex("(\\d{1,2})월\\s*(\\d{1,2})일").find(text)
        if (monthDay != null) {
            val month = monthDay.groupValues[1].toIntOrNull() ?: return 0L
            val day = monthDay.groupValues[2].toIntOrNull() ?: return 0L
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            if (cal.timeInMillis < now.timeInMillis - 86_400_000L) cal.add(Calendar.YEAR, 1)
            hasDate = true
        }
        val time = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?").find(text)
        if (time != null) {
            val ampm = time.groupValues[1]
            var hour = time.groupValues[2].toIntOrNull() ?: 9
            val minute = time.groupValues[3].toIntOrNull() ?: 0
            if (ampm == "오후" && hour < 12) hour += 12
            if (ampm == "오전" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
            if (!hasDate && cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        if (hasDate) {
            cal.set(Calendar.HOUR_OF_DAY, if (typeFromText(text) == "할 일") 18 else 9)
            cal.set(Calendar.MINUTE, 0)
            return cal.timeInMillis
        }
        return 0L
    }

    private fun typeFromText(text: String): String =
        if (listOf("까지", "제출", "해야", "준비", "완료", "마감").any { text.contains(it) }) "할 일" else "일정"

'''
if anchor not in s:
    raise SystemExit('type picker anchor missing')
s = s.replace(anchor, helpers + anchor)
main.write_text(s)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text().replace('versionCode = 5', 'versionCode = 6').replace('versionName = "0.4.0"', 'versionName = "0.5.0"')
gradle.write_text(g)
