package com.sshtools.jini.schema;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;

public class INISchema {
	
	public interface Converter<T> {
		T toType(String str);
		
		default String toString(T type) {
			return type.toString();
		}
	}

	public enum Type {
		ENUM, BOOLEAN, TEXT, NUMBER
	}

	public final static class Builder {
		private INI ini;
		private Map<String, Supplier<List<?>>> lists = new HashMap<>();

		public Builder fromDocument(INI ini) {
			this.ini = ini;
			return this;
		}

		public Builder fromFile(Path path) {
			return fromDocument(INI.fromFile(path));
		}

		public Builder fromInput(InputStream in) {
			return fromDocument(INI.fromInput(in));
		}

		public Builder withList(String name, Supplier<List<?>> supplier) {
			lists.put(name, supplier);
			return this;
		}

		public INISchema build() {
			return new INISchema(this);
		}

	}

	public final static class KeyDescriptor<T> implements Converter<T> {
		private final String key;
		private final Optional<String> description;
		private final Type type;
		private final List<T> values;
		private final Converter<T> converter;
		private final Optional<T> defaultValue;

		private KeyDescriptor(String key, Type type, List<T> values, Optional<T> defaultValue, Optional<String> description, Converter<T> converter) {
			super();
			this.key = key;
			this.type = type;
			this.values = values;
			this.converter = converter;
			this.defaultValue = defaultValue;
			this.description = description;
		}
		
		public String description() {
			return description.orElseThrow(() -> new IllegalStateException(key + " has no description"));
		}
		
		public Optional<String> descriptionOr() {
			return description;
		}
		
		public T defaultValue() {
			return defaultValue.orElseThrow(() -> new IllegalStateException(key + " has no default value"));
		}
		
		public Optional<T> defaultValueOr() {
			return defaultValue;
		}
		
		public List<T> values() {
			return values;
		}

		public String key() {
			return key;
		}

		public Type type() {
			return type;
		}

		@Override
		public T toType(String str) {
			return converter.toType(str);
		}

		@Override
		public String toString(T val) {
			return converter.toString(val);
		}
	}

	public static INISchema fromFile(Path path) {
		return new Builder().fromFile(path).build();
	}

	public static INISchema fromInput(InputStream in) {
		return new Builder().fromInput(in).build();
	}

	private final INI ini;

	private INISchema(Builder bldr) {
		this.ini = bldr.ini;
	}

	public <T> Optional<KeyDescriptor<T>> descriptorOr(Section section, String key, Converter<T> converter) {
		var path = section.path();
		var keyDescPath = new String[path.length + 1];
		System.arraycopy(path, 0, keyDescPath, 0, path.length);
		keyDescPath[path.length] = key;
		return ini.sectionOr(keyDescPath).map(kd ->
			new KeyDescriptor<T>(
					key, 
					Type.valueOf(kd.get("type")),
					kd.getAllOr("value").map(str-> convertList(str, converter)).orElse(Collections.emptyList()),
					kd.getOr("default-value").map(str-> converter.toType(str)),
					kd.getOr("description"),
					converter
			)
		);
	}
	
	<T> List<T> convertList(String[] vals, Converter<T> converter) {
		return Arrays.asList(vals).stream().map(v -> converter.toType(v)).collect(Collectors.toList());
	}
}
