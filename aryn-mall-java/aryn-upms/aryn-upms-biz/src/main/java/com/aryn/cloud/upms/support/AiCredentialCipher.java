package com.aryn.cloud.upms.support;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public final class AiCredentialCipher {
    private final AiCredentialProperties properties;
    private final SecureRandom random=new SecureRandom();
    public AiCredentialCipher(AiCredentialProperties properties) { this.properties=properties; }
    public String encrypt(String plaintext,String tenant,String provider,String version,String purpose) {
        validateReplacement(plaintext);
        try {
            String id=properties.getKeyId(); byte[] nonce=new byte[12]; random.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(id),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(tenant,provider,version,purpose));
            return id+"."+Base64.getEncoder().encodeToString(nonce)+"."+Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("AI 主密钥未配置或加密失败"); }
    }
    public String decrypt(String encrypted,String tenant,String provider,String version,String purpose) {
        try {
            String[] parts=encrypted.split("\\.",-1);
            if (parts.length!=3) throw new IllegalArgumentException();
            byte[] nonce=Base64.getDecoder().decode(parts[1]); if(nonce.length!=12) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(parts[0]),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(tenant,provider,version,purpose));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])),StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("AI 凭证认证失败或主密钥不可用"); }
    }
    private SecretKeySpec key(String id) {
        if(id==null || !id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalStateException();
        String encoded=properties.getKeys().get(id);
        byte[] key=encoded==null?new byte[0]:Base64.getDecoder().decode(encoded);
        if(key.length!=32) throw new IllegalStateException(); return new SecretKeySpec(key,"AES");
    }
    private byte[] aad(String... fields) {
        StringBuilder value=new StringBuilder();
        for(String field:fields) { if(field==null || field.isBlank()) throw new IllegalArgumentException(); value.append(field.length()).append(':').append(field); }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static void validateReplacement(String value) {
        if(value==null || value.isBlank() || value.length()>4096 || value.contains("*") || value.indexOf('\n')>=0 || value.indexOf('\r')>=0) throw new IllegalArgumentException("请输入新的完整 API Key，不能回写掩码");
    }
}
