package com.sshtools.jini.schema;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.WrappedINI;

public class INISchema {
	
	public final static INIReader schemaReader() {
		return new INIReader.Builder().
				withDuplicateKeysAction(DuplicateAction.APPEND).
				withMultiValueMode(MultiValueMode.REPEATED_KEY).
				withDuplicateSectionAction(DuplicateAction.ABORT).
				build();
	}
	
	public final static INIWriter schemaWriter() {
		return new INIWriter.Builder().
				withMultiValueMode(MultiValueMode.REPEATED_KEY).
				withSectionPathSeparator('\\').
				build();
	}

	private static final String SCHEMA_ITEM_ARITY = "arity";
	public static final String SCHEMA_ITEM_DESCRIPTION = "description";
	public static final String SCHEMA_ITEM_NAME = "name";

	public final static class Builder {
		private INI ini;
		private Map<String, Supplier<List<?>>> lists = new HashMap<>();

		public Builder fromDocument(INI ini) {
			this.ini = ini;
			return this;
		}

		public Builder fromFile(Path path) {
		  	try {
				return fromDocument(schemaReader().read(path));
	        } catch (IOException ioe) {
	            throw new UncheckedIOException(ioe);
	        } catch (ParseException e) {
	            throw new IllegalStateException("Failed to parse.", e);
	        }
		}

		public Builder fromInput(InputStream in) {
		  	try {
				return fromDocument(schemaReader().read(in));
	        } catch (IOException ioe) {
	            throw new UncheckedIOException(ioe);
	        } catch (ParseException e) {
	            throw new IllegalStateException("Failed to parse.", e);
	        }
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
		private final Arity arity;

		private SectionDescriptor(String key, String name, Optional<String> description, List<KeyDescriptor> keys,
				List<SectionDescriptor> sections,
				Optional<Arity> arity,
				String... path) {
			super();
			this.key = key;
			this.name = name;
			this.path = path;
			this.description = description;
			this.keys = keys;
			this.sections = sections;
			this.arity = arity.orElse(Arity.ONE);
		}
		
		public Arity arity() {
			return arity;
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
		private final Optional<Discriminator> discriminator;
		private final Arity arity;

		private KeyDescriptor(String key, String name, Type type, Optional<String[]> values, String[] defaultValue,
				Optional<String> description, Optional<Discriminator> discriminator, Optional<Arity> arity) {
			super();
			if(type.equals(Type.SECTION)) {
				throw new IllegalArgumentException("Key may not be of type " + Type.SECTION);
			}
			this.key = key;
			this.name = name;
			this.type = type;
			this.discriminator = discriminator;
			this.values = values;
			this.defaultValues = defaultValue;
			this.description = description;
			this.arity = arity.orElseGet(() ->
				defaultValues.length == 0 ? Arity.ONE : Arity.NO_MORE_THAN_ONE
			);
		}
		
		public Arity arity() {
			return arity;
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

		public Discriminator discriminator() {
			return discriminatorOr().get();
		}

		public Optional<Discriminator> discriminatorOr() {
			return discriminator;
		}
	}

	public static INISchema fromFile(File file) {
		return fromFile(file.toPath());
	}

	public static INISchema fromFile(Path path) {
		return new Builder().fromFile(path).build();
	}

	public static INISchema fromDocument(INI document) {
		return new Builder().fromDocument(document).build();
	}

	public static INISchema fromClass(Class<?> base) {
		return fromClass(base, base.getSimpleName() + ".schema.ini");
	}

	public static INISchema fromClass(Class<?> base, String resource) {
		try (var in = base.getResourceAsStream(resource)) {
			return fromInput(in);
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
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
	 * and values that the schema defines, and all values will be valid.
	 * <p>
	 * Any attempt to put any value that would be considered invalid by the schema
	 * into the document is rejected.
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

	public void maybeWriteDefaults(File defaultsFile)  {
		maybeWriteDefaults(defaultsFile.toPath());
	}
	
	public void maybeWriteDefaults(Path defaultsFile)  {
		if(!Files.exists(defaultsFile)) {
			writeDefaults(defaultsFile);
		}
	}

	public void writeDefaults(File defaultsFile)  {
		writeDefaults(defaultsFile.toPath());
	}

	public void writeDefaults(Path defaultsFile)  {
		try {
			Files.createDirectories(defaultsFile.getParent());
			try(var out = Files.newBufferedWriter(defaultsFile)) {
				writeDefaults(out);
			}
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}
	
	public void writeDefaults(Writer writer) throws IOException {
		var printer = new PrintWriter(writer);
		var first = new AtomicBoolean();
		ini.sections().forEach((key, secs) -> {
			writeSecDefaults(first.get(), secs[0], 0, printer);
			first.set(false);
		});
		printer.flush();
	}

	private void writeSecDefaults(boolean first, Section section, int indent, PrintWriter printer) {
		if(!first) {
			printer.println();
		}
		/* TODO section separator config */
		/* TODO indent config */
		/* TODO multi value config */
		
		var ind = indent(indent);
		
		section.getOr("type").ifPresentOrElse(type -> {
			
			/* If it has a type, its a key */
			printNameAndDescription(section, printer, ind);
			section.getOr("default-value").ifPresentOrElse(val ->
				printer.format(";%s%s = %s%n", ind, section.key(), val) 
			, () -> {
				printer.format(";%s %s = %n", ind, section.key()); 
			});
			
		}, () -> {
			
			/* Otherwise it's a section, so recurse */
			printer.format(";%s%n", repeat('-', 40));

			printNameAndDescription(section, printer, ind);
			
			printer.format(";%s%n", repeat('-', 40));
			printer.format(";%s[%s]%n", ind, String.join(".", section.path()));

			Arrays.asList(section.allSections()).forEach(secsec -> {
				writeSecDefaults(first, secsec, indent + 1, printer);
			});
		});
		
	}

	private void printNameAndDescription(Section section, PrintWriter printer, String ind) {
		section.getOr(SCHEMA_ITEM_NAME).ifPresentOrElse(name -> {
			section.getOr(SCHEMA_ITEM_DESCRIPTION).ifPresentOrElse(desc ->
				printer.format(";%s %s - %s%n", ind, name, desc) 
			, () -> printer.format(";%s %s%n", ind, name));
		}
		, () -> {
			section.getOr(SCHEMA_ITEM_DESCRIPTION).ifPresent(desc ->
				printer.format(";%s %s%n", ind, desc));
		});
	}
	
	private String repeat(char ch, int width) {
		var b = new StringBuilder();
		for(var i = 0 ; i < width ; i++)
			b.append(ch);
		return b.toString();
	}

	
	private String indent(int indent) {
		return indent < 2 ? "" : String.format("%" + ( ( indent - 1 ) * 4 )+ "s", "");
	}

	private Optional<KeyDescriptor> getKeyOr(String... secpath) {
		return ini.sectionOr(secpath)
			.map(sec -> {
				var type = typeForSection(sec); 
				return new KeyDescriptor(
					secpath[secpath.length - 1],
					sec.getOr(SCHEMA_ITEM_NAME).orElse(secpath[secpath.length - 1]),
					type,
					sec.getAllOr("value"),
					sec.getAllElse("default-value"),
					sec.getOr(SCHEMA_ITEM_DESCRIPTION),
					discriminatorForSection(type, sec),
					sec.getOr(SCHEMA_ITEM_ARITY).map(Arity::parse));
			});
	}

	private Optional<Discriminator> discriminatorForSection(Type type, Section sec) {
		return sec.getOr("discriminator").map(d -> type.discriminator(d));
	}

	private SectionDescriptor sectionDescriptor(Section section) {
		var type = typeForSection(section);
		if(!type.equals(Type.SECTION)) {
			throw new IllegalArgumentException("Section may only be of type " + Type.SECTION + ", but a " + type + " was provided");
		}

		var sections = section.sections();
		return new SectionDescriptor(
				section.key(), 
				section.get(SCHEMA_ITEM_NAME, section.key()), 
				section.getOr(SCHEMA_ITEM_DESCRIPTION), 
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
				section.getOr(SCHEMA_ITEM_ARITY).map(Arity::parse),
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
			var allSections = super.allSectionsOr(path).orElse(new Section[0]);
			var mgr = new LinkedHashMap<String, Section[]>();
			for(var sec : allSections) {
				var arr = mgr.get(sec.key());
				if(arr == null)
					mgr.put(sec.key(), new Section[] { sec });
				else {
					var narr = new Section[arr.length + 1];
					System.arraycopy(arr, 0, narr, 0, arr.length);
					narr[arr.length] = sec;
					mgr.put(sec.key(), narr);
				} 
			}
			var vals = mgr.values().stream().flatMap(secs -> Arrays.asList(secs).stream()).collect(Collectors.toList());
			if(!vals.isEmpty()) {
				addSchemaSections(mgr, path);
			}
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
			if(parent == null)
				throw new IllegalArgumentException("A section must have a parent");
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
		public int index() {
			return delegate.index();
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
			var roDelegate = delegate.readOnly();
			return new RootWrapper(roDelegate, userObject);
		}

		@Override
		public INI merge(MergeMode mergeMode, INI... others) {
			throw new UnsupportedOperationException();
		}
	}
}
