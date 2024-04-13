package com.sshtools.jini.prefs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.INI;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.prefs.INIPreferences.Scope;
import com.sshtools.jini.prefs.INIStoreBuilder.SingleFileINIPreferences;

public class INIPreferencesTest extends AbstractTest {

	@Test
	public void testFlush() throws IOException, BackingStoreException {
		try (var store = tempStore(false)) {
			var uroot = store.root();
			uroot.put("key1", "someval");
			var file = ((SingleFileINIPreferences)uroot).file;
			assertFalse(Files.exists(file));
			uroot.flush();
			assertTrue(Files.exists(file));
			
			var ini = INI.fromFile(file);
			assertEquals("someval", ini.getOr("key1", ""));
			assertEquals(1, ini.keys().size());
			assertEquals(0, ini.sections().size());
		}
	}
	@Test
	public void testSync() throws IOException, BackingStoreException {
		try (var store = tempStore(false)) {
			var uroot = store.root();
			uroot.put("key1", "someval");
			var file = ((SingleFileINIPreferences)uroot).file;
			uroot.flush();
			
			var ini = INI.fromFile(file);
			ini.put("key1", "someotherval");
			ini.put("key2", true);
			new INIWriter.Builder().build().write(ini, file);
			
			uroot.sync();
			
			assertEquals("someotherval", ini.getOr("key1", ""));
			assertTrue(ini.getBooleanOr("key2", false));
			assertEquals(2, ini.keys().size());
			assertEquals(0, ini.sections().size());
		}
	}

	@Test
	public void testFlushSpi() throws IOException, BackingStoreException {
		assertThrows(UnsupportedOperationException.class, () -> {
			try (var store = tempStore(false)) {
				var uroot = store.root();
				uroot.put("key1", "someval");
				((SingleFileINIPreferences)uroot).flushSpi();
			}
		});
	}

	@Test
	public void testFailFlush() throws IOException, BackingStoreException {
		assertThrows(BackingStoreException.class, () -> {
			try (var store = tempStore(false)) {
				var uroot = store.root();
				uroot.put("key1", "someval");
				var file = ((SingleFileINIPreferences)uroot).file;
				Files.createFile(file);
				file.toFile().setWritable(false, false);
				uroot.flush();
			}
		});
	}

	@Test
	public void testBasicRoot() throws IOException, BackingStoreException {
		try (var store = tempStore(true)) {
			var uroot = store.root();
			assertEquals(Scope.CUSTOM, store.scope());
			assertTrue(store.root().isUserNode());
			assertEquals("/", store.root().absolutePath());
			testNodeValues(uroot);
			testNodeSections(uroot);
			
		}
	}

	@Test
	public void testConvenienceSystemRoot() throws IOException, BackingStoreException {
		
		var bldr = tempScopedBuilder(Scope.GLOBAL);
		
		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var uroot = INIPreferences.systemRoot();;
			assertEquals(Scope.GLOBAL, store.scope());
			assertFalse(store.root().isUserNode());
			assertEquals("/", uroot.absolutePath());
			testNodeValues(uroot);
			testNodeSections(uroot);
		}
	}

	@Test
	public void testConvenienceUserRoot() throws IOException, BackingStoreException {
		
		var bldr = tempScopedBuilder(Scope.USER);
		
		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var uroot = INIPreferences.userRoot();;
			assertEquals(Scope.USER, store.scope());
			assertTrue(store.root().isUserNode());
			assertEquals("/", uroot.absolutePath());
			testNodeValues(uroot);
			testNodeSections(uroot);
			
		}
	}
}
