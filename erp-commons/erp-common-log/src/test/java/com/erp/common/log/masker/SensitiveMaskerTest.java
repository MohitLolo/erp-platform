package com.erp.common.log.masker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveMaskerTest {

    private SensitiveMasker masker;

    @BeforeEach
    void setUp() {
        masker = new SensitiveMasker(new ObjectMapper());
    }

    @Test
    void registry_caseInsensitive_andCamelCaseSubstring() {
        assertTrue(SensitiveFieldRegistry.isSensitive("password"));
        assertTrue(SensitiveFieldRegistry.isSensitive("Password"));
        assertTrue(SensitiveFieldRegistry.isSensitive("PASSWD"));
        assertTrue(SensitiveFieldRegistry.isSensitive("pwd"));
        assertTrue(SensitiveFieldRegistry.isSensitive("userPassword"));
        assertTrue(SensitiveFieldRegistry.isSensitive("idCard"));
        assertTrue(SensitiveFieldRegistry.isSensitive("bankCardNo"));
        assertFalse(SensitiveFieldRegistry.isSensitive("username"));
        assertFalse(SensitiveFieldRegistry.isSensitive("name"));
        assertFalse(SensitiveFieldRegistry.isSensitive(null));
        assertFalse(SensitiveFieldRegistry.isSensitive(""));
    }

    @Test
    void mask_flatMap() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("username", "alice");
        input.put("password", "p@ssw0rd");
        input.put("idCard", "110101199001011234");

        JsonNode out = masker.mask(input);

        assertEquals("alice", out.get("username").asText());
        assertEquals(SensitiveMasker.MASK, out.get("password").asText());
        assertEquals(SensitiveMasker.MASK, out.get("idCard").asText());
    }

    @Test
    void mask_nestedPojo() {
        Outer input = new Outer();
        input.username = "bob";
        input.token = "eyJhbGc...";
        input.profile = new Inner();
        input.profile.bankCardNo = "6222021234567890";
        input.profile.nickname = "Bobby";

        JsonNode out = masker.mask(input);

        assertEquals("bob", out.get("username").asText());
        assertEquals(SensitiveMasker.MASK, out.get("token").asText());
        assertEquals("Bobby", out.get("profile").get("nickname").asText());
        assertEquals(SensitiveMasker.MASK, out.get("profile").get("bankCardNo").asText());
    }

    @Test
    void mask_listOfMaps() {
        Map<String, Object> a = Map.of("name", "x", "secret", "s1");
        Map<String, Object> b = Map.of("name", "y", "secret", "s2");
        JsonNode out = masker.mask(List.of(a, b));

        assertTrue(out.isArray());
        assertEquals(2, out.size());
        assertEquals("x", out.get(0).get("name").asText());
        assertEquals(SensitiveMasker.MASK, out.get(0).get("secret").asText());
        assertEquals(SensitiveMasker.MASK, out.get(1).get("secret").asText());
    }

    @Test
    void maskToJson_handlesNullAndProducesValidJson() {
        assertNull(masker.maskToJson(null));

        String json = masker.maskToJson(Map.of("apiKey", "abc", "ok", 1));
        assertTrue(json.contains("\"apiKey\":\"***\""));
        assertTrue(json.contains("\"ok\":1"));
    }

    @Test
    void mask_thousandRecords_under100ms() {
        ObjectMapper om = new ObjectMapper();
        SensitiveMasker m = new SensitiveMasker(om);

        Map<String, Object>[] warmup = buildSampleRecords(500);
        for (Map<String, Object> r : warmup) {
            m.maskToJson(r);
        }

        Map<String, Object>[] records = buildSampleRecords(1000);
        long start = System.nanoTime();
        for (Map<String, Object> r : records) {
            m.maskToJson(r);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs < 100, "1000 records (after warmup) took " + elapsedMs + " ms (>= 100ms threshold)");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object>[] buildSampleRecords(int n) {
        Map<String, Object>[] arr = new HashMap[n];
        for (int i = 0; i < n; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", i);
            r.put("username", "u" + i);
            r.put("password", "secret-" + i);
            r.put("nickname", "nick" + i);
            r.put("idCard", "110101200001010000");
            r.put("amount", i * 100);
            r.put("token", "t-" + i);
            r.put("note", "ok");
            arr[i] = r;
        }
        return arr;
    }

    @Data
    static class Outer {
        String username;
        String token;
        Inner profile;
    }

    @Data
    static class Inner {
        String nickname;
        String bankCardNo;
    }
}
