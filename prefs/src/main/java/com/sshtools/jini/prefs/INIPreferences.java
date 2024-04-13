package com.sshtools.jini.prefs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.sshtools.jini.Data.AbstractData;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIWriter;

public class INIPreferences {

	public enum Mode {
		FILE_PER_SECTION, SINGLE_FILE
	}

	public enum Scope {
		USER, GLOBAL, CUSTOM
	}

	abstract static class AbstractINIPreferences extends AbstractPreferences {

		protected AbstractData ini;
		protected final INIStore store;

		protected AbstractINIPreferences(AbstractData ini, String name, INIStore store, AbstractPreferences parent) {
			super(parent, name);
			this.store = store;
			this.ini = ini;
		}

		@Override
		public final String[] childrenNamesSpi() throws BackingStoreException {
			return ini.sections().keySet().toArray(new String[0]);
		}

		@Override
		public final void clear() throws BackingStoreException {
			ini.clear();
		}

		@Override
		public final boolean getBoolean(String key, boolean def) {
			return ini.getBooleanOr(key, def);
		}

		@Override
		public final byte[] getByteArray(String key, byte[] def) {
			return ini.getOr(key).map(Base64.getDecoder()::decode).orElse(def);
		}

		@Override
		public final double getDouble(String key, double def) {
			return ini.getDoubleOr(key, def);
		}

		@Override
		public final float getFloat(String key, float def) {
			return ini.getFloatOr(key, def);
		}

		@Override
		public final int getInt(String key, int def) {
			return ini.getIntOr(key, def);
		}

		@Override
		public final long getLong(String key, long def) {
			return ini.getLongOr(key, def);
		}

		@Override
		public final String getSpi(String key) {
			return ini.getOr(key, null);
		}

		@Override
		public final boolean isUserNode() {
			return !store.scope().equals(Scope.GLOBAL);
		}

		@Override
		public final String[] keysSpi() throws BackingStoreException {
			return ini.keys().toArray(new String[0]);
		}

		@Override
		public final void putBoolean(String key, boolean value) {
			ini.put(key, value);
		}

		@Override
		public final void putByteArray(String key, byte[] value) {
			ini.put(key, Base64.getEncoder().encodeToString(value));
		}

		@Override
		public final void putDouble(String key, double value) {
			ini.put(key, value);
		}

		@Override
		public final void putFloat(String key, float value) {
			ini.put(key, value);
		}

		@Override
		public final void putInt(String key, int value) {
			ini.put(key, value);
		}

		@Override
		public final void putLong(String key, long value) {
			ini.put(key, value);
		}

		@Override
		public final void putSpi(String key, String value) {
			this.ini.put(key, value);
		}

		@Override
		public final void removeSpi(String key) {
			ini.remove(key);
		}

	}

	final static Map<Scope, INIStore> scopedStores = new HashMap<>();
	final static List<INIStore> allStores = new CopyOnWriteArrayList<>();

	public static void configure(INIStore store) {
		synchronized (scopedStores) {
			if (scopedStores.containsKey(store.scope())) {
				throw new IllegalStateException("Already configured.");
			}
			scopedStores.put(store.scope(), store);
		}
	}

	public static void setDefaultApp(Class<?> defaultApp) {
		setDefaultApp(defaultApp.getName());
	}

	public static void setDefaultApp(String defaultApp) {
		INIStoreBuilder.defaultApp = defaultApp;
	}

	public static void setDefaultScope(Scope defaultScope) {
		INIStoreBuilder.defaultScope = defaultScope;
	}

	public final static Preferences systemRoot() {
		synchronized (scopedStores) {
			return scoped(Scope.GLOBAL);
		}
	}

	public final static Preferences userRoot() {
		synchronized (scopedStores) {
			return scoped(Scope.USER);
		}
	}

	static Preferences scoped(Scope scope) {
		var store = scopedStores.get(scope);
		if (store == null) {
			store = new INIStoreBuilder().withApp("jini").withName("jini").withScope(scope).build();
			scopedStores.put(scope, store);
		}
		return store.root();
	}

}