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
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter;
import com.sshtools.jini.WrappedINI;

public class INISchema {

	public final static class Builder {
		private INI ini;
		private Map<String, Supplier<List<?>>> lists = new HashMap<>();

		public INISchema build() {
			return new INISchema(this);
		}

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

	}

	public final static class KeyDescriptor {
		private final Multiplicity multiplicity;
		private final String[] defaultValues;
		private final Optional<String> description;
		private final Optional<Discriminator> discriminator;
		private final String key;
		private final String name;
		private final Type type;
		private final Optional<String[]> values;

		private KeyDescriptor(String key, String name, Type type, Optional<String[]> values, String[] defaultValue,
				Optional<String> description, Optional<Discriminator> discriminator, Optional<Multiplicity> multiplicity) {
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
			this.multiplicity = multiplicity.orElseGet(() ->
				defaultValues.length == 0 ? Multiplicity.ONE : Multiplicity.NO_MORE_THAN_ONE
			);
		}

		public Multiplicity multiplicity() {
			return multiplicity;
		}

		public String[] defaultValues() {
			return defaultValues;
		}

		public String description() {
			return description.orElseThrow(() -> new IllegalStateException(key + " has no description"));
		}

		public Optional<String> descriptionOr() {
			return description;
		}

		public Discriminator discriminator() {
			return discriminatorOr().get();
		}

		public Optional<Discriminator> discriminatorOr() {
			return discriminator;
		}

		public String key() {
			return key;
		}

		public String name() {
			return name;
		}

		public Type type() {
			return type;
		}

		public Optional<String[]> values() {
			return values;
		}

		public <DEL extends Data> boolean validate(DEL delegate) {
			return multiplicity.validate((delegate.contains(key) ? delegate.getAll(key) : defaultValues).length);
		}
	}

	public final static class SectionDescriptor {
		private final Multiplicity multiplicity;
		private final Optional<String> description;
		private final String key;
		private final List<KeyDescriptor> keys;
		private final List<KeyDescriptor> allKeys;
		private final String name;
		private final String[] path;
		private final List<SectionDescriptor> sections;

		private SectionDescriptor(
				String key, 
				String name, 
				Optional<String> description, 
				List<KeyDescriptor> allKeys, 
				List<KeyDescriptor> keys,
				List<SectionDescriptor> sections,
				Optional<Multiplicity> multiplicity,
				String... path) {
			super();
			this.key = key;
			this.name = name;
			this.path = path;
			this.description = description;
			this.allKeys = allKeys;
			this.keys = keys;
			this.sections = sections;
			this.multiplicity = multiplicity.orElse(Multiplicity.ONE);
		}

		public Multiplicity arity() {
			return multiplicity;
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

		public List<KeyDescriptor> keys() {
			return keys;
		}

		public List<KeyDescriptor> allKeys() {
			return allKeys;
		}

		public String name() {
			return name;
		}

		public String[] path() {
			return path;
		}

		public List<SectionDescriptor> sections() {
			return sections;
		}
	}
	
	private abstract static class AbstractSchemaWrapper<DEL extends Data>
			extends WrappedINI.AbstractWrapper<DEL, INISchema, SchemaSectionWrapper> {

		public AbstractSchemaWrapper(DEL delegate, AbstractSchemaWrapper<?> parent, INISchema set) {
			super(delegate, parent, set);
		}

		@Override
		public Optional<Section[]> allSectionsOr(String... path) {
			var allSections = super.allSectionsOr(path).orElse(new Section[0]);
			var mgr = new LinkedHashMap<String, Section[]>();
			for(var sec : allSections) {
				mgr.put(sec.key(), Section.add(sec, mgr.get(sec.key())));
			}
			
			addSchemaSections(mgr, path);
				
			var vals = mgr.values().
					stream().
					flatMap(secs -> Arrays.asList(secs).stream()).
					collect(Collectors.toList());
			
			return vals.isEmpty() ? Optional.empty() : Optional.of(vals.toArray(new Section[0]));
		}

		@Override
		public String[] getComments() {
			return delegate.getComments();
		}

		@Override
		public void setComments(String... comments) {
			delegate.setComments(comments);
		}

		@Override
		public String[] getKeyComments(String key) {
			return delegate.getKeyComments(key);
		}

		@Override
		public void setKeyComments(String key, String... comments) {
			delegate.setKeyComments(key, comments);
		}

		@Override
		public boolean contains(String key) {
			return delegate.contains(key) || userObject.keyOr(this, key).isPresent();
		}

		@Override
		public boolean containsSection(String... key) {
			return super.containsSection(key) || userObject.ini().containsSection(INI.merge(path(), key));
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
		public Set<String> keys() {
			var l = new LinkedHashSet<>(delegate.keys());
			l.addAll(userObject.section(path()).keys().stream().map(KeyDescriptor::key).collect(Collectors.toList()));
			return l;
		}

		@Override
		public Map<String, String[]> rawValues() {
			var fullMap = new LinkedHashMap<>(delegate.rawValues());
			addDefaults(fullMap);
			return fullMap;
		}

		@Override
		public Map<String, Section[]> sections() {
			var mgr = new LinkedHashMap<>(super.sections());
			addSchemaSections(mgr);
			return mgr;
		}

		@Override
		public void setComments(List<String> comments) {
			delegate.setComments(comments);
		}

		@Override
		public void setKeyComments(String key, List<String> comments) {
			delegate.setKeyComments(key, comments);
		}

		private void addSchemaSections(HashMap<String, Section[]> mgr, String... path) {
			userObject.sectionOr(this, path).ifPresent(sd -> {
				if(path.length == 0) {
					for(var sec : sd.sections()) {
						maybeWrapSection(mgr, sec);
					}
				}
				else {
					maybeWrapSection(mgr, sd);
				}
			});
		}

		private void maybeWrapSection(HashMap<String, Section[]> mgr, SectionDescriptor sec) {
			boolean haveValue = shouldWrapSection(sec);
			
			if(haveValue && !mgr.containsKey(sec.key()))
				mgr.put(sec.key(), new Section[] { wrapSection(delegate.create(sec.key())) });
		}

		private boolean shouldWrapSection(SectionDescriptor sec) {
			boolean haveValue;
			if(sec.multiplicity.once()) {
				haveValue = false;
				
				/* If a section can exist exactly once. Then it is valid if ANY
				 * of it's keys are  
				 */
				for(var k : sec.allKeys()) {
					if(k.validate(delegate)) {
						haveValue = true;
						break;
					}
				}
				
				/* Or if ANY of its sections are */
				if(!haveValue) {
					for(var s : sec.sections()) {
						if(shouldWrapSection(s)) {
							haveValue = true;
							break;
						}
					}
				}
				
			}
			else {
				var keys = sec.allKeys();
				haveValue = keys.size() > 0;
				/* Other, it's only valid if ALL of its keys are. */
				for(var k : keys) {
					if(!k.validate(delegate)) {
						haveValue = false;
						break;
					}
				}
				
				/* OR if ALL of its sections are */
				if(!haveValue) {
					var secs = sec.sections();

					haveValue = secs.size() > 0;
					/* Other, it's only valid if ALL of its keys are. */
					for(var s : secs) {
						if(!shouldWrapSection(s)) {
							haveValue = false;
							break;
						}
					}
				}
			}
			return haveValue;
		}

		protected void addDefaults(LinkedHashMap<String, String[]> fullMap) {
			var path = path();
			
			Data data;
			if(path.length == 0) {
				data = userObject.ini();
			}
			else {
				data = userObject.ini().section(path);
			}

			Arrays.asList(data.allSections()).forEach(keysec -> {
				var k = keysec.key();
				if(!fullMap.containsKey(k)) {
					userObject.keyOr(data, k).ifPresent(kd -> {
						if(kd.validate(delegate))
							fullMap.put(k, kd.defaultValues());
					});
				}
			});
		}

		@Override
		protected SchemaSectionWrapper createWrappedSection(Section delSec) {
			return new SchemaSectionWrapper(delSec, this, userObject);
		}
	}
	private final static class SchemaFacadeRootWrapper extends AbstractSchemaWrapper<INI> implements INI {

		public SchemaFacadeRootWrapper(INI delegate, INISchema set) {
			super(delegate, null, set);
		}

		@Override
		public INI merge(MergeMode mergeMode, INI... others) {
			throw new UnsupportedOperationException();
		}

		@Override
		public INI readOnly() {
			var roDelegate = delegate.readOnly();
			return new SchemaFacadeRootWrapper(roDelegate, userObject);
		}
	}
	private final static class SchemaSectionWrapper extends AbstractSchemaWrapper<Section> implements Section {

		public SchemaSectionWrapper(Section delegate, AbstractSchemaWrapper<?> parent, INISchema set) {
			super(delegate, parent, set);
			if(parent == null) {
				throw new IllegalArgumentException("A section must have a parent");
			}
		}

		@Override
		public int index() {
			return delegate.index();
		}

		@Override
		public final String key() {
			return delegate.key();
		}

		@Override
		public final Section parent() {
			if (parent instanceof Section) {
				return (Section) parent;
			} else {
				throw new IllegalStateException("Root section.");
			}
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
		public final void remove() {
			delegate.remove();
			((AbstractSchemaWrapper<?>) parent).removeSection(delegate);
		}

	}

	public static final String SCHEMA_ITEM_DESCRIPTION = "description";
	
	public static final String SCHEMA_ITEM_NAME = "name";

	@Deprecated
	private static final String SCHEMA_ITEM_ARITY = "arity";

	private static final String SCHEMA_ITEM_MULTIPLICITY = "multiplicity";

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

	public static INISchema fromDocument(INI document) {
		return new Builder().fromDocument(document).build();
	}

	public static INISchema fromFile(File file) {
		return fromFile(file.toPath());
	}

	public static INISchema fromFile(Path path) {
		return new Builder().fromFile(path).build();
	}

	public static INISchema fromInput(InputStream in) {
		return new Builder().fromInput(in).build();
	}

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

	private static Type typeForSection(Data sec) {
		return sec.getEnumOr(Type.class, "type").orElse(Type.SECTION);
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
		return new SchemaFacadeRootWrapper(document, this);
	}

	public INI ini() {
		return ini;
	}

	public Optional<KeyDescriptor> keyFromPath(String fullPath) {
		return getKeyOr(fullPath.split("\\."));
	}

	public Optional<KeyDescriptor> keyOr(Data data, String key) {
		return data instanceof Section ? keyOr((Section) data, key) : keyOr((Section) null, key);
	}

	public Optional<KeyDescriptor> keyOr(Section section, String key) {
		return getKeyOr(schemaSectionPath(section, key));
	}

	public Optional<KeyDescriptor> keyOr(String key) {
		return keyOr(null, key);
	}

	public void maybeWriteDefaults(File defaultsFile)  {
		maybeWriteDefaults(defaultsFile.toPath());
	}

	public void maybeWriteDefaults(Path defaultsFile)  {
		if(!Files.exists(defaultsFile)) {
			writeDefaults(defaultsFile);
		}
	}

	public SectionDescriptor section(Data parent, String... path) {
		return sectionOr(parent, path).orElseThrow(() -> new IllegalArgumentException("No such section."));
	}

	public SectionDescriptor section(String... path) {
		return section(null, path);
	}

	public Optional<SectionDescriptor> sectionOr(Data parent, String... path) {
		
		var isRoot = parent == null || parent  instanceof INI;
		var fullPath = isRoot ? path : INI.merge(parent.path(), path);
		if(isRoot && fullPath.length == 0) {
			return Optional.of(sectionDescriptor(ini));
		}
		return ini.sectionOr(fullPath).map(this::sectionDescriptor);
	}

	public Optional<SectionDescriptor> sectionOr(String... path) {
		return sectionOr(null, path);
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

	private Optional<Discriminator> discriminatorForSection(Type type, Section sec) {
		return sec.getOr("discriminator").map(d -> type.discriminator(d));
	}

	private Optional<KeyDescriptor> getKeyOr(String... secpath) {
		return ini.sectionOr(secpath)
			.map(sec -> {
				var type = typeForSection(sec);
				if(type.equals(Type.SECTION)) {
					return null;
				}
				
				if(sec.contains(SCHEMA_ITEM_ARITY))
					System.err.println("[JINI] @Deprecated ARITY is replaced with MULTIPLICITY");
				
				return new KeyDescriptor(
					secpath[secpath.length - 1],
					sec.getOr(SCHEMA_ITEM_NAME).orElse(secpath[secpath.length - 1]),
					type,
					sec.getAllOr("value"),
					sec.getAllElse("default-value"),
					sec.getOr(SCHEMA_ITEM_DESCRIPTION),
					discriminatorForSection(type, sec),
					sec.getOr(SCHEMA_ITEM_MULTIPLICITY).map(Multiplicity::parse).or(() -> sec.getOr(SCHEMA_ITEM_ARITY).map(Multiplicity::parse)));
			});
	}

	private String indent(int indent) {
		return indent < 2 ? "" : String.format("%" + ( ( indent - 1 ) * 4 )+ "s", "");
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
		for(var i = 0 ; i < width ; i++) {
			b.append(ch);
		}
		return b.toString();
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

	private SectionDescriptor sectionDescriptor(Data section) {
		var type = typeForSection(section);
		if(!type.equals(Type.SECTION)) {
			throw new IllegalArgumentException("Section may only be of type " + Type.SECTION + ", but a " + type + " was provided");
		}

		var sections = section.sections();

		if(section.contains(SCHEMA_ITEM_ARITY))
			System.err.println("[JINI] @Deprecated ARITY is replaced with MULTIPLICITY");

		var allKeys = sections.values().stream().
				filter(s -> !typeForSection(s[0]).equals(Type.SECTION)).
				map(k -> {
					return keyOr(section, k[0].key()).
							orElseThrow(() -> new IllegalStateException("Huh? " + k[0].key() + " @ " + String.join(".", section.path())));
				}).
				collect(Collectors.toList());
		
		var keys = allKeys.stream().
				filter(k -> k.validate(section)).
				collect(Collectors.toList());
		
		return new SectionDescriptor(
				section instanceof Section ? ((Section)section).key() : "",
				section.get(SCHEMA_ITEM_NAME, section instanceof Section ? ((Section)section).key() : "Root"),
				section.getOr(SCHEMA_ITEM_DESCRIPTION),
				allKeys,
				keys,
				sections.values().stream().
						filter(s -> typeForSection(s[0]).equals(Type.SECTION)).
						map(k -> {
							return sectionDescriptor(k[0]);
						}).collect(Collectors.toList()),
				section.getOr(SCHEMA_ITEM_MULTIPLICITY).map(Multiplicity::parse).or(() -> section.getOr(SCHEMA_ITEM_ARITY).map(Multiplicity::parse)),
				section.path()
		);
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
}
