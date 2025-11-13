package com.criticalblue.approov.jwt.sfv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simple data model for HTTP Structured Field Values (RFC 8941).
 */
public final class StructuredFieldValues {

    private StructuredFieldValues() {}

    public enum BareItemType {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        STRING,
        TOKEN,
        BYTE_SEQUENCE,
        DATE,
        DISPLAY_STRING
    }

    public static final class DecimalValue {
        private final long scaledValue;

        public DecimalValue(long scaledValue) {
            this.scaledValue = scaledValue;
        }

        public long getScaledValue() {
            return scaledValue;
        }
    }

    public static final class DisplayStringValue {
        private final String decoded;
        private final String encodedPayload;

        public DisplayStringValue(String decoded, String encodedPayload) {
            this.decoded = decoded;
            this.encodedPayload = encodedPayload;
        }

        public String getDecoded() {
            return decoded;
        }

        public String getEncodedPayload() {
            return encodedPayload;
        }
    }

    public static final class BareItem {
        private final BareItemType type;
        private final Object value;

        private BareItem(BareItemType type, Object value) {
            this.type = type;
            this.value = value;
        }

        public static BareItem ofBoolean(boolean value) {
            return new BareItem(BareItemType.BOOLEAN, value);
        }

        public static BareItem ofInteger(long value) {
            return new BareItem(BareItemType.INTEGER, value);
        }

        public static BareItem ofDecimal(long scaledValue) {
            return new BareItem(BareItemType.DECIMAL, new DecimalValue(scaledValue));
        }

        public static BareItem ofString(String value) {
            return new BareItem(BareItemType.STRING, value);
        }

        public static BareItem ofToken(String value) {
            return new BareItem(BareItemType.TOKEN, value);
        }

        public static BareItem ofByteSequence(byte[] data) {
            return new BareItem(BareItemType.BYTE_SEQUENCE, data.clone());
        }

        public static BareItem ofDate(long epochSeconds) {
            return new BareItem(BareItemType.DATE, epochSeconds);
        }

        public static BareItem ofDisplayString(String decoded, String encodedPayload) {
            return new BareItem(
                    BareItemType.DISPLAY_STRING,
                    new DisplayStringValue(decoded, encodedPayload));
        }

        public BareItemType getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }

        public boolean isBooleanTrue() {
            return type == BareItemType.BOOLEAN && Boolean.TRUE.equals(value);
        }
    }

    public interface SfvValue {}

    public static final class Item implements SfvValue {
        private final BareItem bareItem;
        private final LinkedHashMap<String, BareItem> parameters;

        public Item(BareItem bareItem, LinkedHashMap<String, BareItem> parameters) {
            this.bareItem = Objects.requireNonNull(bareItem, "bareItem");
            this.parameters = parameters == null ? new LinkedHashMap<>() : parameters;
        }

        public BareItem getBareItem() {
            return bareItem;
        }

        public Map<String, BareItem> getParameters() {
            return Collections.unmodifiableMap(parameters);
        }

        public LinkedHashMap<String, BareItem> getMutableParameters() {
            return parameters;
        }
    }

    public static final class InnerList implements SfvValue {
        private final List<Item> items;
        private final LinkedHashMap<String, BareItem> parameters;

        public InnerList(List<Item> items, LinkedHashMap<String, BareItem> parameters) {
            this.items = items == null ? new ArrayList<>() : items;
            this.parameters = parameters == null ? new LinkedHashMap<>() : parameters;
        }

        public List<Item> getItems() {
            return Collections.unmodifiableList(items);
        }

        public LinkedHashMap<String, BareItem> getMutableParameters() {
            return parameters;
        }

        public Map<String, BareItem> getParameters() {
            return Collections.unmodifiableMap(parameters);
        }
    }

    public static final class DictionaryEntry {
        private final SfvValue value;
        private final boolean omitBooleanValue;

        public DictionaryEntry(SfvValue value, boolean omitBooleanValue) {
            this.value = Objects.requireNonNull(value, "value");
            this.omitBooleanValue = omitBooleanValue;
        }

        public SfvValue getValue() {
            return value;
        }

        public boolean isOmitBooleanValue() {
            return omitBooleanValue;
        }
    }
}
