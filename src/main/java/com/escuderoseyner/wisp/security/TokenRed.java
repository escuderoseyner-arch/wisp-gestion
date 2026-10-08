package com.escuderoseyner.wisp.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

// Tokens con los que cada MikroTik se identifica (header X-Red-Token).
// En la base de datos solo se guarda su SHA-256: si alguien roba la base, no puede hacerse pasar por el router.
@Component
public class TokenRed {

    // Solo caracteres que no rompen un texto de RouterOS ni el header http-header-field
    // (nada de comillas, $, \, comas ni espacios).
    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9._~+/=-]{20,128}$");
    private static final int BYTES_ALEATORIOS = 32; // 256 bits

    private final SecureRandom random = new SecureRandom();

    // 43 caracteres en Base64 URL (letras, números, - y _)
    public String generar() {
        byte[] bytes = new byte[BYTES_ALEATORIOS];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean formatoValido(String token) {
        return token != null && FORMATO.matcher(token).matches();
    }

    // SHA-256 en hexadecimal (64 caracteres), igual que en tokens_recuperacion
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
