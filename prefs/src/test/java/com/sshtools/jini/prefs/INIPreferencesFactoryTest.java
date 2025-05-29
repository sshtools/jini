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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.prefs.INIPreferences.Scope;

public class INIPreferencesFactoryTest extends AbstractTest {

	@Test
	public void testPrefsUser() throws IOException, BackingStoreException {
		var bldr = tempScopedBuilder(Scope.USER);
		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var prefs = Preferences.userNodeForPackage(INIPreferencesFactoryTest.class);
			assertEquals(Scope.USER, store.scope());
			assertTrue(prefs.isUserNode());
			assertEquals("/" + INIPreferencesFactoryTest.class.getPackage().getName().replace('.', '/'), prefs.absolutePath());
			testNodeValues(prefs);
			testNodeSections(prefs);
		}
	}

	@Test
	public void testPrefsSystem() throws IOException, BackingStoreException {
		var bldr = tempScopedBuilder(Scope.GLOBAL);
		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var prefs = Preferences.systemNodeForPackage(INIPreferencesFactoryTest.class);
			assertEquals(Scope.GLOBAL, store.scope());
			assertFalse(prefs.isUserNode());
			assertEquals("/" + INIPreferencesFactoryTest.class.getPackage().getName().replace('.', '/'), prefs.absolutePath());
			testNodeValues(prefs);
			testNodeSections(prefs);
		}
	}

	@Test
	public void testPreferencesFactoryUserRoot() throws IOException, BackingStoreException {

		var bldr = tempScopedBuilder(Scope.USER);

		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var uroot = new INIPreferencesFactory().userRoot();
			assertEquals(Scope.USER, store.scope());
			assertTrue(uroot.isUserNode());
			assertEquals("/", uroot.absolutePath());
			testNodeValues(uroot);
			testNodeSections(uroot);

		}
	}

	@Test
	public void testPreferencesFactorySystemRoot() throws IOException, BackingStoreException {

		var bldr = tempScopedBuilder(Scope.GLOBAL);

		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var uroot = new INIPreferencesFactory().systemRoot();
			assertEquals(Scope.GLOBAL, store.scope());
			assertFalse(uroot.isUserNode());
			assertEquals("/", uroot.absolutePath());
			testNodeValues(uroot);
			testNodeSections(uroot);

		}
	}
}
