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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sshtools.jini.serialization.INISerialization.FieldReflectionBehaviour;
import com.sshtools.jini.serialization.INISerialization.INIField;
import com.sshtools.jini.serialization.INISerialization.INIMethod;
import com.sshtools.jini.serialization.INISerialization.INISerialized;
import com.sshtools.jini.serialization.INISerialization.Inheritance;
import com.sshtools.jini.serialization.INISerialization.MemberInfo;
import com.sshtools.jini.serialization.INISerialization.MethodNamePattern;
import com.sshtools.jini.serialization.INISerialization.MethodReflectionBehaviour;
import com.sshtools.jini.serialization.INISerialization.Rule;
import com.sshtools.jini.serialization.INISerialization.TypeReflectionBehaviour;

abstract class AbstractSerDeser {
	
	static abstract class AbstractBuilder<A extends  AbstractSerDeser, BLDR extends AbstractBuilder<A, BLDR>> {
		private Optional<TypeReflectionBehaviour> reflectionBehaviour = Optional.empty();
		private Optional<Function<Field, FieldReflectionBehaviour>> fieldReflectionBehaviour = Optional.empty();
		private Optional<Function<Method, MethodReflectionBehaviour>> methodReflectionBehaviour = Optional.empty();
		
		public abstract A build();
		
		@SuppressWarnings("unchecked")
		public BLDR withAttributeReflectionBehaviour(Function<Field, FieldReflectionBehaviour> fieldReflectionBehaviour) {
			this.fieldReflectionBehaviour = Optional.of(fieldReflectionBehaviour);
			return (BLDR)this;
		}
		
		@SuppressWarnings("unchecked")
		public BLDR withMethodReflectionBehaviour(Function<Method, MethodReflectionBehaviour> methodReflectionBehaviour) {
			this.methodReflectionBehaviour = Optional.of(methodReflectionBehaviour);
			return (BLDR)this;
		}
		
		@SuppressWarnings("unchecked")
		public BLDR withTypeReflectionBehaviour(TypeReflectionBehaviour behaviour) {
			this.reflectionBehaviour = Optional.of(behaviour);
			return (BLDR)this;
		}
	}
	
	class FieldInfo implements MemberInfo {
		private Field field;
		FieldReflectionBehaviour behavior;

		FieldInfo(Field field, FieldReflectionBehaviour behaviour) {
			this.field = field;
			this.behavior = behaviour;
		}
		
		@Override
		public Optional<String> resolveReference() {
			return behavior.reference();
		}

		@Override
		public Object get(Object obj) {
			try {
				return field.get(obj);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new IllegalStateException("Could not get value of " + resolveKey() + ".", e);
			}
		}

		@Override
		public Class<?> itemType() {
			var t = behavior.itemType();
			if(t.equals(Void.class)) {
				if(field.getType().isArray()) {
					return field.getType().getComponentType();
				}
				else {
					throw new IllegalStateException("Could not resolve collection item type. Use an array, or add a `INIFIeld.itemType` annotation attribute.");
				}
			}
			else {
				return t;
			}
		}

		@Override
		public String resolveKey() {
			return behavior.key().equals("") ? field.getName() : behavior.key();
		}

		@Override
		public void set(Object obj, Object val) {
			try {
				field.set(obj, (Object)val);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new IllegalStateException("Could not set value of " + resolveKey() + ".", e);
			}
		}
		
		@Override
		public Class<?> type() {
			return field.getType();
		}
	}
	
	class MethodInfo implements MemberInfo {
		private Method method;
		MethodReflectionBehaviour behavior;
		TypeReflectionBehaviour typeBehavior;

		MethodInfo(Method method, MethodReflectionBehaviour behaviour, TypeReflectionBehaviour typeBehavior) {
			this.method = method;
			this.behavior = behaviour;
			this.typeBehavior = typeBehavior;
		}
		
		@Override
		public Optional<String> resolveReference() {
			return behavior.reference();
		}

		@Override
		public Object get(Object obj) {
			try {
				return method.invoke(obj);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException("Could not get value of " + resolveKey() + ".", e);
			}
		}

		@Override
		public Class<?> itemType() {
			var t = behavior.itemType();
			if(t.equals(Void.class)) {
				if(type().isArray()) {
					return type().getComponentType();
				}
				else {
					throw new IllegalStateException("Could not resolve collection item type. Use an array, or add a `INIFIeld.itemType` annotation attribute.");
				}
			}
			else {
				return t;
			}
		}

		@Override
		public String resolveKey() {
			return behavior.key().equals("") ? processedMethodName() : behavior.key();
		}

		@Override
		public void set(Object obj, Object val) {
			try {
				method.invoke(obj, (Object)val);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException("Could not set value of " + resolveKey() + ".", e);
			}
		}

		@Override
		public Class<?> type() {
			if(method.getParameterCount() == 0)
				return method.getReturnType();
			else
				return method.getParameterTypes()[0];
		}

		String processedMethodName() {
			var mname = method.getName();
			switch(resolvePattern()) {
			case BEAN:
				if(isIs(mname)) {
					return mname.substring(2, 3).toLowerCase() + mname.substring(3);
				}
				else if(isGetterOrSetter(mname)) {
					return mname.substring(3, 4).toLowerCase() + mname.substring(4);					
				}
				else {
					throw new IllegalStateException("Cannot derive key from method name `" + mname + "`. Use the `key` attribute.");
				}
			case TERSE:
				return mname;
			default:
				throw new UnsupportedOperationException();
			}
		}

		MethodNamePattern resolvePattern() {
			var pattern = behavior.pattern();
			if(pattern == MethodNamePattern.DEFAULT) {
				pattern = typeBehavior.methodNamePattern();
				if(pattern == MethodNamePattern.DEFAULT) {
					pattern = MethodNamePattern.BEAN;
				}
			}
			return pattern;
		}
	}
	
	protected final Optional<TypeReflectionBehaviour> reflectionBehaviour;
	protected final Optional<Function<Field, FieldReflectionBehaviour>> fieldReflectionBehaviour;
	protected final Optional<Function<Method, MethodReflectionBehaviour>> methodReflectionBehaviour;
	
	AbstractSerDeser(AbstractBuilder<?, ?> builder) {
		this.reflectionBehaviour = builder.reflectionBehaviour;
		this.fieldReflectionBehaviour = builder.fieldReflectionBehaviour;
		this.methodReflectionBehaviour = builder.methodReflectionBehaviour;
	}
	
	protected final List<FieldInfo> fields(TypeReflectionBehaviour trb, Class<?> clz) {
		return Arrays.asList(trb.inheritance() == Inheritance.SUPERS ? clz.getDeclaredFields() : clz.getFields()).
				stream().
				filter(f -> isInclude(trb, f)).
				map(f -> {
					var frb = fieldBehaviour(f);
					if(needsAccess(f) && trb.accessableFields())
						f.setAccessible(true);
					return new FieldInfo(f, frb); 
				}).
				collect(Collectors.toList());
	}

	protected boolean isInclude(TypeReflectionBehaviour trb, Method method) {
		if((method.getName().equals("hashCode") && method.getParameterCount() == 0) ||
		   (method.getName().equals("equals") && method.getParameterCount() == 1) ||
		   (method.getName().equals("toString") && method.getParameterCount() == 0))
			return false;
		
		var frb = methodBehaviour(method);
		
		var typeDefaultRule = trb.rule();
		if(typeDefaultRule == Rule.DEFAULT) {
			typeDefaultRule = Rule.INCLUDE;
			if(trb.methodNamePattern() == MethodNamePattern.BEAN) {
				if(!isIs(method.getName()) && !isGetterOrSetter(method.getName())) {
					typeDefaultRule = Rule.EXCLUDE;
				}
			}
		}
		
		var rule = frb.rule();
		if(rule == Rule.DEFAULT) {
			rule = typeDefaultRule;
		}
		
		return rule == Rule.INCLUDE;
	}

	protected final List<MethodInfo> methods(TypeReflectionBehaviour trb, Class<?> clz) {
		var asList = Arrays.asList(trb.inheritance() == Inheritance.SUPERS ? clz.getDeclaredMethods() : clz.getMethods());
		return asList.
				stream().
				filter(f -> { 
					return isInclude(trb, f);
				}).
				map(f -> {
					var frb = methodBehaviour(f);
					if(needsAccess(f) && trb.accessableFields())
						f.setAccessible(true);
					return new MethodInfo(f, frb, trb); 
				}).
				collect(Collectors.toList());
	}

	protected final TypeReflectionBehaviour typeAnnotation(Class<?> clz) {
		var annot = clz.getAnnotation(INISerialized.class);
		if(annot == null)
			return TypeReflectionBehaviour.defaultBehaviour();
		else {
			return new TypeReflectionBehaviour() {
				
				@Override
				public boolean accessableFields() {
					return annot.privateFields();
				}
				
				@Override
				public boolean accessableMethods() {
					return annot.privateMethods();
				}
				
				@Override
				public Inheritance inheritance() {
					return annot.inheritance();
				}
				
				@Override
				public Rule rule() {
					return annot.rule();
				}

				@Override
				public MethodNamePattern methodNamePattern() {
					return annot.methodNamePattern();
				}
			};
		}
	}

	private final FieldReflectionBehaviour fieldAnnotation(Field clz) {
		var annot = clz.getAnnotation(INIField.class);
		if(annot == null)
			return FieldReflectionBehaviour.defaultBehaviour();
		else {
			return new FieldReflectionBehaviour() {

				@Override
				public Class<?> itemType() {
					return annot.itemType();
				}

				@Override
				public String key() {
					return annot.key();
				}

				@Override
				public Rule rule() {
					return annot.rule();
				}

				@Override
				public Optional<String> reference() {
					var ref = annot.reference(); 
					return ref.equals("") ? Optional.empty() : Optional.of(ref);
				}
				
			};
		}
	}
	
	private final FieldReflectionBehaviour fieldBehaviour(Field field) {
		return fieldReflectionBehaviour.map(f -> f.apply(field)).orElseGet(() -> fieldAnnotation(field));
	}
	
	private final boolean isInclude(TypeReflectionBehaviour trb, Field field) {
		var typeDefaultRule = trb.rule();
		if(typeDefaultRule == Rule.DEFAULT)
			typeDefaultRule = Rule.INCLUDE;
		
		var frb = fieldBehaviour(field);
		var rule = frb.rule();
		if(rule == Rule.DEFAULT) {
			rule = typeDefaultRule;
		}
		
		if(Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
			rule = Rule.EXCLUDE;
		}
		
		return rule == Rule.INCLUDE;
	}
	
	private final MethodReflectionBehaviour methodAnnotation(Method clz) {
		var annot = clz.getAnnotation(INIMethod.class);
		if(annot == null)
			return MethodReflectionBehaviour.defaultBehaviour();
		else {
			return new MethodReflectionBehaviour() {

				@Override
				public Class<?> itemType() {
					return annot.itemType();
				}

				@Override
				public String key() {
					return annot.key();
				}

				@Override
				public MethodNamePattern pattern() {
					return annot.pattern();
				}

				@Override
				public Rule rule() {
					return annot.rule();
				}

				@Override
				public Optional<String> reference() {
					var ref = annot.reference(); 
					return ref.equals("") ? Optional.empty() : Optional.of(ref);
				}

			};
		}
	}
	
	private final MethodReflectionBehaviour methodBehaviour(Method method) {
		return methodReflectionBehaviour.map(f -> f.apply(method)).orElseGet(() -> methodAnnotation(method));
	}
	
	private final boolean needsAccess(Field f) {
		return !Modifier.isPublic(f.getModifiers());
	}
	
	private final boolean needsAccess(Method f) {
		return !Modifier.isPublic(f.getModifiers());
	}

	private static boolean isGetterOrSetter(String mname) {
		return (mname.startsWith("set") || mname.startsWith("get")) && mname.length() > 3;
	}

	private static boolean isIs(String mname) {
		return mname.startsWith("is") && mname.length() > 2;
	}
}
