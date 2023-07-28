/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sshtools.jini;

import com.sshtools.jini.INI.AbstractIO;
import com.sshtools.jini.INI.AbstractIOBuilder;
import com.sshtools.jini.INI.LinkedCaseInsensitiveMap;
import com.sshtools.jini.INI.Section;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * An {@INIReader} can read text (from files, streams or strings) in the INI
 * format to produce an {@link INI} object instance.
 * <p>
 * This class should not be directly created, instead use
 * {@link INIReader.Builder} and configure it accordingly before calling
 * {@link INIReader.Builder#build()}.
 * 
 */
public final class INIReader extends AbstractIO {

    /**
     * Used to configure how to behave when duplicate value or section keys are
     * encountered.
     * 
     * @see Builder#withDuplicateKeysAction(DuplicateAction)
     * @see Builder#withDuplicateSectionAction(DuplicateAction)
     */
    public enum DuplicateAction {
        /**
         * When a duplicate value key or section key is encountered, a
         * {@link ParseException} will be thrown. With this mode no keys can have
         * multiple values.
         */
        ABORT,
        /**
         * When a duplicate value key or section key in encountered, the duplicates
         * value will be entirely ignore. With this mode no keys can have multiple
         * values.
         */
        IGNORE,
        /**
         * When a duplicate value key or section key is encountered, the previous value
         * is entirely replaced. With this mode no keys can have multiple values.
         */
        REPLACE,
        /**
         * When a duplicate value key is encountered, the previous values are merged
         * with the new values.
         */
        MERGE,
        /**
         * When a duplicate value key is encountered, the new values are appended to the
         * existing values
         */
        APPEND
    }

    /**
     * Used to configure how to behave when parsing or writing multiple value or
     * section keys.
     * 
     * 
     */
    public enum MultiValueMode {
        /**
         * Multiple values are expressed as value keys repeating in the content.
         */
        REPEATED_KEY,
        /**
         * Multiple values are expressed as a single key, with it's string value being
         * made up of (comma by default) separated values.
         */
        SEPARATED
    }

    /**
     * Creates {@link INIReader} instances.
     */
    public static class Builder extends AbstractIOBuilder<Builder> {
        private boolean globalSection = true;
        private boolean caseSensitiveKeys = false;
        private boolean caseSensitiveSections = false;
        private boolean preserveOrder = true;
        private boolean nestedSections = true;
        private boolean parseExceptions = true;
        private boolean emptyValues = true;
        private boolean comments = true;
        private boolean inlineComments = true;
        private DuplicateAction duplicateKeysAction = DuplicateAction.REPLACE;
        private DuplicateAction duplicateSectionAction = DuplicateAction.REPLACE;
        private char[] quoteCharacters = new char[] { '"', '\'' };

        /**
         * Configure either the reader to not expect any type of string quotes.
         * 
         * @return this for chaining
         */
        public final Builder withoutStringQuoting() {
            return withQuoteCharacters();
        }

        /**
         * Configure the reader to expect quoted strings using the provided quote
         * characters.
         * 
         * @return this for chaining
         */
        public final Builder withQuoteCharacters(char... quoteCharacters) {
            this.quoteCharacters = quoteCharacters;
            return this;
        }

        /**
         * Do not throw {@link ParseException} when invalid syntax is found when parsing
         * an INI document. In general the line will just be ignored and treated as if
         * it were a comment.
         * 
         * @return this for chaining
         */
        public final Builder withoutParseExceptions() {
            return withParseExceptions(false);
        }

        /**
         * Configure whether to throw {@link ParseException} when invalid syntax is
         * found when parsing an INI document. In general the line will just be ignored
         * and treated as if it were a comment.
         * 
         * @param parseExceptions throw parse exceptions on invalid syntax
         * @return this for chaining
         */
        public final Builder withParseExceptions(boolean parseExceptions) {
            this.parseExceptions = parseExceptions;
            return this;
        }

        /**
         * Do not allow inline comments. Comments will be treated as part of the value.
         * 
         * @return this for chaining
         */
        public final Builder withoutInlineComments() {
            return withInlineComments(false);
        }

        /**
         * Configure whether to allow inline comments. When <code>false</code>, comments
         * will be treated as part of the value.
         * 
         * @param inlineComments allow inline comments
         * @return this for chaining
         */
        public final Builder withInlineComments(boolean inlineComments) {
            this.inlineComments = inlineComments;
            return this;
        }

        /**
         * Do not allow empty values. When reading the content, if a key's value is
         * empty the key will be entirely ignored.
         * 
         * @return this for chaining
         */
        public final Builder withoutEmptyValues() {
            return withEmptyValues(false);
        }

        /**
         * Configure whether to allow empty values. When <code>true</code> and reading
         * the content, if a key's value is empty the key will be entirely ignored.
         * 
         * @param emptyValues allow empty values
         * @return this for chaining
         */
        public final Builder withEmptyValues(boolean emptyValues) {
            this.emptyValues = emptyValues;
            return this;
        }

        /**
         * Configure to not allow comments. Anything that starts with
         * {@link #withCommentCharacter(char)} will be treated as a valid key.
         * 
         * @return this for chaining
         */
        public final Builder withoutComments() {
            return withComments(false);
        }

        /**
         * Configure whether to allow comments. When <code>false</code>, anything that
         * starts with {@link #withCommentCharacter(char)} will be treated as a valid
         * key. When <code>true</code> anything that starts with
         * {@link #withCommentCharacter(char)} will be ignored.
         * 
         * @param comments allow comments
         * @return this for chaining
         */
        public final Builder withComments(boolean comments) {
            this.comments = comments;
            return this;
        }

        /**
         * Configure to not allow any global properties, i.e. all values must be in a
         * section.
         * 
         * @return this for chaining
         */
        public final Builder withoutGlobalProperties() {
            return withGlobalSection(false);
        }

        /**
         * Configure whether to allow any global properties, i.e. all values must be in
         * a section.
         * 
         * @param globalSection allow properties global in section
         * @return this for chaining
         */
        public final Builder withGlobalSection(boolean globalSection) {
            this.globalSection = globalSection;
            return this;
        }

        /**
         * Value keys are by default case insensitive. Use this method to make value
         * keys case sensitive.
         * 
         * @return this for chaining
         */
        public final Builder withCaseSensitiveKeys() {
            return withCaseSensitiveKeys(true);
        }

        /**
         * Configure whether value keys are case sensitive. Value keys are by default
         * case insensitive.
         * 
         * @param caseSensitiveKeys case sensitive keys
         * @return this for chaining
         */
        public final Builder withCaseSensitiveKeys(boolean caseSensitiveKeys) {
            this.caseSensitiveKeys = caseSensitiveKeys;
            return this;
        }

        /**
         * Sections keys are by default case insensitive. Use this method to make
         * section keys case sensitive.
         * 
         * @return this for chaining
         */
        public final Builder withCaseSensitiveSections() {
            return withCaseSensitiveSections(true);
        }

        /**
         * Configure whether section keys are case sensitive. Sections keys are by
         * default case insensitive.
         * 
         * @param caseSensitiveSections case sensitive sections
         * @return this for chaining
         */
        public final Builder withCaseSensitiveSections(boolean caseSensitiveSections) {
            this.caseSensitiveSections = caseSensitiveSections;
            return this;
        }

        /**
         * Configure to not allow nested sections. Any section key encountered that has
         * {@link #withSectionPathSeparator(char)} (by default <code>.</code>), will be
         * treated as literally that key.
         * 
         * @return this for chaining
         */
        public final Builder withoutNestedSections() {
            return withNestedSections(false);
        }

        /**
         * Configure whether to allow nested sections. When <code>false</code>, any
         * section key encountered that has {@link #withSectionPathSeparator(char)} (by
         * default <code>.</code>), will be treated as literally that key. When
         * <code>true</code> the key will be split up based on the separator and that is
         * used as the path to section.
         * 
         * @paran nestedSections nested sections
         * @return this for chaining
         */
        public final Builder withNestedSections(boolean nestedSections) {
            this.nestedSections = nestedSections;
            return this;
        }

        /**
         * Configure to not preserve the order of sections and values as they are
         * inserted. Upon writing, keys and sections will be in an indeterminate order.
         * 
         * @return this for chaining
         */
        public final Builder withoutPreserveOrder() {
            return withPreserveOrder(false);
        }

        /**
         * Configure whether to preserve the order of sections and values as they are
         * inserted. When <code>false</code>, upon writing keys and sections will be in
         * an indeterminate order. When <code>true</code>, upon writing keys and
         * sections will be ordered according to the same rules as
         * {@link String#compareTo(String)}, i.e. alphabetical.
         * 
         * @param preserveOrder preserve order
         * @return this for chaining
         */
        public final Builder withPreserveOrder(boolean preserveOrder) {
            this.preserveOrder = preserveOrder;
            return this;
        }

        /**
         * Configure how to behave when duplicate value keys are encountered.
         * 
         * @param duplicateKeysAction action
         * @return this for chaining
         */
        public final Builder withDuplicateKeysAction(DuplicateAction duplicateKeysAction) {
            this.duplicateKeysAction = duplicateKeysAction;
            return this;
        }

        /**
         * Configure how to behave when duplicate section keys are encountered.
         * 
         * @param duplicateKeysAction action
         * @return this for chaining
         */
        public final Builder withDuplicateSectionAction(DuplicateAction duplicateSectionAction) {
            this.duplicateSectionAction = duplicateSectionAction;
            return this;
        }

        /**
         * Build a new {@link INIReader}.
         * 
         * @return reader
         */
        public final INIReader build() {
            return new INIReader(this);
        }

    }

    private final boolean globalSection;
    private final boolean caseSensitiveKeys;
    private final boolean caseSensitiveSections;
    private final boolean nestedSections;
    private final boolean preserveOrder;
    private final boolean comments;
    private final DuplicateAction duplicateKeysAction;
    private final DuplicateAction duplicateSectionAction;
    private final boolean inlineComments;
    private final boolean emptyValues;
    private final boolean parseExceptions;
    private final char[] quoteCharacters;

    private INIReader(Builder builder) {
        super(builder);
        this.comments = builder.comments;
        this.globalSection = builder.globalSection;
        this.caseSensitiveKeys = builder.caseSensitiveKeys;
        this.caseSensitiveSections = builder.caseSensitiveSections;
        this.nestedSections = builder.nestedSections;
        this.preserveOrder = builder.preserveOrder;
        this.duplicateKeysAction = builder.duplicateKeysAction;
        this.duplicateSectionAction = builder.duplicateSectionAction;
        this.inlineComments = builder.inlineComments;
        this.emptyValues = builder.emptyValues;
        this.parseExceptions = builder.parseExceptions;
        this.quoteCharacters = builder.quoteCharacters;
    }

    /**
     * Create an {@link INI} document instance by reading a file with content in INI
     * format.
     * 
     * @param file file containing INI
     * @return document
     * @throws IOException    on I/O error
     * @throws ParseException on parsing error
     */
    public INI read(Path file) throws IOException, ParseException {
        try (var rdr = Files.newBufferedReader(file)) {
            return read(rdr);
        }
    }

    /**
     * Create an {@link INI} document instance from a string in INI format.
     * 
     * @param content string of INI content
     * @return document
     * @throws IOException    on I/O error
     * @throws ParseException on parsing error
     */
    public INI read(String content) throws IOException, ParseException {
        try (var rdr = new StringReader(content)) {
            return read(rdr);
        }
    }

    /**
     * Create an {@link INI} document instance by reading a stream with content in
     * INI format.
     * 
     * @param reader reader providing stream of INI content
     * @return document
     * @throws IOException    on I/O error
     * @throws ParseException on parsing error
     */
    public INI read(Reader reader) throws IOException, ParseException {
        String line;
        var lineReader = new BufferedReader(reader);
        var lineBuffer = new StringBuilder();
        var continuation = false;
        var offset = 0;
        var rootSections = createSectionMap(preserveOrder, caseSensitiveSections);
        var globalProperties = createPropertyMap(preserveOrder, caseSensitiveKeys);
        Section section = null;

        var ini = new INI(preserveOrder, caseSensitiveKeys, caseSensitiveSections, globalProperties, rootSections);

        while ((line = lineReader.readLine()) != null) {
            offset += line.length();

            if (continuation) {
                lineBuffer.append(' ');
                line = line.stripLeading();
                continuation = false;
            }

            if (lineContinuations && isLineContinuation(line)) {
                line = line.substring(0, line.length() - 1);
                lineBuffer.append(line);
                continuation = true;
                continue;
            }

            lineBuffer.append(line);
            if (lineBuffer.length() == 0)
                continue;

            var fullLine = lineBuffer.toString();
            lineBuffer.setLength(0);
            var lineWithoutLeading = fullLine.stripLeading();

            if (comments && lineWithoutLeading.charAt(0) == commentCharacter) {
                continue;
            }

            var lineChars = fullLine.toCharArray();
            var escape = false;
            var buf = new StringBuilder();
            String key = null;
            char quoted = '\0';
            for (int i = 0; i < lineChars.length; i++) {
                var ch = lineChars[i];
                if (escape) {
                    switch (ch) {
                    case '\\':
                    case '\'':
                    case '"':
                    case '#':
                    case ':':
                        buf.append(ch);
                        break;
                    case '0':
                        buf.append((char) 0);
                        break;
                    case 'a':
                        buf.append((char) 7);
                        break;
                    case 'b':
                        buf.append((char) 8);
                        break;
                    case 't':
                        buf.append((char) 11);
                        break;
                    case 'n':
                        buf.append((char) 10);
                        break;
                    case 'r':
                        buf.append((char) 13);
                        break;
                    // TODO unicode escape
                    default:
                        if ((comments && ch == commentCharacter) || ch == valueSeparator)
                            buf.append(ch);
                        else {
                            buf.append('\\');
                            buf.append(ch);
                        }
                        break;
                    }
                    escape = false;
                } else {
                    if (ch == '\\') {
                        escape = true;
                    } else {
                        if (quoted != '\0') {
                            if (quoted == ch) {
                                quoted = '\0';
                                continue;
                            } else
                                buf.append(ch);
                        } else {
                            if (isQuote(ch)) {
                                quoted = ch;
                            } else if (ch == commentCharacter && comments && inlineComments) {
                                break;
                            } else if (key == null) {
                                if (ch == valueSeparator) {
                                    key = buf.toString().translateEscapes();
                                    buf.setLength(0);
                                }
//								else if(ch == ' ' || ch == '\t') {
//									continue;
//								}
                                else
                                    buf.append(ch);
                            } else {
                                buf.append(ch);
                            }
                        }
                    }
                }
            }
            if (key == null) {
                key = buf.toString();
                buf.setLength(0);
            }

            if (valueSeparatorWhitespace)
                key = key.stripTrailing();

            if (key.startsWith("[")) {
                var eidx = key.indexOf(']', 1);
                if (eidx == -1) {
                    if (parseExceptions) {
                        throw new ParseException("Incorrect syntax for section name, no closing ']'.", offset);
                    }
                } else {
                    if (eidx != key.length() - 1) {
                        if (parseExceptions) {
                            throw new ParseException(
                                    "Incorrect syntax for section name, trailing content after closing ']'.", offset);
                        } else
                            continue;
                    }
                    key = key.substring(1, eidx);

                    String[] sectionPath;
                    if (nestedSections) {
                        sectionPath = tokenize(key, sectionPathSeparator);
                    } else {
                        sectionPath = new String[] { key };
                    }

                    var parent = rootSections;
                    Section lastSection = null;

                    for (int i = 0; i < sectionPath.length; i++) {
                        var sectionKey = sectionPath[i];
                        var last = i == sectionPath.length - 1;

                        var sectionsForKey = parent.get(sectionKey);

                        if (last) {
                            var newSection = new Section(preserveOrder, caseSensitiveKeys, caseSensitiveSections,
                                    lastSection == null ? ini : lastSection, sectionKey);
                            if (sectionsForKey == null) {
                                /* Doesn't exist, just add */
                                sectionsForKey = new Section[] { newSection };
                                parent.put(sectionKey, sectionsForKey);
                            } else {
                                switch (duplicateSectionAction) {
                                case ABORT:
                                    throw new ParseException(
                                            MessageFormat.format("Duplicate section key {0}.", sectionKey), offset);
                                case REPLACE:
                                    sectionsForKey = new Section[] { newSection };
                                    parent.put(sectionKey, sectionsForKey);
                                    break;
                                case IGNORE:
                                    continue;
                                case APPEND:
                                    var newSections = new Section[sectionsForKey.length + 1];
                                    System.arraycopy(sectionsForKey, 0, newSections, 0, sectionsForKey.length);
                                    newSections[sectionsForKey.length] = newSection;
                                    parent.put(sectionKey, newSections);
                                    sectionsForKey = newSections;
                                    break;
                                case MERGE:
                                    newSection.merge(sectionsForKey[0].values());
                                    parent.put(sectionKey, new Section[] { newSection });
                                    sectionsForKey = new Section[] { newSection };
                                    break;
                                }
                            }
                        } else {
                            if (sectionsForKey == null) {
                                /* Doesn't exist, just add */
                                sectionsForKey = new Section[] { new Section(preserveOrder, caseSensitiveKeys,
                                        caseSensitiveSections, lastSection, sectionKey) };
                                parent.put(sectionKey, sectionsForKey);
                            }
                        }
                        parent = sectionsForKey[0].sections;
                        section = sectionsForKey[0];
                    }
                }
            } else {
                var val = buf.toString();
                if (val.isEmpty() && !emptyValues) {
                    if (parseExceptions)
                        throw new ParseException("Empty values are not allowed.", offset);
                } else {
                    if (trimmedValue) {
                        val = val.trim();
                    } else if (valueSeparatorWhitespace) {
                        val = val.stripLeading();
                    }

                    Map<String, String[]> sectionProperties;
                    if (section == null) {
                        if (globalSection) {
                            sectionProperties = globalProperties;
                        } else {
                            if (parseExceptions)
                                throw new ParseException(
                                        "Global properties are not allowed, all properties must be in a [Section].",
                                        offset);
                            else
                                continue;
                        }
                    } else {
                        sectionProperties = section.values;
                    }

                    String[] values;
                    if (multiValueMode == MultiValueMode.REPEATED_KEY) {
                        values = new String[] { val };
                    } else {
                        values = tokenize(val, multiValueSeparator);
                        if (trimmedValue)
                            values = trim(values);
                    }

                    var valuesForKey = sectionProperties.get(key);
                    if (valuesForKey == null) {
                        /* Doesn't exist, just add */
                        sectionProperties.put(key, values);
                    } else {
                        switch (duplicateKeysAction) {
                        case ABORT:
                            throw new ParseException(MessageFormat.format("Duplicate property key {0}.", key), offset);
                        case MERGE:
                        case REPLACE:
                            sectionProperties.put(key, values);
                            break;
                        case IGNORE:
                            continue;
                        case APPEND:
                            var newValues = new String[valuesForKey.length + values.length];
                            System.arraycopy(valuesForKey, 0, newValues, 0, valuesForKey.length);
                            System.arraycopy(values, 0, newValues, valuesForKey.length, values.length);
                            sectionProperties.put(key, newValues);
                            break;
                        }
                    }
                }
            }

        }
        return ini;
    }

    private boolean isQuote(char ch) {
        for (var c : quoteCharacters)
            if (c == ch)
                return true;
        return false;
    }

    boolean isLineContinuation(String line) {
        var contCount = 0;
        for (int i = line.length() - 1; i >= 0; i--) {
            var ch = line.charAt(i);
            if (ch != '\\')
                break;
            contCount++;
        }
        return contCount % 2 == 1;
    }

    static String[] tokenize(String val, char sep) {
        var tkns = new StringTokenizer(val, String.valueOf(sep));
        var arr = new String[tkns.countTokens()];
        for (var i = 0; tkns.hasMoreTokens(); i++) {
            arr[i] = tkns.nextToken();
        }
        return arr;
    }

    static String[] trim(String[] arr) {
        for (int i = 0; i < arr.length; i++)
            arr[i] = arr[i].trim();
        return arr;
    }

    static Map<String, Section[]> createSectionMap(boolean preserveOrder, boolean caseSensitiveSections) {
        if (preserveOrder) {
            if (caseSensitiveSections) {
                return new LinkedHashMap<>();
            } else {
                return new LinkedCaseInsensitiveMap<>();
            }
        } else {
            if (caseSensitiveSections) {
                return new HashMap<>();
            } else {
                return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            }
        }
    }

    static Map<String, String[]> createPropertyMap(boolean preserveOrder, boolean caseSensitiveKeys) {
        if (preserveOrder) {
            if (caseSensitiveKeys) {
                return new LinkedHashMap<>();
            } else {
                return new LinkedCaseInsensitiveMap<>();
            }
        } else {
            if (caseSensitiveKeys) {
                return new HashMap<>();
            } else {
                return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            }
        }
    }

}
