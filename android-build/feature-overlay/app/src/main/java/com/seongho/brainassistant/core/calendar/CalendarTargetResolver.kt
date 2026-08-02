package com.seongho.brainassistant.core.calendar

data class CalendarTarget(
    val id: Long,
    val accountType: String,
    val visible: Boolean,
    val writable: Boolean,
)

class CalendarTargetResolver(
    private val targets: () -> List<CalendarTarget>,
) {
    fun resolve(requestedCalendarId: String): Long {
        requestedCalendarId.toLongOrNull()?.let { return it }
        return targets()
            .firstOrNull { it.accountType == GOOGLE_ACCOUNT_TYPE && it.visible && it.writable }
            ?.id
            ?: throw CalendarAuthorizationRequiredException("쓰기 가능한 Google 캘린더를 찾을 수 없습니다. 기기 계정과 캘린더 동기화를 확인해 주세요.")
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
