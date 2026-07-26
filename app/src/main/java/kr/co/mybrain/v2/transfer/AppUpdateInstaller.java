package kr.co.mybrain.v2.transfer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import kr.co.mybrain.v2.BuildConfig;

/** 선택한 APK가 현재 MyBrain 앱의 안전한 업데이트인지 확인한 뒤 설치 화면을 엽니다. */
public final class AppUpdateInstaller {
    private static final long MAX_APK_BYTES = 300L * 1024L * 1024L;

    private AppUpdateInstaller() {}

    public static UpdateInfo prepare(Context context, Uri source) throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("업데이트 임시 폴더를 만들 수 없습니다.");
        File target = new File(directory, "MyBrain-update.apk");
        copyLimited(context, source, target);

        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(target.getAbsolutePath(), flags);
        if (archive == null || archive.packageName == null) {
            target.delete();
            throw new IllegalArgumentException("Android APK 파일을 읽지 못했습니다.");
        }
        if (!BuildConfig.APPLICATION_ID.equals(archive.packageName)) {
            target.delete();
            throw new IllegalArgumentException("다른 앱의 APK입니다. MyBrain AI 업데이트만 설치할 수 있습니다.");
        }

        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        long currentCode = versionCode(installed);
        long newCode = versionCode(archive);
        if (newCode <= currentCode) {
            target.delete();
            throw new IllegalArgumentException("현재 버전보다 새로운 APK가 아닙니다. 현재 " + currentCode + " / 선택 " + newCode);
        }

        Set<String> currentSigners = signerDigests(installed);
        Set<String> updateSigners = signerDigests(archive);
        if (currentSigners.isEmpty() || updateSigners.isEmpty() || !currentSigners.equals(updateSigners)) {
            target.delete();
            throw new SecurityException("업데이트 서명이 현재 설치 앱과 다릅니다. 같은 고정 서명 Release APK를 선택하세요.");
        }

        String versionName = archive.versionName == null ? "버전 " + newCode : archive.versionName;
        return new UpdateInfo(target, versionName, newCode, updateSigners.iterator().next());
    }

    public static boolean canInstallPackages(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent unknownSourceSettings(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    public static void launchInstall(Context context, UpdateInfo update) {
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                update.apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static void copyLimited(Context context, Uri source, File target) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IllegalArgumentException("선택한 파일을 열 수 없습니다.");
            byte[] buffer = new byte[64 * 1024];
            long total = 0L;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_APK_BYTES) throw new IllegalArgumentException("APK 파일이 허용 크기를 초과했습니다.");
                output.write(buffer, 0, count);
            }
            output.flush();
            if (total < 1024L) throw new IllegalArgumentException("APK 파일이 비어 있거나 손상되었습니다.");
        } catch (Exception error) {
            target.delete();
            throw error;
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
    }

    private static Set<String> signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) return new HashSet<>();
            signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures == null) return result;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            byte[] value = digest.digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte b : value) hex.append(String.format("%02x", b));
            result.add(hex.toString());
        }
        return result;
    }

    public static final class UpdateInfo {
        public final File apkFile;
        public final String versionName;
        public final long versionCode;
        public final String certificateSha256;

        UpdateInfo(File apkFile, String versionName, long versionCode, String certificateSha256) {
            this.apkFile = apkFile;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.certificateSha256 = certificateSha256;
        }
    }
}