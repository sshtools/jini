package com.sshtools.jini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sshtools.jini.config.INISet.Scope;

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
        	testschema().document().readOnly().put("XXX", "YYY");
    	});
    }

    @Test
    public void testFacadeForMultipleSections() throws Exception {
    	Files.writeString(checkDir(userSets.resolve("jini")).resolve("jini.ini"), 
    			String.format("[section]%nkey1a=false%n[section]%n"));
    	var set = testschema();
    	var doc = set.document();
    	System.out.println(doc.asString());
    	var secs = doc.allSections("section");
    	assertEquals(2, secs.length);
    	assertEquals(false, secs[0].getBoolean("key1a"));
    	assertEquals(true, secs[1].getBoolean("key1a"));    	
    }

	private INISet testschema() {
		return new INISet.Builder("jini").
				withSchema(INISetTest.class).
				withPath(Scope.USER, userSets).
				withPath(Scope.USER, globalSets).
				build();
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
