package com.erp.common.log.masker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * 敏感字段脱敏器：递归把命中 {@link SensitiveFieldRegistry} 的字段值替换为 {@code "***"}。
 *
 * <p>实现策略：先用 Jackson 把任意 POJO / Map / Collection 转成 {@link JsonNode} 树，
 * 再原地遍历改写命中字段，最后序列化回 JSON 字符串。这样可以一次处理嵌套对象、数组、Map，
 * 而无需对各种容器类型单独写反射。</p>
 *
 * <p>性能基准：1000 条扁平 Map（每条 8 字段）脱敏耗时 &lt; 100ms（设计文档 §4.3 验收）。</p>
 */
public class SensitiveMasker {

    public static final String MASK = "***";

    private final ObjectMapper objectMapper;

    public SensitiveMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把任意对象脱敏后序列化为 JSON 字符串。
     * 入参为 null 时返回 null；序列化失败回退到 {@code String.valueOf(input)} 避免抛到切面外。
     */
    public String maskToJson(Object input) {
        if (input == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.valueToTree(input);
            maskInPlace(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return String.valueOf(input);
        }
    }

    /**
     * 仅做脱敏，返回 {@link JsonNode}（便于上层组合再处理）。
     */
    public JsonNode mask(Object input) {
        if (input == null) {
            return null;
        }
        JsonNode root = objectMapper.valueToTree(input);
        maskInPlace(root);
        return root;
    }

    private void maskInPlace(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return;
        }
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (SensitiveFieldRegistry.isSensitive(entry.getKey())) {
                    obj.put(entry.getKey(), MASK);
                } else {
                    maskInPlace(entry.getValue());
                }
            }
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode child : arr) {
                maskInPlace(child);
            }
        }
    }
}
