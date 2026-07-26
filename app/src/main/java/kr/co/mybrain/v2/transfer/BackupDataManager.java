package kr.co.mybrain.v2.transfer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import kr.co.mybrain.v2.BuildConfig;
import kr.co.mybrain.v2.data.WorkItemEntity;
import kr.co.mybrain.v2.settings.AiBudgetSettings;
import kr.co.mybrain.v2.settings.AiSettings;

/** 백업에 포함할 데이터와 비밀값이 아닌 설정을 JSON으로 변환합니다. */
public final class BackupDataManager {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_ITEMS = 50_000;

    private BackupDataManager() {}

    public static String createPayload(Context context, List<WorkItemEntity> items) throws Exception {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (WorkItemEntity item : items) array.put(toJson(item));
        }
        AiSettings ai = AiSettings.load(context);
        AiBudgetSettings budget = AiBudgetSettings.load(context);
        JSONObject settings = new JSONObject()
                .put("provider", ai.provider)
                .put("openAiModel", ai.openAiModel)
                .put("geminiModel", ai.geminiModel)
                .put("wifiOnly", budget.wifiOnly)
                .put("budgetEnabled", budget.budgetEnabled)
                .put("blockAtLimit", budget.blockAtLimit)
                .put("monthlyLimitWon", budget.monthlyLimitWon)
                .put("warningPercent", budget.warningPercent)
                .put("wonPerUsd", budget.wonPerUsd)
                .put("notificationsEnabled", budget.notificationsEnabled);

        return new JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("exportedAt", System.currentTimeMillis())
                .put("timezone", ZoneId.systemDefault().getId())
                .put("credentialsIncluded", false)
                .put("settings", settings)
                .put("workItems", array)
                .toString();
    }

    public static BackupPayload parsePayload(String json) throws Exception {
        JSONObject root = new JSONObject(json == null ? "" : json);
        int schema = root.optInt("schemaVersion", -1);
        if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("지원하지 않는 백업 데이터 버전입니다.");
        JSONArray array = root.optJSONArray("workItems");
        if (array == null) throw new IllegalArgumentException("백업에 일정 데이터가 없습니다.");
        if (array.length() > MAX_ITEMS) throw new IllegalArgumentException("백업 항목 수가 허용 범위를 초과했습니다.");
        List<WorkItemEntity> items = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.optJSONObject(i);
            if (value == null) throw new IllegalArgumentException("백업 항목 형식이 올바르지 않습니다.");
            items.add(fromJson(value));
        }
        return new BackupPayload(
                items,
                root.optJSONObject("settings"),
                root.optString("appVersion", "알 수 없음"),
                root.optLong("exportedAt", 0L),
                root.optString("timezone", ""));
    }

    public static void restoreNonSecretSettings(Context context, JSONObject settings) {
        if (settings == null) return;
        AiSettings ai = AiSettings.load(context);
        ai.provider = AiSettings.normalizeProvider(settings.optString("provider", ai.provider));
        ai.openAiModel = AiSettings.normalizeModel(settings.optString("openAiModel", ai.openAiModel), AiSettings.DEFAULT_OPENAI_MODEL);
        ai.geminiModel = AiSettings.normalizeModel(settings.optString("geminiModel", ai.geminiModel), AiSettings.DEFAULT_GEMINI_MODEL);
        ai.save(context);

        AiBudgetSettings budget = AiBudgetSettings.load(context);
        budget.wifiOnly = settings.optBoolean("wifiOnly", budget.wifiOnly);
        budget.budgetEnabled = settings.optBoolean("budgetEnabled", budget.budgetEnabled);
        budget.blockAtLimit = settings.optBoolean("blockAtLimit", budget.blockAtLimit);
        budget.monthlyLimitWon = settings.optLong("monthlyLimitWon", budget.monthlyLimitWon);
        budget.warningPercent = settings.optInt("warningPercent", budget.warningPercent);
        budget.wonPerUsd = settings.optInt("wonPerUsd", budget.wonPerUsd);
        budget.notificationsEnabled = settings.optBoolean("notificationsEnabled", budget.notificationsEnabled);
        budget.save(context);
    }

    private static JSONObject toJson(WorkItemEntity item) throws Exception {
        return new JSONObject()
                .put("externalId", item.externalId)
                .put("type", item.type)
                .put("title", item.title)
                .put("content", item.content)
                .put("sourceText", item.sourceText)
                .put("startAt", nullable(item.startAt))
                .put("endAt", nullable(item.endAt))
                .put("allDay", item.allDay)
                .put("completed", item.completed)
                .put("priority", item.priority)
                .put("repeatRule", item.repeatRule)
                .put("reminderAt", nullable(item.reminderAt))
                .put("color", item.color)
                .put("aiProvider", item.aiProvider)
                .put("aiConfidence", item.aiConfidence)
                .put("createdAt", item.createdAt)
                .put("updatedAt", item.updatedAt)
                .put("deletedAt", nullable(item.deletedAt));
    }

    private static WorkItemEntity fromJson(JSONObject json) {
        WorkItemEntity item = new WorkItemEntity();
        String externalId = json.optString("externalId", "").trim();
        item.externalId = externalId.isEmpty() ? UUID.randomUUID().toString() : limit(externalId, 100);
        String type = json.optString("type", WorkItemEntity.TYPE_MEMO);
        item.type = WorkItemEntity.TYPE_SCHEDULE.equals(type) || WorkItemEntity.TYPE_TASK.equals(type)
                ? type : WorkItemEntity.TYPE_MEMO;
        item.title = limit(json.optString("title", ""), 240);
        item.content = limit(json.optString("content", ""), 20_000);
        item.sourceText = limit(json.optString("sourceText", ""), 20_000);
        item.startAt = nullableLong(json, "startAt");
        item.endAt = nullableLong(json, "endAt");
        item.allDay = json.optBoolean("allDay", false);
        item.completed = json.optBoolean("completed", false);
        String priority = json.optString("priority", "NORMAL");
        item.priority = "LOW".equals(priority) || "HIGH".equals(priority) ? priority : "NORMAL";
        item.repeatRule = limit(json.optString("repeatRule", "NONE"), 180);
        if (item.repeatRule.isEmpty()) item.repeatRule = "NONE";
        item.reminderAt = nullableLong(json, "reminderAt");
        item.color = limit(json.optString("color", "DEFAULT"), 60);
        item.aiProvider = limit(json.optString("aiProvider", "LOCAL"), 40);
        item.aiConfidence = (float) Math.max(0.0, Math.min(1.0, json.optDouble("aiConfidence", 0.0)));
        item.createdAt = Math.max(1L, json.optLong("createdAt", System.currentTimeMillis()));
        item.updatedAt = Math.max(1L, json.optLong("updatedAt", item.createdAt));
        item.deletedAt = nullableLong(json, "deletedAt");
        return item;
    }

    private static Object nullable(Long value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static Long nullableLong(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        long value = json.optLong(key, Long.MIN_VALUE);
        return value == Long.MIN_VALUE ? null : value;
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    public static final class BackupPayload {
        public final List<WorkItemEntity> items;
        public final JSONObject settings;
        public final String sourceAppVersion;
        public final long exportedAt;
        public final String timezone;

        BackupPayload(List<WorkItemEntity> items, JSONObject settings, String sourceAppVersion,
                      long exportedAt, String timezone) {
            this.items = items;
            this.settings = settings;
            this.sourceAppVersion = sourceAppVersion;
            this.exportedAt = exportedAt;
            this.timezone = timezone;
        }
    }
}