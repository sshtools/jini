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
