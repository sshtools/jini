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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
 * ~/.config/jini/jini.d. All files in here are read as if they were a
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
	
	public enum CreateDefaultsMode {
		NONE, INI, SAMPLE
	}

	private static final String DEFAULT_APP_NAME = "jini";
	private final static String os = System.getProperty("os.name", "unknown").toLowerCase();
	
	private final static class MergeResults {
		private Set<String> sectionPaths = new HashSet<>();
		private Set<String> keyPaths = new HashSet<>();
	}

	private abstract static class AbstractSetWrapper<DEL extends Data> extends WrappedINI.AbstractWrapper<DEL, INISet, SetSectionWrapper> {

		protected final boolean systemPropertyOverrides;

		public AbstractSetWrapper(boolean systemPropertyOverrides, DEL delegate, AbstractSetWrapper<?> parent, INISet set) {
			super(delegate, parent, set); 
			this.systemPropertyOverrides = systemPropertyOverrides;
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
			if(path.length > 0 && systemPropertyOverrides) {
				var var = System.getProperty(userObject.name + "." +String.join(".", path) + "." + key);
				if(var != null) {
					return Optional.of(new String[] { var });
				}
			}
			return delegate.getAllOr(key);
		}

		@Override
		protected SetSectionWrapper createWrappedSection(Section delSec) {
			return new SetSectionWrapper(systemPropertyOverrides, delSec, this, userObject);
		}

		@Override
		public final <E extends Enum<E>> void putAllEnum(String key, @SuppressWarnings("unchecked") E... values) {
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
				var wtrblDoc = ref.document();
				if (delegate instanceof INI) {
					task.accept(wtrblDoc);
				} else {
					var sec = (Section) delegate;
					var thisSectionPath = sec.path();
					
					if(sec.parentOr().isPresent()) {
						int index = sec.index();
						Section allSec = wtrblDoc.allSections(thisSectionPath)[index];
						task.accept(allSec);
					}
					else {
						task.accept(wtrblDoc.obtainSection(thisSectionPath));
					}
				}
				ref.write();
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			} finally {
				task.accept(delegate);
			}
		}
	}

	private final static class SetSectionWrapper extends AbstractSetWrapper<Section> implements Section {

		public SetSectionWrapper(boolean systemPropertyOverrides, Section delegate, AbstractSetWrapper<?> parent, INISet set) {
			super(systemPropertyOverrides, delegate, parent, set);
			if(parent == null)
				throw new IllegalStateException("A section must have a parent");
		}

		@Override
		public final void remove() {
			delegate.remove();
			((AbstractSetWrapper<?>) parent).removeSection(delegate);
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
		public int index() {
			return delegate.index();
		}

		@Override
		public final Section parent() {
			if (parent instanceof Section) {
				return (Section) parent;
			} else
				throw new IllegalStateException("Root section.");
		}

	}

	private final static class RootSetWrapper extends AbstractSetWrapper<INI> implements INI {

		public RootSetWrapper(boolean systemPropertyOverrides,INI delegate, INISet set) {
			super(systemPropertyOverrides, delegate, null, set);
		}

		@Override
		public INI readOnly() {
			return new RootSetWrapper(systemPropertyOverrides, delegate.readOnly(), userObject);
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
		private Optional<Scope> writeScope;
		private Optional<INISchema> schema = Optional.empty();
		private Optional<INI> defaultIni = Optional.empty();
        private Optional<InputStream> defaultIniStream = Optional.empty();
		private Optional<String> app = Optional.empty();
		private Optional<Monitor> monitor = Optional.empty();
		private Map<Scope, Path> paths = new HashMap<>();
		private List<Scope> scopes = new ArrayList<>();
		private boolean systemPropertyOverrides = true;
		private String extension = ".ini";
		private boolean dropInDirectories = true;
		private CreateDefaultsMode createDefaults = CreateDefaultsMode.NONE;
		private Optional<Supplier<INIReader.Builder>> readerFactory = Optional.empty();
        private Optional<Supplier<INIWriter.Builder>> writerFactory = Optional.empty();

		private final String name;
		public boolean closeDefaultIniStream;

		public Builder(String name) {
			this.name = name;
		}
		
		public Builder withReaderFactory(Supplier<INIReader.Builder> readerFactory) {
		    this.readerFactory = Optional.of(readerFactory);
		    return this;
		}
        
        public Builder withWriterFactory(Supplier<INIWriter.Builder> writerFactory) {
            this.writerFactory = Optional.of(writerFactory);
            return this;
        }

		public Builder withoutDropInDirectories() {
			return withDropInDirectories(false);
		}
		
		public Builder withDropInDirectories(boolean dropInDirectories) {
			this.dropInDirectories = dropInDirectories;
			return this;
		}
		
		public Builder withExtension(String extension) {
			this.extension = extension;
			return this;
		}

		public Builder withCreateDefaultsAsINI() {
			return withCreateDefaults(CreateDefaultsMode.INI);
		}

		public Builder withCreateDefaultsAsSampleINI() {
			return withCreateDefaults(CreateDefaultsMode.SAMPLE);
		}

		public Builder withCreateDefaults(CreateDefaultsMode createDefaults) {
			this.createDefaults = createDefaults;
			return this;
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

		public Builder withAllScopes() {
			return withScopes(Scope.values());
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

		public Builder withSchema(Class<?> base) {
			return withSchema(base, base.getSimpleName() + ".schema.ini");
		}

		public Builder withSchema(Class<?> base, String resource) {
			try (var in = base.getResourceAsStream(resource)) {
				return withSchema(in);
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}

		public Builder withDefault(Class<?> base, String resource) {
			closeDefaultIniStream = true;
			return withDefault(base.getResourceAsStream(resource));
		}

		public Builder withOptionalDefault(Class<?> base, String resource) {
			var in = base.getResourceAsStream(resource);
			if(in == null)
				return this;
			return withDefault(in);
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
		    this.defaultIniStream = Optional.of(in);
		    return this;
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
	
	private static INIReader.Builder defaultReader() {
        return new INIReader.Builder().	
        		withInterpolator(Interpolation.defaults());
	}

	public final static class INIRef {
		private final Optional<Path> path;
		private final Scope scope;
		private INI ini;
        private final Optional<Supplier<com.sshtools.jini.INIWriter.Builder>> writerFactory;

		INIRef(INI doc, Optional<Supplier<com.sshtools.jini.INIWriter.Builder>> writerFactory) {
			this.scope = Scope.GLOBAL;
			this.ini = doc;
			this.path = Optional.empty();
			this.writerFactory = writerFactory;
		}
		

		public INI document() {
			return ini;
		}

		INIRef(Path path, Scope scope, Optional<Supplier<INIReader.Builder>> readerFactory, Optional<Supplier<INIWriter.Builder>> writerFactory) {
			this.path = Optional.of(path);
			this.scope = scope;
			this.writerFactory = writerFactory;
			if (Files.exists(path)) {
				try {
					ini = readerFactory.map(Supplier::get).orElseGet(INISet::defaultReader).build().read(path);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e);
				}
			} else {
				ini = INI.create();
			}
		}

		public void write() throws IOException {
		    writerFactory.map(Supplier::get).orElseGet(this::defaultWriterBuilder).build().write(document(),
					path.orElseThrow(() -> new IllegalStateException("No path.")));
		}

	    protected INIWriter.Builder defaultWriterBuilder() {
	        return new INIWriter.Builder();
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
	private final List<Scope> scopes;
	private final String app;
	private final String extension;
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
	private final boolean dropInDirectories;
    private final Optional<Supplier<INIReader.Builder>> readerFactory;
    private final Optional<Supplier<INIWriter.Builder>> writerFactory;
	private CreateDefaultsMode createDefaults;

	private INISet(Builder builder) {
	    this.readerFactory = builder.readerFactory;
	    this.writerFactory = builder.writerFactory;
		this.monitor = builder.monitor;
		this.dropInDirectories = builder.dropInDirectories;
		this.extension = builder.extension;
		this.systemPropertyOverrides = builder.systemPropertyOverrides;
		this.schema = builder.schema;
		if(builder.defaultIniStream.isPresent()) {
			var in = builder.defaultIniStream.get();
		    try {
		        this.defaultIni = Optional.of(readerFactory.map(Supplier::get).orElseGet(INISet::defaultReader).build().read(in));
		    } catch (IOException e) {
		        throw new UncheckedIOException(e);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e);
            } finally {
            	if(builder.closeDefaultIniStream) {
            		try {
						in.close();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
            	}
            }
		}
		else {
		    this.defaultIni = builder.defaultIni;
		};
		
		this.app = builder.app.orElse(DEFAULT_APP_NAME);
		this.paths = Collections.unmodifiableMap(new HashMap<>(builder.paths));
		this.name = builder.name;
		this.scopes = builder.scopes.isEmpty() 
				? detectScopes() 
				: Collections.unmodifiableList(new ArrayList<>(builder.scopes));
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.writeScope = builder.writeScope == null
				? detectWriteScope()
				: builder.writeScope;
		
		this.createDefaults = builder.createDefaults;
		if(builder.createDefaults != CreateDefaultsMode.NONE) {
			maybeWriteDefaults(this.writeScope.orElseGet(() -> scopes().get(0)));
		}

		master = load();
		
		if(this.schema.isPresent()) {
			wrapper = this.schema.get().facadeFor(new RootSetWrapper(systemPropertyOverrides, master, this));
		}
		else
			wrapper = new RootSetWrapper(systemPropertyOverrides, master, this);
	}

	public void maybeWriteDefaults(Scope scope) {
		if(createDefaults == CreateDefaultsMode.INI)
			schema().maybeWriteDefaults(appPathForScope(scope).resolve(name + extension));
		else
			schema().maybeWriteDefaults(appPathForScope(scope).resolve(name + ".sample" + extension));
	}
	
	public String app() {
		return app;
	}

	public String name() {
		return name;
	}

	public List<Scope> scopes() {
		return scopes;
	}

	public Optional<Scope> writeScope() {
		return writeScope;
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
			return getDefaultPathForScope(scope);
		}
		return root;
	}

	public static Path getDefaultPathForScope(Scope scope) {
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

	private Optional<Scope> detectWriteScope() {
		for(var p : Scope.values()) {
			if(isWritableDir(appPathForScope(p)))  {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}
	
	private List<Scope> detectScopes() {
		var l = new ArrayList<Scope>();
		var path = appPathForScope(Scope.GLOBAL);
		
		if (isWritableDir(path)) {
			l.add(Scope.GLOBAL);
		}
		else {
			l.add(Scope.USER);
		}
		
		return l;
	}
	
	private boolean isWritableDir(Path path) {
		if (Files.exists(path)) {
			if(Files.isWritable(path)) {
				return true;
			}
		}
		else {
			try {
				Files.createDirectories(path);
				return true;
			} catch (IOException ioe) {
			}
		}

		return false;
	}

	private void closeMonitorHandles() {
		handles.forEach(MonitorHandle::close);
		handles.clear();
	}

	private INI load() {
		/* First add the default, if any */
		defaultIni.ifPresent(doc -> refs.add(new INIRef(doc, writerFactory)));

		var scplst = scopes();
		if (scplst.isEmpty()) {
			load(Scope.GLOBAL);
			load(Scope.USER);
		} else {
			scplst.forEach(this::load);
		}

		return mergeToMaster();
	}

	private INI mergeToMaster() {
		INI master = null;
		var results = new MergeResults();
		for (var ref : refs) {
			
			if (master == null) {
				master = ref.ini;
			} else {
				merge(results, master, ref.ini, true);
			}
		}
		if (master == null)
			master = INI.create();
		return master;
	}

	private void load(Scope scope) {
		var path = appPathForScope(scope);
		var setRootPath = path.resolve(name + extension);
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
		refs.add(new INIRef(setRootPath, scope, readerFactory, writerFactory));

		if (Files.exists(path)) {

			/* Now look for <name>.d as a directory */
			if (dropInDirectories && Files.exists(setRootDirPath)) {
				
				try (var strm = Files.newDirectoryStream(setRootDirPath,
						f -> f.getFileName().toString().endsWith(extension))) {

					strm.forEach(p -> {
						refs.add(new INIRef(p, scope, readerFactory, writerFactory)); 
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
				var results = new MergeResults();
				merge(results, master, load(), false);
				finaliseMerge(results, master);
			} catch (Exception e) {
			}
		}, 1, TimeUnit.SECONDS);
	}

	private void finaliseMerge(MergeResults results, INI document) {
		removeValues(results, document);
		
	}
	private void removeValues(MergeResults results, Data doc) {
		var it = doc.values().entrySet().iterator();
		while(it.hasNext()) {
			var en = it.next();
			if(!results.keyPaths.contains(keyPath(doc, en.getKey()))) {
				it.remove();
			}
		}
	}

	private void merge(MergeResults results, Data oldDoc, Data newDoc, boolean init) {
		mergeValues(results, oldDoc, newDoc, init);
		mergeSections(results, oldDoc, newDoc, init);
	}

	private void mergeSections(MergeResults results, Data oldDoc, Data newDoc, boolean init) {
		for (var en : newDoc.sections().keySet()) {
			
			results.sectionPaths.add(keyPath(oldDoc, en));
			
			var newSecs = newDoc.allSections(en);
			var oldSecs = oldDoc.allSectionsOr(en).orElse(new Section[0]);
			var it1 = Arrays.asList(newSecs).iterator();
			var it2 = Arrays.asList(oldSecs).iterator();
			while(it1.hasNext()) {
				var newIt = it1.next();
				if(it2.hasNext()) {
					var oldIt = it2.next();
					merge(results, oldIt, newIt, init);
				}
				else {
					merge(results, oldDoc.create(en), newIt, init);
				}
			}
		}
	}

	private String keyPath(Data data, String suffix) {
		return String.join("/", INI.merge(suffix, data.path()));
	}

	private void mergeValues(MergeResults results, Data oldDoc, Data newDoc, boolean init) {
		for (var en : newDoc.rawValues().entrySet()) {
			
			results.keyPaths.add(keyPath(oldDoc, en.getKey()));
			
			var oldVal = oldDoc.rawValues().get(en.getKey());
			var newVal = en.getValue();

			if (!Arrays.equals(oldVal, newVal)) {
				if (!init) {
					if (oldVal == null) {
						if (newDoc instanceof Section) {
							// TODO
//							var sec = (Section)newDoc;
						}
					} 
				}
				oldDoc.putAll(en.getKey(), newVal);
			}
		}
	}

	protected void cancelReloadTask() {
		if (reloadTask != null) {
			reloadTask.cancel(false);
		}
	}

	private static Path resolveHome() {
		return Paths.get(System.getProperty("user.home"));
	}
}
