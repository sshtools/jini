# jini

[![Maven Build/Test JDK 17](https://github.com/sshtools/jini/actions/workflows/maven.yml/badge.svg)](https://github.com/sshtools/jini/actions/workflows/maven.yml)

A small Java library to read and write [INI](https://en.wikipedia.org/wiki/INI_file) files. 
 
 * Zero dependencies.
 * Configurable to be usable with most variants of the format.
 * Global sections.
 * Nested sections.
 * Line continuations
 * Configurable delimiters.
 * Configurable whitespace usage.
 * Supports various modes for handling of duplicate properties and sections.
 * Configurable case sensitivity.
 * Configurable order preservation.
 * JPMS compliant.
 
## WIP

 * Tests (84% coverage so far)
 
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
