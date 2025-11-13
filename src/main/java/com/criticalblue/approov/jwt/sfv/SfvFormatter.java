package com.criticalblue.approov.jwt.sfv;

import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.BareItem;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.BareItemType;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.DecimalValue;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.DictionaryEntry;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.DisplayStringValue;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.InnerList;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.Item;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.SfvValue;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formatter for HTTP Structured Field Values (RFC 8941).
 */
public final class SfvFormatter {

    private SfvFormatter() {}

    public static String serializeItem(SfvValue value) {
        if (value instanceof InnerList) {
            return serializeInnerList((InnerList) value);
        }
        if (value instanceof Item) {
            return serializeItemValue((Item) value);
        }
        throw new IllegalArgumentException("Unsupported value type: " + value);
    }

    public static String serializeList(List<SfvValue> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(serializeValue(values.get(i)));
        }
        return builder.toString();
    }

    public static String serializeDictionary(LinkedHashMap<String, DictionaryEntry> dictionary) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, DictionaryEntry> entry : dictionary.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey());
            DictionaryEntry dictionaryEntry = entry.getValue();
            SfvValue value = dictionaryEntry.getValue();
            if (dictionaryEntry.isOmitBooleanValue()) {
                if (!(value instanceof Item)) {
                    throw new IllegalArgumentException(
                            "Dictionary entry without explicit value must be an item");
                }
                Item item = (Item) value;
                if (!item.getBareItem().isBooleanTrue()) {
                    throw new IllegalArgumentException(
                            "Dictionary entry marked as boolean true but actual value differs");
                }
                builder.append(serializeParameters(item.getParameters()));
            } else {
                builder.append('=');
                builder.append(serializeValue(value));
            }
        }
        return builder.toString();
    }

    private static String serializeValue(SfvValue value) {
        if (value instanceof InnerList) {
            return serializeInnerList((InnerList) value);
        }
        if (value instanceof Item) {
            return serializeItemValue((Item) value);
        }
        throw new IllegalArgumentException("Unsupported value type: " + value);
    }

    private static String serializeItemValue(Item item) {
        return serializeBareItem(item.getBareItem()) + serializeParameters(item.getParameters());
    }

    private static String serializeInnerList(InnerList innerList) {
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        List<Item> items = innerList.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(serializeItemValue(items.get(i)));
        }
        builder.append(')');
        builder.append(serializeParameters(innerList.getParameters()));
        return builder.toString();
    }

    private static String serializeParameters(Map<String, BareItem> params) {
        if (params.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, BareItem> entry : params.entrySet()) {
            builder.append(';').append(entry.getKey());
            BareItem value = entry.getValue();
            if (value != null && value.getType() == BareItemType.BOOLEAN && value.isBooleanTrue()) {
                continue;
            }
            builder.append('=').append(serializeBareItem(value));
        }
        return builder.toString();
    }

    private static String serializeBareItem(BareItem item) {
        BareItemType type = item.getType();
        switch (type) {
            case BOOLEAN:
                return (Boolean) item.getValue() ? "?1" : "?0";
            case INTEGER:
                return Long.toString((Long) item.getValue());
            case DECIMAL:
                return formatDecimal(((DecimalValue) item.getValue()).getScaledValue());
            case STRING:
                return escapeString((String) item.getValue());
            case TOKEN:
                return (String) item.getValue();
            case BYTE_SEQUENCE:
                return ':' + Base64.getEncoder().encodeToString((byte[]) item.getValue()) + ':';
            case DATE:
                return '@' + Long.toString((Long) item.getValue());
            case DISPLAY_STRING:
                DisplayStringValue display = (DisplayStringValue) item.getValue();
                return "%\"" + display.getEncodedPayload() + "\"";
            default:
                throw new IllegalStateException("Unknown bare item type: " + type);
        }
    }

    private static String escapeString(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (char ch : value.toCharArray()) {
            if (ch == '"' || ch == '\\') {
                builder.append('\\');
            }
            builder.append(ch);
        }
        builder.append('"');
        return builder.toString();
    }

    private static String formatDecimal(long scaled) {
        boolean negative = scaled < 0;
        long abs = Math.abs(scaled);
        long integral = abs / 1000;
        int fractional = (int) (abs % 1000);
        if (fractional == 0) {
            return (negative ? "-" : "") + integral;
        }
        String fraction = String.format("%03d", fractional);
        int end = fraction.length();
        while (end > 1 && fraction.charAt(end - 1) == '0') {
            end--;
        }
        String trimmed = fraction.substring(0, end);
        return (negative ? "-" : "") + integral + '.' + trimmed;
    }
}
