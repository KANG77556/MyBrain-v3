from pathlib import Path

main = Path('app/src/main/java/com/brainassistant/app/MainActivity.kt')
s = main.read_text()

needle = '''    private fun confirmNaturalItem(source: String) {
        val apiKey = encryptedPrefs.getString("api_key", "").orEmpty().trim()'''
replacement = '''    private fun confirmNaturalItem(source: String) {
        if (tryCreateRangeSchedule(source)) return
        val apiKey = encryptedPrefs.getString("api_key", "").orEmpty().trim()'''
if needle not in s:
    raise SystemExit('confirmNaturalItem anchor missing')
s = s.replace(needle, replacement, 1)

anchor = '''    private fun confirmNaturalItem(source: String) {'''
helpers = r'''
    private fun tryCreateRangeSchedule(source: String): Boolean {
        val weekdayRange = Regex("(월|화|수|목|금|토|일)요일부터\\s*(월|화|수|목|금|토|일)요일까지").find(source) ?: return false
        val timeRange = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?부터\\s*(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?까지").find(source) ?: return false

        val dayOrder = listOf("월", "화", "수", "목", "금", "토", "일")
        val startDayIndex = dayOrder.indexOf(weekdayRange.groupValues[1])
        val endDayIndex = dayOrder.indexOf(weekdayRange.groupValues[2])
        if (startDayIndex < 0 || endDayIndex < startDayIndex) return false

        fun hour(ampm: String, raw: String): Int {
            var value = raw.toIntOrNull() ?: 9
            if (ampm == "오후" && value < 12) value += 12
            if (ampm == "오전" && value == 12) value = 0
            return value.coerceIn(0, 23)
        }

        val startHour = hour(timeRange.groupValues[1], timeRange.groupValues[2])
        val startMinute = timeRange.groupValues[3].toIntOrNull()?.coerceIn(0, 59) ?: 0
        val endAmpm = timeRange.groupValues[4].ifBlank { timeRange.groupValues[1] }
        val endHour = hour(endAmpm, timeRange.groupValues[5])
        val endMinute = timeRange.groupValues[6].toIntOrNull()?.coerceIn(0, 59) ?: 0

        val title = source
            .replace(Regex("다음\\s*주|이번\\s*주"), "")
            .replace(weekdayRange.value, "")
            .replace(timeRange.value, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "반복 일정" }

        val monday = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val current = get(Calendar.DAY_OF_WEEK)
            val daysUntilMonday = (Calendar.MONDAY - current + 7) % 7
            add(Calendar.DAY_OF_YEAR, if (source.contains("다음 주") || source.contains("다음주")) daysUntilMonday + 7 else daysUntilMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }

        val newItems = (startDayIndex..endDayIndex).map { index ->
            val start = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, index)
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
            }
            val end = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, index)
                set(Calendar.HOUR_OF_DAY, endHour)
                set(Calendar.MINUTE, endMinute)
            }
            val detail = "${dayOrder[index]}요일 ${String.format("%02d:%02d", startHour, startMinute)}~${String.format("%02d:%02d", endHour, endMinute)} · ${source.trim()}"
            BrainItem(
                UUID.randomUUID().toString(), "일정", title, detail,
                formatDateTime(start.timeInMillis), false, System.currentTimeMillis(),
                start.timeInMillis, 30
            )
        }

        val existing = loadItems()
        val conflicts = newItems.filter { candidate ->
            existing.any { saved -> !saved.completed && saved.type == "일정" && saved.dueAt == candidate.dueAt }
        }
        val dateText = "${formatDateTime(newItems.first().dueAt).substringBefore(" 오전").substringBefore(" 오후")} ~ ${formatDateTime(newItems.last().dueAt).substringBefore(" 오전").substringBefore(" 오후")}" 
        val warning = if (conflicts.isEmpty()) "" else "\\n\\n⚠ 같은 시작 시각의 기존 일정 ${conflicts.size}건이 있습니다."
        val summary = "제목: $title\\n기간: $dateText\\n반복: ${dayOrder.subList(startDayIndex, endDayIndex + 1).joinToString("·")}요일\\n시간: ${String.format("%02d:%02d", startHour, startMinute)}~${String.format("%02d:%02d", endHour, endMinute)}\\n생성: ${newItems.size}개 일정\\n알림: 30분 전$warning"

        AlertDialog.Builder(this)
            .setTitle("반복 일정 분석 결과")
            .setMessage(summary)
            .setNegativeButton("취소", null)
            .setNeutralButton("다시 입력") { _, _ -> showNaturalLanguageInput(source) }
            .setPositiveButton("${newItems.size}개 저장") { _, _ ->
                val all = loadItems().toMutableList()
                newItems.reversed().forEach { all.add(0, it) }
                saveItems(all)
                newItems.forEach { scheduleAlarm(it) }
                toast("반복 일정 ${newItems.size}개를 저장했습니다.")
                showHome()
            }
            .show()
        return true
    }

'''
if anchor not in s:
    raise SystemExit('function insertion anchor missing')
s = s.replace(anchor, helpers + anchor, 1)
main.write_text(s)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text().replace('versionCode = 8', 'versionCode = 9').replace('versionName = "0.7.0"', 'versionName = "0.8.0"')
gradle.write_text(g)
