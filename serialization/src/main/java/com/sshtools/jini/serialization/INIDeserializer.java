package com.sshtools.jini.serialization;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.serialization.INISerialization.TypeReflectionBehaviour;

/**
 * De-serializes an object from an INI file. Do not directly construct, use a {@link Builder} or
 * one of the convenience methods such as {@link #fromString(String ini, Class<T> type)}.
 */
public final class INIDeserializer extends AbstractSerDeser {
	
	/**
	 * Convenience method to construct a Java object from an {@link INI} document.
	 * 
	 * @param <T> type of object
	 * @param ini document
	 * @param type type
	 * @return object
	 */
	public static <T> T fromINI(INI ini, Class<T> type) {
		return new Builder().build().deserialize(ini, type);
	}

	/**
	 * Convenience method to construct a Java object from an INI format string.
	 * 
	 * @param <T> type of object
	 * @param ini string in INI format
	 * @param type type
	 * @return object
	 */
	public static <T> T fromString(String ini, Class<T> type) {
		return fromINI(INI.fromString(ini), type);
	}

	/**
	 * Convenience method to read an object stored in INI format from a file path.
	 * 
	 * @param <T> type of object
	 * @param path path of file
	 * @param type type
	 * @return object
	 */
	public static <T> T read(String path, Class<T> type) {
		return read(Paths.get(path), type);
	}

	/**
	 * Convenience method to read an object stored in INI format from a file path.
	 * 
	 * @param <T> type of object
	 * @param path path of file
	 * @param type type
	 * @return object
	 */
	public static <T> T read(Path path, Class<T> type) {
		try {
			return fromINI(new INIReader.Builder().build().read(path), type);
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		} catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Convenience method to read an object in INI format from a {@link Reader}.
	 * 
	 * @param <T> type of object
	 * @param reader reader
	 * @param type type
	 * @return object
	 */
	public static <T> T read(Reader reader, Class<T> type) {
		try {
			return fromINI(new INIReader.Builder().build().read(reader), type);
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		} catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	/**
	 * Build a new {@link INIDeserializer}
	 */
	public final static class Builder extends AbstractBuilder<INIDeserializer, Builder> {
		
		/**
		 * Build
		 */
		public INIDeserializer build() {
			return new INIDeserializer(this);
		}
	}

	@Override
	protected boolean isInclude(TypeReflectionBehaviour trb, Method method) {
		/* TODO check name pattern too */
		if(method.getParameterCount() != 1 || Modifier.isStatic(method.getModifiers())) {
			return false;
		}
		return super.isInclude(trb, method);
	}

	private INIDeserializer(Builder builder) {
		super(builder);
	}
	
	/**
	 * De-serialize an object from an {@link INI} document.
	 * 
	 * @param <T> type
	 * @param ini document
	 * @param type type
	 * @return object
	 */
	public <T> T deserialize(INI ini, Class<T> type) {
		return deserializeObject(ini, constructOrNull(ini, type));
	}
	
	private <T> T constructOrNull(Data data, Class<T> type) {
		return isNull(data) ? null : construct(type);
	}
	
	private boolean isNull(Data data) {
		return data.contains(INISerializer.INSTRUCTION_KEY) && data.get(INISerializer.INSTRUCTION_KEY).equals(INISerializer.NULL);
	}

	private <T> T deserializeObject(Data data, T object) {
		if(object != null) {
			var clz = object.getClass();
			var trb = reflectionBehaviour.orElseGet(() -> typeAnnotation(clz));
			
			var flds = fields(trb, clz);
			var mthds = methods(trb, clz);
			
			/* First, all fields that are single primitives, or collections of primitives, just add as plain keys  */
			flds.stream().filter(FieldInfo::isPrimitiveOrGrouping).forEach(f-> {
				setPrimitivesOrGrouping(data, object, f);
			});

			/* Second, all methods that returns single primitives, or collections of primitives, just add as plain keys */
			mthds.stream().filter(MethodInfo::isPrimitiveOrGrouping).forEach(f-> {
				setPrimitivesOrGrouping(data, object, f);
			});

			
			/* Third, all fields that are complex objects, add each as a section */
			flds.stream().filter(FieldInfo::isObject).forEach(f-> {
				setObjects(data, object, f);
			});

			
			/* Fourth, all methods that return complex objects, add each as a section */
			mthds.stream().filter(MethodInfo::isObject).forEach(f-> {
				setObjects(data, object, f);
			});
			
		}
		return object;
	}

	private <T> void setObjects(Data data, T object, MemberInfo f) {
		var k = f.resolveKey();
		if(f.isMap()) {
			throw new UnsupportedOperationException();
		}
		else {
			if(data.containsSection(k)) {
				var sec = data.section(k);
				f.set(object, deserializeObject(sec, constructOrNull(sec, f.type())));
			}
			else {
				f.set(object, null);
			}
		}
	}

	private <T> void setPrimitivesOrGrouping(Data data, T object, MemberInfo f) {
		var k = f.resolveKey();
		if(f.isMap()) {
			
			@SuppressWarnings("unchecked")
			var current = (Map<Object, Object>)f.get(object);
			
			if(data.containsSection(k)) {
				if(current == null) {
					if(f.type().equals(Map.class)) {
						current = new LinkedHashMap<>();
						f.set(object, current);
					}
					else {
						throw new IllegalArgumentException("If a Map is not the generic type, and is null, it must be created at construction, either as an initialized field, or in a default constructor.");
					}
				}	
				var map = current;
				map.clear();
				
				var sec = data.section(k);
				sec.values().forEach((sk,sv) -> {
					/* TODO type of key will need to be conveyed somehow */
					if(f.isCollection()) {
//						current.put(sk, parseStrings(object, sv, f));
						throw new UnsupportedOperationException();
					}
					else {
						map.put(sk, parseString(sv[0], f.itemType()));
					}
				});
			}
			else if(current != null) {				
				current.clear();
			}
		}
		else {
			if(f.isCollection()) {
				if(isPrimitive(f.itemType())) {
					/* Collection of primitives */
					if(data.contains(k)) {
						f.set(object, parseStrings(object, data.getAll(k), f));
					}
					else {
						clearCollection(object, f);
					}
				}
				else {
					/* Collection of objects */
					if(data.containsSection(k)) {
						var allSecs = data.allSections(k);
						f.set(object, 
							toCollection(object, 
								Arrays.asList( allSecs).stream().
									map(sec ->deserializeObject(sec, constructOrNull(sec, f.itemType()))), 
								allSecs.length, 
								f));
						;
					}
					else {
						clearCollection(object, f);
					}
				}
			}
			else {
				/* Single primitive. Single objects handled by setObjects */
				if(data.contains(k)) {
					f.set(object, parseString(data.get(k), f.type()));
				}
				else {
					f.set(object, null);
				}
			}
			
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void clearCollection(T object, MemberInfo f) {
		var current = f.get(object);
		if(current != null) {
			if(f.type().isArray()) {
				f.set(object, Array.newInstance(f.itemType(), 0));
			}
			else {
				((Collection<Object>)current).clear();
			}
		}
	}

	private Object parseStrings(Object obj, String[] strs, MemberInfo f) {
		return toCollection(obj, Arrays.asList(strs).stream().map(s -> parseString(s, f.itemType())), strs.length, f);
	}

	private Object toCollection(Object obj, Stream<Object> items, int count, MemberInfo f) {
		var itype = f.itemType();
		if(f.type().isArray()) {
			var arr = (Object[])Array.newInstance(itype, count);
			var idx = new AtomicInteger();
			items.forEach(i -> arr[idx.getAndIncrement()] = i);
			return arr;
		}
		else {
			@SuppressWarnings("unchecked")
			var collection = (Collection<Object>)f.get(obj);
			if(collection == null) {
				var ctype = f.type();
				if(Set.class.equals(ctype)) {
					collection = new LinkedHashSet<>();
				}
				else if(List.class.equals(ctype)) {
					collection = new ArrayList<>();
				}
				else {
					throw new IllegalArgumentException("A null collection name `" + f.resolveKey() + "` is of a type that cannot be automatically constructed. Construct it your self during object initialisation, either as an initialized field or in a default constructor.");
				}
			}
			else {
				collection.clear();
			}
			items.forEach(collection::add);			
			return collection;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object parseString(String str, Class<?> type) {
		if(isData(type)) {
			var data = Base64.getDecoder().decode(str);
			if(type.equals(ByteBuffer.class)) {
				return ByteBuffer.wrap(data);
			}
			else {
				return data;
			}
		}
		else if(type.isEnum()) {
			return Enum.valueOf((Class<Enum>) type, str);
		}
		else if(type.equals(String.class)) {
			return str;
		}
		else if(BigInteger.class.isAssignableFrom(type)) {
			return new BigInteger(str);
		}
		else if(Long.class.isAssignableFrom(type) || type == long.class) {
			return Long.parseLong(str);
		}
		else if(Double.class.isAssignableFrom(type) || type == double.class) {
			return Double.parseDouble(str);
		}
		else if(Float.class.isAssignableFrom(type) || type == float.class) {
			return Float.parseFloat(str);
		}
		else if(Integer.class.isAssignableFrom(type) || type == int.class) {
			return Integer.parseInt(str);
		}
		else if(Short.class.isAssignableFrom(type) || type == short.class) {
			return Short.parseShort(str);
		}
		else if(Character.class.isAssignableFrom(type)  || type == char.class) {
			return str.equals("") ? '\0' : Character.valueOf(str.charAt(0));
		}
		else if(Boolean.class.isAssignableFrom(type)  || type == boolean.class) {
			return Boolean.parseBoolean(str);
		}
		else 
			throw new UnsupportedOperationException("Unknown primitive type " + type);
	}
	
}
