package com.example.gutapp.session;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

public class CryptoUtility {

    public static final String PREF_NAME = "gut_session_prefs";
    public static final String KEY_USER = "saved_username";
    public static final String KEY_PASS = "saved_password";

    public static class CryptoContext{
        //initialized throughout the handshake process,
        // when the hand shake is finished the ctx is fully initialized and all variable are usable
        public KeyPair keyPair;
        public SecretKey aesKey;
        public long sendNonce;
        public long recvNonce;

        public CryptoContext(){
            this.sendNonce = 0;
            this.recvNonce = 0;
        }

        public byte[] getNextSendNonce() {
            // GCM standard IV is 12 bytes.
            // We put the 8-byte long into the end of a 12-byte array.
            return ByteBuffer.allocate(12).putLong(4, sendNonce++).array();
        }

        public byte[] getNextRecvNonce() {
            return ByteBuffer.allocate(12).putLong(4, recvNonce++).array();
        }
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048, new SecureRandom());
        return keyPairGen.generateKeyPair();
    }

    //foramts the rsa key according to server expectations
    public static String convertToPEM(byte[] publicKeyBytes) {
        // 1. Encode the raw DER bytes to Base64 string
        // Use NO_WRAP to keep it on one line, or CRLF if your server is strict
        String base64Key = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);

        // 2. Wrap with the standard OpenSSL headers
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64Key +
                "\n-----END PUBLIC KEY-----";
    }

    public static byte[] decryptRSA(byte[] encrypted, PrivateKey rsaPrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-1",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
        );
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey, oaepParams);
        return cipher.doFinal(encrypted);
    }

    public static SecretKey bytesToAESKey(byte[] keyBytes) {
        // Validation: AES-256 requires exactly 32 bytes (256 bits)
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("Key length must be 32 bytes for AES-256");
        }

        // Arguments: (the bytes, the name of the algorithm)
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static byte[] encryptAESECB(String data, SecretKey key) throws Exception {
        // 1. Initialize the Cipher for AES in ECB mode with PKCS5 Padding
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

        // 2. Set to Encrypt mode with the SecretKey you generated earlier
        cipher.init(Cipher.ENCRYPT_MODE, key);

        // 3. Convert String to bytes and encrypt
        byte[] plaintext = data.getBytes(StandardCharsets.US_ASCII);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptAESGCM(byte[] encryptedPayload, CryptoContext ctx) throws Exception {

        //Prepare the 12-byte IV from the current recv_nonce
        byte[] recv_nonce = ctx.getNextRecvNonce();


        // 2. Initialize Cipher (GCM/NoPadding)
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // 128-bit authentication tag length
        GCMParameterSpec spec = new GCMParameterSpec(128, recv_nonce);

        SecretKey key = ctx.aesKey;
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        Log.i(NETWORK_LOG_TAG, "decrypted with nonce: " + (ctx.recvNonce-1));
        // 3. Decrypt and verify tag
        return cipher.doFinal(encryptedPayload);
    }

    public static byte[] encryptAESGCM(byte[] payload, CryptoContext ctx) throws Exception {
        byte[] send_nonce = ctx.getNextSendNonce();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // 128-bit authentication tag length
        GCMParameterSpec spec = new GCMParameterSpec(128, send_nonce);
        SecretKey key = ctx.aesKey;
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        Log.i(NETWORK_LOG_TAG, "encrypted with nonce: " + (ctx.sendNonce-1));
        return cipher.doFinal(payload);
    }

    public static SharedPreferences getVault(Context appContext) {
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    appContext,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(NETWORK_LOG_TAG, "failed to access the shared preference");
            return null; // Vault failed to open
        }
    }
}
