package kr.co.mybrain.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * GPT와 Gemini API 키를 Android Keystore 기반 AES-GCM 방식으로 암호화합니다.
 * 평문 API 키는 SharedPreferences나 소스 코드에 저장하지 않습니다.
 */
public final class SecureApiKeyStore {
    public static final String KEY_OPENAI = "openai_api_key";
    public static final String KEY_GEMINI = "gemini_api_key";

    private static final String PREFS = "mybrain_ai_secure";
    private static final String KEY_ALIAS = "mybrain_ai_api_key_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private SecureApiKeyStore() {
    }

    /** API 키를 암호화하여 저장합니다. 빈 문자열은 저장하지 않습니다. */
    public static void save(Context context, String name, String plainText) throws Exception {
        String value = plainText == null ? "" : plainText.trim();
        if (value.isEmpty()) return;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(name + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        editor.putString(name + "_data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        editor.apply();
    }

    /** 저장된 API 키를 복호화합니다. 복구할 수 없는 경우 빈 문자열을 반환합니다. */
    public static String read(Context context, String name) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String ivText = prefs.getString(name + "_iv", "");
            String dataText = prefs.getString(name + "_data", "");
            if (ivText == null || ivText.isEmpty() || dataText == null || dataText.isEmpty()) return "";

            byte[] iv = Base64.decode(ivText, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(dataText, Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 기기 복원 등으로 Keystore 키가 달라진 경우 오래된 암호문은 사용하지 않습니다.
            return "";
        }
    }

    public static boolean has(Context context, String name) {
        return !read(context, name).isEmpty();
    }

    /** 선택한 공급자의 저장된 API 키만 삭제합니다. */
    public static void clear(Context context, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(name + "_iv")
                .remove(name + "_data")
                .apply();
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
