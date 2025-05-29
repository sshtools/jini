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
package com.sshtools.jini.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.prefs.BackingStoreException;

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
			assertEquals("someval", ini.get("key1", ""));
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
			
			assertEquals("someotherval", ini.get("key1", ""));
			assertTrue(ini.getBoolean("key2", false));
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
