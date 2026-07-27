package kr.co.mybrain.v2.transfer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** 현재 설치 앱이 고정 서명 Release 업데이트 체계에 들어갔는지 진단합니다. */
public final class ReleaseReadiness {
    public static final String EXPECTED_CERT_SHA256 =
            "ee9b89627074c2708f7d91ae1a9fcf5ebd8f9611b4df0719e8aa4eef63765520";

    private ReleaseReadiness() {}

    public static Report inspect(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), flags);
            ApplicationInfo app = context.getApplicationInfo();
            boolean debuggable = (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            String cert = firstDigest(info);
            boolean expectedSigner = EXPECTED_CERT_SHA256.equalsIgnoreCase(cert);
            boolean ready = !debuggable && expectedSigner;
            String state;
            if (ready) state = "정식 업데이트 준비 완료";
            else if (debuggable) state = "개발용 디버그 빌드";
            else if (cert.isEmpty()) state = "서명 인증서를 확인할 수 없음";
            else state = "고정 서명 인증서 불일치";
            return new Report(true, context.getPackageName(),
                    info.versionName == null ? "알 수 없음" : info.versionName,
                    versionCode, debuggable, cert, expectedSigner, ready, state, null);
        } catch (Exception error) {
            return new Report(false, context.getPackageName(), "알 수 없음", 0L,
                    false, "", false, false, "진단 실패", error.getMessage());
        }
    }

    private static String firstDigest(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) return "";
            signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return "";
        byte[] value = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder hex = new StringBuilder();
        for (byte b : value) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    public static List<String> checklist(Report report) {
        List<String> rows = new ArrayList<>();
        rows.add((report.packageName.equals("kr.co.mybrain.v2") ? "✓" : "✗") + " 패키지명 kr.co.mybrain.v2");
        rows.add((!report.debuggable ? "✓" : "✗") + " Release 빌드");
        rows.add((report.expectedSigner ? "✓" : "✗") + " 고정 서명 인증서");
        rows.add((report.versionCode > 0 ? "✓" : "✗") + " 버전 코드 확인");
        return rows;
    }

    public static final class Report {
        public final boolean success;
        public final String packageName;
        public final String versionName;
        public final long versionCode;
        public final boolean debuggable;
        public final String certificateSha256;
        public final boolean expectedSigner;
        public final boolean updateReady;
        public final String state;
        public final String error;

        Report(boolean success, String packageName, String versionName, long versionCode,
               boolean debuggable, String certificateSha256, boolean expectedSigner,
               boolean updateReady, String state, String error) {
            this.success = success;
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.debuggable = debuggable;
            this.certificateSha256 = certificateSha256;
            this.expectedSigner = expectedSigner;
            this.updateReady = updateReady;
            this.state = state;
            this.error = error;
        }
    }
}
