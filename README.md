# jini

![Maven Build/Test JDK 17](https://github.com/sshtools/jini/actions/workflows/maven.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.sshtools/jini/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.sshtools/jini)
[![Coverage Status](https://coveralls.io/repos/github/sshtools/jini/badge.svg)](https://coveralls.io/github/sshtools/jini)
![Static Badge](https://img.shields.io/badge/JPMS-com.sshtools.jini-green)

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
 * Configurable case sensitivity.
 * Configurable order preservation.
 * JPMS compliant.
 * Currently requires JDK 17 (may backport to Java 11 if there is any interest)
 
## WIP

 * Tests (see above badge for current coverage)
 
## Installation

Available on Maven Central, so just add the following dependency to your project's `pom.xml`.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>com.sshtools</artifactId>
    <version>0.0.4</version>
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
