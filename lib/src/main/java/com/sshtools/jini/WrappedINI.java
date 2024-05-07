package com.sshtools.jini;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.sshtools.jini.INI.Section;

public class WrappedINI {
	
	public abstract static class AbstractWrapper<DEL extends Data, USEROBJ, SECWRAP extends Section> implements Data {
		protected final DEL delegate;
		protected final AbstractWrapper<? extends Data, USEROBJ, SECWRAP> parent;
		protected final USEROBJ userObject;

		protected final Map<Section, SECWRAP> map = new HashMap<>();

		public AbstractWrapper(DEL delegate, AbstractWrapper<? extends Data, USEROBJ, SECWRAP> parent, USEROBJ userObject) {
			this.delegate = delegate;
			this.parent = parent;
			this.userObject = userObject;
		}

		@Override
        public boolean empty() {
        	return delegate.empty();
        }
        
		@Override
		public Optional<String[]> getAllOr(String key) {
			return delegate.getAllOr(key);
		}

		@Override
		public <E extends Enum<E>> void putAllEnum(String key, E... values) {
			delegate.putAllEnum(key, values);
		}

		@Override
		public void putAll(String key, String... values) {
			delegate.putAll(key, values);
		}

		@Override
		public void putAll(String key, int... values) {
			delegate.putAll(key, values);
		}

		@Override
		public void putAll(String key, short... values) {
			delegate.putAll(key, values);
		}

		@Override
		public void putAll(String key, long... values) {
			delegate.putAll(key, values);

		}

		@Override
		public void putAll(String key, float... values) {
			delegate.putAll(key, values);
		}

		@Override
		public void putAll(String key, double... values) {
			delegate.putAll(key, values);

		}

		@Override
		public void putAll(String key, boolean... values) {
			delegate.putAll(key, values);
		}

		@Override
		public boolean remove(String key) {
			return delegate.remove(key);
		}

		@Override
		public Map<String, String[]> rawValues() {
			return delegate.rawValues();
		}

		@Override
		public Map<String, String[]> values() {
			return rawValues();
		}

		@Override
		public final int size() {
			return delegate.size();
		}

		@Override
		public Data readOnly() {
			return delegate.readOnly();
		}

		@Override
		public Set<String> keys() {
			return delegate.keys();
		}

		@Override
		public final INI document() {
			/* TODO make INI an interface too so we can wrap that */
			return delegate.document();
		}

		@Override
		public boolean containsSection(String... key) {
			return delegate.containsSection(key);
		}

		@Override
		public boolean contains(String key) {
			return delegate.contains(key);
		}

        @Override
        public String asString() {
            return new INIWriter.Builder().build().write(this);
        }

		@Override
		public Map<String, Section[]> sections() {
			return delegate.sections().entrySet().stream()
					.map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), wrapSections(e.getValue())))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}

		protected Section[] wrapSections(Section[] section) {
			var s = new Section[section.length];
			for (int i = 0; i < section.length; i++) {
				var ms = map.get(section[i]);
				s[i] = ms == null ? wrapSection(section[i]) : ms;
			}
			return s;
		}

		@Override
		public Section create(String... path) {
			return wrapSection(delegate.create(path));
		}

		protected SECWRAP wrapSection(Section delSec) {
			var wrpr = createWrappedSection(delSec);
			map.put(delSec, wrpr);
			return wrpr;
		}
		
		protected abstract SECWRAP createWrappedSection(Section delSec);

		@Override
		public final Optional<Section> parentOr() {
			return Optional.ofNullable(parent instanceof Section ? (Section) parent : null);
		}

		@Override
		public final Handle onValueUpdate(ValueUpdate listener) {
			return delegate.onValueUpdate(listener);
		}

		@Override
		public final Handle onSectionUpdate(SectionUpdate listener) {
			return delegate.onSectionUpdate(listener);
		}

		@Override
		public final void clear() {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public Optional<Section[]> allSectionsOr(String... path) {
			return delegate.allSectionsOr(path).map(this::wrapSections);
		}

		public final void removeSection(Section delegate) {
			map.remove(delegate);
		}
	}
}
