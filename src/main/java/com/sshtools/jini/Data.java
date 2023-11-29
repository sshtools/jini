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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import com.sshtools.jini.INI.Section;

/**
 * Base interface shared by both the document object {@link INI} and the sections
 * contained within that document {@link Section}. 
 */
public interface Data {

    /**
     * Abstract implementation of {@link Data}.
     */
    public abstract class AbstractData implements Data {

        final Map<String, Section[]> sections;
        final Map<String, String[]> values;
        final boolean preserveOrder;
        final boolean caseSensitiveKeys;
        final boolean caseSensitiveSections;
        final boolean emptyValues; 

        AbstractData(boolean emptyValues, boolean preserveOrder, boolean caseSensitiveKeys, boolean caseSensitiveSections,
                Map<String, String[]> values, Map<String, Section[]> sections) {
            super();
            this.emptyValues = emptyValues;
            this.sections = sections;
            this.values = values;
            this.preserveOrder = preserveOrder;
            this.caseSensitiveKeys = caseSensitiveKeys;
            this.caseSensitiveSections = caseSensitiveSections;
        }

        AbstractData(boolean emptyValues, boolean preserveOrder, boolean caseSensitiveKeys, boolean caseSensitiveSections) {
            this(emptyValues, preserveOrder, caseSensitiveKeys, caseSensitiveSections,
                    INIReader.createPropertyMap(preserveOrder, caseSensitiveKeys),
                    INIReader.createSectionMap(preserveOrder, caseSensitiveSections));
        }

        @Override
        public boolean remove(String key) {
            return values.remove(key) != null;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public boolean containsSection(String... key) {
        	if(key.length == 0)
        		throw new IllegalArgumentException();
        	else if(key.length == 1)
        		return sections.containsKey(key[0]);
        	else {
        		var section = this;
        		for(var k : key) {
        			if(section.sections.containsKey(k)) {
        				section = section.sections.get(k)[0];
        			}
        			else {
        				return false; 
        			}
        		}
        		return true;
        	}
        }

        @Override
		public Set<String> keys() {
			return values.keySet();
		}

		@Override
        public void putAll(String key, String... values) {
            this.values.put(key, nullCheck(values));
        }

        @Override
        public void putAll(String key, int... values) {
            this.values.put(key, nullCheck(IntStream.of(values).boxed().map(i -> i.toString()).toArray((s) -> new String[s])));
        }

        @Override
        public void putAll(String key, short... values) {
            this.values.put(key, nullCheck(arrayToList(values).stream().map(i -> i.toString()).toArray((s) -> new String[s])));
        }

        @Override
        public void putAll(String key, long... values) {
            this.values.put(key, nullCheck(LongStream.of(values).boxed().map(i -> i.toString()).toArray((s) -> new String[s])));
        }

        @Override
        public void putAll(String key, float... values) {
            this.values.put(key, nullCheck(arrayToList(values).stream().map(i -> i.toString()).toArray((s) -> new String[s])));
        }

        @Override
        public void putAll(String key, double... values) {
            this.values.put(key, nullCheck(arrayToList(values).stream().map(i -> i.toString()).toArray((s) -> new String[s])));
        }

        @Override
        public void putAll(String key, boolean... values) {
            this.values.put(key, nullCheck(arrayToList(values).stream().map(i -> {
                return i.toString();
            }).toArray((s) -> new String[s])));
        }

        @Override
        public Map<String, String[]> values() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public Map<String, Section[]> sections() {
            return Collections.unmodifiableMap(sections);
        }

        @Override
        public Optional<Section[]> allSectionsOr(String... path) {
        	if(path.length == 0) {
        		return Optional.of(sections().values().stream().flatMap(sections -> Arrays.asList(sections).stream()).collect(Collectors.toList()).toArray(new Section[0]));
        	}
            Data current = this;
            Section[] sections = null;
            for(var key : path) {
                sections = current.sections().get(key);
                if(sections == null)
                    return Optional.empty();
                else
                    current = sections[0];
            }
            return Optional.ofNullable(sections);
        }

        @Override
        public Optional<String[]> getAllOr(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Section create(String... path) {
            Section newSection = null;
            Section parent = this instanceof Section ? (Section) this : null;
            for (int i = 0; i < path.length; i++) {
                var last = i == path.length - 1;
                var name = path[i];
                var existing = parent == null ? sections.get(name) : parent.sections.get(name);
                if (existing == null) {
                    newSection = new Section(emptyValues, preserveOrder, caseSensitiveKeys, caseSensitiveKeys,
                            parent == null ? this : parent, name);
                    (parent == null ? sections : parent.sections).put(name, new Section[] { newSection });
                } else {
                    if (last) {
                        newSection = new Section(emptyValues, preserveOrder, caseSensitiveKeys, caseSensitiveKeys,
                                parent == null ? this : parent, name);
                        var newSections = new Section[existing.length + 1];
                        System.arraycopy(existing, 0, newSections, 0, existing.length);
                        newSections[existing.length] = newSection;
                    } else {
                        newSection = existing[0];
                    }
                }
                parent = newSection;
            }
            if (newSection == null)
                throw new IllegalArgumentException("No section path");
            return newSection;
        }
        
        void remove(Section section) {
            var v = sections.get(section.key());
            if (v == null) {
                throw new IllegalArgumentException("Section not part of this section");
            }
            var l = new ArrayList<>(Arrays.asList(v));
            l.remove(section);
            if (l.isEmpty())
                sections.remove(section.key());
            else
                sections.put(section.key(), l.toArray(new Section[0]));
        }
        
        private String[] nullCheck(String... objs) {
            if(objs == null) {
                if(emptyValues)
                    return new String[0];
                else
                    throw new IllegalArgumentException("Value may not be null.");
            }
            else  {
                if((objs.length == 1 && objs[0] == null) || (objs.length == 0)) {
                    if(emptyValues)
                        return new String[0];
                    else
                        throw new IllegalArgumentException("Value may not be null.");
                }
                else {
                    for(var obj : objs)
                        if(obj == null)
                            throw new IllegalArgumentException("Only a single value may contain null values.");
                }
            }
            return objs;
        }
    }

    /**
     * Get an unmodifiable set of the underlying keys.
     * 
     * @return map of keys in this section or document
     */
    Set<String> keys();

    /**
     * Get an unmodifiable map of the underlying values. The returned array of values
     * will never be <code>null</code>, but may potentially be an empty array.
     * 
     * @return map of values in this section or document
     */
    Map<String, String[]> values();

    /**
     * Get an unmodifiable map of the underlying sections. The returned array of values
     * will never be <code>null</code>, and will never be an empty array.
     * 
     * @return map of sections in this section or document
     */
    Map<String, Section[]> sections();

    /**
     * Get a {@link Section} given it's its path relative to this document or parent
     * section. If there are no sections with such a path, an {@link IllegalArgumentException} will
     * be thrown. If there are more than one sections with such a path, the
     * first section will be returned.
     * 
     * @param path path to section 
     * @return optional section 
     */
    default Section section(String... path) {
        return sectionOr(path)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No section with path {0}", String.join(".", path))));
    }
    
    /**
     * Get whether this document or section contains a value (empty or otherwise).
     * 
     * @param key key of value
     * @return document or section contains key
     */
    boolean contains(String key);
    
    /**
     * Remove a value give it's key.
     * 
     * @param key key of value
     * @return value was removed
     */
    boolean remove(String key);
    
    /**
     * Get whether this document or section contains a child section. Nested sections
     * may be specified by provided each path element.
     * 
     * @param key key of section
     * @return document or section contains section
     */
    boolean containsSection(String... key);

    /**
     * Put a string value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, String value) {
        putAll(key, value);
    }

    /**
     * Put an integer value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, int value) {
        putAll(key, value);
    }

    /**
     * Put a short value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, short value) {
        putAll(key, value);
    }

    /**
     * Put a long value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, long value) {
        putAll(key, value);
    }

    /**
     * Put a float value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, float value) {
        putAll(key, value);
    }

    /**
     * Put a double value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, double value) {
        putAll(key, value);
    }

    /**
     * Put a boolean value into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * 
     * @param key key to store value under
     * @param value value to store
     */
    default void put(String key, boolean value) {
        putAll(key, value);
    }

    /**
     * Put zero or more string values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, String... values);

    /**
     * Put zero or more integer values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>. 
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, int... values);

    /**
     * Put zero or more short values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, short... values);

    /**
     * Put zero or more long values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, long... values);

    /**
     * Put zero or more float values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, float... values);

    /**
     * Put zero or more double values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, double... values);

    /**
     * Put zero or more boolean values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty array of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    void putAll(String key, boolean... values);

    /**
     * Put one or more string values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void put(String key, Collection<String> values) {
        putAll(key, values.toArray(new String[0]));
    }

    /**
     * Put one or more integer values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void putInt(String key, Collection<Integer> values) {
        putAll(key, values.stream().mapToInt(i -> i).toArray());

    }

    /**
     * Put one or more short values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void putShort(String key, Collection<Short> values) {
        var result = new short[values.size()];
        var i = 0;
        for (var f : values) {
            result[i++] = f;
        }
        putAll(key, result);
    }

    /**
     * Put one or more long values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void putLong(String key, Collection<Long> values) {
        putAll(key, values.stream().mapToLong(i -> i).toArray());
    }

    /**
     * Put one or more float values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void putFloat(String key, Collection<Float> values) {
        var result = new float[values.size()];
        var i = 0;
        for (var f : values) {
            result[i++] = f;
        }
        putAll(key, result);
    }

    /**
     * Put one or more double values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void putDouble(String key, Collection<Double> values) {
        putAll(key, values.stream().mapToDouble(i -> i).toArray());

    }

    /**
     * Put one or more boolean values into this document or section with the given key. Any existing
     * values with the same key will be entirely replaced.
     * <p>
     * If an empty list of values is supplied, an empty value will be inserted. This will 
     * always be output by an {@link INIWriter}, but for an {@link INIReader} to be able 
     * to correctly read this, {@link INIReader.Builder#withEmptyValues(boolean)}
     * must be <code>true</code>.
     * 
     * @param key key to store value under
     * @param values values to store
     */
    default void putBoolean(String key, Collection<Boolean> values) {
        var result = new boolean[values.size()];
        var i = 0;
        for (var f : values) {
            result[i++] = f;
        }
        putAll(key, result);

    }

    /**
     * Get a {@link Section} given it's its path relative to this document or parent
     * section. If there are no sections with such a path, {@link Optional#isEmpty()} will
     * return <code>true</code>. If there are more than one sections with such a path, the
     * first section will be returned.
     * 
     * @param path path to section 
     * @return optional section 
     */
    default Optional<Section> sectionOr(String... path) {
        var all = allSectionsOr(path);
        if (all.isEmpty())
            return Optional.empty();
        else {
            return Optional.of(all.get()[0]);
        }
    }

    /**
     * Get all sections with the given path that is relative to this document or parent. If
     * there are no sections with such a path, an {@link IllegalArgumentException} will be thrown.
     * An empty array will never be returned.
     * <p>
     * If no section paths are provided, all sections in this document or parent are returned.
     *  
     * @param path path to section 
     * @return sections with path
     */
    default Section[] allSections(String... path) {
        return allSectionsOr(path)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No section with path {0}", String.join(".", path))));
    }

    /**
     * Get all sections with the given path that is relative to this document or parent. 
     * If there are no sections with such a path, {@link Optional#isEmpty()} will
     * return <code>true</code>. An empty array will never be returned.
     * <p>
     * If no section paths are provided, all sections in this document or parent are returned.
     * 
     * @param path path to section
     * @return optional sections with path
     */
    Optional<Section[]> allSectionsOr(String... path);

    /**
     * Create either a single section inside this document or parent section, or create 
     * nested sections starting inside this document or parent section.
     * <p>
     * When creating a path of sections, if specified parent sections already exist they
     * will not be overwritten.
     * <p>
     * The final element of the path, if such a section with the same key already exists,
     * a 2nd section with the same key will be created.
     * 
     * @param path
     * @return the new section
     */
    Section create(String... path);

    /**
     * Get a single string value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default String get(String key) {
        return getOr(key)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No property with key {0}", key)));
    }

    /**
     * Get a single string value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default String getOr(String key, String defaultValue) {
        return getOr(key).orElse(defaultValue);
    }

    /**
     * Get a single string value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<String> getOr(String key) {
        var all = getAllOr(key);
        if (all.isEmpty())
            return Optional.empty();
        else {
            var a = all.get();
            if (a.length == 0)
                return Optional.empty();
            else
                return Optional.of(a[0]);
        }
    }

    /**
     * Get all string values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default String[] getAll(String key) {
        return getAllOr(key)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No property with key {0}", key)));
    }

    /**
     * Get all string values given a key or a default value if no such key exists.
     * The returned array may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @param defaultValues default values
     * @return values
     */
    default String[] getAllOr(String key, String... defaultValues) {
        return getAllOr(key).orElse(defaultValues);
    }

    /**
     * Get all string values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    Optional<String[]> getAllOr(String key);

    /**
     * Get a single double value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    /**
     * Get a single double value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default double getDoubleOr(String key, double defaultValue) {
        return getOr(key).map(i -> Double.parseDouble(i)).orElse(defaultValue);
    }

    /**
     * Get a single double value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<Double> getDoubleOr(String key) {
        return getOr(key).map(i -> Double.parseDouble(i));
    }

    /**
     * Get all double values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default double[] getAllDouble(String key) {
        return Arrays.asList(getAll(key)).stream().mapToDouble(v -> Double.parseDouble(v)).toArray();
    }

    /**
     * Get all double values given a key or a default value if no such key exists.
     * The returned array may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @param defaultValues default values
     * @return values
     */
    default double[] getAllDoubleOr(String key, double... defaultValues) {
        return getAllDoubleOr(key).orElse(defaultValues);
    }

    /**
     * Get all double values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<double[]> getAllDoubleOr(String key) {
        return getAllOr(key).map(s -> Arrays.asList(s).stream().mapToDouble(v -> Double.parseDouble(v)).toArray());
    }

    /**
     * Get a long value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default long getLong(String key) {
        return Long.parseLong(get(key));
    }

    /**
     * Get a single long value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default long getLongOr(String key, long defaultValue) {
        return getOr(key).map(i -> Long.parseLong(i)).orElse(defaultValue);
    }

    /**
     * Get a single long value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<Long> getLongOr(String key) {
        return getOr(key).map(i -> Long.parseLong(i));
    }

    /**
     * Get all long values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default long[] getAllLong(String key) {
        return Arrays.asList(getAll(key)).stream().mapToLong(v -> Long.parseLong(v)).toArray();
    }

    /**
     * Get all long values given a key or a default value if no such key exists.
     * The returned array may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @param defaultValues default values
     * @return values
     */
    default long[] getAllLongOr(String key, long... defaultValues) {
        return getAllLongOr(key).orElse(defaultValues);
    }

    /**
     * Get all long values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<long[]> getAllLongOr(String key) {
        return getAllOr(key).map(s -> Arrays.asList(s).stream().mapToLong(v -> Long.parseLong(v)).toArray());
    }

    /**
     * Get an integer value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    /**
     * Get a single integer value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default int getIntOr(String key, int defaultValue) {
        return getOr(key).map(i -> Integer.parseInt(i)).orElse(defaultValue);
    }

    /**
     * Get a single integer value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<Integer> getIntOr(String key) {
        return getOr(key).map(i -> Integer.parseInt(i));
    }

    /**
     * Get all integer values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default int[] getAllInt(String key) {
        return Arrays.asList(getAll(key)).stream().mapToInt(v -> Integer.parseInt(v)).toArray();
    }

    /**
     * Get all integer values given a key or a default value if no such key exists.
     * The returned array may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @param defaultValues default values
     * @return values
     */
    default int[] getAllIntOr(String key, int... defaultValues) {
        return getAllIntOr(key).orElse(defaultValues);
    }

    /**
     * Get all integer values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<int[]> getAllIntOr(String key) {
        return getAllOr(key).map(s -> Arrays.asList(s).stream().mapToInt(v -> Integer.parseInt(v)).toArray());
    }

    /**
     * Get a short value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default short getShort(String key) {
        return Short.parseShort(get(key));
    }

    /**
     * Get a single short value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default short getShortOr(String key, short defaultValue) {
        return getOr(key).map(i -> Short.parseShort(i)).orElse(defaultValue);
    }

    /**
     * Get a single short value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<Short> getShortOr(String key) {
        return getOr(key).map(i -> Short.parseShort(i));
    }

    /**
     * Get all short values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default short[] getAllShort(String key) {
        return toPrimitiveShortArray(Arrays.asList(getAll(key)).stream().map(v -> Short.parseShort(v)).toArray());
    }

    /**
     * Get all short values given a key or a default value if no such key exists.
     * The returned array may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @param defaultValues default values
     * @return values
     */
    default short[] getAllShortOr(String key, short... defaultValues) {
        return getAllShortOr(key).orElse(defaultValues);
    }

    /**
     * Get all short values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<short[]> getAllShortOr(String key) {
        var arr = getAllOr(key).map(s -> Arrays.asList(s).stream().map(v -> Short.parseShort(v)).toArray());
        return arr.isEmpty() ? Optional.empty() : Optional.of(toPrimitiveShortArray(arr.get()));
    }

    /**
     * Get a float value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default float getFloat(String key) {
        return Float.parseFloat(get(key));
    }

    /**
     * Get a single float value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default float getFloatOr(String key, float defaultValue) {
        return getOr(key).map(i -> Float.parseFloat(i)).orElse(defaultValue);
    }

    /**
     * Get a single float value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<Float> getFloatOr(String key) {
        return getOr(key).map(i -> Float.parseFloat(i));
    }

    /**
     * Get all float values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default float[] getAllFloat(String key) {
        return toPrimitiveFloatArray(Arrays.asList(getAll(key)).stream().map(v -> Float.parseFloat(v)).toArray());
    }

    /**
     * Get all float values given a key or a default value if no such key exists.
     * The returned array may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @param defaultValues default values
     * @return values
     */
    default float[] getAllFloatOr(String key, float... defaultValues) {
        return getAllFloatOr(key).orElse(defaultValues);
    }

    /**
     * Get all float values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<float[]> getAllFloatOr(String key) {
        var arr = getAllOr(key).map(s -> Arrays.asList(s).stream().map(v -> Float.parseFloat(v)).toArray());
        return arr.isEmpty() ? Optional.empty() : Optional.of(toPrimitiveFloatArray(arr.get()));
    }

    /**
     * Get a boolean value given it's key. If the key does not exist, 
     * an {@link IllegalArgumentException} will be thrown. If there are multiple
     * values for a key, the first value will be returned.
     *  
     * @param key key
     * @return value
     */
    default boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    /**
     * Get a single boolean value given it's key, or a default value if does not exist.
     * If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @param defaultValue default value
     * @return value or default
     */
    default boolean getBooleanOr(String key, boolean defaultValue) {
        return getOr(key).map(i -> Boolean.parseBoolean(i)).orElse(defaultValue);
    }

    /**
     * Get a single boolean value given it's key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. If there are multiple values for a key, the first value will be returned.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<Boolean> getBooleanOr(String key) {
        return getOr(key).map(i -> Boolean.parseBoolean(i));
    }

    /**
     * Get all boolean values given a key. If no such key exists, an
     * {@link IllegalArgumentException} will be thrown. The returned array
     * may potentially be empty, but never <code>null</code>.
     *  
     * @param key key
     * @return values
     */
    default boolean[] getAllBoolean(String key) {
        return toPrimitiveBooleanArray(Arrays.asList(getAll(key)).stream().map(v -> Boolean.parseBoolean(v)).toArray());
    }

    /**
     * Get all boolean values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default boolean[] getAllBooleanOr(String key, boolean[] defaultValues) {
        return getAllBooleanOr(key).orElse(defaultValues);
    }

    /**
     * Get all boolean values given a key. If no such key exists, {@link Optional#isEmpty()}
     * will return <code>true</code>. The returned array may potentially be empty.
     *  
     * @param key key
     * @return optional value
     */
    default Optional<boolean[]> getAllBooleanOr(String key) {
        var arr = getAllOr(key).map(s -> Arrays.asList(s).stream().map(v -> Boolean.parseBoolean(v)).toArray());
        return arr.isEmpty() ? Optional.empty() : Optional.of(toPrimitiveBooleanArray(arr.get()));
    }

    private static short[] toPrimitiveShortArray(final Object[] shortList) {
        final short[] primitives = new short[shortList.length];
        int index = 0;
        for (Object object : shortList) {
            primitives[index++] = ((Short) object).shortValue();
        }
        return primitives;
    }

    private static boolean[] toPrimitiveBooleanArray(final Object[] booleanList) {
        final boolean[] primitives = new boolean[booleanList.length];
        int index = 0;
        for (Object object : booleanList) {
            primitives[index++] = object == Boolean.TRUE ? true : Boolean.FALSE;
        }
        return primitives;
    }

    private static float[] toPrimitiveFloatArray(final Object[] floatList) {
        final float[] primitives = new float[floatList.length];
        int index = 0;
        for (Object object : floatList) {
            primitives[index++] = ((Float) object).floatValue();
        }
        return primitives;
    }

    private static Collection<Float> arrayToList(float[] values) {
        var l = new ArrayList<Float>(values.length);
        for(var v : values)
            l.add(v);
        return l;
    }

    private static Collection<Double> arrayToList(double[] values) {
        var l = new ArrayList<Double>(values.length);
        for(var v : values)
            l.add(v);
        return l;
    }

    private static Collection<Short> arrayToList(short[] values) {
        var l = new ArrayList<Short>(values.length);
        for(var v : values)
            l.add(v);
        return l;
    }

    private static Collection<Boolean> arrayToList(boolean[] values) {
        var l = new ArrayList<Boolean>(values.length);
        for(var v : values)
            l.add(v);
        return l;
    }

}