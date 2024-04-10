package com.sshtools.jini;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

import com.sshtools.jini.Data.AbstractData;
import com.sshtools.jini.INI.Section;

public class INIPreferences {

	public interface INIStore extends Closeable {

		Preferences root();

		Scope scope();
		
	}

	public final static class INIStoreBuilder {
		private static String defaultApp = INI.class.getName();

		private static Scope defaultScope = Scope.USER;
		private String app = defaultApp;
		private Optional<Path> customRoot = Optional.empty();
		private boolean failOnMissingFile = true;
		private boolean failOnParsingError = true;
		private boolean autoFlush = true;
		private Optional<String> name = Optional.empty();
		private Optional<Path> path = Optional.empty();
		private Scope scope = defaultScope;
		private Mode mode = Mode.SINGLE_FILE;

		public INIStore build() {
			return new INIStoreImpl(this);
		}

		public INIStoreBuilder withApp(Class<?> app) {
			return withApp(app.getName());
		}

		public INIStoreBuilder withApp(String app) {
			this.app = app;
			return this;
		}

		public INIStoreBuilder withCustomRoot(Optional<Path> customRoot) {
			this.customRoot = customRoot;
			this.scope = Scope.CUSTOM;
			return this;
		}

		public INIStoreBuilder withCustomRoot(Path customRoot) {
			return withCustomRoot(Optional.of(customRoot));
		}

		public INIStoreBuilder withCustomRoot(String customRoot) {
			return withPath(Path.of(customRoot));
		}

		public INIStoreBuilder withFailOnMissingFile(boolean failOnMissingFile) {
			this.failOnMissingFile = failOnMissingFile;
			return this;
		}

		public INIStoreBuilder withFailOnParsingError(boolean failOnParsingError) {
			this.failOnParsingError = failOnParsingError;
			return this;
		}

		public INIStoreBuilder withMode(Mode mode) {
			this.mode = mode;
			return this;
		}

		public INIStoreBuilder withoutAutoFlush() {
			return withAutoFlush(false);
		}
		
		public INIStoreBuilder withAutoFlush(boolean autoFlush) {
			this.autoFlush = autoFlush;
			return this;
		}

		public INIStoreBuilder withName(String name) {
			this.name = Optional.of(name);
			return this;
		}

		public INIStoreBuilder withoutFailOnMissingFile() {
			return withFailOnMissingFile(false);
		}

		public INIStoreBuilder withoutFailOnParsingError() {
			return withFailOnParsingError(false);
		}

		public INIStoreBuilder withPath(Optional<Path> path) {
			this.path = path;
			return this;
		}

		public INIStoreBuilder withPath(Path path) {
			return withPath(Optional.of(path));
		}

		public INIStoreBuilder withPath(String path) {
			return withPath(Path.of(path));
		}

		public INIStoreBuilder withScope(Scope scope) {
			if (customRoot.isPresent() && scope != Scope.CUSTOM) {
				throw new IllegalArgumentException("A custom root has been set. Scope may not be used.");
			}
			this.scope = scope;
			return this;
		}

		String getApp() {
			return app;
		}

		Optional<Path> getCustomRoot() {
			return customRoot;
		}

		Optional<String> getName() {
			return name;
		}

		Optional<Path> getPath() {
			return path;
		}

		Scope getScope() {
			return scope;
		}

		boolean isFailOnMissingFile() {
			return failOnMissingFile;
		}

		boolean isFailOnParsingError() {
			return failOnParsingError;
		}
	}

	public static class INIPreferencesFactory implements PreferencesFactory {

		@Override
		public Preferences systemRoot() {
			synchronized (scopedStores) {
				return scoped(Scope.GLOBAL);
			}
		}

		@Override
		public Preferences userRoot() {
			synchronized (scopedStores) {
				return scoped(Scope.USER);
			}
		}
	}

	public enum Mode {
		FILE_PER_SECTION, SINGLE_FILE
	}

	public enum Scope {
		USER, GLOBAL, CUSTOM
	}

	abstract static class AbstractINIPreferences extends AbstractPreferences {

		protected AbstractData ini;
		protected final INIStore store;

		private AbstractINIPreferences(AbstractData ini, String name, INIStore store, AbstractPreferences parent) {
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

	private final static class INIStoreImpl implements INIStore {

		private static Timer syncTimer = new Timer(true);

		private static final int SYNC_INTERVAL = Math.max(1, AccessController
				.doPrivileged((PrivilegedAction<Integer>) () -> Integer.getInteger("jini.prefs.syncInterval", 30)));

		static {
			AccessController.doPrivileged(new PrivilegedAction<Void>() {
				public Void run() {
					Runtime.getRuntime().addShutdownHook(new Thread(null, () -> {
						syncTimer.cancel();
						allStores.forEach(str -> {
							try {
								str.close();
							} catch (IOException e) {
							}
						});
					}, "Jini Sync Timer Thread", 0, false));
					return null;
				}
			});
		}

		private final static String os = System.getProperty("os.name", "unknown").toLowerCase();
		private final boolean failOnMissingFile;
		private final boolean failOnParsingError;
		private final String name;
		private final Scope scope;
		private final Preferences root;
		private final TimerTask task;

		INIStoreImpl(INIStoreBuilder builder) {

			if(builder.autoFlush) {
				task = new TimerTask() {
					public void run() {
						try {
							root.flush();
						}
						catch(Exception e) {
							System.err.println("WARNING: Failed to flush preferences. " + e.getMessage());
						}
					}
				};
				allStores.add(this);
				syncTimer.schedule(task, SYNC_INTERVAL * 1000, SYNC_INTERVAL * 1000);
			}
			else {
				task = null;
			}

			this.scope = builder.getScope();

			var path = calcRootForScopeAppAndOs(builder.getScope(), builder.getApp(), builder.getCustomRoot());
			if (builder.getPath().isPresent()) {
				path = path.resolve(builder.getPath().get());
			}

			checkDir(path);

			this.failOnMissingFile = builder.isFailOnMissingFile();
			this.failOnParsingError = builder.isFailOnParsingError();
			this.name = builder.getName().orElseThrow(() -> new IllegalStateException("Name must be set."));

			switch (builder.mode) {
			case SINGLE_FILE:
				root = new SingleFileINIPreferences(path.resolve(this.name + ".ini"), "", this, null);
				break;
			default:
				throw new UnsupportedOperationException("TODO");
			}
		}

		@Override
		public void close() {
			if(scope != Scope.CUSTOM) {
				scopedStores.remove(scope);
			}
			if(task != null) {
				try {
					task.cancel();
				}
				finally {
					allStores.remove(this);
					task.run();
				}
			}
		}

		@Override
		public Preferences root() {
			return root;
		}

		@Override
		public Scope scope() {
			return scope;
		}

		private Path calcRootForOs(Scope scope, Optional<Path> customRoot) {
			if (scope == Scope.CUSTOM) {
				return customRoot.orElseThrow(() -> new IllegalArgumentException(
						MessageFormat.format("Scope is {0}, but no custom root set.", scope)));
			}

			if (os.contains("linux")) {
				switch (scope) {
				case GLOBAL:
					return Paths.get("/etc");
				case USER:
					return resolveHome().resolve(".config");
				default:
					throw new UnsupportedOperationException();
				}

			} else if (os.contains("windows")) {
				switch (scope) {
				case GLOBAL:
					return Paths.get("C:\\Program Files\\Common Files");
				case USER:
					return resolveHome().resolve("AppData").resolve("Roaming");
				default:
					throw new UnsupportedOperationException();
				}
			} else {
				switch (scope) {
				case GLOBAL:
					return Paths.get("/etc");
				case USER:
					return resolveHome();
				default:
					throw new UnsupportedOperationException();
				}
			}
		}

		private Path calcRootForScopeAppAndOs(Scope scope, String app, Optional<Path> customRoot) {
			var root = calcRootForOs(scope, customRoot);
			return root.resolve(root.equals(resolveHome()) ? "." + app : app);
		}

		private Path checkDir(Path path) {
			if (Files.exists(path)) {
				if (!Files.isDirectory(path)) {
					throw new IllegalArgumentException(MessageFormat.format("{0} is not a directory.", path));
				}
			} else {
				try {
					Files.createDirectories(path);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			return path;
		}

		private Path resolveHome() {
			return Paths.get(System.getProperty("user.home"));
		}
	}

	final static class SingleFileINIPreferences extends AbstractINIPreferences {

		final Path file;

		SingleFileINIPreferences(AbstractData data, Path file, String name, INIStore store,
				AbstractPreferences parent) {
			super(data, name, store, parent);
			this.file = file;
		}

		SingleFileINIPreferences(Path file, String name, INIStore store, AbstractPreferences parent) {
			super(INI.fromFileIfExists(file), name, store, parent);
			this.file = file;
		}

		@Override
		public AbstractPreferences childSpi(String pathName) {
			var secOr = ini.sectionOr(pathName);
			Section sec;
			if (secOr.isPresent()) {
				sec = secOr.get();
			} else {
				sec = ini.create(pathName);
			}
			return new SingleFileINIPreferences(sec, file, pathName, store, this);
		}

		@Override
		protected void flushSpi() throws BackingStoreException {
			/* flush rather than flustSpi because we write whole tree at once */
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() throws BackingStoreException {
			/* flush rather than flustSpi because we write whole tree at once */
			try {
				new INIWriter.Builder().build().write((INI) ini, file);
			} catch (IOException e) {
				throw new BackingStoreException(e);
			}
		}

		@Override
		public void removeNodeSpi() throws BackingStoreException {
			if (this.equals(store.root())) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException e) {
					throw new BackingStoreException(e);
				}
				ini = INI.create();

			} else {
				((AbstractINIPreferences)parent()).ini.sectionOr(name()).ifPresent(sec -> {
					sec.remove();
				});
			}
		}

		@Override
		public void syncSpi() throws BackingStoreException {
			ini = INI.fromFileIfExists(file);
		}

	}

	private final static Map<Scope, INIStore> scopedStores = new HashMap<>();
	private final static List<INIStore> allStores = new CopyOnWriteArrayList<>();

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

	private static Preferences scoped(Scope scope) {
		var store = scopedStores.get(scope);
		if (store == null) {
			store = new INIStoreBuilder().withApp("jini").withName("jini").withScope(scope).build();
			scopedStores.put(scope, store);
		}
		return store.root();
	}

}
