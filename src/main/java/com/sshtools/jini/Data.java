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

import com.sshtools.jini.INI.Section;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public interface Data {

    public abstract class AbstractData implements Data {

        final Map<String, Section[]> sections;
        final Map<String, String[]> values;
        final boolean preserveOrder;
        final boolean caseInsensitiveKeys;
        final boolean caseInsensitiveSections;

        AbstractData(boolean preserveOrder, boolean caseInsensitiveKeys, boolean caseInsensitiveSections,
                Map<String, String[]> values, Map<String, Section[]> sections) {
            super();
            this.sections = sections;
            this.values = values;
            this.preserveOrder = preserveOrder;
            this.caseInsensitiveKeys = caseInsensitiveKeys;
            this.caseInsensitiveSections = caseInsensitiveSections;
        }

        AbstractData(boolean preserveOrder, boolean caseInsensitiveKeys, boolean caseInsensitiveSections) {
            this(preserveOrder, caseInsensitiveKeys, caseInsensitiveSections,
                    INIReader.createPropertyMap(preserveOrder, caseInsensitiveKeys),
                    INIReader.createSectionMap(preserveOrder, caseInsensitiveSections));
        }

        @Override
        public boolean containsKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public boolean containsSection(String key) {
            return sections.containsKey(key);
        }

        @Override
        public void remove(Section section) {
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

        @Override
        public void putAll(String key, String... values) {
            this.values.put(key, values);

        }

        @Override
        public void putAll(String key, int... values) {
            this.values.put(key, IntStream.of(values).boxed().map(i -> i.toString()).toArray((s) -> new String[s]));
        }

        @Override
        public void putAllShort(String key, short... values) {
            this.values.put(key, arrayToList(values).stream().map(i -> i.toString()).toArray((s) -> new String[s]));
        }

        @Override
        public void putAll(String key, long... values) {
            this.values.put(key, LongStream.of(values).boxed().map(i -> i.toString()).toArray((s) -> new String[s]));
        }

        @Override
        public void putAll(String key, float... values) {
            this.values.put(key, arrayToList(values).stream().map(i -> i.toString()).toArray((s) -> new String[s]));
        }

        @Override
        public void putAll(String key, double... values) {
            this.values.put(key, arrayToList(values).stream().map(i -> i.toString()).toArray((s) -> new String[s]));
        }

        @Override
        public void putAll(String key, boolean... values) {
            this.values.put(key, arrayToList(values).stream().map(i -> {
                return i.toString();
            }).toArray((s) -> new String[s]));
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
        public Optional<Section[]> allSectionsOr(String key) {
            return Optional.ofNullable(sections.get(key));
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
                    newSection = new Section(preserveOrder, caseInsensitiveKeys, caseInsensitiveKeys,
                            parent == null ? this : parent, name);
                    (parent == null ? sections : parent.sections).put(name, new Section[] { newSection });
                } else {
                    if (last) {
                        newSection = new Section(preserveOrder, caseInsensitiveKeys, caseInsensitiveKeys,
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
    }

    Map<String, String[]> values();

    Map<String, Section[]> sections();

    default Section section(String key) {
        return sectionOr(key)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No section with key {0}", key)));
    }
    
    boolean containsKey(String key);
    
    boolean containsSection(String key);

    default void put(String key, String value) {
        putAll(key, value);
    }

    default void put(String key, int value) {
        putAll(key, value);
    }

    default void put(String key, short value) {
        putAllShort(key, value);
    }

    default void put(String key, long value) {
        putAll(key, value);
    }

    default void put(String key, float value) {
        putAll(key, value);
    }

    default void put(String key, double value) {
        putAll(key, value);
    }

    default void put(String key, boolean value) {
        putAll(key, value);
    }

    void putAll(String key, String... values);

    void putAll(String key, int... values);

    void putAllShort(String key, short... values);

    void putAll(String key, long... values);

    void putAll(String key, float... values);

    void putAll(String key, double... values);

    void putAll(String key, boolean... values);

    default void put(String key, Collection<String> values) {
        putAll(key, values.toArray(new String[0]));
    }

    default void putInt(String key, Collection<Integer> values) {
        putAll(key, values.stream().mapToInt(i -> i).toArray());

    }

    default void putShort(String key, Collection<Short> values) {
        var result = new short[values.size()];
        var i = 0;
        for (var f : values) {
            result[i++] = f;
        }
        putAllShort(key, result);
    }

    default void putLong(String key, Collection<Long> values) {
        putAll(key, values.stream().mapToLong(i -> i).toArray());
    }

    default void putFloat(String key, Collection<Float> values) {
        var result = new float[values.size()];
        var i = 0;
        for (var f : values) {
            result[i++] = f;
        }
        putAll(key, result);
    }

    default void putDouble(String key, Collection<Double> values) {
        putAll(key, values.stream().mapToDouble(i -> i).toArray());

    }

    default void putBoolean(String key, Collection<Boolean> values) {
        var result = new boolean[values.size()];
        var i = 0;
        for (var f : values) {
            result[i++] = f;
        }
        putAll(key, result);

    }

    default Optional<Section> sectionOr(String key) {
        var all = allSectionsOr(key);
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

    default Section[] allSections(String key) {
        return allSectionsOr(key)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No section with key {0}", key)));
    }

    Optional<Section[]> allSectionsOr(String key);

    Section create(String... path);

    default String get(String key) {
        return getOr(key)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No property with key {0}", key)));
    }

    default String getOr(String key, String defaultValue) {
        return getOr(key).orElse(defaultValue);
    }

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

    default String[] getAll(String key) {
        return getAllOr(key)
                .orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No property with key {0}", key)));
    }

    default String[] getAllOr(String key, String[] defaultValues) {
        return getAllOr(key).orElse(defaultValues);
    }

    Optional<String[]> getAllOr(String key);

    default double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    default double getDoubleOr(String key, double defaultValue) {
        return getOr(key).map(i -> Double.parseDouble(i)).orElse(defaultValue);
    }

    default Optional<Double> getDoubleOr(String key) {
        return getOr(key).map(i -> Double.parseDouble(i));
    }

    default double[] getAllDouble(String key) {
        return Arrays.asList(getAll(key)).stream().mapToDouble(v -> Double.parseDouble(v)).toArray();
    }

    default double[] getAllDoubleOr(String key, double[] defaultValues) {
        return getAllDoubleOr(key).orElse(defaultValues);
    }

    default Optional<double[]> getAllDoubleOr(String key) {
        return getAllOr(key).map(s -> Arrays.asList(s).stream().mapToDouble(v -> Double.parseDouble(v)).toArray());
    }

    default long getLong(String key) {
        return Long.parseLong(get(key));
    }

    default long getLongOr(String key, long defaultValue) {
        return getOr(key).map(i -> Long.parseLong(i)).orElse(defaultValue);
    }

    default Optional<Long> getLongOr(String key) {
        return getOr(key).map(i -> Long.parseLong(i));
    }

    default long[] getAllLong(String key) {
        return Arrays.asList(getAll(key)).stream().mapToLong(v -> Long.parseLong(v)).toArray();
    }

    default long[] getAllLongOr(String key, long[] defaultValues) {
        return getAllLongOr(key).orElse(defaultValues);
    }

    default Optional<long[]> getAllLongOr(String key) {
        return getAllOr(key).map(s -> Arrays.asList(s).stream().mapToLong(v -> Long.parseLong(v)).toArray());
    }

    default int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    default int getIntOr(String key, int defaultValue) {
        return getOr(key).map(i -> Integer.parseInt(i)).orElse(defaultValue);
    }

    default Optional<Integer> getIntOr(String key) {
        return getOr(key).map(i -> Integer.parseInt(i));
    }

    default int[] getAllInt(String key) {
        return Arrays.asList(getAll(key)).stream().mapToInt(v -> Integer.parseInt(v)).toArray();
    }

    default int[] getAllIntOr(String key, int[] defaultValues) {
        return getAllIntOr(key).orElse(defaultValues);
    }

    default Optional<int[]> getAllIntOr(String key) {
        return getAllOr(key).map(s -> Arrays.asList(s).stream().mapToInt(v -> Integer.parseInt(v)).toArray());
    }

    default int getShort(String key) {
        return Short.parseShort(get(key));
    }

    default int getShortOr(String key, short defaultValue) {
        return getOr(key).map(i -> Short.parseShort(i)).orElse(defaultValue);
    }

    default Optional<Short> getShortOr(String key) {
        return getOr(key).map(i -> Short.parseShort(i));
    }

    default short[] getAllShort(String key) {
        return toPrimitiveShortArray(Arrays.asList(getAll(key)).stream().map(v -> Short.parseShort(v)).toArray());
    }

    default short[] getAllShortOr(String key, short[] defaultValues) {
        return getAllShortOr(key).orElse(defaultValues);
    }

    default Optional<short[]> getAllShortOr(String key) {
        var arr = getAllOr(key).map(s -> Arrays.asList(s).stream().map(v -> Short.parseShort(v)).toArray());
        return arr.isEmpty() ? Optional.empty() : Optional.of(toPrimitiveShortArray(arr.get()));
    }

    default float getFloat(String key) {
        return Float.parseFloat(get(key));
    }

    default float getFloatOr(String key, float defaultValue) {
        return getOr(key).map(i -> Float.parseFloat(i)).orElse(defaultValue);
    }

    default Optional<Float> getFloatOr(String key) {
        return getOr(key).map(i -> Float.parseFloat(i));
    }

    default float[] getAllFloat(String key) {
        return toPrimitiveFloatArray(Arrays.asList(getAll(key)).stream().map(v -> Float.parseFloat(v)).toArray());
    }

    default float[] getAllFloatOr(String key, float[] defaultValues) {
        return getAllFloatOr(key).orElse(defaultValues);
    }

    default Optional<float[]> getAllFloatOr(String key) {
        var arr = getAllOr(key).map(s -> Arrays.asList(s).stream().map(v -> Float.parseFloat(v)).toArray());
        return arr.isEmpty() ? Optional.empty() : Optional.of(toPrimitiveFloatArray(arr.get()));
    }

    default boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    default boolean getBooleanOr(String key, boolean defaultValue) {
        return getOr(key).map(i -> Boolean.parseBoolean(i)).orElse(defaultValue);
    }

    default Optional<Boolean> getBooleanOr(String key) {
        return getOr(key).map(i -> Boolean.parseBoolean(i));
    }

    default boolean[] getAllBoolean(String key) {
        return toPrimitiveBooleanArray(Arrays.asList(getAll(key)).stream().map(v -> Boolean.parseBoolean(v)).toArray());
    }

    default boolean[] getAllBooleanOr(String key, boolean[] defaultValues) {
        return getAllBooleanOr(key).orElse(defaultValues);
    }

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

    void remove(Section section);

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