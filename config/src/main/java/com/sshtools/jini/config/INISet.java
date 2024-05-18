package com.sshtools.jini.config;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.Interpolation;
import com.sshtools.jini.WrappedINI;
import com.sshtools.jini.config.Monitor.MonitorHandle;
import com.sshtools.jini.schema.INISchema;

/**
 * Manages a set of INI files for configuration of a particular subsystem. Every
 * set has an "App", this determines the name of the root configuration
 * directory. Files can be scoped globally (e.g. /etc/pretty), or per-user
 * (~/.config/pretty). The path becomes the root configuration directories.
 * <p>
 * Further, every INISet has a name. This determines the name in the apps root
 * configuration directories of the primary file, e.g. /etc/pretty/pretty.ini.
 * <p>
 * Files scope globally are read-only, but the <strong>primary</strong> user
 * scoped files may be written.
 * <p>
 * Every scoped file may have a counterpart drop-in directory, e.g.
 * ~/.config/pretty/pretty.d. All files in here are read as if they were a
 * section in the primary file with the same name as the file.
 * <p>
 * Files are in the order ..
 * <ul>
 * <li>Default class path configuration</li>
 * <li>Global scoped primary file</li>
 * <li>Global scoped drop-in files</li>
 * <li>User scoped primary file</li>
 * <li>User scoped drop-in files</li>
 * </ul>
 * <p>
 * As each file is read, keys that already exist are replaced, and sections that
 * already exist are merged.
 */
public final class INISet implements Closeable {

	private static final String DEFAULT_APP_NAME = "pretty";
	private final static String os = System.getProperty("os.name", "unknown").toLowerCase();

	private abstract static class AbstractWrapper<DEL extends Data> extends WrappedINI.AbstractWrapper<DEL, INISet, SectionWrapper> {

		public AbstractWrapper(DEL delegate, AbstractWrapper<?> parent, INISet set) {
			super(delegate, parent, set);
		}

		@Override
		public Section create(String... path) {
//			var ref = userObject.ref(Scope.USER);
//			var wtrbl = ref.writable();
//			var wtrblDoc = wtrbl.document();
//			var fullSec = this instanceof Section ? wtrblDoc.section(path()) : wtrblDoc;
//			fullSec.create(path);
//			try {
//				wtrbl.write();
//			} catch (IOException e) {
//				throw new UncheckedIOException(e);
//			}
			
			return super.create(path);
		}

		@Override
		public final Optional<String[]> getAllOr(String key) {
			var path = path();
			if(path.length > 0) {
				var var = System.getProperty("slf4jtty." +String.join(".", path) + "." + key);
				if(var != null) {
					return Optional.of(new String[] { var });
				}
			}
			return delegate.getAllOr(key);
		}

		@Override
		protected SectionWrapper createWrappedSection(Section delSec) {
			return new SectionWrapper(delSec, this, userObject);
		}

		@Override
		public final <E extends Enum<E>> void putAllEnum(String key, E... values) {
			doOnWritable(key, data -> data.putAllEnum(key, values));
		}

		@Override
		public final void putAll(String key, String... values) {
			doOnWritable(key, data -> data.putAll(key, values));
		}

		@Override
		public final void putAll(String key, int... values) {
			doOnWritable(key, data -> data.putAll(key, values));
		}

		@Override
		public final void putAll(String key, short... values) {
			doOnWritable(key, data -> data.putAll(key, values));
		}

		@Override
		public final void putAll(String key, long... values) {
			doOnWritable(key, data -> data.putAll(key, values));

		}

		@Override
		public final void putAll(String key, float... values) {
			doOnWritable(key, data -> data.putAll(key, values));

		}

		@Override
		public final void putAll(String key, double... values) {
			doOnWritable(key, data -> data.putAll(key, values));

		}

		@Override
		public final void putAll(String key, boolean... values) {
			doOnWritable(key, data -> data.putAll(key, values));
		}

		@Override
		public final boolean remove(String key) {
			var res = new AtomicBoolean();
			doOnWritable(key, data -> res.set(data.remove(key)));
			return res.get();
		}

		private void doOnWritable(String key, Consumer<Data> task) {
			var ref = userObject.ref(userObject.writeScope.orElse(Scope.USER));
			try {
				var wtrbl = ref.writable();
				var wtrblDoc = wtrbl.document();
				if (delegate instanceof INI) {
					task.accept(wtrblDoc);
				} else {
					var sec = (Section) delegate;
					var thisSectionPath = sec.path();
					var wtrblSec = wtrblDoc.obtainSection(thisSectionPath);
					task.accept(wtrblSec);
				}
				wtrbl.write();
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			} finally {
				task.accept(delegate);
			}
		}
	}

	private final static class SectionWrapper extends AbstractWrapper<Section> implements Section {

		public SectionWrapper(Section delegate, AbstractWrapper<?> parent, INISet set) {
			super(delegate, parent, set);
		}

		@Override
		public final void remove() {
			delegate.remove();
			((AbstractWrapper<?>) parent).removeSection(delegate);
		}

		@Override
		public final String key() {
			return delegate.key();
		}

		@Override
		public final Section[] parents() {
			return wrapSections(delegate.parents());
		}

		@Override
		public final String[] path() {
			return delegate.path();
		}

		@Override
		public final Section parent() {
			if (parent instanceof Section) {
				return (Section) parent;
			} else
				throw new IllegalStateException("Root section.");
		}

	}

	private final static class RootWrapper extends AbstractWrapper<INI> implements INI {

		public RootWrapper(INI delegate, INISet set) {
			super(delegate, null, set);
		}

		@Override
		public INI readOnly() {
			return delegate.readOnly();
		}

		@Override
		public INI merge(MergeMode mergeMode, INI... others) {
			throw new UnsupportedOperationException();
		}
	}

	public enum Scope {
		GLOBAL, USER
	}

	public final static class Builder {
		private Optional<Scope> writeScope = Optional.empty();
		private Optional<INISchema> schema = Optional.empty();
		private Optional<INI> defaultIni = Optional.empty();
		private Optional<String> app = Optional.empty();
		private Optional<Monitor> monitor = Optional.empty();
		private Map<Scope, Path> paths = new HashMap<>();
		private List<Scope> scopes = new ArrayList<>();
		private boolean systemPropertyOverrides = true;

		private final String name;

		public Builder(String name) {
			this.name = name;
		}

		public Builder withoutSystemPropertyOverrides() {
			return withSystemPropertyOverrides(false);
		}

		public Builder withSystemPropertyOverrides(boolean systemPropertyOverrides) {
			this.systemPropertyOverrides = systemPropertyOverrides;
			return this;
		}

		public Builder withMonitor(Monitor monitor) {
			this.monitor = Optional.of(monitor);
			return this;
		}

		public Builder withWriteScope(Scope scope) {
			this.writeScope = Optional.of(scope);
			return this;
		}

		public Builder withScopes(Scope... scopes) {
			this.scopes = Arrays.asList(scopes);
			return this;
		}

		public Builder withApp(Class<?> app) {
			return withApp(app.getName());
		}

		public Builder withApp(String app) {
			this.app = Optional.of(app);
			return this;
		}

		public Builder withSchema(Class<?> base, String resource) {
			try (var in = base.getResourceAsStream(resource)) {
				return withSchema(in);
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		public Builder withDefault(Class<?> base, String resource) {
			try (var in = base.getResourceAsStream(resource)) {
				return withDefault(in);
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		public Builder withOptionalDefault(Class<?> base, String resource) {
			try {
				var in = base.getResourceAsStream(resource);
				if(in == null)
					return this;
				try {
					return withDefault(in);
				} finally {
					in.close();
				}
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		public Builder withSchema(Path path) {
			return withSchema(INISchema.fromFile(path));
		}

		public Builder withSchema(InputStream in) {
			return withSchema(INISchema.fromInput(in));
		}

		public Builder withSchema(INISchema schema) {
			this.schema = Optional.of(schema);
			return this;
		}

		public Builder withDefault(InputStream in) {
			try {
				return withDefault(iniReader().read(in));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (ParseException e) {
				throw new IllegalArgumentException(e);
			}
		}

		public Builder withDefault(INI defaultIni) {
			this.defaultIni = Optional.of(defaultIni);
			return this;
		}

		public Builder withPath(Scope scope, Path path) {
			paths.put(scope, path);
			return this;
		}

		public INISet build() {
			return new INISet(this);
		}

	}

	public final static class INIRef {
		private final Optional<Path> path;
		private final Optional<INI> ini;
		private final Scope scope;

		INIRef(INI doc) {
			this.scope = Scope.GLOBAL;
			this.ini = Optional.of(doc);
			this.path = Optional.empty();
		}

		public INI document() {
			return ini.orElseThrow(() -> new IllegalStateException("No document"));
		}

		INIRef(Path path, Scope scope, INI ini) {
			this.path = Optional.of(path);
			this.scope = scope;
			this.ini = Optional.of(ini);
		}

		INIRef(Path path, Scope scope) {
			this.path = Optional.of(path);
			this.scope = scope;
			if (Files.exists(path)) {
				try {
					ini = Optional.of(iniReader().read(path));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
			} else {
				ini = Optional.empty();
			}
		}

		public INIRef writable() {
			if (ini.isPresent()) {
				return this;
			} else {
				return new INIRef(path.orElseThrow(() -> new IllegalStateException("No path.")), scope, INI.create());
			}
		}

		public void write() throws IOException {
			new INIWriter.Builder().build().write(document(),
					path.orElseThrow(() -> new IllegalStateException("No path.")));
		}

		public boolean isWritable() {
			return path.isPresent() && Files.isWritable(path.get());
		}

		public Path path() {
			return path.orElseThrow(() -> new IllegalStateException("No path."));
		}

//		Reader reader() {
//			if(ini.isPresent()) {
//				return new StringReader(ini.toString());
//			}
//			else
//				return new StringReader("");
//		}
	}

	private final Optional<INISchema> schema;
	private final Optional<INI> defaultIni;
	private List<Scope> scopes = new ArrayList<>();
	private final String app;
	private final Map<Scope, Path> paths;
	private final List<INIRef> refs = new ArrayList<>();
	private final List<MonitorHandle> handles = new ArrayList<>();

	private final String name;
	private final Optional<Monitor> monitor;
	private final INI master;
	private final Optional<Scope> writeScope;

	private final ScheduledExecutorService executor;
	private ScheduledFuture<?> reloadTask;
	private final INI wrapper;
	private final boolean systemPropertyOverrides;

	private INISet(Builder builder) {
		this.monitor = builder.monitor;
		this.systemPropertyOverrides = builder.systemPropertyOverrides;
		this.schema = builder.schema;
		this.defaultIni = builder.defaultIni;
		this.app = builder.app.orElse(DEFAULT_APP_NAME);
		this.paths = Collections.unmodifiableMap(new HashMap<>(builder.paths));
		this.name = builder.name;
		this.scopes = Collections.unmodifiableList(new ArrayList<>(builder.scopes));
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.writeScope = builder.writeScope;

		master = load();
		
		if(this.schema.isPresent()) {
			wrapper = this.schema.get().facadeFor(new RootWrapper(master, this));
		}
		else
			wrapper = new RootWrapper(master, this);
	}
	
	public INISchema schema() {
		return schema.get();
	}

	public INIRef ref(Scope scope) {
		return refs.stream().filter(ref -> ref.scope.equals(scope)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(MessageFormat.format("No ref for scope {0}", scope)));
	}

	public INI document() {
		return wrapper;
	}

	public Path rootPathForScope(Scope scope) {
		var root = paths.get(scope);
		if (root == null) {
			if (isLinux()) {
				switch (scope) {
				case GLOBAL:
					return Paths.get("/etc");
				case USER:
					return resolveHome().resolve(".config");
				default:
					throw new UnsupportedOperationException();
				}

			} else if (isWindows()) {
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
		return root;
	}

	private static boolean isLinux() {
		return os.contains("linux");
	}

	private static boolean isWindows() {
		return os.contains("windows");
	}

	public Path appPathForScope(Scope scope) {
		var root = rootPathForScope(scope);
		if (!isLinux() && !isWindows() && scope == Scope.USER) {
			return root.resolve("." + app);
		} else {
			return root.resolve(app);
		}
	}

	@Override
	public void close() {
		try {
			cancelReloadTask();
			closeMonitorHandles();
		} finally {
			executor.shutdown();
		}
	}

	private void closeMonitorHandles() {
		handles.forEach(MonitorHandle::close);
		handles.clear();
	}

	private INI load() {
		/* First add the default, if any */
		defaultIni.ifPresent(doc -> refs.add(new INIRef(doc)));

		if (scopes.isEmpty()) {
			load(Scope.GLOBAL);
			load(Scope.USER);
		} else {
			scopes.forEach(this::load);
		}

		return mergeToMaster();
	}

	private static INIReader iniReader() {
		return new INIReader.Builder().withInterpolator(Interpolation.defaults()).build();
	}

	private INI mergeToMaster() {
		INI master = null;
		for (var ref : refs) {
			if (ref.ini.isPresent()) {
				if (master == null) {
					master = ref.ini.get();
				} else {
					merge(master, ref.ini.get(), true);
				}
			}
		}
		if (master == null)
			master = INI.create();
		return master;
	}

	private void load(Scope scope) {
		var path = appPathForScope(scope);
		var setRootPath = path.resolve(name + ".ini");
		var setRootDirPath = path.resolve(name + ".d");

		/*
		 * Watch for either [name].ini appearing, disappearing or changing, or [name].d
		 * appearing / disappearing
		 */
		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path);
			} catch (IOException ioe) {
			}
		}

		/* Next look for <name>.ini as a file */
		refs.add(new INIRef(setRootPath, scope));

		if (Files.exists(path)) {

			/* Now look for <name>.d as a directory */
			if (Files.exists(setRootDirPath)) {
				
				try (var strm = Files.newDirectoryStream(setRootDirPath,
						f -> f.getFileName().toString().endsWith(".ini"))) {

					strm.forEach(p -> {
						refs.add(new INIRef(p, scope)); 
					});
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}

				
				monitor.ifPresent(mtr -> {
					handles.add(mtr.monitor(setRootDirPath, (ce) -> {
						reload();
					}));	
				});
				
			}

			monitor.ifPresent(mtr -> {
				handles.add(mtr.monitor(path, (ce) -> {
					var fullContext = path.resolve(ce.context());
	
					if (fullContext.equals(setRootPath) || fullContext.equals(setRootDirPath)) {
						reload();
					}
				}));
			});
		}
	}

	private void reload() {
		cancelReloadTask();
		reloadTask = executor.schedule(() -> {
			try {
				refs.clear();
				closeMonitorHandles();
				merge(master, load(), false);
			} catch (Exception e) {
			}
		}, 1, TimeUnit.SECONDS);
	}

	private void merge(Data oldDoc, Data newDoc, boolean init) {
		mergeValues(oldDoc, newDoc, init);
		mergeSections(oldDoc, newDoc, init);
	}

	private String sectionName(Data newDoc) {
		if (newDoc instanceof Section) {
			var sec = (Section)newDoc;
			return String.join(".", sec.path());
		}
		else
			return "<root>";
	}

	private void mergeSections(Data oldDoc, Data newDoc, boolean init) {
		/* TODO: multiple sections with same key */
		for (var en : newDoc.sections().keySet()) {
			var newSec = newDoc.section(en);
			var oldSec = oldDoc.sectionOr(en).orElse(oldDoc.create(en));
			merge(oldSec, newSec, init);
		}

		if (!init) {
			for (var it = oldDoc.sections().values().iterator(); it.hasNext();) {
				var oldSec = it.next()[0];
				if (!newDoc.containsSection(oldSec.key())) {
					it.remove();
				}
			}
		}
	}

	private void mergeValues(Data oldDoc, Data newDoc, boolean init) {
		for (var en : newDoc.rawValues().entrySet()) {
			var oldVal = oldDoc.rawValues().get(en.getKey());
			var newVal = en.getValue();

			if (!Arrays.equals(oldVal, newVal)) {
				if (!init) {
					if (oldVal == null) {
						if (newDoc instanceof Section) {
							var sec = (Section)newDoc;
						}
					} 
				}
				oldDoc.putAll(en.getKey(), newVal);
			}
		}

		if (!init) {
			for (var it = oldDoc.keys().iterator(); it.hasNext();) {
				var key = it.next();
				if (!newDoc.contains(key)) {
					if (!init) {
						if (newDoc instanceof Section) {
							var sec = (Section)newDoc;
						} 
					}
					it.remove();
				}
			}
		}
	}

	protected void cancelReloadTask() {
		if (reloadTask != null) {
			reloadTask.cancel(false);
		}
	}

	private Path resolveHome() {
		return Paths.get(System.getProperty("user.home"));
	}
}
