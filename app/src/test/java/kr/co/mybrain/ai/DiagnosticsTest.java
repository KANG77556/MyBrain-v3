package kr.co.mybrain.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import java.util.Arrays;

public class DiagnosticsTest {
    @Test public void grantedPermissionIsNormal() {
        assertEquals(DiagnosticItem.Status.NORMAL, DiagnosticStatusResolver.permission(true));
    }
    @Test public void deniedPermissionNeedsAction() {
        assertEquals(DiagnosticItem.Status.ACTION_REQUIRED, DiagnosticStatusResolver.permission(false));
    }
    @Test public void unsupportedExactAlarmIsUnsupported() {
        assertEquals(DiagnosticItem.Status.UNSUPPORTED, DiagnosticStatusResolver.exactAlarm(false, false));
    }
    @Test public void reportDoesNotExposeApiKeyLikeText() {
        DiagnosticItem item = new DiagnosticItem(DiagnosticItem.Type.APP_INFO, "앱 정보", "API KEY sk-secret", DiagnosticItem.Status.NORMAL, null);
        String report = DiagnosticsReportFormatter.format(Arrays.asList(item));
        assertFalse(report.contains("sk-secret"));
        assertFalse(report.toLowerCase().contains("api key sk"));
    }
}
