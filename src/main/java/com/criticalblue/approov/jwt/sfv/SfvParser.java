package com.criticalblue.approov.jwt.sfv;

import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.BareItem;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.DictionaryEntry;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.InnerList;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.Item;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.SfvValue;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Parser for HTTP Structured Field Values (RFC 8941).
 */
public final class SfvParser {

    private SfvParser() {}

    public static SfvValue parseItem(String input) {
        Parser parser = new Parser(input);
        parser.skipOWS();
        SfvValue value = parser.parseItemOrInnerList();
        parser.skipOWS();
        parser.ensureFullyParsed();
        return value;
    }

    public static List<SfvValue> parseList(String input) {
        Parser parser = new Parser(input);
        List<SfvValue> members = new ArrayList<>();
        parser.skipOWS();
        if (!parser.hasMore()) {
            return members;
        }
        while (true) {
            members.add(parser.parseListMember());
            parser.skipOWS();
            if (!parser.consume(',')) {
                break;
            }
            parser.skipOWS();
            if (!parser.hasMore()) {
                throw parser.error("List cannot end with comma");
            }
        }
        parser.skipOWS();
        parser.ensureFullyParsed();
        return members;
    }

    public static LinkedHashMap<String, DictionaryEntry> parseDictionary(String input) {
        Parser parser = new Parser(input);
        LinkedHashMap<String, DictionaryEntry> dictionary = new LinkedHashMap<>();
        parser.skipOWS();
        if (!parser.hasMore()) {
            return dictionary;
        }
        while (true) {
            String key = parser.parseKey();
            parser.skipOWS();
            boolean omitBooleanValue = false;
            SfvValue value;
            if (parser.consume('=')) {
                parser.skipOWS();
                value = parser.parseDictionaryValue();
            } else {
                LinkedHashMap<String, BareItem> params = parser.parseParameters();
                value = new Item(BareItem.ofBoolean(true), params);
                omitBooleanValue = true;
            }
            dictionary.put(key, new DictionaryEntry(value, omitBooleanValue));
            parser.skipOWS();
            if (!parser.consume(',')) {
                break;
            }
            parser.skipOWS();
            if (!parser.hasMore()) {
                throw parser.error("Dictionary cannot end with comma");
            }
        }
        parser.skipOWS();
        parser.ensureFullyParsed();
        return dictionary;
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            if (input == null) {
                throw new SfvParseException("Input cannot be null");
            }
            this.input = input;
            this.index = 0;
        }

        private boolean hasMore() {
            return index < input.length();
        }

        private char peek() {
            return input.charAt(index);
        }

        private char read() {
            return input.charAt(index++);
        }

        private boolean consume(char ch) {
            if (hasMore() && input.charAt(index) == ch) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char ch) {
            if (!consume(ch)) {
                throw error("Expected '" + ch + "'");
            }
        }

        private void skipOWS() {
            while (hasMore()) {
                char ch = input.charAt(index);
                if (ch == ' ' || ch == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private int skipSpaces() {
            int start = index;
            while (hasMore() && input.charAt(index) == ' ') {
                index++;
            }
            return index - start;
        }

        private void ensureFullyParsed() {
            if (hasMore()) {
                throw error("Unexpected trailing data starting at position " + index);
            }
        }

        private SfvValue parseItemOrInnerList() {
            if (!hasMore()) {
                throw error("Unexpected end of input");
            }
            if (peek() == '(') {
                return parseInnerList();
            }
            BareItem bare = parseBareItem();
            LinkedHashMap<String, BareItem> params = parseParameters();
            return new Item(bare, params);
        }

        private SfvValue parseListMember() {
            if (!hasMore()) {
                throw error("Unexpected end of list");
            }
            if (peek() == '(') {
                return parseInnerList();
            }
            BareItem bareItem = parseBareItem();
            LinkedHashMap<String, BareItem> params = parseParameters();
            return new Item(bareItem, params);
        }

        private SfvValue parseDictionaryValue() {
            if (!hasMore()) {
                throw error("Unexpected end of dictionary value");
            }
            if (peek() == '(') {
                return parseInnerList();
            }
            BareItem bareItem = parseBareItem();
            LinkedHashMap<String, BareItem> params = parseParameters();
            return new Item(bareItem, params);
        }

        private InnerList parseInnerList() {
            expect('(');
            skipOWS();
            List<Item> items = new ArrayList<>();
            if (consume(')')) {
                LinkedHashMap<String, BareItem> params = parseParameters();
                return new InnerList(items, params);
            }
            while (true) {
                BareItem bareItem = parseBareItem();
                LinkedHashMap<String, BareItem> params = parseParameters();
                items.add(new Item(bareItem, params));
                int spacesConsumed = skipSpaces();
                if (!hasMore()) {
                    throw error("Unterminated inner list");
                }
                if (peek() == ')') {
                    break;
                }
                if (spacesConsumed == 0) {
                    throw error("Inner list members must be separated by a single SP");
                }
            }
            expect(')');
            LinkedHashMap<String, BareItem> params = parseParameters();
            return new InnerList(items, params);
        }

        private LinkedHashMap<String, BareItem> parseParameters() {
            LinkedHashMap<String, BareItem> params = new LinkedHashMap<>();
            while (hasMore() && peek() == ';') {
                read(); // consume ';'
                String key = parseKey();
                BareItem value;
                if (hasMore() && peek() == '=') {
                    read();
                    value = parseBareItem();
                } else {
                    value = BareItem.ofBoolean(true);
                }
                params.put(key, value);
            }
            return params;
        }

        private String parseKey() {
            if (!hasMore()) {
                throw error("Expected key but reached end of input");
            }
            char first = peek();
            if (!isKeyFirstChar(first)) {
                throw error("Invalid key character '" + first + "'");
            }
            int start = index;
            index++;
            while (hasMore() && isKeyChar(peek())) {
                index++;
            }
            return input.substring(start, index);
        }

        private boolean isKeyFirstChar(char ch) {
            return (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || ch == '*'
                    || (ch >= '0' && ch <= '9');
        }

        private boolean isKeyChar(char ch) {
            return isKeyFirstChar(ch) || ch == '_' || ch == '-' || ch == '.';
        }

        private BareItem parseBareItem() {
            if (!hasMore()) {
                throw error("Unexpected end of bare item");
            }
            char ch = peek();
            if (ch == '?') {
                return parseBoolean();
            }
            if (ch == '"') {
                return parseString();
            }
            if (ch == '%') {
                return parseDisplayString();
            }
            if (ch == ':') {
                return parseByteSequence();
            }
            if (ch == '@') {
                return parseDate();
            }
            if (ch == '-' || isDigit(ch)) {
                return parseNumber();
            }
            if (isTokenStart(ch)) {
                return parseToken();
            }
            throw error("Invalid bare item start '" + ch + "'");
        }

        private BareItem parseBoolean() {
            expect('?');
            if (!hasMore()) {
                throw error("Incomplete boolean value");
            }
            char value = read();
            if (value == '1') {
                return BareItem.ofBoolean(true);
            }
            if (value == '0') {
                return BareItem.ofBoolean(false);
            }
            throw error("Invalid boolean value '?" + value + "'");
        }

        private BareItem parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (hasMore()) {
                char ch = read();
                if (ch == '"') {
                    return BareItem.ofString(builder.toString());
                }
                if (ch == '\\') {
                    if (!hasMore()) {
                        throw error("Incomplete escape sequence in string");
                    }
                    char escaped = read();
                    if (escaped != '"' && escaped != '\\') {
                        throw error("Invalid escape '" + escaped + "' in string");
                    }
                    builder.append(escaped);
                } else {
                    if (ch < 0x20 || ch > 0x7E) {
                        throw error("Invalid character in string value");
                    }
                    builder.append(ch);
                }
            }
            throw error("Unterminated string value");
        }

        private BareItem parseDisplayString() {
            expect('%');
            expect('"');
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            StringBuilder encodedBuilder = new StringBuilder();
            while (hasMore()) {
                char ch = read();
                if (ch == '"') {
                    String payload = encodedBuilder.toString();
                    String decodedString = new String(decoded.toByteArray(), StandardCharsets.UTF_8);
                    return BareItem.ofDisplayString(decodedString, payload);
                }
                if (ch == '%') {
                    if (!hasMore()) {
                        throw error("Incomplete percent-encoded sequence");
                    }
                    char h1 = read();
                    if (!hasMore()) {
                        throw error("Incomplete percent-encoded sequence");
                    }
                    char h2 = read();
                    int value = decodeHexPair(h1, h2);
                    decoded.write(value);
                    encodedBuilder.append('%').append(h1).append(h2);
                } else {
                    if (ch < 0x20 || ch > 0x7E) {
                        throw error("Invalid character in display string");
                    }
                    decoded.write((byte) ch);
                    encodedBuilder.append(ch);
                }
            }
            throw error("Unterminated display string");
        }

        private int decodeHexPair(char h1, char h2) {
            int high = Character.digit(h1, 16);
            int low = Character.digit(h2, 16);
            if (high == -1 || low == -1) {
                throw error("Invalid hex digits in percent-encoding");
            }
            return (high << 4) + low;
        }

        private BareItem parseByteSequence() {
            expect(':');
            int start = index;
            while (hasMore() && peek() != ':') {
                char ch = read();
                if (!isBase64Char(ch)) {
                    throw error("Invalid character in byte sequence");
                }
            }
            if (!hasMore()) {
                throw error("Byte sequence missing closing ':'");
            }
            String payload = input.substring(start, index);
            expect(':');
            try {
                byte[] data = Base64.getDecoder().decode(payload);
                return BareItem.ofByteSequence(data);
            } catch (IllegalArgumentException e) {
                throw error("Invalid base64 content in byte sequence");
            }
        }

        private boolean isBase64Char(char ch) {
            return (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '+'
                    || ch == '/'
                    || ch == '=';
        }

        private BareItem parseDate() {
            expect('@');
            String number = readNumberLiteral();
            long value = parseLong(number);
            return BareItem.ofDate(value);
        }

        private BareItem parseNumber() {
            int start = index;
            if (peek() == '-') {
                read();
            }
            int digits = readDigits();
            if (digits == 0) {
                throw error("Bare item number must have at least one digit");
            }
            boolean isDecimal = false;
            if (hasMore() && peek() == '.') {
                isDecimal = true;
                read();
                int fracDigits = readDigits();
                if (fracDigits < 1 || fracDigits > 3) {
                    throw error("Decimal must have 1 to 3 fractional digits");
                }
            }
            String literal = input.substring(start, index);
            if (isDecimal) {
                BigDecimal decimal = new BigDecimal(literal).setScale(3, RoundingMode.UNNECESSARY);
                long scaled = decimal.movePointRight(3).longValueExact();
                return BareItem.ofDecimal(scaled);
            }
            long value = parseLong(literal);
            if (Math.abs(value) > 999_999_999_999_999L) {
                throw error("Integer bare item out of range");
            }
            return BareItem.ofInteger(value);
        }

        private String readNumberLiteral() {
            int start = index;
            if (!hasMore()) {
                throw error("Number literal missing digits");
            }
            if (peek() == '-') {
                read();
            }
            int digits = readDigits();
            if (digits == 0) {
                throw error("Number literal missing digits");
            }
            return input.substring(start, index);
        }

        private int readDigits() {
            int start = index;
            while (hasMore() && isDigit(peek())) {
                index++;
            }
            return index - start;
        }

        private long parseLong(String literal) {
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException ex) {
                throw error("Invalid numeric literal '" + literal + "'");
            }
        }

        private BareItem parseToken() {
            int start = index;
            index++; // consume first char
            while (hasMore() && isTokenChar(peek())) {
                index++;
            }
            return BareItem.ofToken(input.substring(start, index));
        }

        private boolean isTokenStart(char ch) {
            return (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || ch == '*';
        }

        private boolean isTokenChar(char ch) {
            return isTokenStart(ch)
                    || (ch >= '0' && ch <= '9')
                    || ch == ':'
                    || ch == '/'
                    || ch == '_'
                    || ch == '-'
                    || ch == '.'
                    || ch == '#'
                    || ch == '$'
                    || ch == '%'
                    || ch == '&'
                    || ch == '\''
                    || ch == '+'
                    || ch == '^'
                    || ch == '`'
                    || ch == '|'
                    || ch == '~'
                    || ch == '!';
        }

        private boolean isDigit(char ch) {
            return ch >= '0' && ch <= '9';
        }

        private SfvParseException error(String message) {
            return new SfvParseException(message + " at position " + index);
        }
    }
}
