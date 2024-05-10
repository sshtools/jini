package com.sshtools.jini.schema;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.WrappedINI;

public class INISchema {

	public enum Type {
		SECTION, ENUM, BOOLEAN, TEXT, NUMBER, LIST, LOCATION, FLOAT, COLOR
	}

	public final static class Builder {
		private INI ini;
		private Map<String, Supplier<List<?>>> lists = new HashMap<>();

		public Builder fromDocument(INI ini) {
			this.ini = ini;
			return this;
		}

		public Builder fromFile(Path path) {
			return fromDocument(INI.fromFile(path));
		}

		public Builder fromInput(InputStream in) {
			return fromDocument(INI.fromInput(in));
		}

		public Builder withList(String name, Supplier<List<?>> supplier) {
			lists.put(name, supplier);
			return this;
		}

		public INISchema build() {
			return new INISchema(this);
		}

	}

	public final static class SectionDescriptor {
		private final String key;
		private final String name;
		private final Optional<String> description;
		private final String[] path;
		private final List<KeyDescriptor> keys;
		private final List<SectionDescriptor> sections;

		private SectionDescriptor(String key, String name, Optional<String> description, List<KeyDescriptor> keys,
				List<SectionDescriptor> sections,
				String... path) {
			super();
			this.key = key;
			this.name = name;
			this.path = path;
			this.description = description;
			this.keys = keys;
			this.sections = sections;
		}

		public String name() {
			return name;
		}

		public List<KeyDescriptor> keys() {
			return keys;
		}

		public List<SectionDescriptor> sections() {
			return sections;
		}

		public String[] path() {
			return path;
		}

		public String description() {
			return description.orElseThrow(() -> new IllegalStateException(key + " has no description"));
		}

		public Optional<String> descriptionOr() {
			return description;
		}

		public String key() {
			return key;
		}
	}

	public final static class KeyDescriptor {
		private final String key;
		private final String name;
		private final Optional<String> description;
		private final Type type;
		private final Optional<String[]> values;
		private final String[] defaultValues;

		private KeyDescriptor(String key, String name, Type type, Optional<String[]> values, String[] defaultValue,
				Optional<String> description) {
			super();
			if(type.equals(Type.SECTION)) {
				throw new IllegalArgumentException("A key may not be of type " + Type.SECTION);
			}
			this.key = key;
			this.name = name;
			this.type = type;
			this.values = values;
			this.defaultValues = defaultValue;
			this.description = description;
		}

		public String description() {
			return description.orElseThrow(() -> new IllegalStateException(key + " has no description"));
		}

		public Optional<String> descriptionOr() {
			return description;
		}

		public String[] defaultValues() {
			return defaultValues;
		}

		public String name() {
			return name;
		}

		public Optional<String[]> values() {
			return values;
		}

		public String key() {
			return key;
		}

		public Type type() {
			return type;
		}
	}

	public static INISchema fromFile(Path path) {
		return new Builder().fromFile(path).build();
	}

	public static INISchema fromInput(InputStream in) {
		return new Builder().fromInput(in).build();
	}

	private final INI ini;

	private INISchema(Builder bldr) {
		super();
		this.ini = bldr.ini;
	}

	/**
	 * Returns a new {@link INI} document that is guaranteed to contain all sections
	 * and values that the schem defines, and all values will be valid.
	 * <p>
	 * Any attempt to put any value that would be considered invalid by the schema
	 * into the document.
	 *
	 * @param document base document
	 * @return validated document
	 */
	public INI facadeFor(INI document) {
		return new RootWrapper(document, this);
	}

//	public List<SectionDescriptor> sections(String... path) {
//		return sections(null, path);
//	}
//
//	public List<SectionDescriptor> sections(Data parent, String... path) {
//		var fullPath = parent == null ? path : INI.merge(parent.path(), path);
//		System.out.println("Getting all sections in '" + String.join(".", parent.path()) + "' for path '" + String.join(".", path) + "' which makes '" + String.join(".", fullPath) + "'");
//		Section[] allSecs = ini.allSectionsOr(fullPath).orElse(new Section[0]);
//		if(allSecs.length == 0)
//			return Collections.emptyList();
//		else {
//			Section[] childSections = allSecs[0].allSections();
//			return Arrays.asList(childSections).stream().
//					filter(sec -> typeForSection(sec).equals(Type.SECTION) ).
//					map(this::sectionDescriptor).
//					collect(Collectors.toList());
//		}
//	}

	private static Type typeForSection(Section sec) {
		return sec.getEnumOr(Type.class, "type").orElse(Type.SECTION);
	}

	public SectionDescriptor section(String... path) {
		return section(null, path);
	}

	public Optional<SectionDescriptor> sectionOr(String... path) {
		return sectionOr(null, path);
	}

	public SectionDescriptor section(Data parent, String... path) {
		return sectionOr(parent, path).orElseThrow(() -> new IllegalArgumentException("No such section."));
	}

	public Optional<SectionDescriptor> sectionOr(Data parent, String... path) {
		var fullPath = parent == null ? path : INI.merge(parent.path(), path);
		return ini.sectionOr(fullPath).map(this::sectionDescriptor);
	}

	public Optional<KeyDescriptor> keyOr(String key) {
		return keyOr(null, key);
	}

	public Optional<KeyDescriptor> keyOr(Data data, String key) {
		return data instanceof Section ? keyOr((Section) data, key) : keyOr((Section) null, key);
	}

	public Optional<KeyDescriptor> keyOr(Section section, String key) {
		return getKeyOr(schemaSectionPath(section, key));
	}

	public Optional<KeyDescriptor> keyFromPath(String fullPath) {
		return getKeyOr(fullPath.split("\\."));
	}

	private Optional<KeyDescriptor> getKeyOr(String... secpath) {
		return ini.sectionOr(secpath)
			.map(sec ->
				new KeyDescriptor(
					secpath[secpath.length - 1],
					sec.getOr("name").orElse(secpath[secpath.length - 1]),
					typeForSection(sec),
					sec.getAllOr("value"),
					sec.getAllElse("default-value"),
					sec.getOr("description"))
			);
	}

	private SectionDescriptor sectionDescriptor(Section section) {
		var type = typeForSection(section);
		if(!type.equals(Type.SECTION)) {
			throw new IllegalArgumentException("Section may only be of type " + Type.SECTION);
		}

		var sections = section.sections();
		return new SectionDescriptor(
				section.key(), 
				section.get("name", section.key()), 
				section.getOr("description"), 
				sections.values().stream().
					filter(s -> !typeForSection(s[0]).equals(Type.SECTION)).
					map(k -> {
						return keyOr(section, k[0].key()).
								orElseThrow(() -> new IllegalStateException("Huh? " + k[0].key() + " @ " + String.join(".", section.path())));
					}).collect(Collectors.toList()), 
				sections.values().stream().
						filter(s -> typeForSection(s[0]).equals(Type.SECTION)).
						map(k -> { 
							return sectionDescriptor(k[0]); 
						}).collect(Collectors.toList()),
				section.path()
		);
	}

	private String[] schemaSectionPath(Section section, String key) {
		if (section == null) {
			return new String[] { key };
		} else {
			var path = section.path();
			var keyDescPath = new String[path.length + 1];
			System.arraycopy(path, 0, keyDescPath, 0, path.length);
			keyDescPath[path.length] = key;
			return keyDescPath;
		}
	}

	public INI ini() {
		return ini;
	}

	private abstract static class AbstractWrapper<DEL extends Data>
			extends WrappedINI.AbstractWrapper<DEL, INISchema, SectionWrapper> {

		public AbstractWrapper(DEL delegate, AbstractWrapper<?> parent, INISchema set) {
			super(delegate, parent, set);
		}

		@Override
		public Optional<String[]> getAllOr(String key) {
			var res = delegate.getAllOr(key);
			if (res.isPresent()) {
				return res;
			} else {
				var meta = userObject.keyOr(this, key);
				if(meta.isEmpty()) {
					return Optional.empty();
				} else {
					var def = meta.map(KeyDescriptor::defaultValues);
					return Optional.of(def.orElse(new String[0]));
				}
			}
		}

		@Override
		public Optional<Section[]> allSectionsOr(String... path) {
			Section[] allSections = super.allSectionsOr(path).orElse(new Section[0]);
			var mgr = new LinkedHashMap<String, Section[]>(Arrays.asList(allSections).stream().collect(Collectors.toMap(Section::key, (s) -> {
				return new Section[] { s };
			})));
			addSchemaSections(mgr, path);
			var vals = mgr.values().stream().flatMap(secs -> Arrays.asList(secs).stream()).collect(Collectors.toList());
			return vals.isEmpty() ? Optional.empty() : Optional.of(vals.toArray(new Section[0]));
		}

		@Override
		public Map<String, Section[]> sections() {
			var mgr = new LinkedHashMap<>(super.sections());
			addSchemaSections(mgr, path());
			return mgr;
		}

		private void addSchemaSections(HashMap<String, Section[]> mgr, String... path) {
			userObject.sectionOr(this, path).ifPresent(sd -> {
				if(path.length == 0) {
					for(var sec : sd.sections()) {
						if(!mgr.containsKey(sec.key()))
							mgr.put(sec.key(), new Section[] { wrapSection(delegate.create(sec.key())) });
					}
				}
				else {
					if(!mgr.containsKey(sd.key()))
						mgr.put(sd.key(), new Section[] { wrapSection(delegate.create(sd.key())) });
				}
			});
		}

		@Override
		public Map<String, String[]> rawValues() {
			var fullMap = new LinkedHashMap<>(super.rawValues());
			addDefaults(fullMap);
			return fullMap;
		}

		@Override
		public Map<String, String[]> values() {
			var fullMap = new LinkedHashMap<>(super.values());
			addDefaults(fullMap);
			return fullMap;
		}

		@Override
		public Set<String> keys() {
			var l = new LinkedHashSet<>(delegate.keys());
			l.addAll(userObject.section(path()).keys().stream().map(KeyDescriptor::key).collect(Collectors.toList()));
			return l;
		}

		@Override
		public boolean containsSection(String... key) {
			return super.containsSection(key) || userObject.ini().containsSection(INI.merge(path(), key));
		}

		@Override
		public boolean contains(String key) {
			return delegate.contains(key) || userObject.keyOr(this, key).isPresent();
		}

		@Override
		protected SectionWrapper createWrappedSection(Section delSec) {
			return new SectionWrapper(delSec, this, userObject);
		}

		protected void addDefaults(LinkedHashMap<String, String[]> fullMap) {
			userObject.section(path()).keys().forEach(kd -> {
				if(!fullMap.containsKey(kd.key())) {
					fullMap.put(kd.key(), kd.defaultValues());
				}
			});
		}
	}

	private final static class SectionWrapper extends AbstractWrapper<Section> implements Section {

		public SectionWrapper(Section delegate, AbstractWrapper<?> parent, INISchema set) {
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
			} else {
				throw new IllegalStateException("Root section.");
			}
		}

	}

	private final static class RootWrapper extends AbstractWrapper<INI> implements INI {

		public RootWrapper(INI delegate, INISchema set) {
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
}
