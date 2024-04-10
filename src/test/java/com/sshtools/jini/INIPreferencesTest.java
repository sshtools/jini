package com.sshtools.jini;

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

import com.sshtools.jini.INIPreferences.INIPreferencesFactory;
import com.sshtools.jini.INIPreferences.INIStore;
import com.sshtools.jini.INIPreferences.INIStoreBuilder;
import com.sshtools.jini.INIPreferences.Scope;
import com.sshtools.jini.INIPreferences.SingleFileINIPreferences;

public class INIPreferencesTest {

	static INIStore tempStore(boolean autoFlush) {
		try {
			var dir = Files.createTempDirectory("jinitestdir");
			var delegate = new INIStoreBuilder().
					withCustomRoot(dir).
					withAutoFlush(autoFlush).
					withName("jini.test").
					build();
			return new INIStore() {
				@Override
				public Scope scope() {
					return delegate.scope();
				}

				@Override
				public Preferences root() {
					return delegate.root();
				}

				@Override
				public void close() throws IOException {
					try {
						delegate.close();
					} finally {
						Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
					}
				}
			};
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

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
	public void testPreferencesFactorySystemRoot() throws IOException, BackingStoreException {
		
		var bldr = tempScopedBuilder(Scope.GLOBAL);
		
		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var uroot = new INIPreferencesFactory().systemRoot();
			assertEquals(Scope.GLOBAL, store.scope());
			assertFalse(store.root().isUserNode());
			assertEquals("/", uroot.absolutePath());
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
	public void testPreferencesFactoryUserRoot() throws IOException, BackingStoreException {
		
		var bldr = tempScopedBuilder(Scope.USER);
		
		try (var store = bldr.build()) {
			INIPreferences.configure(store);
			var uroot = new INIPreferencesFactory().userRoot();
			assertEquals(Scope.USER, store.scope());
			assertTrue(store.root().isUserNode());
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

	protected INIStoreBuilder tempScopedBuilder(Scope scope) throws IOException {
		return new INIStoreBuilder().
				withScope(scope).
				withoutAutoFlush().
				withName("jini.test").
				withPath(Files.createTempDirectory("jinitestdir"));
	}

	protected void testNodeSections(Preferences uroot) throws BackingStoreException {
		assertEquals(0, uroot.childrenNames().length);

		assertFalse(uroot.nodeExists("subsec1"));
		var subsec1 = uroot.node("subsec1");
		assertEquals((uroot.absolutePath().equals("/") ? "" : uroot.absolutePath()) + "/" + "subsec1", subsec1.absolutePath());
		assertTrue(uroot.nodeExists("subsec1"));
		assertArrayEquals(new String[] {"subsec1"}, uroot.childrenNames());
		uroot.node("subsec1");
		assertTrue(uroot.nodeExists("subsec1"));
		subsec1.removeNode();
		assertFalse(uroot.nodeExists("subsec1"));
		
	}

	protected void testNodeValues(Preferences uroot) throws BackingStoreException {
		assertEquals(null, uroot.get("key1", null));
		assertFalse(uroot.getBoolean("key2", false));
		assertEquals(null, uroot.getByteArray("key3", null));
		assertEquals(0d, uroot.getDouble("key4", 0d));
		assertEquals(0f, uroot.getFloat("key5", 0f));
		assertEquals(0, uroot.getInt("key6", 0));
		assertEquals(0, uroot.getLong("key7", 0l));
		assertEquals(0, uroot.keys().length);

		var br = new Random();
		var rnd = "str" + String.valueOf(br.nextInt());
		var bytes = new byte[32 + br.nextInt(20)];
		br.nextBytes(bytes);
		var dblval = br.nextDouble();
		var fltval = br.nextFloat();
		var intval = br.nextInt();
		var lngval = br.nextLong();
		
		uroot.put("key1", rnd);
		uroot.putBoolean("key2", true);
		uroot.putByteArray("key3", bytes);
		uroot.putDouble("key4", dblval);
		uroot.putFloat("key5", fltval);
		uroot.putInt("key6", intval);
		uroot.putLong("key7", lngval);
		assertEquals(7, uroot.keys().length);
		assertArrayEquals(new String[] {"key1", "key2", "key3", "key4", "key5", "key6", "key7"}, uroot.keys());

		assertEquals(rnd, uroot.get("key1", null));
		assertTrue(uroot.getBoolean("key2", false));
		assertArrayEquals(bytes, uroot.getByteArray("key3", null));
		assertEquals(dblval, uroot.getDouble("key4", 0d));
		assertEquals(fltval, uroot.getFloat("key5", 0f));
		assertEquals(intval, uroot.getInt("key6", 0));
		assertEquals(lngval, uroot.getLong("key7", 0l));

		uroot.remove("key1");
		uroot.remove("key2");
		uroot.remove("key3");
		uroot.remove("key4");
		
		assertEquals(null, uroot.get("key1", null));
		assertFalse(uroot.getBoolean("key2", false));
		assertEquals(null, uroot.getByteArray("key3", null));
		assertEquals(0d, uroot.getDouble("key4", 0d));
		assertEquals(fltval, uroot.getFloat("key5", 0f));
		assertEquals(fltval, uroot.getFloat("key5", 0f));
		assertEquals(intval, uroot.getInt("key6", 0));
		assertEquals(lngval, uroot.getLong("key7", 0l));
		assertEquals(3, uroot.keys().length);
		assertArrayEquals(new String[] {"key5", "key6", "key7"}, uroot.keys());
		
		uroot.clear();
		
		assertEquals(null, uroot.get("key1", null));
		assertFalse(uroot.getBoolean("key2", false));
		assertEquals(null, uroot.getByteArray("key3", null));
		assertEquals(0d, uroot.getDouble("key4", 0d));
		assertEquals(0f, uroot.getFloat("key5", 0f));
		assertEquals(0, uroot.getInt("key6", 0));
		assertEquals(0, uroot.getLong("key7", 0l));
		assertEquals(0, uroot.keys().length);
	}
}
