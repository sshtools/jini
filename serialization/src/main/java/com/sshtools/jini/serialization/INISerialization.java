package com.sshtools.jini.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Defines annotations, enums and interfaces used to provide object serialization to and from 
 * INI format text files.
 */
public interface INISerialization {
	
	//
	// Enums
	//

	/**
	 * Use with {@link INIField} and {@link INISerialized} to declare if a field or
	 * method should be ignore for serialization or de-serialization. 
	 */
	public enum Rule {
		/**
		 * Include field or method for serialization / de-serialization
		 */
		INCLUDE, 
		/**
		 * Exclude field or method from serialization / de-serialization
		 */
		EXCLUDE, 
		/**
		 * Use the default rule for  serialization / de-serialization. If this is used
		 * with {@link INIField} or {@link INIMethod}, then the rule from {@link INISerialized} will be used.
		 */
		DEFAULT
	}
	
	/**
	 * Use with {@link INISerialized} to declare whether super class(es) variables should
	 * be serialized too. The default is to do so.
	 */
	public enum Inheritance {
		/**
		 * Only serialize or de-serialize members in the concrete object.
		 */
		CONCRETE, SUPERS
	}
	
	/**
	 * The pattern to use for accessor methods when fields cannot directly be accessed. 
	 */
	public enum MethodNamePattern {
		/**
		 * Bean pattern, i.e. getters and setters (and iss). 
		 */
		BEAN, 
		/**
		 * Method name is same as field name with no prefix or suffix.  
		 */
		TERSE,
		/**
		 * Use the default pattern. If this is used
		 * with {@link INIField} or {@link INIMethod}, then the patternfrom {@link INISerialized} will be used.
		 */
		DEFAULT
	}
	
	//
	// Annotations
	//
	
	/**
	 * Type annotation to describe overall serialization / de-serialization behaviour
	 * for the class as a whole
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface INISerialized {
		/**
		 * Whether to search super classes for members fields and methods.
		 * 
		 * @return inheritance
		 */
		Inheritance inheritance() default Inheritance.SUPERS;
		
		/**
		 * Whether to exclude or include explicitly or use the default rule (INCLUDE)
		 * @return rule
		 */
		Rule rule() default Rule.DEFAULT;
		
		/**
		 * Try to acesss private fields.
		 * 
		 * @return private fields
		 */
		boolean privateFields() default true;
		
		/**
		 * Try to access private methods
		 * 
		 * @return private methods
		 */
		boolean privateMethods() default true;
		
		/**
		 * The method name pattern to use.
		 * 
		 * @return method name pattern
		 */
		MethodNamePattern methodNamePattern() default MethodNamePattern.BEAN;
	}

	/**
	 * Field annotation to describe how to behave with individual member fields.	 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface INIField {
		/**
		 * The key to use in the INI file for this item. When not-supplied (blank string),
		 * will derive from field or method name.
		 * 
		 * @return key
		 */
		String key() default "";
		
		/**
		 * Whether to exclude or include explicitly or use the default rule (from {@link INISerialized})
		 * @return rule
		 */
		Rule rule() default Rule.DEFAULT;
		
		/**
		 * If this a collection or map (type of {@link Collection} or {@link Map}), the type of items cannot
		 * be derived at runtime so must be explicitly provided.
		 * 
		 * @return item type
		 */
		Class<?> itemType() default Void.class;
		
		/**
		 * Set a reference if this should serialize an object reference instead
		 * of the object itself, or if it should de-serialize from an object reference
		 * instead of INI data. 
		 * <p>
		 * A blank string indicates to serialize the object itself.
		 * 
		 * @return reference reference
		 */
		String reference() default "";
	}
	
	/**
	 * Method annotation to describe how to behave with individual methods.	 
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface INIMethod {

		/**
		 * The key to use in the INI file for this item. When not-supplied (blank string),
		 * will derive from field or method name.
		 * 
		 * @return key
		 */
		String key() default "";
		
		/**
		 * Whether to exclude or include explicitly or use the default rule (from {@link INISerialized})
		 * @return rule
		 */
		Rule rule() default Rule.DEFAULT;

		/**
		 * The method name pattern to use.
		 * 
		 * @return method name pattern
		 */
		MethodNamePattern pattern() default MethodNamePattern.BEAN;

		/**
		 * If this a collection or map (type of {@link Collection} or {@link Map}), the type of items cannot
		 * be derived at runtime so must be explicitly provided. {@link Void.class} means to try discover
		 * automatically.
		 * 
		 * @return item type
		 */
		Class<?> itemType() default Void.class;
		
		/**
		 * Set a reference if this should serialize an object reference instead
		 * of the object itself, or if it should de-serialize from an object reference
		 * instead of INI data. 
		 * <p>
		 * A blank string indicates to serialize the object itself.
		 * 
		 * @return reference reference
		 */
		String reference() default "";
	}
	
	//
	// Interfaces
	//

	/**
	 * Can be used to programmatically provide the same information as {@link INIField}. Use
	 * with {@link INISerializer.Builder} or {@link INIDeserializer.Builder}.
	 */
	public interface FieldReflectionBehaviour {

		/**
		 * Defaults
		 */
		public final static class Defaults {
			private final static FieldReflectionBehaviour DEFAULT = new FieldReflectionBehaviour() {
	
				@Override
				public String key() {
					return "";
				}
	
				@Override
				public Rule rule() {
					return Rule.DEFAULT;
				}
	
				@Override
				public Class<?> itemType() {
					return Void.class;
				}

				@Override
				public Optional<String> reference() {
					return Optional.empty();
				}
				
			};
		}

		/**
		 * The key to use in the INI file for this item. When not-supplied (blank string),
		 * will derive from field or method name.
		 * 
		 * @return key
		 */
		String key();

		/**
		 * Whether to exclude or include explicitly or use the default rule (from {@link INISerialized})
		 * @return rule
		 */
		Rule rule();

		/**
		 * If this a collection or map (type of {@link Collection} or {@link Map}), the type of items cannot
		 * be derived at runtime so must be explicitly provided. {@link Void.class} means to try discover
		 * automatically.
		 * 
		 * @return item type
		 */
		Class<?> itemType();
		
		/**
		 * Resolve a reference that should be used during serialization or de-serialization.
		 * 
		 * @return reference reference
		 */
		Optional<String> reference();
	
		static FieldReflectionBehaviour defaultBehaviour() {
			return FieldReflectionBehaviour.Defaults.DEFAULT;
		}
	}

	/**
	 * Can be used to programmatically provide the same information as {@link INIMethod}. Use
	 * with {@link INISerializer.Builder} or {@link INIDeserializer.Builder}.
	 */
	public interface MethodReflectionBehaviour {

		/**
		 * Defaults
		 */
		public final static class Defaults {
			private final static MethodReflectionBehaviour DEFAULT = new MethodReflectionBehaviour() {

				@Override
				public String key() {
					return "";
				}

				@Override
				public Rule rule() {
					return Rule.DEFAULT;
				}

				@Override
				public MethodNamePattern pattern() {
					return MethodNamePattern.BEAN;
				}

				@Override
				public Class<?> itemType() {
					return Void.class;
				}

				@Override
				public Optional<String> reference() {
					return Optional.empty();
				}
				
			};
		}

		/**
		 * The key to use in the INI file for this item. When not-supplied (blank string),
		 * will derive from field or method name.
		 * 
		 * @return key
		 */
		String key();

		/**
		 * Whether to exclude or include explicitly or use the default rule (from {@link INISerialized})
		 * @return rule
		 */
		Rule rule();

		/**
		 * The method name pattern to use.
		 * 
		 * @return method name pattern
		 */
		MethodNamePattern pattern();

		/**
		 * If this a collection or map (type of {@link Collection} or {@link Map}), the type of items cannot
		 * be derived at runtime so must be explicitly provided. {@link Void.class} means to try discover
		 * automatically.
		 * 
		 * @return item type
		 */
		Class<?> itemType();
		
		/**
		 * Resolve a reference that should be used during serialization or de-serialization.
		 * 
		 * @return reference reference
		 */
		Optional<String> reference();
		
		static MethodReflectionBehaviour defaultBehaviour() {
			return MethodReflectionBehaviour.Defaults.DEFAULT;
		}
	}


	/**
	 * Can be used to programmatically provide the same information as {@link INISerialized}. Use
	 * with {@link INISerializer.Builder} or {@link INIDeserializer.Builder}.
	 */
	public interface TypeReflectionBehaviour {
		
		public final static class Defaults {
			private final static TypeReflectionBehaviour DEFAULT = new TypeReflectionBehaviour() {

				@Override
				public Inheritance inheritance() {
					return Inheritance.SUPERS;
				}

				@Override
				public boolean accessableFields() {
					return true;
				}

				@Override
				public boolean accessableMethods() {
					return true;
				}

				@Override
				public Rule rule() {
					return Rule.DEFAULT;
				}

				@Override
				public MethodNamePattern methodNamePattern() {
					return MethodNamePattern.BEAN;
				}
				
			};
		}

		/**
		 * Whether to search super classes for members fields and methods.
		 * 
		 * @return inheritance
		 */
		Inheritance inheritance();
		
		/**
		 * Try to acesss private fields.
		 * 
		 * @return private fields
		 */
		boolean accessableFields();
		
		/**
		 * Try to access private methods
		 * 
		 * @return private methods
		 */
		boolean accessableMethods();
		
		/**
		 * Whether to exclude or include explicitly or use the default rule (INCLUDE)
		 * @return rule
		 */
		Rule rule();
		
		/**
		 * The method name pattern to use.
		 * 
		 * @return method name pattern
		 */
		MethodNamePattern methodNamePattern();
		
		static TypeReflectionBehaviour defaultBehaviour() {
			return TypeReflectionBehaviour.Defaults.DEFAULT;
		}
	}
	
	interface MemberInfo {
		Object get(Object obj);
		
		default boolean isCollection() {
			return INISerialization.isCollection(type());
		}
		
		default boolean isMap() {
			return INISerialization.isMap(type());
		}
		
		default boolean isPrimitiveOrGrouping() {
			return INISerialization.isPrimitiveOrGrouping(type());
		}
		
		default boolean isObject() {
			return !isPrimitiveOrGrouping();
		}
		
		Class<?> itemType();
		
		String resolveKey();
		
		Optional<String> resolveReference();
		
		void set(Object obj, Object val);
		
		Class<?> type();
	}
	

	
	static boolean isData(Class<?> type) {
		return ( type.isArray() && type.getComponentType().equals(byte.class) ) ||
				ByteBuffer.class.isAssignableFrom(type);
	}

	static boolean isPrimitiveOrGrouping(Class<?> type) {
		return isPrimitive(type) || 
				   isCollection(type) ||
				   isMap(type);
	}
	
	static boolean isPrimitive(Class<?> type) {
		return type.isPrimitive() ||
			   type.isEnum() ||
			   Number.class.isAssignableFrom(type) ||
			   type.equals(Boolean.class) ||
			   type.equals(Character.class) ||
			   type.equals(BigInteger.class) ||
			   type.equals(String.class) ||
			   isData(type);
	}
	
	static <T> T construct(Class<T> type) {
		try {
			return (T)type.getConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	static boolean isCollection(Class<?> type) {
		return ( type.isArray() ||
				Collection.class.isAssignableFrom(type) ) && !isData(type);
	}
	

	static boolean isMap(Class<?> type) {
		return Map.class.isAssignableFrom(type);
	}
}
