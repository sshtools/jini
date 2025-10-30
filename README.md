# jini

![Maven Build/Test JDK 17](https://github.com/sshtools/jini/actions/workflows/maven.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.sshtools/jini/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.sshtools/jini)
[![Coverage Status](https://coveralls.io/repos/github/sshtools/jini/badge.svg)](https://coveralls.io/github/sshtools/jini)
[![javadoc](https://javadoc.io/badge2/com.sshtools/jini/javadoc.svg)](https://javadoc.io/doc/com.sshtools/jini)
![JPMS](https://img.shields.io/badge/JPMS-com.sshtools.jini-purple) 

A small Java library to read and write [INI](https://en.wikipedia.org/wiki/INI_file) files. 
 
 * Zero dependencies.
 * Configurable to be usable with most variants of the format.
 * Global sections.
 * Nested sections.
 * Line continuations
 * Configurable delimiters.
 * Configurable whitespace usage.
 * Multiple modes for handling of duplicate properties and sections.
 * Multiple modes for handling of multi-value keys
 * Multiple modes for handling of escape characters
 * Configurable case sensitivity.
 * Configurable order preservation.
 * JPMS compliant.
 * String interpolation with configurable variable pattern.
 * Requires JDK 11 or above (JDK 17 for tests).
 * Supports comments.
 
## Optional Modules

 * [Preferences](#preferences-backing-store) implementation.
 * [Object Serialization and De-serialization](#object-serialization-and-de-serialization) using reflection or plain Java.
 * Use a [Schema](#schemas) to describe your document and ensure it always contains valid data.
 * A generic [Configuration](#configuration) API with change monitoring. Similar to the prefences API, but using the native API with support for schemas.
 
## WIP

 * Tests (see above badge for current coverage)
 * `jini-schema` current just returns a document that always complies with the schema, there are currently no validation or generation tools (planned).
 
## Installation

Available on Maven Central, so just add the following dependency to your project's `pom.xml`.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>jini-lib</artifactId>
    <version>0.5.5</version>
</dependency>
```

_See badge above for version available on Maven Central. Snapshot versions are in the [Sonatype OSS Snapshot Repository](https://oss.sonatype.org/content/repositories/snapshots/)._

```xml
<repository>
    <id>oss-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots</url>
    <snapshots />
    <releases>
        <enabled>false</enabled>
    </releases>
</repository>
```
### JPMS

If you are using [JPMS](https://en.wikipedia.org/wiki/Java_Platform_Module_System), add `com.sshtools.jini` to your `module-info.java`.

### Build From Source

Using [Apache Maven](maven.apache.org/) is recommended.

 * Clone this module
 * Change directory to where you cloned to
 * Run `mvn package`
 * Jar Artifacts will be in the `target` directory.
 
## Usage

The general pattern for reading an INI document is ..

 * Create a configured `INIReader` via `INIReader.Builder`.
 * Get an `INI` instance using `reader.read(...)`.
 * Query the `INI` instance for sections, properties etc.
 
And for writing an INI document ..

 * Either use an `INI` document you have obtained from an `INIReader`, create a default 
   document (order preserved, case insensitive keys) using `INI.create()`, or use `INI.Builder()` to configure behaviour.
 * Create a configured `INIWriter` via `INIWriter.Builder`.
 * Write the instance to some target using `INIWriter.write(..)`.
 
## Example


### Write A New INI Document

```java
    var ini = INI.create();
    ini.put("Name", "Alice");
    ini.put("Age", 34);
    ini.put("Registered", false);
    
    var sec = ini.create("Address");
    sec.put("Street", "15 Stone Lane");
    sec.put("Area", "");
    sec.put("City", "Arbington");
    sec.put("County", "Inishire");
    sec.put("PostCode", "ABC 123");
    
    var wrt = new INIWriter.Builder().build();
    
    try(var out = Files.newBufferedWriter(Paths.get("data.ini"))) {
        wrt.write(ini, out);
    }
```

### Read An INI Document From A File

```java
    var ini = INI.fromFile(Paths.get("data.ini"));
    System.out.format("Name: %s%n". ini.get("Name")); 
    System.out.format("Age: %d%n". ini.getInt("Age"));
    if(ini.getBoolean("Registered"))
        System.out.println("Is registered%n");
    
    ini.sectionOr("Address").ifPresent(s -> {  
        System.out.println("Address");
        System.out.format("  Street: %s%n". s.get("Street"));
        System.out.format("  Area: %s%n". s.get("Area"));
        System.out.format("  City: %s%n". s.get("City"));
        System.out.format("  County: %s%n". s.get("County"));
        System.out.format("  PostCode: %s%n". s.get("PostCode"));
        System.out.format("  Tel: %s%n". s.get("PostCode", "N/A"));
    });
    
```

## Preferences Backing Store

An optional [java.util.prefs.Preferences](https://docs.oracle.com/javase/8/docs/api/java/util/prefs/Preferences.html) implementation is available, which will
replace the default when added to the CLASSPATH. 

Instead of storing preferences in the registry, or XML files, or whatever your platform might use by default, all preferences would be stored in `.ini` files in appropriate locations.

Just add the following dependency to your project's `pom.xml`.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>jini-prefs</artifactId>
    <version>0.5.6</version>
</dependency>
```

### Usage

The `.ini` file backend will not be initialised until the first time you access a `Preferences` node. Files will not be written until the first `.put()` or `.flush()`. 

```java
	var prefs = Preferences.userRoot();
	prefs.put("akey", "Some Value");
```

It is possible to configure INI based preferences if you do so *before* the first access to
a `Preferences` node.

```java
	var bldr = new INIStoreBuilder().
				withScope(Scope.USER).
				withoutAutoFlush().
				withName("jini.test").
				withPath(Files.createTempDirectory("jinitestdir"));
				
	var store = bldr.build();
	INIPreferences.configure(store);		
	var prefs = Preferences.userRoot();
	prefs.put("akey", "Some Value");
	System.out.println(prefs.get("akey", "No value!"));
	
	// ...
```

You can also use this builder make use of the `Preferences` API without using the static methods in `Preferences` such as `systemRoot()`. You can create as many roots as you like (to different file paths), and make use of them in any manner you like.

```java
	var bldr = new INIStoreBuilder().
				withScope(Scope.USER).
				withoutAutoFlush().
				withName("jini.test").
				withPath(Files.createTempDirectory("jinitestdir"));
				
	try (var store = bldr.build()) {
		var prefs = store.root();
		prefs.put("akey", "Some Value");
		System.out.println(prefs.get("akey", "No value!"));
	}			
```

Note that a `store` is scoped, and should be closed when finished with.

## Object Serialization and De-serialization

The optional `jini-serialization` module adds support for writing an object graph as human-readable INI files, as well as reading such INI files and recreating said object graph.

Just add the following dependency to your project's `pom.xml`.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>jini-serialization</artifactId>
    <version>0.5.6</version>
</dependency>
```

### Limitations

 * Collections and Maps must provide an `itemType` for their values for de-serialization to work. 
 * Maps currently only support `String` keys, and values can only be primitive types. 
 * The entire object graph is serialized (subject to include / exclude rules). There is no support for object references yet.

### Usage

Serialization and de-serialization works using Java's reflection feature. It will look for accessible fields that are not otherwise configured to be excluded, inspect their value and convert it an entry in a INI section with a key and a string value. If the value is a complex object, it may create further INI sections and inspect the object for it's values, and so on.

Sometimes fields may be not be immediately accessible, for example if they are `private`. By default, Jini will try to make such fields accessible to reflection.

Modern Java has further restriction on accessing such fields, and it reflection may not be possible. In these cases, Jini will also look for accessor *methods* that can be reflected and provide the value. By default, it expects to use the JavaBean pattern, i.e. "getter" and "setter" methods. 

#### Serialization - Writing An Object

The entry point to serializing an object can be via a (reusable) `INISerializer` which itself cannot be constructed directly, but instead is created by `INISerializer.Builder`. Once an `INISerializer` is obtained, you then call `srlzr.serialize(myObject)` which will return an `INI` object that you mean then write as normal (e.g. using an `INIWriter`). 

Alternatively, you can use one of several convenience methods.

 * `INISerializer.toINI(Object object)` creates an `INI` from an `Object`. 
 * `INISerializer.write(Object object, String path)` and `INISerializer.writer(Object object, Path path)` writes an object to a file.
 * `INISerializer.write(Object object, Writer writer)` writes the contents of the `INI` to a `Writer`. 

If your object contains only primitives, `String` and other primitive objects, `ByteBuffer`, any other `Object` that follows follows the same rules, or  arrays of such objects or primitives, then no additional code will be to allow serialization to happen.

```java
class Address {
	String line1 = "99 Some Road";
	String city = "Nodnol";
}

class Person {
	String name = "Mr Crumbly";
	int age = 99;
	Address address = new Address();
	String[] telephones = new String[] { "123 456789", "987 654321" };
}

INISerialize.write(new Person(), "crumbly.ini");
```

If your object contains any other `Object` that does not fit this description, or a `Collection` or `Map` of any type, then you many need to add additional meta-data (such as `itemType` to overcome type-erasure) to allow Jini to serialize the object.

This can be done by either adding annotations to the target object, or using the programmatic callback API provided by `INISerializer.Builder`. This customisation also allows fields or methods to be excluded, have different `key`s used in the INI file and other behavioural changes. 

#### De-serialization - Reading An Object

The entry point to de-serializing an object can be via a (reusable) `INIDeserializer` which itself cannot be constructed directly, but instead is created by `INIDeserializer.Builder`. Once an `INIDeserializer` is obtained, you then call `desrlzr.deserialize(ini, MyObject.class)` which will return an instance of type `MyObject` constructed from the data in an `INI` document that you obtained as normal (e.g. using an `INIReader`). 

Alternatively, you can use one of several convenience methods.

 * `INIDeserializer.fromINI(INI ini, MyObject.class)` creates a `MyObject` from an `INI`. 
 * `INIDeserializer.read(String path, MyObject.class)` and `INISerializer.read(Path path, MyObject.class)` reads an object from a file.
 * `INISerializer.read(Reader reader, MyObject.class)` reader INI contents from a `Reader` and construct a `MyObject` from it. 

```java
class Address {
	String line1;
	String city;
}

class Person {
	String name;
	int age;
	Address address;
	String[] telephones;
}

var person = INIDeserializer.read("crumbly.ini", Person.class);
```

The same rules apply as in serialization, i.e. when the type of an object cannot be derived any other way it must be provided programmatically or via an annotation.  The same annotations used in serialization are also used for de-serialization.

De-serialization imposes some additional requirements and restrictions too.

 * All types that have no special handling, must have empty public constructors.
 * For `Collection` and `Map` types, if you want it to be of a particular type of collection or map, you should make sure it is initialized in construction (i.e. an initialized field, or created in a default constructor). In this case it must be a modifiable. If the field is `null` when deserializing, and data for the collection is present, then an unmodifiable variant of the collections will be automatically created and filled.
 
## Schemas

Schemas allow you to specify rules as to the content allowed in a document including data types, sections and default values.

### Writing A Schema

A schema is itself just an INI document. 

 * Each *Key* that you might use in a document, has a corresponding *Section* in the schema. 
 * For each *Section* in the document, there will again be a corresponding *Section* in the schema.
 * Each *Key* must have a `type` attribute in its schema section. 
 * A schema section without a `type` key is treated as a *Section*.

```
[name]
	name = Name
	description = The users name. 
	type = TEXT
	
[subscribed]
	name = Subscribed
	description = When true, will be sent notifications.
	type = BOOLEAN
	default-value = TRUE

[favourite-number]
	name = Favourite Number
	description = The users favourite number.
	type = NUMBER
	default-value = 50

[role]
	name = Role
	description = The users role.
	type = ENUM
	value = ADMINISTRATOR, STANDARD, GUEST
	default-value = GUEST
```

### Creating The Facade

You then wrap an existing `INI` instance with a schema, producing another `INI` instance that is guaranteed to conform to the schema. E.g. if no value for a particular key exists, its default value (if any) will be returned.

```java
var schema = INISchema.fromFile(Paths.get("data.schema.ini"));
var ini = INI.fromFile(Paths.get("data.ini"));
var wrapped = schema.facadeFor(wrapped);
```

There are a number of convenience methods, and a builder `INISchema.Builder` that may be used to create a schema. 

## Configuration

The `jini-config` module provides a small framework for dealing with a common pattern you might use in many applications. If any of the following are true, this module may be for you.

 * Your application requires some kind of configuration that can be broken up into multiple files.
 * You want to provide a public known location where other applications (or users) might add configuration that you can read ("Drop-in" configuration directories).
 * You want to monitor external changes to configuration, and have it reload automatically.
 * Your applications provides default configuration in system configuration directories, but you want to allow the user to override (some?) of them per-user.
 * You don't want to worry yourself with where to store your configuration files, you want them placed in the correct locations for the current operation system.
 
While `jini-config` depends on `jini-schema` (you must always at least include it), you do not have to use a schema, although it is highly recommended you use one.

The module centres around an `INISet`. This represents a set of configuration files of a particular *Name* for a particular *Application*. A single *Application* may have many `INISet`s each with a different *Name*. While supported, it is not really recommended to use multiple named applications in the same physical application.

### Simple Example

`INISet` is obtained using `INISet.Builder`. The constructor for this 

```java

// Make the config set
var set =  new INISet.Builder("bookmarks").
    withApp("my-file-browser").
    withScopes(Scope.USER).
    build();
    
// Set a value in the document
var ini = set.document();
ini.put("bookmark1", "https://jadaptive.com");
    
```

After running this code, if you were to look in `$HOME\.config\my-file-browser` (on Linux or Mac OS), or `%HOME%\AppData\Roaming\my-file-browser` (on Windows), you would see a `bookmarks.ini` file you a single value in it.


## Credits

Thanks to others who have contributed to Jini.

 * [A248](https://github.com/A248)

## Changes

### 0.6.2

 * `Monitor` might crash sometimes if multiple files are being watched.

### 0.6.1

 * *Feature* Generic attributes can be added to schema items. Use `attributes()` in `KeyDescriptor` and `SectionDescriptor`. Add a section `[<item-name>.attributes]` with 
   arbitrary keys. Recommend using colon notation for keys, and prefix with the `x:` key,
   e.g. `x:prefererred-presentation`. 
 * *Bug* Could not have an empty string as a value. 

### 0.6.0

 * `INIParseException` now used instead of `ParseException` that carries that line number as well. Existing code using ParseException should be fine. Thanks [A248](https://github.com/A248) for [PR #3](https://github.com/sshtools/jini/pull/3).
 * Single-line string value would not be escaped with some `INIWriter` configurations.

### 0.5.6

 * Allow comments to be set from `List<String>` as well as string arrays.
 * Typo in the build `withtLineSeparator()` should read `withLineSeparator()`.
 * Certain `EscapeMode` would not work with line continuations.

### 0.5.5

 * Initial support for handling comments. Comments arrays can be added to documents, sections and
   keys. They can be read with `INIReader` and are written back with `INIWriter`.
 * Mac OS is now treated same as Linux for `jini-configuration` default locations (i.e. user config in `$HOME/.config/<app>`).

### 0.5.4

 * `Monitor` never started.

### 0.5.3

 * False positives for detecting if deprecated `arity` was being used.

### 0.5.2

 * `INISet` would not include a parent section if it ONLY had child sections with no keys.
    This was fixed by applying the same multiplicity rules of keys to sections.

### 0.5.1

 * `INISet` now by default will only use one scope, either `USER` or `GLOBAL`. If the path where the global scoped files are located is writable, then the scope is `GLOBAL`, otherwise it is `USER`. To return to previous behaviour of both scopes, use `withAllScopes()` on  the builder.

### 0.5.0

 * Schema multi-value mode is now `REPEATED_KEY`.
 * `INISet` (via `INISchema`) can now generate a default commented INI file from the schema.
 * Lots of tests and fixes for `INISchema`.
 * `Arity` renamed to `Multiplicity`.

### 0.4.1

 * Append a new section in an `INISet` would fail (the first section would be replaced).
 * Added `Section.index()`, returning the index of the section in its parent.

### 0.4.0
 * New `jini-serialization` module for object serialization and deserialization. See `INISerializer` and `INIDeserializer`.
 * `duplicateSectionAction` default is now `DuplicateAction.APPEND`. This means multiple sections with the same name by default will now all be available.
 * Multi-line string support for keys and values. Wrap strings in supported quote character, e.g `''' ..... '''` in a manner similar to Java. 
 * Arity support in schema.
 * Minor bug fixes.

### 0.3.3

 * Fixes for reloading.
 * Removal of value events not fired by `INISet`.
 * Removed schema types `FLOAT`, `LOCATION` and `COLOR`. These are now `Discriminator` enumeration instances.  There are two discriminator implementations, `TextDiscriminator` to be used for `Type.TEXT` and `NumberDiscriminator` to be used for `Type.NUMBER`. The list of discriminators is now likely to grow instead of the `Type`.


### 0.3.2

 * More work on `INISchema`, can now wrap an `INI` to provide a proxied instance that guarantees correctness.
 * Created `jini-config` module that can be used to provide a monitored INI based configuration system for applications.

### 0.3.1

 * Added string interpolation features.
 * Renamed some getters to shorten them, and also remove an ambiguity with `getAllOr()`.
 * Separated values separator wasn't being ignored in quotes.

### 0.3.0

 * Added `EscapeMode` for `INIWriter` and `INIReader` that allows altering behaviour of escaping.
 * `java.util.prefs.Preferences` implementation

### 0.2.5

 * Added `Section.readOnly()` and `Document.readOnly()` to create a read-only facades.
 * Added `INI.empty()` that returns a static empty and read-only document. 
 * Events are fired from all parent `Section` or `INI` on value or child section change. 
 * Added `obtainSetion()` that gets or creates sections given a path.

### 0.2.4

 * Added `onValueUpdate()` and `onSectionUpdate()` to listen for changes to document.

### 0.2.3

 * Added `keys()` method.
 * Made `containsSection(String)` now support testing for nested sections by changing the signature to `containsSection(String...)`.

## Credits

Uses [LinkedCaseInsensitiveMap](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/LinkedCaseInsensitiveMap.html) from Spring Utilities.