package kr.co.mybrain.v2.transfer;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** 사용자 비밀번호로 백업 JSON 전체를 AES-256-GCM 암호화합니다. */
public final class BackupCrypto {
    private static final String MAGIC = "MYBRAIN_BACKUP";
    private static final int FORMAT_VERSION = 1;
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int MAX_BACKUP_BYTES = 25 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCrypto() {}

    public static void encryptToStream(String plainJson, char[] password, OutputStream output) throws Exception {
        validatePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);
        byte[] key = derive(password, salt, ITERATIONS);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD((MAGIC + ":" + FORMAT_VERSION).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal((plainJson == null ? "{}" : plainJson).getBytes(StandardCharsets.UTF_8));
            JSONObject envelope = new JSONObject()
                    .put("format", MAGIC)
                    .put("version", FORMAT_VERSION)
                    .put("kdf", "PBKDF2WithHmacSHA256")
                    .put("iterations", ITERATIONS)
                    .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .put("cipher", "AES-256-GCM")
                    .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            output.write(envelope.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    public static String decryptFromStream(InputStream input, char[] password) throws Exception {
        validatePassword(password);
        byte[] raw = readLimited(input);
        JSONObject envelope = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        Arrays.fill(raw, (byte) 0);
        if (!MAGIC.equals(envelope.optString("format"))) {
            throw new IllegalArgumentException("MyBrain 백업 파일이 아닙니다.");
        }
        int version = envelope.optInt("version", -1);
        if (version != FORMAT_VERSION) throw new IllegalArgumentException("지원하지 않는 백업 형식입니다.");
        int iterations = envelope.optInt("iterations", 0);
        if (iterations < 100_000 || iterations > 1_000_000) throw new IllegalArgumentException("백업 암호화 설정이 올바르지 않습니다.");
        byte[] salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP);
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(envelope.getString("data"), Base64.NO_WRAP);
        if (salt.length != SALT_BYTES || iv.length != IV_BYTES || encrypted.length == 0) {
            throw new IllegalArgumentException("백업 파일이 손상되었습니다.");
        }
        byte[] key = derive(password, salt, iterations);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD((MAGIC + ":" + version).getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(encrypted);
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (javax.crypto.AEADBadTagException error) {
            throw new IllegalArgumentException("비밀번호가 다르거나 백업 파일이 손상되었습니다.");
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 6) {
            throw new IllegalArgumentException("백업 비밀번호는 6자 이상이어야 합니다.");
        }
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        if (input == null) throw new IllegalArgumentException("백업 파일을 열 수 없습니다.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BACKUP_BYTES) throw new IllegalArgumentException("백업 파일이 허용 크기를 초과했습니다.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}