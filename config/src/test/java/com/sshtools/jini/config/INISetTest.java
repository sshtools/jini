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
package com.sshtools.jini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sshtools.jini.Data.ValueUpdateEvent;
import com.sshtools.jini.INI;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.config.INISet.Builder;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.schema.INISchema;

public class INISetTest {
	
	Path userSets;
	Path globalSets;
	
	@BeforeEach
	public void setup() throws Exception {
		userSets = Files.createTempDirectory("jiniuser");
		globalSets = Files.createTempDirectory("jiniuser");
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		deleteAll(globalSets);
		deleteAll(userSets);
	}

    @Test
    public void testReadOnly() throws Exception {
    	assertThrows(UnsupportedOperationException.class, () -> {
    		testSet(INISchema.fromClass(INISetTest.class)).document().readOnly().put("XXX", "YYY");
    	});
    }

    @Test
    public void testFacadeObtainSectionWithOnlyDefaultValues() throws Exception {
    	var schm = INISchema.fromClass(INISetTest.class, "INISetTest.testFacadeObtainSectionWithOnlyDefaultValues.schema.ini");
    	var set = testSet(schm);
		var facade = set.document();
		var sec = facade.section("section");		
    	var k1v = sec.getBoolean("key1");
		assertEquals(true, k1v);
    	var k2v = sec.get("key2");
		assertEquals("Some value",  k2v);
	}

    @Test
    public void testEmptySectionsNotGettingCreated() throws Exception {
    	var schm = INISchema.fromClass(INISetTest.class, "INISetTest.testFacadeObtainSectionWithSomeDefaultValues.schema.ini");
    	var set = testSet(schm);
		var facade = set.document();
		
		assertEquals(0, Files.list(globalSets.resolve("jini")).count());
		assertEquals(0, Files.list(userSets.resolve("jini")).count());
		facade.section("section");		
		Thread.sleep(10000);
		assertEquals(0, Files.list(globalSets.resolve("jini")).count());
		assertEquals(0, Files.list(userSets.resolve("jini")).count());
	}

    @Test
    public void testFacadeObtainSectionWithSomeDefaultValues() throws Exception {
    	var schm = INISchema.fromClass(INISetTest.class, "INISetTest.testFacadeObtainSectionWithSomeDefaultValues.schema.ini");
    	var set = testSet(schm);
		var facade = set.document();
		var sec = facade.section("section");		
    	var k1v = sec.getBoolean("key1");
		assertEquals(true, k1v);
    	var k2v = sec.get("key2");
		assertEquals("Some value",  k2v);
	}

    @Test
    public void testFacadeObtainSectionWithNoDefaultValues() throws Exception {
    	var schm = INISchema.fromClass(INISetTest.class, "INISetTest.testFacadeObtainSectionWithNoDefaultValues.schema.ini");
    	var set = testSet(schm);
		var facade = set.document();
		var sec = facade.section("emptySec");		
		var k1All = sec.getAllOr("key1");
		assertTrue(k1All.isPresent());
		assertEquals(0, k1All.get().length);
    	var k1v = sec.getOr("key1");
		assertEquals(true, k1v.isEmpty());
	}

    @Test
    public void testChangeForUserSets() throws Exception {
    	testChangeFor(userSets);
	}

    @Test
    public void testChangeForGlobalSets() throws Exception {
    	testChangeFor(globalSets);
	}

    @Test
    public void testProgrammaticAddSections() throws Exception {
    	var schm = INISchema.fromClass(INISetTest.class);
		var set = testSetBuilder(schm).
				withMonitor(new Monitor()).
				withWriteScope(Scope.USER).
				build();
		
		set.groupWrite(ini -> {
			ini.put("key1", "Value X");
			for(var i = 0; i < 10; i++) {
				var sec = ini.create("section");
				sec.put("key1a", false);
			}			
		});
		
		var file = userSets.resolve("jini").resolve("jini.ini");
		var ini = INI.fromFile(file);
		assertEquals("Value X", ini.get("key1"));
		var secs = ini.allSections("section");
		assertEquals(10, secs.length);
		for(var i = 0; i < 10; i++) {
			assertEquals(false, secs[i].getBoolean("key1a"));
		}
	}

    @Test
    public void testFacadeForMultipleSections() throws Exception {
    	var file = checkDir(userSets.resolve("jini")).resolve("jini.ini");
    	var ini = INI.fromResource(INISetTest.class, "testFacadeForMultipleSections.ini");
    	new INIWriter.Builder().build().write(ini, file);
    	var set = testSet(INISchema.fromClass(INISetTest.class));
    	var facade = set.document();
    	
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

	private INISet testSet(INISchema schema) {
		return testSetBuilder(schema).
				build();
	}

	private Builder testSetBuilder(INISchema schema) {
		return new INISet.Builder("jini").
				withAllScopes().
				withSchema(schema).
				withPath(Scope.USER, userSets).
				withPath(Scope.GLOBAL, globalSets);
	}

	private void testChangeFor(Path sets) throws IOException, InterruptedException {
		try(var  monitor = new Monitor()) {
	    	var schm = INISchema.fromClass(INISetTest.class);
	    	var set = testSetBuilder(schm).
	    			withMonitor(monitor).
	    			build();
			var facade = set.document();
			var sec = facade.section("section");
			
			var updates = new ArrayList<ValueUpdateEvent>();
			
			sec.onValueUpdate(vu -> {
				updates.add(vu);
				synchronized(updates) {
					updates.notifyAll();
				}
			});
	
	    	var file = checkDir(sets.resolve("jini")).resolve("jini.ini");
			try(var wtr = new PrintWriter(Files.newBufferedWriter(file), true)) {
				wtr.format("[section]%nkey1a=MyValue");
			}
			
			synchronized(updates) {
				updates.wait(10000);
			}
			
			assertEquals(1, updates.size());
			assertEquals("key1a", updates.get(0).key());
			assertArrayEquals(new String[] { "MyValue" }, updates.get(0).newValues());
    	}
	}
	
	static Path checkDir(Path path) throws IOException {
		Files.createDirectories(path);
		return path;
	}
	
	static void deleteAll(Path path) throws IOException {
		try (var paths = Files.walk(path)) {
	        paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
	    }
	}
}
