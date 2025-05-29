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
package com.sshtools.jini.serialization;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.serialization.INISerialization.MemberInfo;
import com.sshtools.jini.serialization.INISerialization.TypeReflectionBehaviour;

/**
 * Serializes an object as an INI file. Do not directly construct, use a {@link Builder} or
 * one of the convenience methods such as {@link #asString(Object)}.
 */
public final class INISerializer extends AbstractSerDeser {
	
	/**
	 * Implementations generate a string reference key given all
	 * the known information about an object value.
	 * <p>
	 * If default behaviour is required (i.e. fully serialize the object), then
	 * a {@link Optional#empty()} should be returned.
	 */
	public interface ReferenceResolver extends Function<MemberInfo, Optional<String>> {
	}
	
	public static final String INSTRUCTION_KEY = "__instruction";
	public static final String NULL = "NULL";

	/**
	 * Convenience method to convert an object to a string in INI format.
	 * 
	 * @param object object
	 * @return 
	 */
	public static String asString(Object object) {
		return toINI(object).asString();
	}

	/**
	 * Convenience method to convert an object to an {@link INI} document.
	 * 
	 * @param object object
	 * @return document
	 */
	public static INI toINI(Object object) {
		return new Builder().build().serialize(object);
	}

	/**
	 * Convenience method to write an object to a file path in INI format.
	 * 
	 * @param object object
	 * @param path of file
	 */
	public static void write(Object object, String path) {
		write(object, Paths.get(path));
	}

	/**
	 * Convenience method to write an object to a file path in INI format.
	 * 
	 * @param object object
	 * @param path of file
	 */
	public static void write(Object object, Path path) {
		try {
			new INIWriter.Builder().build().write(toINI(object), path);
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	/**
	 * Convenience method to write an object to a {@link Writer} in INI format.
	 * 
	 * @param object object
	 * @param path of file
	 */
	public static void write(Object object, Writer writer) {
		new INIWriter.Builder().build().write(toINI(object), writer); 
	}
	
	/**
	 * Build a new {@link INISerializer}.
	 */
	public final static class Builder extends AbstractBuilder<INISerializer, Builder> {
		
		private Optional<ReferenceResolver> referenceResolver = Optional.empty();
		
		/**
		 * Use a {@link ReferenceResolver} to generate a string reference given everything
		 * known about an object value to be serialized.
		 * <p>
		 * If a reference should not be generated, the {@link Optional#empty} should be returned
		 * indicating the object should be fully serialized.
		 */
		public Builder withReferenceResolver(ReferenceResolver referenceResolver) {
			this.referenceResolver = Optional.of(referenceResolver);
			return this;
		}
		
		/**
		 * Build
		 */
		public INISerializer build() {
			return new INISerializer(this);
		}
	}

	@Override
	protected boolean isInclude(TypeReflectionBehaviour trb, Method method) {
		/* TODO check name pattern too */
		if(method.getParameterCount() > 0 || Modifier.isStatic(method.getModifiers())) {
			return false;
		}
		return super.isInclude(trb, method);
	}

	private final Optional<ReferenceResolver> referenceResolver;

	private INISerializer(Builder builder) {
		super(builder);
		this.referenceResolver = builder.referenceResolver;
	}
	
	/**
	 * Serialize the object to an {@link INI} document.
	 * 
	 * @param object
	 * @return 
	 */
	public INI serialize(Object object) {
		return serializeObject(object, INI.create());
	}

	private <D extends Data> D serializeObject(Object object, D data) {
		
		if(object == null) {
			data.put(INSTRUCTION_KEY, NULL);
		}
		else {
			var clz = object.getClass();
			var trb = reflectionBehaviour.orElseGet(() -> typeAnnotation(clz));
			
			var flds = fields(trb, clz);
			var mthds = methods(trb, clz);
			
			/* First, all fields that are single primitives, or collections of primitives, just add as plain keys  */
			flds.stream().filter(FieldInfo::isPrimitiveOrGrouping).forEach(f-> {
				putPrimitivesOrGrouping(object, data, f);
			});

			/* Second, all methods that returns single primitives, or collections of primitives, just add as plain keys */
			mthds.stream().filter(m -> !data.contains(m.resolveKey())).filter(MethodInfo::isPrimitiveOrGrouping).forEach(f-> {
				putPrimitivesOrGrouping(object, data, f);
			});

			
			/* Third, all fields that are complex objects, add each as a section */
			flds.stream().filter(m -> !data.containsSection(m.resolveKey())).filter(FieldInfo::isObject).forEach(f-> {
				putObjects(object, data, f);
			});

			
			/* Fourth, all methods that return complex objects, add each as a section */
			mthds.stream().filter(m -> !data.containsSection(m.resolveKey())).filter(MethodInfo::isObject).forEach(f-> {
				putObjects(object, data, f);
			});
			
		}
		return data;
	}

	private <D extends Data> void putObjects(Object object, D data, MemberInfo f) {
		var val = f.get(object);
		if(f.isMap()) {
			var sec = data.create(f.resolveKey());
			@SuppressWarnings("unchecked")
			var map = (Map<Object, Object>)object;
			map.forEach((k,v) -> {
				sec.put(toString(k), toString(v));
			});
		}
		else if(f.isCollection()) {
			expand(val).forEach(v -> serializeObject(v, data.create(f.resolveKey())));
		}
		else {
			serializeObject(val, data.create(f.resolveKey()));
		}
	}

	private <D extends Data> void putPrimitivesOrGrouping(Object object, D data, MemberInfo f) {
		var val = f.get(object);
		if(val != null) {
			if(f.isMap()) {
				var sec = data.create(f.resolveKey());
				@SuppressWarnings("unchecked")
				var map = (Map<Object, Object>)f.get(object);
				map.forEach((k,v) -> {
					sec.put(toString(k), toString(v));
				});
			}
			else if(f.isCollection()) {
				var itype = f.itemType();
				if(INISerialization.isPrimitive(itype)) {
					data.putAll(f.resolveKey(), expand(val).map(this::toString).collect(Collectors.toList()).toArray(new String[0]));
				}
				else { 
					expand(val).forEach(o -> serializeObject(o, data.create(f.resolveKey())));
				}
			}
			else {
				data.putAll(f.resolveKey(), toString(val));
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Stream<Object> expand(Object object) {
		var type = object.getClass();
		if(type.isArray()) {
			var sz = Array.getLength(object);
			var l = new ArrayList<>(sz);
			for(int i = 0 ; i < sz; i++) {
				l.add(Array.get(object, i));
			}
			return l.stream();
		}
		else {
			return ((Collection)object).stream();
		}
	}
	
	private String toString(Object obj) {
		if(obj == null)
			return null;
		else {
			if(INISerialization.isData(obj.getClass())) {
				byte[] data;
				if(obj instanceof ByteBuffer) {
					var bb = (ByteBuffer)obj;
					if(bb.hasArray()) {
						data = bb.array();
					}
					else {
						data = new byte[bb.capacity()];
						bb.get(data, 0, bb.capacity());
					}
				}
				else {
					data = (byte[])obj;
				}
				return Base64.getEncoder().encodeToString(data);
			}
			else {
				return obj.toString();
			}
		}
	}
	
}
