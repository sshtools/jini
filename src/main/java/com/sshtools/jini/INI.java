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

import com.sshtools.jini.Data.AbstractData;
import com.sshtools.jini.INIReader.MultiValueMode;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents the top-level of an <strong>INI</strong> format document.
 * <p>
 * It contains multiple string key/value pairs, internally stored as string
 * arrays. There are convenience methods to convert values to primitive types
 * when retrieved.
 * <p>
 * It may also contains multiple {@link Section}s, which provide the same
 * key/value pairs as the top level document, and themselves may contain further
 * nested sections.
 * <p>
 * An {@link INI} may either be directly created using {@link INI#create()} and
 * alternatives. For alternative configuration of the document, use
 * {@link Builder}, or to create a document from a text stream or string use an
 * {@link INIReader} created by an {@link INIReader.Builder}.
 * 
 */
public class INI extends AbstractData {

    /**
     * Build {@link INI} objects. Builders may be re-used, once {@link #build()} is
     * used, any changes to the builder will not affect the created instance.
     */
    public final static class Builder {

        private boolean caseSensitiveKeys = false;
        private boolean caseSensitiveSections = false;
        private boolean preserveOrder = true;
        private boolean emptyValues = true;

        /**
         * Configure the to not allow empty values. Any <code>null</code> or empty array
         * values inserted will throw a {@link IllegalArgumentException}.
         * 
         * @param emptyValues allow empty values
         * @return this for chaining
         */
        public final Builder withoutEmptyValues() {
            return withEmptyValues(false);
        }

        /**
         * Configure the document whether to allow empty values. When <code>true</code>
         * any <code>null</code> values inserted will be converted to empty values. When
         * <code>false</code> any <code>null</code> or empty array values inserted will
         * throw a {@link IllegalArgumentException}.
         * <p>
         * By default empty values are allowed.
         * 
         * @param emptyValues allow empty values
         * @return this for chaining
         */
        public final Builder withEmptyValues(boolean emptyValues) {
            this.emptyValues = emptyValues;
            return this;
        }

        /**
         * Configure the document to use case sensitive keys.
         * 
         * @return this for chaining
         */
        public final Builder withCaseSensitiveKeys() {
            return withCaseSensitiveKeys(true);
        }

        /**
         * Configure whether the document uses case sensitive keys.
         * 
         * @param caseSensitiveKeys case sensitive keys
         * @return this for chaining
         */
        public final Builder withCaseSensitiveKeys(boolean caseSensitiveKeys) {
            this.caseSensitiveKeys = caseSensitiveKeys;
            return this;
        }

        /**
         * Configure the document to use case sensitive sections.
         * 
         * @return this for chaining
         */
        public final Builder withCaseSensitiveSections() {
            return withCaseSensitiveSections(true);
        }

        /**
         * Configure whether the document uses case sensitive sections.
         * 
         * @param caseSensitiveSections case sensitive sections
         * @return this for chaining
         */
        public final Builder withCaseSensitiveSections(boolean caseSensitiveSections) {
            this.caseSensitiveSections = caseSensitiveSections;
            return this;
        }

        /**
         * Configure the document to not preserve order of insertion of values and
         * sections.
         * 
         * @return this for chaining
         */
        public final Builder withoutPreserveOrder() {
            return withPreserveOrder(false);
        }

        /**
         * Configure whether document should preserve order of insertion of values and
         * sections.
         * 
         * @return this for chaining
         */
        public final Builder withPreserveOrder(boolean preserveOrder) {
            this.preserveOrder = preserveOrder;
            return this;
        }

        /**
         * Create the configured {@link INI} document.
         * 
         * @return document
         */
        public INI build() {
            return new INI(emptyValues, preserveOrder, caseSensitiveKeys, caseSensitiveSections);
        }
    }

    /**
     * Create a new default {@link INI} document. It will have case insensitive keys
     * for values and sections, and insertion order will be preserved.
     * 
     * @return document
     */
    public static INI create() {
        return new INI.Builder().build();
    }

    /**
     * Parse a file that contains a document in INI format. It will have case
     * insensitive keys for values and sections, and insertion order will be
     * preserved.
     * 
     * @return document
     */
    public static INI fromFile(Path file) {
        try (var in = Files.newBufferedReader(file)) {
            return fromReader(in);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Parse a stream that contains a document in INI format. It will have case
     * insensitive keys for values and sections, and insertion order will be
     * preserved.
     * 
     * @return document
     */
    public static INI fromReader(Reader reader) {
        try {
            return new INIReader.Builder().build().read(reader);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to parse.", e);
        }
    }

    /**
     * Parse a string that contains a document in INI format. It will have case
     * insensitive keys for values and sections, and insertion order will be
     * preserved.
     * 
     * @return document
     */
    public static INI fromString(String content) {
        return fromReader(new StringReader(content));
    }

    static abstract class AbstractIO {

        protected final char sectionPathSeparator;
        protected final char valueSeparator;
        protected final char commentCharacter;
        protected final boolean lineContinuations;
        protected final boolean valueSeparatorWhitespace;
        protected final boolean trimmedValue;
        protected final MultiValueMode multiValueMode;
        protected final char multiValueSeparator;
        protected final boolean emptyValues;

        AbstractIO(AbstractIOBuilder<?> builder) {
            this.sectionPathSeparator = builder.sectionPathSeparator;
            this.valueSeparator = builder.valueSeparator;
            this.commentCharacter = builder.commentCharacter;
            this.lineContinuations = builder.lineContinuations;
            this.valueSeparatorWhitespace = builder.valueSeparatorWhitespace;
            this.trimmedValue = builder.trimmedValue;
            this.multiValueMode = builder.multiValueMode;
            this.multiValueSeparator = builder.multiValueSeparator;
            this.emptyValues = builder.emptyValues;
        }
    }

    static abstract class AbstractIOBuilder<B extends AbstractIOBuilder<B>> {

        char sectionPathSeparator = '.';
        boolean lineContinuations = true;
        boolean valueSeparatorWhitespace = true;
        char valueSeparator = '=';
        char commentCharacter = ';';
        boolean trimmedValue = true;
        MultiValueMode multiValueMode = MultiValueMode.REPEATED_KEY;
        char multiValueSeparator = ',';
        boolean emptyValues = true;

        /**
         * Do not allow empty values. When reading the content, if a key's value is
         * empty the key will be entirely ignored. When writing the content, if a key's
         * value is empty neither key nor value will be written.
         * <p>
         * By default empty values are allowed.
         * 
         * @return this for chaining
         */
        public final B withoutEmptyValues() {
            return withEmptyValues(false);
        }

        /**
         * Configure whether to allow empty values.
         * <p>
         * When <code>true</code> and reading the content, if a key's value is empty the
         * key will be stored with an empty value. When writing the content, if a key's
         * value is empty it will be written with it's key, but no value.
         * <p>
         * When <code>false</code> and reading the content, if a key's value is empty
         * the key will be entirely ignored. When writing the content, if a key's value
         * is empty neither key nor value will be written.
         * 
         * @param emptyValues allow empty values
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withEmptyValues(boolean emptyValues) {
            this.emptyValues = emptyValues;
            return (B) this;
        }

        /**
         * Configure how to behave when duplicate value keys are encountered. See
         * {@link MultiValueMode}.
         * 
         * @param multiValueMode mode
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withMultiValueMode(MultiValueMode multiValueMode) {
            this.multiValueMode = multiValueMode;
            return (B) this;
        }

        /**
         * Configure the separator character to use when outputting multiple values and
         * {@link MultiValueMode#SEPARATED} is in use.
         * 
         * @param multiValueSeparator separator
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withMultiValueSeparator(char multiValueSeparator) {
            this.multiValueSeparator = multiValueSeparator;
            return (B) this;
        }

        /**
         * Configure the separator character to use to express nested sections.
         * 
         * @param sectionPathSeparator separator
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withSectionPathSeparator(char sectionPathSeparator) {
            this.sectionPathSeparator = sectionPathSeparator;
            return (B) this;
        }

        /**
         * By default, whitespace is trimmed from the start and end of values. This will
         * prevent that, leaving any whitespace intact.
         * 
         * @return this for chaining
         */
        public final B withoutTrimmedValue() {
            return withTrimmedValue(false);
        }

        /**
         * Configure whether whitespace is trimmed from the start and end of values.
         * When <code>true</code>, whitespace will be trimmed, when <code>false>
         * whitespace will be left intact.
         * 
         * @param trimmedValue trim whitespace from value
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withTrimmedValue(boolean trimmedValue) {
            this.trimmedValue = trimmedValue;
            return (B) this;
        }

        /**
         * Do not expect or output whitespace either side of the value separator. I.e.
         * by default you would expect <code>Key1 = Value1</code>. Using this method
         * would result in <code>Key1=Value</code>.
         * 
         * @return this for chaining
         */
        public final B withoutValueSeparatorWhitespace() {
            return withValueSeparatorWhitespace(false);
        }

        /**
         * Configure whether to expect or output whitespace either side of the value
         * separator. I.e. when <code>true</code> a value would be parsed and output as
         * <code>Key1 = Value1</code>. When <code>false</code> a value would be parsed
         * and out as <code>Key1=Value</code>.
         *
         * @param valueSeparatorWhitespace value separator whitespace
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withValueSeparatorWhitespace(boolean valueSeparatorWhitespace) {
            this.valueSeparatorWhitespace = valueSeparatorWhitespace;
            return (B) this;
        }

        /**
         * Configure to not allow line continuations. When the line continuation
         * character is encountered, it will simply be ignored.
         * 
         * @return this for chaining
         */
        public final B withoutLineContinuations() {
            return withLineContinuations(false);
        }

        /**
         * Configure whether to allow line continuations. If a line ends with the
         * continuation character (a <code>\</code>), the text starting from the first
         * non-whitespace character of the next line will be treated as part of the same
         * value. This will continue until a line doesn't end with the continuation
         * character.
         * 
         * @param lineContinuations allow line continuations
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withLineContinuations(boolean lineContinuations) {
            this.lineContinuations = lineContinuations;
            return (B) this;
        }

        /**
         * Set the character to use for value separator. By default this is a
         * <code>=</code>.
         * 
         * @param valueSeparator value separator
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withValueSeparator(char valueSeparator) {
            this.valueSeparator = valueSeparator;
            return (B) this;
        }

        /**
         * Set the character to use for line comments. By default this is a
         * <code>;</code>.
         * 
         * @param valueSeparator value separator
         * @return this for chaining
         */
        @SuppressWarnings("unchecked")
        public final B withCommentCharacter(char commentCharacter) {
            this.commentCharacter = commentCharacter;
            return (B) this;
        }

    }

    /**
     * INI documents may contain (potentially nested) sections that can be used to
     * group command value keys.
     * <p>
     * A section is generally introduced by using the pattern
     * <code>[SectionName]</code>. To express a nested section, the path to the
     * section is used instead with each element separated by some character
     * (<code>.</code> by default), for example <code>[Level1.Level2.Level3]</code>.
     */
    public final static class Section extends AbstractData {
        private final String key;
        private final Optional<Data> parent;
        private final INI ini;

        Section(boolean emptyValues, boolean preserveOrder, boolean caseSensitiveKeys, boolean caseSenstiveSections,
                Data parent, String key) {
            super(emptyValues, preserveOrder, caseSensitiveKeys, caseSenstiveSections);
            this.parent = Optional.of(parent);
            this.key = key;

            Data p = parent;
            INI ini;
            while (true) {
                if (p instanceof INI) {
                    ini = (INI) p;
                    break;
                } else
                    p = ((Section) p).parent.get();
            }
            this.ini = ini;
        }

        /**
         * Remove this section from it's parent section or document.
         */
        public void remove() {
            ((AbstractData) parent.get()).remove(this);
        }

        /**
         * Get the root document {@link INI} this section is part of.
         * 
         * @return document
         */
        public INI document() {
            return ini;
        }

        /**
         * Get the parent {@link Section} or thrown an {@link IllegalArgumentException}
         * if this section is in the root document.
         * 
         * @return parent
         */
        public Section parent() {
            return parentOr().orElseThrow(() -> new IllegalStateException("Has no parent."));
        }

        /**
         * Return this sections path, with each element in the array being an element of
         * the path starting from the root document.
         * 
         * @return path
         */
        public String[] path() {

            var s = this;
            var l = new ArrayList<>(Arrays.asList(key()));
            while (s.parentOr().isPresent()) {
                s = s.parent();
                l.add(s.key());
            }
            Collections.reverse(l);
            return l.toArray(new String[0]);
        }

        /**
         * Return all parent paths up to but excluding the root document.
         * 
         * @return parents
         */
        public Section[] parents() {
            var s = this;
            var l = new ArrayList<Section>();
            while (s.parentOr().isPresent()) {
                s = s.parent();
                l.add(s);
            }
            return l.toArray(new Section[0]);
        }

        /**
         * Get the optional parent. If this section is part of the root document,
         * {@link Optional#isEmpty()} will return <code>true</code>.
         * 
         * @return optional parent
         */
        public Optional<Section> parentOr() {
            return parent.get() instanceof Section ? parent.map(d -> (Section) d) : Optional.empty();
        }

        /**
         * Get the key used for this section.
         * 
         * @return key
         */
        public String key() {
            return key;
        }

        void merge(Map<String, String[]> values) {
            this.values.putAll(values);
        }

        @Override
        public Map<String, Section[]> sections() {
            return sections;
        }

    }

    INI(boolean emptyValues, boolean preserveOrder, boolean caseInsensitiveKeys, boolean caseInsensitiveSections,
            Map<String, String[]> values, Map<String, Section[]> sections) {
        super(emptyValues, preserveOrder, caseInsensitiveKeys, caseInsensitiveSections, values, sections);
    }

    INI(boolean emptyValues, boolean preserveOrder, boolean caseSensitiveKeys, boolean caseSenstiveSections) {
        super(emptyValues, preserveOrder, caseSensitiveKeys, caseSenstiveSections);
    }

    @Override
    public String toString() {
        return new INIWriter.Builder().build().write(this);
    }

    /*
     * Copyright 2002-2021 the original author or authors.
     *
     * Licensed under the Apache License, Version 2.0 (the "License"); you may not
     * use this file except in compliance with the License. You may obtain a copy of
     * the License at
     *
     * https://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
     * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
     * License for the specific language governing permissions and limitations under
     * the License.
     */

    /**
     * {@link LinkedHashMap} variant that stores String keys in a case-insensitive
     * manner, for example for key-based access in a results table.
     *
     * <p>
     * Preserves the original order as well as the original casing of keys, while
     * allowing for contains, get and remove calls with any case of key.
     *
     * <p>
     * Does <i>not</i> support {@code null} keys.
     *
     * @author Juergen Hoeller
     * @author Phillip Webb
     * @since 3.0
     * @param <V> the value type
     */
    @SuppressWarnings("serial")
    public static class LinkedCaseInsensitiveMap<V> implements Map<String, V>, Serializable, Cloneable {

        /**
         * Default load factor for {@link HashMap}/{@link LinkedHashMap} variants.
         *
         * @see #newHashMap(int)
         * @see #newLinkedHashMap(int)
         */
        static final float DEFAULT_LOAD_FACTOR = 0.75f;

        /**
         * Instantiate a new {@link HashMap} with an initial capacity that can
         * accommodate the specified number of elements without any immediate
         * resize/rehash operations to be expected.
         * <p>
         * This differs from the regular {@link HashMap} constructor which takes an
         * initial capacity relative to a load factor but is effectively aligned with
         * the JDK's
         * {@link java.util.concurrent.ConcurrentHashMap#ConcurrentHashMap(int)}.
         *
         * @param expectedSize the expected number of elements (with a corresponding
         *                     capacity to be derived so that no resize/rehash
         *                     operations are needed)
         * @since 5.3
         * @see #newLinkedHashMap(int)
         */
        public static <K, V> HashMap<K, V> newHashMap(int expectedSize) {
            return new HashMap<>(computeMapInitialCapacity(expectedSize), DEFAULT_LOAD_FACTOR);
        }

        private static int computeMapInitialCapacity(int expectedSize) {
            return (int) Math.ceil(expectedSize / (double) DEFAULT_LOAD_FACTOR);
        }

        private final LinkedHashMap<String, V> targetMap;

        private final HashMap<String, String> caseInsensitiveKeys;

        private final Locale locale;

        private transient volatile Set<String> keySet;

        private transient volatile Collection<V> values;

        private transient volatile Set<Entry<String, V>> entrySet;

        /**
         * Create a new LinkedCaseInsensitiveMap that stores case-insensitive keys
         * according to the default Locale (by default in lower case).
         *
         * @see #convertKey(String)
         */
        public LinkedCaseInsensitiveMap() {
            this((Locale) null);
        }

        /**
         * Create a new LinkedCaseInsensitiveMap that stores case-insensitive keys
         * according to the given Locale (in lower case).
         *
         * @param locale the Locale to use for case-insensitive key conversion
         * @see #convertKey(String)
         */
        public LinkedCaseInsensitiveMap(Locale locale) {
            this(12, locale); // equivalent to LinkedHashMap's initial capacity of 16
        }

        /**
         * Create a new LinkedCaseInsensitiveMap that wraps a {@link LinkedHashMap} with
         * an initial capacity that can accommodate the specified number of elements
         * without any immediate resize/rehash operations to be expected, storing
         * case-insensitive keys according to the default Locale (in lower case).
         *
         * @param expectedSize the expected number of elements (with a corresponding
         *                     capacity to be derived so that no resize/rehash
         *                     operations are needed)
         * @see CollectionUtils#newHashMap(int)
         * @see #convertKey(String)
         */
        public LinkedCaseInsensitiveMap(int expectedSize) {
            this(expectedSize, null);
        }

        /**
         * Create a new LinkedCaseInsensitiveMap that wraps a {@link LinkedHashMap} with
         * an initial capacity that can accommodate the specified number of elements
         * without any immediate resize/rehash operations to be expected, storing
         * case-insensitive keys according to the given Locale (in lower case).
         *
         * @param expectedSize the expected number of elements (with a corresponding
         *                     capacity to be derived so that no resize/rehash
         *                     operations are needed)
         * @param locale       the Locale to use for case-insensitive key conversion
         * @see CollectionUtils#newHashMap(int)
         * @see #convertKey(String)
         */
        public LinkedCaseInsensitiveMap(int expectedSize, Locale locale) {
            this.targetMap = new LinkedHashMap<>((int) (expectedSize / DEFAULT_LOAD_FACTOR), DEFAULT_LOAD_FACTOR) {
                @Override
                public boolean containsKey(Object key) {
                    return LinkedCaseInsensitiveMap.this.containsKey(key);
                }

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                    boolean doRemove = LinkedCaseInsensitiveMap.this.removeEldestEntry(eldest);
                    if (doRemove) {
                        removeCaseInsensitiveKey(eldest.getKey());
                    }
                    return doRemove;
                }
            };
            this.caseInsensitiveKeys = newHashMap(expectedSize);
            this.locale = (locale != null ? locale : Locale.getDefault());
        }

        /**
         * Copy constructor.
         */
        @SuppressWarnings("unchecked")
        private LinkedCaseInsensitiveMap(LinkedCaseInsensitiveMap<V> other) {
            this.targetMap = (LinkedHashMap<String, V>) other.targetMap.clone();
            this.caseInsensitiveKeys = (HashMap<String, String>) other.caseInsensitiveKeys.clone();
            this.locale = other.locale;
        }

        // Implementation of java.util.Map

        @Override
        public int size() {
            return this.targetMap.size();
        }

        @Override
        public boolean isEmpty() {
            return this.targetMap.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return (key instanceof String && this.caseInsensitiveKeys.containsKey(convertKey((String) key)));
        }

        @Override
        public boolean containsValue(Object value) {
            return this.targetMap.containsValue(value);
        }

        @Override

        public V get(Object key) {
            if (key instanceof String) {
                String caseInsensitiveKey = this.caseInsensitiveKeys.get(convertKey((String) key));
                if (caseInsensitiveKey != null) {
                    return this.targetMap.get(caseInsensitiveKey);
                }
            }
            return null;
        }

        @Override

        public V getOrDefault(Object key, V defaultValue) {
            if (key instanceof String) {
                String caseInsensitiveKey = this.caseInsensitiveKeys.get(convertKey((String) key));
                if (caseInsensitiveKey != null) {
                    return this.targetMap.get(caseInsensitiveKey);
                }
            }
            return defaultValue;
        }

        @Override

        public V put(String key, V value) {
            String oldKey = this.caseInsensitiveKeys.put(convertKey(key), key);
            V oldKeyValue = null;
            if (oldKey != null && !oldKey.equals(key)) {
                oldKeyValue = this.targetMap.remove(oldKey);
            }
            V oldValue = this.targetMap.put(key, value);
            return (oldKeyValue != null ? oldKeyValue : oldValue);
        }

        @Override
        public void putAll(Map<? extends String, ? extends V> map) {
            if (map.isEmpty()) {
                return;
            }
            map.forEach(this::put);
        }

        @Override

        public V putIfAbsent(String key, V value) {
            String oldKey = this.caseInsensitiveKeys.putIfAbsent(convertKey(key), key);
            if (oldKey != null) {
                V oldKeyValue = this.targetMap.get(oldKey);
                if (oldKeyValue != null) {
                    return oldKeyValue;
                } else {
                    key = oldKey;
                }
            }
            return this.targetMap.putIfAbsent(key, value);
        }

        @Override

        public V computeIfAbsent(String key, Function<? super String, ? extends V> mappingFunction) {
            String oldKey = this.caseInsensitiveKeys.putIfAbsent(convertKey(key), key);
            if (oldKey != null) {
                V oldKeyValue = this.targetMap.get(oldKey);
                if (oldKeyValue != null) {
                    return oldKeyValue;
                } else {
                    key = oldKey;
                }
            }
            return this.targetMap.computeIfAbsent(key, mappingFunction);
        }

        @Override

        public V remove(Object key) {
            if (key instanceof String) {
                String caseInsensitiveKey = removeCaseInsensitiveKey((String) key);
                if (caseInsensitiveKey != null) {
                    return this.targetMap.remove(caseInsensitiveKey);
                }
            }
            return null;
        }

        @Override
        public void clear() {
            this.caseInsensitiveKeys.clear();
            this.targetMap.clear();
        }

        @Override
        public Set<String> keySet() {
            Set<String> keySet = this.keySet;
            if (keySet == null) {
                keySet = new KeySet(this.targetMap.keySet());
                this.keySet = keySet;
            }
            return keySet;
        }

        @Override
        public Collection<V> values() {
            Collection<V> values = this.values;
            if (values == null) {
                values = new Values(this.targetMap.values());
                this.values = values;
            }
            return values;
        }

        @Override
        public Set<Entry<String, V>> entrySet() {
            Set<Entry<String, V>> entrySet = this.entrySet;
            if (entrySet == null) {
                entrySet = new EntrySet(this.targetMap.entrySet());
                this.entrySet = entrySet;
            }
            return entrySet;
        }

        @Override
        public LinkedCaseInsensitiveMap<V> clone() {
            return new LinkedCaseInsensitiveMap<>(this);
        }

        @Override
        public boolean equals(Object other) {
            return (this == other || this.targetMap.equals(other));
        }

        @Override
        public int hashCode() {
            return this.targetMap.hashCode();
        }

        @Override
        public String toString() {
            return this.targetMap.toString();
        }

        // Specific to LinkedCaseInsensitiveMap

        /**
         * Return the locale used by this {@code LinkedCaseInsensitiveMap}. Used for
         * case-insensitive key conversion.
         *
         * @since 4.3.10
         * @see #LinkedCaseInsensitiveMap(Locale)
         * @see #convertKey(String)
         */
        public Locale getLocale() {
            return this.locale;
        }

        /**
         * Convert the given key to a case-insensitive key.
         * <p>
         * The default implementation converts the key to lower-case according to this
         * Map's Locale.
         *
         * @param key the user-specified key
         * @return the key to use for storing
         * @see String#toLowerCase(Locale)
         */
        protected String convertKey(String key) {
            return key.toLowerCase(getLocale());
        }

        /**
         * Determine whether this map should remove the given eldest entry.
         *
         * @param eldest the candidate entry
         * @return {@code true} for removing it, {@code false} for keeping it
         * @see LinkedHashMap#removeEldestEntry
         */
        protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
            return false;
        }

        private String removeCaseInsensitiveKey(String key) {
            return this.caseInsensitiveKeys.remove(convertKey(key));
        }

        private class KeySet extends AbstractSet<String> {

            private final Set<String> delegate;

            KeySet(Set<String> delegate) {
                this.delegate = delegate;
            }

            @Override
            public int size() {
                return this.delegate.size();
            }

            @Override
            public boolean contains(Object o) {
                return this.delegate.contains(o);
            }

            @Override
            public Iterator<String> iterator() {
                return new KeySetIterator();
            }

            @Override
            public boolean remove(Object o) {
                return LinkedCaseInsensitiveMap.this.remove(o) != null;
            }

            @Override
            public void clear() {
                LinkedCaseInsensitiveMap.this.clear();
            }

            @Override
            public Spliterator<String> spliterator() {
                return this.delegate.spliterator();
            }

            @Override
            public void forEach(Consumer<? super String> action) {
                this.delegate.forEach(action);
            }
        }

        private class Values extends AbstractCollection<V> {

            private final Collection<V> delegate;

            Values(Collection<V> delegate) {
                this.delegate = delegate;
            }

            @Override
            public int size() {
                return this.delegate.size();
            }

            @Override
            public boolean contains(Object o) {
                return this.delegate.contains(o);
            }

            @Override
            public Iterator<V> iterator() {
                return new ValuesIterator();
            }

            @Override
            public void clear() {
                LinkedCaseInsensitiveMap.this.clear();
            }

            @Override
            public Spliterator<V> spliterator() {
                return this.delegate.spliterator();
            }

            @Override
            public void forEach(Consumer<? super V> action) {
                this.delegate.forEach(action);
            }
        }

        private class EntrySet extends AbstractSet<Entry<String, V>> {

            private final Set<Entry<String, V>> delegate;

            public EntrySet(Set<Entry<String, V>> delegate) {
                this.delegate = delegate;
            }

            @Override
            public int size() {
                return this.delegate.size();
            }

            @Override
            public boolean contains(Object o) {
                return this.delegate.contains(o);
            }

            @Override
            public Iterator<Entry<String, V>> iterator() {
                return new EntrySetIterator();
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean remove(Object o) {
                if (this.delegate.remove(o)) {
                    removeCaseInsensitiveKey(((Map.Entry<String, V>) o).getKey());
                    return true;
                }
                return false;
            }

            @Override
            public void clear() {
                this.delegate.clear();
                caseInsensitiveKeys.clear();
            }

            @Override
            public Spliterator<Entry<String, V>> spliterator() {
                return this.delegate.spliterator();
            }

            @Override
            public void forEach(Consumer<? super Entry<String, V>> action) {
                this.delegate.forEach(action);
            }
        }

        private abstract class EntryIterator<T> implements Iterator<T> {

            private final Iterator<Entry<String, V>> delegate;

            private Entry<String, V> last;

            public EntryIterator() {
                this.delegate = targetMap.entrySet().iterator();
            }

            protected Entry<String, V> nextEntry() {
                Entry<String, V> entry = this.delegate.next();
                this.last = entry;
                return entry;
            }

            @Override
            public boolean hasNext() {
                return this.delegate.hasNext();
            }

            @Override
            public void remove() {
                this.delegate.remove();
                if (this.last != null) {
                    removeCaseInsensitiveKey(this.last.getKey());
                    this.last = null;
                }
            }
        }

        private class KeySetIterator extends EntryIterator<String> {

            @Override
            public String next() {
                return nextEntry().getKey();
            }
        }

        private class ValuesIterator extends EntryIterator<V> {

            @Override
            public V next() {
                return nextEntry().getValue();
            }
        }

        private class EntrySetIterator extends EntryIterator<Entry<String, V>> {

            @Override
            public Entry<String, V> next() {
                return nextEntry();
            }
        }

    }

}
