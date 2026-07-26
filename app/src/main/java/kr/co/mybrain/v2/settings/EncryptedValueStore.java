package kr.co.mybrain.v2.settings;

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

public final class EncryptedValueStore {
    public static final String OPENAI_CREDENTIAL = "openai_credential";
    public static final String GEMINI_CREDENTIAL = "gemini_credential";

    private static final String PREFS = "mybrain_v2_secure_values";
    private static final String KEY_ALIAS = "mybrain_v2_cloud_value_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private EncryptedValueStore() {}

    public static void save(Context context, String name, String plainText) throws Exception {
        String value = plainText == null ? "" : plainText.trim();
        if (value.isEmpty()) return;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(name + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(name + "_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    public static String read(Context context, String name) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String ivText = prefs.getString(name + "_iv", "");
            String dataText = prefs.getString(name + "_data", "");
            if (ivText == null || ivText.isEmpty() || dataText == null || dataText.isEmpty()) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(),
                    new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean has(Context context, String name) {
        return !read(context, name).isEmpty();
    }

    public static void clear(Context context, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(name + "_iv")
                .remove(name + "_data")
                .apply();
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
