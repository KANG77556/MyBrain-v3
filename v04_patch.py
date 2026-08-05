from pathlib import Path

main = Path('app/src/main/java/com/brainassistant/app/MainActivity.kt')
s = main.read_text()

s = s.replace('import android.graphics.Color\n', '''import android.Manifest\nimport android.app.AlarmManager\nimport android.app.DatePickerDialog\nimport android.app.PendingIntent\nimport android.app.TimePickerDialog\nimport android.content.Intent\nimport android.content.pm.PackageManager\nimport android.graphics.Color\n''')
s = s.replace('import android.os.Bundle\n', 'import android.os.Build\nimport android.os.Bundle\n')
s = s.replace('import androidx.core.content.ContextCompat\n', 'import androidx.core.app.ActivityCompat\nimport androidx.core.content.ContextCompat\n')
s = s.replace('import java.util.Date\n', 'import java.util.Calendar\nimport java.util.Date\n')

s = s.replace('''    data class BrainItem(\n        val id: String,\n        val type: String,\n        val title: String,\n        val details: String,\n        val due: String,\n        val completed: Boolean,\n        val createdAt: Long\n    )''', '''    data class BrainItem(\n        val id: String,\n        val type: String,\n        val title: String,\n        val details: String,\n        val due: String,\n        val completed: Boolean,\n        val createdAt: Long,\n        val dueAt: Long = 0L,\n        val remindMinutes: Int = -1\n    )''')

s = s.replace('''        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)\n        encryptedPrefs = EncryptedSharedPreferences.create(\n            "secure_settings", masterKeyAlias, this,\n            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,\n            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM\n        )\n        dataPrefs = getSharedPreferences("brain_data", MODE_PRIVATE)\n        buildShell()\n        showHome()''', '''        encryptedPrefs = try {\n            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)\n            EncryptedSharedPreferences.create(\n                "secure_settings", masterKeyAlias, this,\n                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,\n                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM\n            )\n        } catch (_: Exception) {\n            getSharedPreferences("secure_settings_fallback", MODE_PRIVATE)\n        }\n        dataPrefs = getSharedPreferences("brain_data", MODE_PRIVATE)\n        requestNotificationPermission()\n        buildShell()\n        showHome()''')

old_editor = '''        val dueInput = EditText(this).apply { hint = if (type == "메모") "날짜 또는 분류(선택)" else "날짜·시간 또는 마감(예: 7월 26일 14:00)"; setText(existing?.due.orEmpty()) }\n        container.addView(titleInput, margin(bottom = 8, height = 52)); container.addView(detailInput, margin(bottom = 8)); container.addView(dueInput, margin(bottom = 4, height = 52))'''
new_editor = '''        var selectedDueAt = existing?.dueAt ?: 0L\n        val dueInput = EditText(this).apply {\n            hint = if (type == "메모") "날짜 또는 분류(선택)" else "날짜·시간 선택"\n            setText(if (selectedDueAt > 0L) formatDateTime(selectedDueAt) else existing?.due.orEmpty())\n            if (type != "메모") {\n                isFocusable = false\n                isClickable = true\n                setOnClickListener {\n                    pickDateTime(selectedDueAt) { value ->\n                        selectedDueAt = value\n                        setText(formatDateTime(value))\n                    }\n                }\n            }\n        }\n        val reminderLabels = arrayOf("알림 없음", "정각", "10분 전", "30분 전", "1시간 전", "하루 전")\n        val reminderValues = intArrayOf(-1, 0, 10, 30, 60, 1440)\n        val reminderSpinner = Spinner(this).apply {\n            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, reminderLabels)\n            val current = reminderValues.indexOf(existing?.remindMinutes ?: -1)\n            setSelection(if (current >= 0) current else 0)\n            visibility = if (type == "메모") View.GONE else View.VISIBLE\n        }\n        container.addView(titleInput, margin(bottom = 8, height = 52))\n        container.addView(detailInput, margin(bottom = 8))\n        container.addView(dueInput, margin(bottom = 4, height = 52))\n        container.addView(reminderSpinner, margin(bottom = 4, height = 52))'''
if old_editor not in s:
    raise SystemExit('editor target missing')
s = s.replace(old_editor, new_editor)

old_saved = '''                val saved = BrainItem(existing?.id ?: UUID.randomUUID().toString(), type, title, detailInput.text.toString().trim(), dueInput.text.toString().trim(), existing?.completed ?: false, existing?.createdAt ?: System.currentTimeMillis())'''
new_saved = '''                val reminder = if (type == "메모") -1 else reminderValues[reminderSpinner.selectedItemPosition]\n                val saved = BrainItem(\n                    existing?.id ?: UUID.randomUUID().toString(),\n                    type,\n                    title,\n                    detailInput.text.toString().trim(),\n                    dueInput.text.toString().trim(),\n                    existing?.completed ?: false,\n                    existing?.createdAt ?: System.currentTimeMillis(),\n                    selectedDueAt,\n                    reminder\n                )'''
if old_saved not in s:
    raise SystemExit('saved target missing')
s = s.replace(old_saved, new_saved)
s = s.replace('''                saveItems(all)\n                dialog.dismiss(); toast("${type}을 저장했습니다.")''', '''                saveItems(all)\n                cancelAlarm(saved.id)\n                scheduleAlarm(saved)\n                dialog.dismiss(); toast("${type}을 저장했습니다.")''')

s = s.replace('''                    saveItems(loadItems().filterNot { it.id == item.id }); toast("삭제했습니다."); showPlanner()''', '''                    cancelAlarm(item.id)\n                    saveItems(loadItems().filterNot { it.id == item.id }); toast("삭제했습니다."); showPlanner()''')

s = s.replace('''    private fun updateCompleted(id: String, completed: Boolean) {\n        saveItems(loadItems().map { if (it.id == id) it.copy(completed = completed) else it })\n    }''', '''    private fun updateCompleted(id: String, completed: Boolean) {\n        val updated = loadItems().map { if (it.id == id) it.copy(completed = completed) else it }\n        saveItems(updated)\n        val item = updated.firstOrNull { it.id == id }\n        if (completed) cancelAlarm(id) else if (item != null) scheduleAlarm(item)\n    }''')

s = s.replace('''            BrainItem(o.getString("id"), o.getString("type"), o.getString("title"), o.optString("details"), o.optString("due"), o.optBoolean("completed"), o.optLong("createdAt"))''', '''            BrainItem(\n                o.getString("id"), o.getString("type"), o.getString("title"),\n                o.optString("details"), o.optString("due"), o.optBoolean("completed"),\n                o.optLong("createdAt"), o.optLong("dueAt"), o.optInt("remindMinutes", -1)\n            )''')
s = s.replace('''                put("id", item.id); put("type", item.type); put("title", item.title); put("details", item.details); put("due", item.due); put("completed", item.completed); put("createdAt", item.createdAt)''', '''                put("id", item.id); put("type", item.type); put("title", item.title); put("details", item.details); put("due", item.due); put("completed", item.completed); put("createdAt", item.createdAt); put("dueAt", item.dueAt); put("remindMinutes", item.remindMinutes)''')

helpers = r'''
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun pickDateTime(initial: Long, onSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { if (initial > 0L) timeInMillis = initial }
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(year, month, day, hour, minute, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                onSelected(calendar.timeInMillis)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatDateTime(value: Long): String =
        SimpleDateFormat("yyyy년 M월 d일 (E) a h:mm", Locale.KOREA).format(Date(value))

    private fun alarmPendingIntent(item: BrainItem): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("itemId", item.id)
            putExtra("title", item.title)
            putExtra("details", item.details)
            putExtra("type", item.type)
        }
        return PendingIntent.getBroadcast(
            this, item.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarm(item: BrainItem) {
        if (item.completed || item.dueAt <= 0L || item.remindMinutes < 0) return
        val triggerAt = item.dueAt - item.remindMinutes * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager
        val pending = alarmPendingIntent(item)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancelAlarm(id: String) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this, id.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }
'''
s = s.replace('    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()\n}', helpers + '\n    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()\n}')
main.write_text(s)

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text()
m = m.replace('<uses-permission android:name="android.permission.RECORD_AUDIO" />', '<uses-permission android:name="android.permission.RECORD_AUDIO" />\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />')
m = m.replace('android:name=".MainActivity"', 'android:name="com.brainassistant.app.MainActivity"')
m = m.replace('''        </activity>\n    </application>''', '''        </activity>\n        <receiver android:name="com.brainassistant.app.AlarmReceiver" android:exported="false" />\n        <receiver android:name="com.brainassistant.app.BootReceiver" android:enabled="true" android:exported="true">\n            <intent-filter>\n                <action android:name="android.intent.action.BOOT_COMPLETED" />\n                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />\n            </intent-filter>\n        </receiver>\n    </application>''')
manifest.write_text(m)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text().replace('versionCode = 3', 'versionCode = 5').replace('versionName = "0.3.0"', 'versionName = "0.4.0"')
gradle.write_text(g)

receiver = Path('app/src/main/java/com/brainassistant/app/AlarmReceiver.kt')
receiver.write_text(r'''package com.brainassistant.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "brain_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "일정 및 할 일 알림", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = intent.getStringExtra("title") ?: "브레인 비서 알림"
        val details = intent.getStringExtra("details").orEmpty()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(if (details.isBlank()) "확인할 시간이 되었습니다." else details)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()
        manager.notify(intent.getStringExtra("itemId")?.hashCode() ?: title.hashCode(), notification)
    }
}
''')

boot = Path('app/src/main/java/com/brainassistant/app/BootReceiver.kt')
boot.write_text(r'''package com.brainassistant.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences("brain_data", Context.MODE_PRIVATE)
        val array = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optBoolean("completed")) continue
            val dueAt = item.optLong("dueAt")
            val remind = item.optInt("remindMinutes", -1)
            if (dueAt <= 0L || remind < 0) continue
            val triggerAt = dueAt - remind * 60_000L
            if (triggerAt <= System.currentTimeMillis()) continue
            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("itemId", item.optString("id"))
                putExtra("title", item.optString("title"))
                putExtra("details", item.optString("details"))
                putExtra("type", item.optString("type"))
            }
            val pending = PendingIntent.getBroadcast(
                context, item.optString("id").hashCode(), alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }
}
''')
