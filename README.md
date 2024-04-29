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
 * Requires JDK 11 or above (JDK 17 for tests).
 * Optional [Preferences](#preferences-backing-store) implementation.
 * String interpolation with configurable variable pattern. 
 
## WIP

 * Tests (see above badge for current coverage)
 
## Installation

Available on Maven Central, so just add the following dependency to your project's `pom.xml`.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>jini-lib</artifactId>
    <version>0.3.0</version>
</dependency>
    
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
    <version>0.3.0</version>
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


## Changes

### 0.3.2

 * More work on `INISchema`, can now wrap an `INI` to provide a proxied instance that guarantees correctness.

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