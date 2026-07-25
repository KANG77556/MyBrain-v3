from pathlib import Path

main = Path('app/src/main/java/com/brainassistant/app/MainActivity.kt')
s = main.read_text()

s = s.replace('import java.util.Calendar\n', '''import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
''')

old = '''    private fun confirmNaturalItem(source: String) {
        val item = parseNaturalItem(source)
        val whenText = if (item.dueAt > 0L) formatDateTime(item.dueAt) else "날짜 없음"
        val message = "분류: ${item.type}\\n제목: ${item.title}\\n시간: $whenText\\n\\n이 내용으로 저장할까요?"
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
'''
new = '''    private fun confirmNaturalItem(source: String) {
        val apiKey = encryptedPrefs.getString("api_key", "").orEmpty().trim()
        if (apiKey.isBlank()) {
            confirmParsedItem(source, parseNaturalItem(source), "기기 분석")
            return
        }
        val progress = AlertDialog.Builder(this)
            .setTitle("AI 분석 중")
            .setMessage("문장에서 일정·할 일·메모 정보를 추출하고 있습니다.")
            .setCancelable(false)
            .create()
        progress.show()
        Thread {
            try {
                val item = requestCloudAnalysis(source, apiKey)
                runOnUiThread {
                    progress.dismiss()
                    confirmParsedItem(source, item, encryptedPrefs.getString("provider", "GPT") ?: "AI")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    toast("AI 연결에 실패해 기기 분석을 사용합니다.")
                    confirmParsedItem(source, parseNaturalItem(source), "기기 분석")
                }
            }
        }.start()
    }

    private fun confirmParsedItem(source: String, item: BrainItem, analyzer: String) {
        val whenText = if (item.dueAt > 0L) formatDateTime(item.dueAt) else "날짜 없음"
        val message = "분석: $analyzer\\n분류: ${item.type}\\n제목: ${item.title}\\n시간: $whenText\\n\\n이 내용으로 저장할까요?"
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

    private fun requestCloudAnalysis(source: String, apiKey: String): BrainItem {
        val provider = encryptedPrefs.getString("provider", "GPT") ?: "GPT"
        val modelMode = encryptedPrefs.getString("model", "자동 선택") ?: "자동 선택"
        val nowText = SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.KOREA).format(Date())
        val prompt = """현재 한국 시간은 $nowText 입니다.
사용자 문장을 업무 기록 하나로 분석하세요.
반드시 JSON 객체만 출력하세요. 마크다운 금지.
스키마: {"type":"일정|할 일|메모","title":"짧은 제목","details":"원문을 포함한 설명","dueAt":0,"remindMinutes":-1}
dueAt은 한국 시간 기준 Unix epoch 밀리초입니다. 날짜가 없으면 0입니다.
일정 기본 알림은 30분 전, 할 일은 정각, 메모는 -1입니다.
사용자 문장: $source"""
        val jsonText = if (provider == "Gemini") {
            requestGemini(prompt, apiKey, modelMode)
        } else {
            requestOpenAI(prompt, apiKey, modelMode)
        }
        val clean = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = org.json.JSONObject(clean)
        val type = obj.optString("type", "메모").let { if (it in listOf("일정", "할 일", "메모")) it else "메모" }
        val title = obj.optString("title", source.take(40)).ifBlank { source.take(40) }
        val details = obj.optString("details", source).ifBlank { source }
        val dueAt = obj.optLong("dueAt", 0L)
        val reminderDefault = when (type) { "일정" -> 30; "할 일" -> 0; else -> -1 }
        val reminder = obj.optInt("remindMinutes", reminderDefault)
        return BrainItem(
            UUID.randomUUID().toString(), type, title, details,
            if (dueAt > 0L) formatDateTime(dueAt) else "",
            false, System.currentTimeMillis(), dueAt, reminder
        )
    }

    private fun requestOpenAI(prompt: String, apiKey: String, mode: String): String {
        val model = when (mode) {
            "고성능 모델" -> "gpt-5.6-sol"
            "빠른 모델" -> "gpt-5.6-luna"
            else -> "gpt-5.6-terra"
        }
        val body = org.json.JSONObject().apply {
            put("model", model)
            put("input", prompt)
            put("store", false)
            put("text", org.json.JSONObject().put("format", org.json.JSONObject().put("type", "json_object")))
        }.toString()
        val response = postJson("https://api.openai.com/v1/responses", body, mapOf("Authorization" to "Bearer $apiKey"))
        val root = org.json.JSONObject(response)
        val output = root.optJSONArray("output") ?: throw IllegalStateException("AI 응답이 없습니다.")
        for (i in 0 until output.length()) {
            val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val text = content.optJSONObject(j)?.optString("text").orEmpty()
                if (text.isNotBlank()) return text
            }
        }
        throw IllegalStateException("AI 텍스트 응답이 없습니다.")
    }

    private fun requestGemini(prompt: String, apiKey: String, mode: String): String {
        val model = when (mode) {
            "고성능 모델" -> "gemini-3.5-pro"
            "빠른 모델" -> "gemini-3.5-flash-lite"
            else -> "gemini-3.5-flash"
        }
        val body = org.json.JSONObject().apply {
            put("contents", org.json.JSONArray().put(org.json.JSONObject().put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", prompt)))))
            put("generationConfig", org.json.JSONObject().put("responseMimeType", "application/json"))
        }.toString()
        val response = postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body, mapOf("x-goog-api-key" to apiKey)
        )
        return org.json.JSONObject(response)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }

    private fun postJson(endpoint: String, body: String, headers: Map<String, String>): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
        connection.disconnect()
        if (status !in 200..299) throw IllegalStateException("HTTP $status: ${text.take(180)}")
        return text
    }
'''
if old not in s:
    raise SystemExit('confirmNaturalItem target missing')
s = s.replace(old, new)

s = s.replace('AI 연동은 v0.4에서 실제 API 호출과 함께 연결됩니다.', '등록한 GPT 또는 Gemini API 키로 자연어를 분석합니다. 연결 실패 시 기기 분석으로 자동 전환됩니다.')
main.write_text(s)

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text()
if 'android.permission.INTERNET' not in m:
    m = m.replace('<manifest', '<manifest', 1).replace('>', '>\n    <uses-permission android:name="android.permission.INTERNET" />', 1)
manifest.write_text(m)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text().replace('versionCode = 6', 'versionCode = 7').replace('versionName = "0.5.0"', 'versionName = "0.6.0"')
gradle.write_text(g)
