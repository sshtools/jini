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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.sshtools.jini.INI.Section;

public interface Data {

	public class AbstractData implements Data {

		final Map<String, Section[]> sections;
		final Map<String, String[]> values;

		AbstractData(Map<String, String[]> values, Map<String, Section[]> sections) {
			this.values = values;
			this.sections = sections;
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
	}

	Map<String, String[]> values();

	Map<String, Section[]> sections();

	default Section section(String key) {
		return sectionOr(key)
				.orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No section with key {0}", key)));
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
}