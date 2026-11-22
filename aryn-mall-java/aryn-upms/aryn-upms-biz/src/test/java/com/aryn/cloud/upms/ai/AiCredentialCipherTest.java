package com.aryn.cloud.upms.ai;
import com.aryn.cloud.upms.config.AiCredentialProperties;
import com.aryn.cloud.upms.support.AiCredentialCipher;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AiCredentialCipherTest {
    AiCredentialProperties properties() {
        var p=new AiCredentialProperties(); p.setKeyId("key1");
        p.setKeys(Map.of("key1",Base64.getEncoder().encodeToString(new byte[32]),"key2",Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes()))); return p;
    }
    @Test void authenticatedEncryptionUsesRandomNonceAndBoundIdentity() {
        var c=new AiCredentialCipher(properties());
        String first=c.encrypt("real-api-secret","tenant","provider","credential","AI_PROVIDER");
        String second=c.encrypt("real-api-secret","tenant","provider","credential","AI_PROVIDER");
        assertNotEquals(first,second); assertFalse(first.contains("real-api-secret"));
        assertEquals("real-api-secret",c.decrypt(first,"tenant","provider","credential","AI_PROVIDER"));
        for (String[] identity:List.of(new String[]{"other","provider","credential","AI_PROVIDER"},new String[]{"tenant","other","credential","AI_PROVIDER"},new String[]{"tenant","provider","old","AI_PROVIDER"},new String[]{"tenant","provider","credential","OTHER"}))
            assertThrows(IllegalStateException.class,()->c.decrypt(first,identity[0],identity[1],identity[2],identity[3]));
        assertThrows(IllegalStateException.class,()->c.decrypt(first.substring(0,first.length()-5)+"AAAAA","tenant","provider","credential","AI_PROVIDER"));
    }
    @Test void rotationCanReadOldKeyAndMissingKeyDoesNotBreakConstruction() {
        var p=properties(); var c=new AiCredentialCipher(p);
        String old=c.encrypt("secret","t","p","v","AI_PROVIDER");
        p.setKeyId("key2"); assertEquals("secret",c.decrypt(old,"t","p","v","AI_PROVIDER"));
        assertTrue(c.encrypt("secret","t","p","v2","AI_PROVIDER").startsWith("key2."));
        var missing=new AiCredentialCipher(new AiCredentialProperties());
        assertThrows(IllegalStateException.class,()->missing.encrypt("s","t","p","v","AI_PROVIDER"));
    }
    @Test void secretOperationsRejectMaskAndAmbiguousInputs() {
        assertThrows(IllegalArgumentException.class,()->AiCredentialCipher.validateReplacement("******"));
        assertThrows(IllegalArgumentException.class,()->AiCredentialCipher.validateReplacement(""));
        assertThrows(IllegalArgumentException.class,()->AiCredentialCipher.validateReplacement("a\r\nb"));
        assertDoesNotThrow(()->AiCredentialCipher.validateReplacement("sk-real-key"));
    }
}
