package com.sshtools.jini.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.INI;
import com.sshtools.jini.INI.MergeMode;
import com.sshtools.jini.schema.INISchema.KeyDescriptor;
import com.sshtools.jini.schema.INISchema.SectionDescriptor;

public class INISchemaTest {

    @Test
    public void testReadOnly() throws Exception {
    	assertThrows(UnsupportedOperationException.class, () -> {
        	testschema().ini().readOnly().put("XXX", "YYY");
    	});
    }

    @Test
    public void testFacadeReadOnly() throws Exception {
    	assertThrows(UnsupportedOperationException.class, () -> {
        	var schm = testschema();
        	var ini = INI.create();
        	schm.facadeFor(ini).readOnly().put("XXX", "YYY");
    	});
    }

    @Test
    public void testMergeUnsuported() throws Exception {
    	assertThrows(UnsupportedOperationException.class, () -> {
        	var schm = testschema();
        	var ini = INI.create();
        	schm.facadeFor(ini).merge(MergeMode.FLATTEN_SECTIONS, ini, INI.create());
    	});
    }

    @Test
    public void testDefaultsFromSchemaGeneration() throws Exception {
        assertGeneratedDefaultsMatchExpected(toString(testschema()));
    }

    @Test
    public void testDefaultsFromSchemaGenerationToFile() throws Exception {
    	/* Use File instead of Path for more coverage cheaply */
    	var schm = testschema();
    	var tempf = File.createTempFile("jini", ".ini");
    	try {
    		/* Test can just force write */ 
	    	schm.writeDefaults(tempf);
	        assertGeneratedDefaultsMatchExpected(Files.readString(tempf.toPath()));

    		/* Delete and test if will write if doesn't exists */
	        tempf.delete();
	    	schm.maybeWriteDefaults(tempf);
	        assertGeneratedDefaultsMatchExpected(Files.readString(tempf.toPath()));
	        
    		/* Wait, maybe write again, and check nothing changed */
	        var modded = tempf.lastModified();
	        Thread.sleep(1001);
	    	schm.maybeWriteDefaults(tempf);
	    	assertEquals(modded, tempf.lastModified());
    	}
    	finally {
    		tempf.delete();
    	}
    }

    @Test
    public void testIOExceptionWritingDefaultsToFile() throws Exception {
    	assertThrows(UncheckedIOException.class, () -> {
    		var schm = testschema();
        	var tempf = Files.createTempDirectory("jini");
        	try {
        		/* Test can just force write */ 
    	    	schm.writeDefaults(tempf);
        	}
        	finally {
        		Files.delete(tempf);
        	}	
    	});
    	
    }

    @Test
    public void testSchemaFromFile() throws Exception {
    	var tempf = File.createTempFile("jini", ".schema.ini");
    	try(var out = new FileOutputStream(tempf)) {
    		try(var in = INISchemaTest.class.getResourceAsStream("INISchemaTest.schema.ini")) {
    			in.transferTo(out);
    		}
    	}
        assertGeneratedDefaultsMatchExpected(toString(INISchema.fromFile(tempf)));
    }

    @Test
    public void testSchemaFromDoc() throws Exception {
        assertGeneratedDefaultsMatchExpected(toString(INISchema.fromDocument(testschema().ini())));
    }

    @Test
    public void testSchemaAttrs() throws Exception {
        var schm = testschema();
        
        var sec = schm.section("section");
        assertEquals("A Section", sec.name());
        assertEquals("section", sec.key());
        assertEquals("section", String.join(".", sec.path()));
        assertEquals("A section to put other keys or sections in", sec.description());
        assertEquals(Multiplicity.ANY, sec.arity());
        
        assertSection1(schm.section("section", "section1"));
        assertSection2(schm.sectionOr("section", "section2").get());
        assertTrue(schm.sectionOr("section", "section88").isEmpty());
        
        assertEquals(sec.sections().size(), 2);
        var secSec = sec.sections().get(0);
		assertSection1(secSec);
        assertSection2(sec.sections().get(1));

        assertKey1(schm.keyFromPath("key1").get());
        assertKey2(schm.keyOr("key2").get());

        assertEquals(sec.keys().size(), 2);
        
        assertKey1A(sec.keys().get(0));
        assertKey1A(schm.keyFromPath("section.key1a").get());
        
        assertKey2A(sec.keys().get(1));
        assertKey2A(schm.keyFromPath("section.key2a").get());

        assertKey1B(schm.keyFromPath("section.section1.key1b").get());
        assertKey1C(schm.keyFromPath("section.section2.key1c").get());
        
    }

    @Test
    public void testFacadeForMultipleSections() throws Exception {
    	var schm = testschema();
    	var ini = INI.fromResource(INISchemaTest.class, "INISchemaTest.testFacadeForMultipleSections.ini");
    	var facade = schm.facadeFor(ini);
    	var secs = facade.allSections("section");
    	assertEquals(2, secs.length);
    	assertEquals(false, secs[0].getBoolean("key1a"));
    	assertEquals(true, secs[1].getBoolean("key1a"));
    	assertEquals("CHOICE1", secs[0].get("key2a"));
    	assertEquals("CHOICE1", secs[1].get("key2a"));
    	
    	var subSecs = secs[0].allSections();
    	assertEquals(2, subSecs.length);
    	assertEquals(false, secs[0].getBoolean("key1a"));
    	assertEquals("section1", subSecs[0].key());
    	assertEquals("section2", subSecs[1].key());
    }

    @Test
    public void testNoValidItems() throws Exception {
    	var schm = INISchema.fromClass(INISchemaTest.class, "INISchemaTest.noValidItems.schema.ini");
    	var facade = schm.facadeFor(INI.create());
    	System.out.println(">> " + facade.asString());
    	assertEquals(0, facade.sections().size());
    	assertEquals(0, facade.values().size());
    }

    @Test
    public void testNoValidItemsAny() throws Exception {
    	var schm = INISchema.fromClass(INISchemaTest.class, "INISchemaTest.noValidItemsAny.schema.ini");
    	var facade = schm.facadeFor(INI.create());
    	System.out.println(">> " + facade.asString());
    	assertEquals(0, facade.sections().size());
    	assertEquals(0, facade.values().size());
    }
    
    @Test
    public void testOneValidItem() throws Exception {
    	var schm = INISchema.fromClass(INISchemaTest.class, "INISchemaTest.oneValidItem.schema.ini");
    	var facade = schm.facadeFor(INI.create());
    	assertEquals(1, facade.sections().size());
    	assertEquals(0, facade.values().size());
    	var sec = facade.section("item");
    	assertEquals(1, sec.values().size());
    	assertEquals("/", sec.get("val3"));
    	
    }

    @Test
    public void testSomeValidItems() throws Exception {
    	var schm = INISchema.fromClass(INISchemaTest.class, "INISchemaTest.noValidItems.schema.ini");
    	var facade = schm.facadeFor(INI.create());
    	System.out.println(facade.asString());
    	assertEquals(0, facade.sections().size());
    	assertEquals(0, facade.values().size());
    }

	private void assertKey1(KeyDescriptor key1) {
		assertEquals("Key1", key1.name());
        assertEquals("key1", key1.key());
        assertEquals(Type.TEXT, key1.type());
        assertEquals("The first key.", key1.description());
        assertEquals("Value 1", key1.defaultValues()[0]);
        assertEquals(Multiplicity.NO_MORE_THAN_ONE, key1.arity());
	}

	private void assertKey2(KeyDescriptor key2) {
		assertEquals("Key2", key2.name());
        assertEquals("key2", key2.key());
        assertEquals(Type.NUMBER, key2.type());
        assertEquals("The first key (number).", key2.description());
        assertEquals("123", key2.defaultValues()[0]);
        assertEquals(Multiplicity.NO_MORE_THAN_ONE, key2.arity());
        assertTrue(key2.discriminatorOr().isEmpty());
	}

	private void assertKey1A(KeyDescriptor key1a) {
		assertEquals("Key1 In A Section", key1a.name());
        assertEquals("key1a", key1a.key());
        assertEquals(Type.BOOLEAN, key1a.type());
        assertEquals("true", key1a.defaultValues()[0]);
        assertEquals(Multiplicity.NO_MORE_THAN_ONE, key1a.arity());
        assertTrue(key1a.descriptionOr().isEmpty());
        assertTrue(key1a.discriminatorOr().isEmpty());
	}

	private void assertKey1C(KeyDescriptor key1c) {
		assertEquals("Another Key1 In A 2nd Section In A Section", key1c.name());
        assertEquals("key1c", key1c.key());
        assertEquals(Type.NUMBER, key1c.type());
        assertEquals("12.34", key1c.defaultValues()[0]);
        assertEquals(NumberDiscriminator.DOUBLE, key1c.discriminator());
        assertEquals(Multiplicity.NO_MORE_THAN_ONE, key1c.arity());
        assertTrue(key1c.descriptionOr().isEmpty());
	}

	private void assertKey1B(KeyDescriptor key1a) {

//name = Key1 In A Section In A Section
//type = NUMBER
//discriminator = DOUBLE
//default-value = 987654.2345678

		assertEquals("Key1 In A Section In A Section", key1a.name());
        assertEquals("key1b", key1a.key());
        assertEquals(Type.NUMBER, key1a.type());
        assertEquals("987654.2345678", key1a.defaultValues()[0]);
        assertEquals(NumberDiscriminator.DOUBLE, key1a.discriminator());
        assertEquals(Multiplicity.NO_MORE_THAN_ONE, key1a.arity());
        assertTrue(key1a.descriptionOr().isEmpty());
	}
	
	private void assertKey2A(KeyDescriptor key2a) {
		assertEquals("Key2 In A Section", key2a.name());
        assertEquals("key2a", key2a.key());
        assertEquals(Type.ENUM, key2a.type());
        assertEquals("CHOICE1", key2a.defaultValues()[0]);
        assertTrue(key2a.descriptionOr().isEmpty());
        assertThrows(IllegalStateException.class, () -> key2a.description());
        assertEquals(Multiplicity.NO_MORE_THAN_ONE, key2a.arity());
        assertEquals(Arrays.asList("CHOICE1", "CHOICE2", "CHOICE3"), Arrays.asList(key2a.values().get()));
	}

	private void assertSection2(SectionDescriptor secSec2) {
		assertEquals("section2", secSec2.key());
        assertEquals("section.section2", String.join(".", secSec2.path()));
        assertEquals("A Section In A Section Without A Description", secSec2.name());
        assertTrue(secSec2.descriptionOr().isEmpty());
        assertThrows(IllegalStateException.class, () -> secSec2.description());
	}

	private void assertSection1(SectionDescriptor secSec) {
		assertEquals("section.section1", String.join(".", secSec.path()));
        assertEquals("A Section In A Section", secSec.name());
        assertEquals("A section in a section to put other keys in", secSec.description());
        assertEquals(Multiplicity.ONE, secSec.arity());
	}

    
	private void assertGeneratedDefaultsMatchExpected(String actual) throws IOException {
		try(var in = INISchemaTest.class.getResourceAsStream("INISchemaTest.expected.ini")) {
        	assertEquals(toString(in), actual);
        }
	}

	private INISchema testschema() {
		return INISchema.fromClass(INISchemaTest.class);
	}
    
    static String toString(InputStream in) {
    	try {
	    	var baos = new ByteArrayOutputStream();
	    	in.transferTo(baos);
	    	return new String(baos.toByteArray(), "UTF-8");
    	}
    	catch(IOException ioe) {
    		throw new UncheckedIOException(ioe);
    	}
    }
    
    static String toString(INISchema s) {
    	var sw = new StringWriter();
    	try {
			s.writeDefaults(sw);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
    	return sw.toString();
    }
}
