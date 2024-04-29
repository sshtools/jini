package com.sshtools.jini.prefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.prefs.INIPreferences.AbstractINIPreferences;
import com.sshtools.jini.prefs.INIPreferences.Mode;
import com.sshtools.jini.prefs.INIPreferences.Scope;

public final class INIStoreBuilder {
	static String defaultApp = INI.class.getName();

	static Scope defaultScope = Scope.USER;
	private String app = defaultApp;
	private Optional<Path> customRoot = Optional.empty();
	private boolean failOnParsingError = true;
	private boolean failOnMissingFile = false;
	boolean autoFlush = true;
	private Optional<String> name = Optional.empty();
	private Optional<Path> path = Optional.empty();
	private Scope scope = defaultScope;
	Mode mode = Mode.SINGLE_FILE;

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
	
	final static class INIStoreImpl implements INIStore {

		private static Timer syncTimer = new Timer(true);

		private static final int SYNC_INTERVAL = Math.max(1, AccessController
				.doPrivileged((PrivilegedAction<Integer>) () -> Integer.getInteger("jini.prefs.syncInterval", 30)));

		static {
			AccessController.doPrivileged(new PrivilegedAction<Void>() {
				public Void run() {
					Runtime.getRuntime().addShutdownHook(new Thread(null, () -> {
						syncTimer.cancel();
						INIPreferences.allStores.forEach(str -> {
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
				INIPreferences.allStores.add(this);
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
				INIPreferences.scopedStores.remove(scope);
			}
			if(task != null) {
				try {
					task.cancel();
				}
				finally {
					INIPreferences.allStores.remove(this);
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

		SingleFileINIPreferences(Data data, Path file, String name, INIStore store,
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
}