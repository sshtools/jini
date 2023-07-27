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
package com.sshtools.jini;

import com.sshtools.jini.INI.AbstractIO;
import com.sshtools.jini.INI.AbstractIOBuilder;
import com.sshtools.jini.INI.LinkedCaseInsensitiveMap;
import com.sshtools.jini.INI.Section;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

public final class INIReader extends AbstractIO {

	public enum DuplicateAction {
		ABORT, IGNORE, REPLACE, MERGE, APPEND
	}

	public static class Builder extends AbstractIOBuilder<Builder> {
		private boolean globalSection = true;
		private boolean caseInsensitiveKeys = true;
		private boolean caseInsensitiveSections = true;
        private boolean preserveOrder = true;
		private boolean nestedSections = true;
		private boolean parseExceptions = true;
		private boolean emptyValues = true;
		private boolean comments = true;
		private boolean inlineComments = true;
		private DuplicateAction duplicateKeysAction = DuplicateAction.REPLACE;
		private DuplicateAction duplicateSectionAction = DuplicateAction.REPLACE;
		private char[] quoteCharacters = new char[] { '"', '\'' };

		public final Builder withoutStringQuoting() {
			return withQuoteCharacters();
		}
		
		public final Builder withQuoteCharacters(char... quoteCharacters) {
			this.quoteCharacters = quoteCharacters;
			return this;
		}


		public final Builder withoutParseExceptions() {
			return withParseExceptions(false);
		}
		
		public final Builder withParseExceptions(boolean parseExceptions) {
			this.parseExceptions = parseExceptions;
			return this;
		}

		public final Builder withoutInlineComments() {
			return withInlineComments(false);
		}
		
		public final Builder withInlineComments(boolean inlineComments) {
			this.inlineComments = inlineComments;
			return this;
		}

		public final Builder withoutEmptyValues() {
			return withEmptyValues(false);
		}
		
		public final Builder withEmptyValues(boolean emptyValues) {
			this.emptyValues = emptyValues;
			return this;
		}

		public final Builder withoutComments() {
			return withComments(false);
		}
		
		public final Builder withComments(boolean comments) {
			this.comments = comments;
			return this;
		}

		public final Builder withoutGlobalProperties() {
			return withGlobalSection(false);
		}
		
		public final Builder withGlobalSection(boolean globalSection) {
			this.globalSection = globalSection;
			return this;
		}

		public final Builder withoutCaseInsensitiveKeys() {
			return withCaseInsensitiveKeys(false);
		}

		public final Builder withCaseInsensitiveKeys(boolean caseInsensitiveKeys) {
			this.caseInsensitiveKeys = caseInsensitiveKeys;
			return this;
		}

		public final Builder withoutCaseInsensitiveSections() {
			return withCaseInsensitiveSections(false);
		}

		public final Builder withCaseInsensitiveSections(boolean caseInsensitiveSections) {
			this.caseInsensitiveSections = caseInsensitiveSections;
			return this;
		}

		public final Builder withoutNestedSections() {
			return withNestedSections(false);
		}

		public final Builder withNestedSections(boolean nestedSections) {
			this.nestedSections = nestedSections;
			return this;
		}

		public final Builder withoutPreserveOrder() {
			return withPreserveOrder(false);
		}

		public final Builder withPreserveOrder(boolean preserveOrder) {
			this.preserveOrder = preserveOrder;
			return this;
		}

		public final Builder withDuplicateKeysAction(DuplicateAction duplicateKeysAction) {
			this.duplicateKeysAction = duplicateKeysAction;
			return this;
		}

		public final Builder withDuplicateSectionAction(DuplicateAction duplicateSectionAction) {
			this.duplicateSectionAction = duplicateSectionAction;
			return this;
		}
		
		public final INIReader build() {
			return new INIReader(this);
		}

	}
	
	private final boolean globalSection;
	private final boolean caseInsensitiveKeys;
	private final boolean caseInsensitiveSections;
	private final boolean nestedSections;
	private final boolean preserveOrder;
	private final boolean comments;
	private final DuplicateAction duplicateKeysAction;
	private final DuplicateAction duplicateSectionAction;
	private final boolean inlineComments;
	private final boolean emptyValues;
	private final boolean parseExceptions;
	private final char[] quoteCharacters;

	private INIReader(Builder builder) {
		super(builder);
		this.comments = builder.comments;
		this.globalSection = builder.globalSection;
		this.caseInsensitiveKeys = builder.caseInsensitiveKeys;
		this.caseInsensitiveSections = builder.caseInsensitiveSections;
		this.nestedSections = builder.nestedSections;
		this.preserveOrder = builder.preserveOrder;
		this.duplicateKeysAction = builder.duplicateKeysAction;
		this.duplicateSectionAction = builder.duplicateSectionAction;
		this.inlineComments = builder.inlineComments;
		this.emptyValues = builder.emptyValues;
		this.parseExceptions = builder.parseExceptions;
		this.quoteCharacters = builder.quoteCharacters;
	}

	public INI read(Path file) throws IOException, ParseException {
		try(var rdr = Files.newBufferedReader(file)) {
			return read(rdr);
		}
	}

	public INI read(String content) throws IOException, ParseException {
		try(var rdr = new StringReader(content)) {
			return read(rdr);
		}
	}
	
	public INI read(Reader reader) throws IOException, ParseException {
		String line;
		var lineReader = new BufferedReader(reader);
		var lineBuffer = new StringBuilder();
		var continuation = false;
		var offset = 0;
		var rootSections = createSectionMap(preserveOrder, caseInsensitiveSections);
		var globalProperties = createPropertyMap(preserveOrder, caseInsensitiveKeys);
		Section section = null;
		
		while ( ( line = lineReader.readLine()) != null) {
			offset += line.length();
			
			if(continuation) {
				lineBuffer.append(' ');
				line = line.stripLeading();
				continuation = false;
			}
			
			if(lineContinuations && isLineContinuation(line)) {
				line = line.substring(0, line.length() - 1);
				lineBuffer.append(line);
				continuation = true;
				continue;
			}

			lineBuffer.append(line);
			if(lineBuffer.length() == 0)
				continue;
			
			var fullLine = lineBuffer.toString();
			lineBuffer.setLength(0);
			var lineWithoutLeading = fullLine.stripLeading();
			
			if(comments && lineWithoutLeading.charAt(0) == commentCharacter) {
				continue;
			}
			
			var lineChars = fullLine.toCharArray();
			var escape = false;
			var buf = new StringBuilder();
			String key = null;
			char quoted = '\0';
			for(int i = 0 ; i < lineChars.length; i++) {
				var ch = lineChars[i];
				if(escape) {
					switch(ch) {
					case '\\':
					case '\'':
					case '"':
					case '#':
					case ':':
						buf.append(ch);
						break;
					case '0':
						buf.append((char)0);
						break;
					case 'a':
						buf.append((char)7);
						break;
					case 'b':
						buf.append((char)8);
						break;
					case 't':
						buf.append((char)11);
						break;
					case 'n':
						buf.append((char)10);
						break;
					case 'r':
						buf.append((char)13);
						break;
					// TODO unicode escape
					default:
						if((comments && ch == commentCharacter) || ch == valueSeparator)
							buf.append(ch);
						else {
							buf.append('\\');
							buf.append(ch);
						}
						break;
					}
					escape = false;
				}
				else {
					if(ch == '\\') {
						escape = true;
					}
					else {
						if(quoted != '\0') {
							if(quoted == ch) {
								quoted = '\0';
								continue;
							}
							else
								buf.append(ch);
						}
						else {
							if(isQuote(ch)) {
								quoted = ch;
							}
							else if(ch == commentCharacter && comments && inlineComments) {
								break;
							}
							else if(key == null) {
								if(ch == valueSeparator) {
									key = buf.toString().translateEscapes();
									buf.setLength(0);
								}
//								else if(ch == ' ' || ch == '\t') {
//									continue;
//								}
								else
									buf.append(ch); 
							}
							else {
								buf.append(ch);
							}
						}
					}
				}
			}
			if(key == null) {
				key = buf.toString();
				buf.setLength(0);
			}
			
			if(valueSeparatorWhitespace)
				key = key.stripTrailing();
			
			if(key.startsWith("[")) {
				var eidx = key.indexOf(']', 1);
				if(eidx == -1) {
				    if(parseExceptions) {
				        throw new ParseException("Incorrect syntax for section name, no closing ']'.", offset);
				    }
				}
				else {
					if(eidx != key.length() - 1) {
	                    if(parseExceptions) {
	                        throw new ParseException("Incorrect syntax for section name, trailing content after closing ']'.", offset);
	                    }
	                    else
	                        continue;
					}
					key = key.substring(1, eidx);
					
					String[] sectionPath;
					if(nestedSections) {
    					var tkns = new StringTokenizer(key, String.valueOf(sectionPathSeparator));
    					sectionPath = new String[tkns.countTokens()];
    					for(var i = 0 ; tkns.hasMoreTokens(); i++) {
    					    sectionPath[i] = tkns.nextToken();
    					}
					}
					else {
					    sectionPath = new String[] { key };
					}
				
					var parent = rootSections;
					
					for(int i = 0 ; i < sectionPath.length; i++) {
					    var sectionKey = sectionPath[i];
					    var last = i == sectionPath.length - 1;
					
    					var sectionsForKey = parent.get(sectionKey);
    					
    					if(last) {
        					var newSection = new Section(sectionKey, createPropertyMap(preserveOrder, caseInsensitiveKeys), new HashMap<>());
        					if(sectionsForKey == null) {
        						/* Doesn't exist, just add */
                                sectionsForKey = new Section[] {newSection};
        					    parent.put(sectionKey, sectionsForKey);
        					}
        					else {
        						switch(duplicateSectionAction) {
        						case ABORT:
        							throw new ParseException(MessageFormat.format("Duplicate section key {0}.", sectionKey), offset);
        						case REPLACE:
                                    sectionsForKey = new Section[] {newSection};
        						    parent.put(sectionKey, sectionsForKey);
        							break;
        						case IGNORE:
        						    continue;
        						case APPEND:
        							var newSections = new Section[sectionsForKey.length + 1];
        							System.arraycopy(sectionsForKey, 0, newSections, 0, sectionsForKey.length);
        							newSections[sectionsForKey.length] = newSection;
        							parent.put(sectionKey, newSections);
        							sectionsForKey = newSections;
        							break;
        						case MERGE:
        							newSection.merge(sectionsForKey[0].values());
        							parent.put(sectionKey, new Section[] { newSection });
                                    sectionsForKey = new Section[] {newSection};
        							break;
        						}
        					}
    					}
    					else {
    					    if(sectionsForKey == null) {
                                /* Doesn't exist, just add */
                                sectionsForKey = new Section[] {new Section(sectionKey, createPropertyMap(preserveOrder, caseInsensitiveKeys), new HashMap<>())};
                                parent.put(sectionKey, sectionsForKey);
                            }
    					}
    					parent = sectionsForKey[0].sections;
    					section = sectionsForKey[0];
					}
				}
			}
			else {
				var val = buf.toString();
				if(val.isEmpty() && !emptyValues) {
					if(parseExceptions)
						throw new ParseException("Empty values are not allowed.", offset);
				}
				else {
					if(trimmedValue) {
						val = val.trim();
					}
					else if(valueSeparatorWhitespace) {
						val = val.stripLeading();
					}
					
					Map<String, String[]> sectionProperties; 
					if(section == null) {
						if(globalSection) {
							sectionProperties = globalProperties;
						}
						else {
							if(parseExceptions)
								throw new ParseException("Global properties are not allowed, all properties must be in a [Section].", offset);
							else
								continue;
						}
					}
					else {
						sectionProperties = section.values;
					}
					
					var valuesForKey = sectionProperties.get(key);
					if(valuesForKey == null) {
						/* Doesn't exist, just add */
						sectionProperties.put(key, new String[] { val });
					}
					else {
						switch(duplicateKeysAction) {
						case ABORT:
							throw new ParseException(MessageFormat.format("Duplicate property key {0}.", key), offset);
						case MERGE:
						case REPLACE:
							sectionProperties.put(key, new String[] { val });
							break;
						case IGNORE:
                            continue;
						case APPEND:
							var newValues = new String[valuesForKey.length + 1];
							System.arraycopy(valuesForKey, 0, newValues, 0, valuesForKey.length);
							newValues[valuesForKey.length] = val;
							sectionProperties.put(key, newValues);
							break;
						}
					}
				}
			}
			
		}
		return new INI(globalProperties, rootSections);
	}
	
	private boolean isQuote(char ch) {
		for(var c : quoteCharacters)
			if(c == ch)
				return true;
		return false;
	}

	boolean isLineContinuation(String line) {
		var contCount = 0;
		for(int i = line.length() - 1; i>= 0 ; i--) {
			var ch = line.charAt(i);
			if(ch != '\\')
				break;
			contCount++;
		}
		return contCount % 2 == 1;
	}
	
	static Map<String, Section[]> createSectionMap(boolean preserveOrder, boolean caseInsensitiveSections) {
		if(preserveOrder) {
			if(caseInsensitiveSections) {
				return new LinkedCaseInsensitiveMap<>();
			}
			else {
				return new LinkedHashMap<>();
			}
		}
		else {
			if(caseInsensitiveSections) {
				return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			}
			else {
				return new HashMap<>();
			}
		}
	}
	
	static Map<String, String[]> createPropertyMap(boolean preserveOrder, boolean caseInsensitiveKeys) {
		if(preserveOrder) {
			if(caseInsensitiveKeys) {
				return new LinkedCaseInsensitiveMap<>();
			}
			else {
				return new LinkedHashMap<>();
			}
		}
		else {
			if(caseInsensitiveKeys) {
				return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			}
			else {
				return new HashMap<>();
			}
		}
	}
	
	

}
