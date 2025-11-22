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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.TreeMap;

import com.sshtools.jini.INI.AbstractIO;
import com.sshtools.jini.INI.AbstractIOBuilder;
import com.sshtools.jini.INI.DefaultINI;
import com.sshtools.jini.INI.EscapeMode;
import com.sshtools.jini.INI.LinkedCaseInsensitiveMap;
import com.sshtools.jini.INI.MissingVariableMode;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INI.SectionImpl;
import com.sshtools.jini.Interpolation.Interpolator;

/**
 * An {@INIReader} can read text (from files, streams or strings) in the INI
 * format to produce an {@link INI} object instance.
 * <p>
 * This class should not be directly created, instead use
 * {@link INIReader.Builder} and configure it accordingly before calling
 * {@link INIReader.Builder#build()}.
 * 
 */
public final class INIReader extends AbstractIO {

    /**
     * Used to configure how to behave when duplicate value or section keys are
     * encountered.
     * 
     * @see Builder#withDuplicateKeysAction(DuplicateAction)
     * @see Builder#withDuplicateSectionAction(DuplicateAction)
     */
    public enum DuplicateAction {
        /**
         * When a duplicate value key or section key is encountered, an
         * {@link INIParseException} will be thrown. With this mode no keys can have
         * multiple values.
         */
        ABORT,
        /**
         * When a duplicate value key or section key in encountered, the duplicates
         * value will be entirely ignore. With this mode no keys can have multiple
         * values.
         */
        IGNORE,
        /**
         * When a duplicate value key or section key is encountered, the previous value
         * is entirely replaced. With this mode no keys can have multiple values.
         */
        REPLACE,
        /**
         * When a duplicate value key is encountered, the previous values are merged
         * with the new values.
         */
        MERGE,
        /**
         * When a duplicate value key is encountered, the new values are appended to the
         * existing values
         */
        APPEND
    }

    /**
     * Used to configure how to behave when parsing or writing multiple value or
     * section keys.
     * 
     * 
     */
    public enum MultiValueMode {
        /**
         * Multiple values are expressed as value keys repeating in the content.
         */
        REPEATED_KEY,
        /**
         * Multiple values are not allowed at all, a value will always be treated
         * as a single value.
         */
        OFF,
        /**
         * Multiple values are expressed as a single key, with it's string value being
         * made up of (comma by default) separated values.
         */
        SEPARATED
    }

    /**
     * Creates {@link INIReader} instances. Builders may be re-used, once {@link #build()}
     * is used, any changes to the builder will not affect the created instance.
     */
    public static class Builder extends AbstractIOBuilder<Builder> {
        private boolean globalSection = true;
        private boolean caseSensitiveKeys = false;
        private boolean caseSensitiveSections = false;
        private boolean preserveOrder = true;
        private boolean nestedSections = true;
        private boolean parseExceptions = true;
        private boolean comments = true;
        private boolean inlineComments = true;
        private DuplicateAction duplicateKeysAction = DuplicateAction.REPLACE;
        private DuplicateAction duplicateSectionAction = DuplicateAction.APPEND;
        private char[] quoteCharacters = new char[] { '"', '\'' };
        private Optional<Interpolator> interpolator = Optional.empty();
        private Optional<String> variablePattern = Optional.empty();
        private MissingVariableMode missingVariableMode = MissingVariableMode.ERROR;
        
        /**
         * Configure how to react when a variable is encountered that does not exist.
         * 
         * @param missingVariableMode missing variable mode
         * @return this for chaining
         */
        public final Builder withMissingVariableMode(MissingVariableMode missingVariableMode) {
        	this.missingVariableMode = missingVariableMode;
        	return this;
        }
        
        /**
         * Configure the regular expression pattern to use to extract interpolatable variables.
         * 
         * @param variablePattern variable pattern
         * @return this for chaining
         */
        public final Builder withVariablePattern(String variablePattern) {
        	this.variablePattern = Optional.of(variablePattern);
        	return this;
        }
        
        /**
         * Configure an "Interpolator", that processes string values before they
         * are returned, replacing string variable patterns. See {@link Interpolation}
         * for some default interpolators.
         * 
         * @param interpolator interpolator
         * @return this for chaining
         */
        public final Builder withInterpolator(Interpolator interpolator) {
        	this.interpolator = Optional.of(interpolator);
        	return this;
        }

        /**
         * Configure either the reader to not expect any type of string quotes.
         * 
         * @return this for chaining
         */
        public final Builder withoutStringQuoting() {
            return withQuoteCharacters();
        }

        /**
         * Configure the reader to expect quoted strings using the provided quote
         * characters.
         * 
         * @return this for chaining
         */
        public final Builder withQuoteCharacters(char... quoteCharacters) {
            this.quoteCharacters = quoteCharacters;
            return this;
        }

        /**
         * Do not throw {@link INIParseException} when invalid syntax is found when parsing
         * an INI document. In general the line will just be ignored and treated as if
         * it were a comment.
         * 
         * @return this for chaining
         */
        public final Builder withoutParseExceptions() {
            return withParseExceptions(false);
        }

        /**
         * Configure whether to throw {@link INIParseException} when invalid syntax is
         * found when parsing an INI document. In general the line will just be ignored
         * and treated as if it were a comment.
         * 
         * @param parseExceptions throw parse exceptions on invalid syntax
         * @return this for chaining
         */
        public final Builder withParseExceptions(boolean parseExceptions) {
            this.parseExceptions = parseExceptions;
            return this;
        }

        /**
         * Do not allow inline comments. Comments will be treated as part of the value.
         * 
         * @return this for chaining
         */
        public final Builder withoutInlineComments() {
            return withInlineComments(false);
        }

        /**
         * Configure whether to allow inline comments. When <code>false</code>, comments
         * will be treated as part of the value.
         * 
         * @param inlineComments allow inline comments
         * @return this for chaining
         */
        public final Builder withInlineComments(boolean inlineComments) {
            this.inlineComments = inlineComments;
            return this;
        }

        /**
         * Configure to not allow comments. Anything that starts with
         * {@link #withCommentCharacter(char)} will be treated as a valid key.
         * 
         * @return this for chaining
         */
        public final Builder withoutComments() {
            return withComments(false);
        }

        /**
         * Configure whether to allow comments. When <code>false</code>, anything that
         * starts with {@link #withCommentCharacter(char)} will be treated as a valid
         * key. When <code>true</code> anything that starts with
         * {@link #withCommentCharacter(char)} will be ignored.
         * 
         * @param comments allow comments
         * @return this for chaining
         */
        public final Builder withComments(boolean comments) {
            this.comments = comments;
            return this;
        }

        /**
         * Configure to not allow any global properties, i.e. all values must be in a
         * section.
         * 
         * @return this for chaining
         */
        public final Builder withoutGlobalProperties() {
            return withGlobalSection(false);
        }

        /**
         * Configure whether to allow any global properties, i.e. all values must be in
         * a section.
         * 
         * @param globalSection allow properties global in section
         * @return this for chaining
         */
        public final Builder withGlobalSection(boolean globalSection) {
            this.globalSection = globalSection;
            return this;
        }

        /**
         * Value keys are by default case insensitive. Use this method to make value
         * keys case sensitive.
         * 
         * @return this for chaining
         */
        public final Builder withCaseSensitiveKeys() {
            return withCaseSensitiveKeys(true);
        }

        /**
         * Configure whether value keys are case sensitive. Value keys are by default
         * case insensitive.
         * 
         * @param caseSensitiveKeys case sensitive keys
         * @return this for chaining
         */
        public final Builder withCaseSensitiveKeys(boolean caseSensitiveKeys) {
            this.caseSensitiveKeys = caseSensitiveKeys;
            return this;
        }

        /**
         * Sections keys are by default case insensitive. Use this method to make
         * section keys case sensitive.
         * 
         * @return this for chaining
         */
        public final Builder withCaseSensitiveSections() {
            return withCaseSensitiveSections(true);
        }

        /**
         * Configure whether section keys are case sensitive. Sections keys are by
         * default case insensitive.
         * 
         * @param caseSensitiveSections case sensitive sections
         * @return this for chaining
         */
        public final Builder withCaseSensitiveSections(boolean caseSensitiveSections) {
            this.caseSensitiveSections = caseSensitiveSections;
            return this;
        }

        /**
         * Configure to not allow nested sections. Any section key encountered that has
         * {@link #withSectionPathSeparator(char)} (by default <code>.</code>), will be
         * treated as literally that key.
         * 
         * @return this for chaining
         */
        public final Builder withoutNestedSections() {
            return withNestedSections(false);
        }

        /**
         * Configure whether to allow nested sections. When <code>false</code>, any
         * section key encountered that has {@link #withSectionPathSeparator(char)} (by
         * default <code>.</code>), will be treated as literally that key. When
         * <code>true</code> the key will be split up based on the separator and that is
         * used as the path to section.
         * 
         * @paran nestedSections nested sections
         * @return this for chaining
         */
        public final Builder withNestedSections(boolean nestedSections) {
            this.nestedSections = nestedSections;
            return this;
        }

        /**
         * Configure to not preserve the order of sections and values as they are
         * inserted. Upon writing, keys and sections will be in an indeterminate order.
         * 
         * @return this for chaining
         */
        public final Builder withoutPreserveOrder() {
            return withPreserveOrder(false);
        }

        /**
         * Configure whether to preserve the order of sections and values as they are
         * inserted. When <code>false</code>, upon writing keys and sections will be in
         * an indeterminate order. When <code>true</code>, upon writing keys and
         * sections will be ordered according to the same rules as
         * {@link String#compareTo(String)}, i.e. alphabetical.
         * 
         * @param preserveOrder preserve order
         * @return this for chaining
         */
        public final Builder withPreserveOrder(boolean preserveOrder) {
            this.preserveOrder = preserveOrder;
            return this;
        }

        /**
         * Configure how to behave when duplicate value keys are encountered.
         * 
         * @param duplicateKeysAction action
         * @return this for chaining
         */
        public final Builder withDuplicateKeysAction(DuplicateAction duplicateKeysAction) {
            this.duplicateKeysAction = duplicateKeysAction;
            return this;
        }

        /**
         * Configure how to behave when duplicate section keys are encountered.
         * 
         * @param duplicateKeysAction action
         * @return this for chaining
         */
        public final Builder withDuplicateSectionAction(DuplicateAction duplicateSectionAction) {
            this.duplicateSectionAction = duplicateSectionAction;
            return this;
        }

        /**
         * Build a new {@link INIReader}.
         * 
         * @return reader
         */
        public final INIReader build() {
            return new INIReader(this);
        }

    }

    private final boolean globalSection;
    private final boolean caseSensitiveKeys;
    private final boolean caseSensitiveSections;
    private final boolean nestedSections;
    private final boolean preserveOrder;
    private final boolean comments;
    private final DuplicateAction duplicateKeysAction;
    private final DuplicateAction duplicateSectionAction;
    private final boolean inlineComments;
    private final boolean parseExceptions;
    private final char[] quoteCharacters;
	private final Optional<Interpolator> interpolator;
	private final Optional<String> variablePattern;
	private final MissingVariableMode missingVariableMode;

    private INIReader(Builder builder) {
        super(builder);
        this.interpolator = builder.interpolator;
        this.comments = builder.comments;
        this.globalSection = builder.globalSection;
        this.caseSensitiveKeys = builder.caseSensitiveKeys;
        this.caseSensitiveSections = builder.caseSensitiveSections;
        this.nestedSections = builder.nestedSections;
        this.preserveOrder = builder.preserveOrder;
        this.duplicateKeysAction = builder.duplicateKeysAction;
        this.duplicateSectionAction = builder.duplicateSectionAction;
        this.inlineComments = builder.inlineComments;
        this.parseExceptions = builder.parseExceptions;
        this.quoteCharacters = builder.quoteCharacters;
        this.variablePattern = builder.variablePattern;
        this.missingVariableMode = builder.missingVariableMode;
    }

    /**
     * Create an {@link INI} document instance by reading a file with content in INI
     * format.
     * 
     * @param file file containing INI
     * @return document
     * @throws IOException    on I/O error
     * @throws INIParseException on parsing error
     */
    public INI read(Path file) throws IOException, INIParseException {
        try (var rdr = Files.newBufferedReader(file)) {
            return read(rdr);
        }
    }

    /**
     * Create an {@link INI} document instance from a string in INI format.
     * 
     * @param content string of INI content
     * @return document
     * @throws IOException    on I/O error
     * @throws INIParseException on parsing error
     */
    public INI read(String content) throws IOException, INIParseException {
        try (var rdr = new StringReader(content)) {
            return read(rdr);
        }
    }
    
    /**
     * Create an {@link INI} document instance by reading a stream with content in
     * INI format. The current default character set encoding will be used.
     * 
     * @param input stream of INI content
     * @return document
     * @throws IOException    on I/O error
     * @throws INIParseException on parsing error
     */
    public INI read(InputStream input) throws IOException, INIParseException {
    	return read(new InputStreamReader(input));
    }
    
    /**
     * Create an {@link INI} document instance by reading a stream with content in
     * INI format. 
     * 
     * @param input stream of INI content
     * @param charset character set
     * @return document
     * @throws IOException    on I/O error
     * @throws INIParseException on parsing error
     */
    public INI read(InputStream input, String charset) throws IOException, INIParseException {
    	return read(new InputStreamReader(input, charset));
    }

    /**
     * Create an {@link INI} document instance by reading a stream with content in
     * INI format.
     * 
     * @param reader reader providing stream of INI content
     * @return document
     * @throws IOException    on I/O error
     * @throws INIParseException on parsing error
     */
    public INI read(Reader reader) throws IOException, INIParseException {
        String line;
        var lineReader = new BufferedReader(reader);
        var lineBuffer = new StringBuilder();
        var continuation = false;
        var offset = 0;
        var rootSections = createSectionMap(preserveOrder, caseSensitiveSections);
        var globalProperties = createPropertyMap(preserveOrder, caseSensitiveKeys);
        SectionImpl section = null;
        var lineNo = 0;
        var lastAppendedLine = -1;
        var ini = new DefaultINI(emptyValues, preserveOrder, caseSensitiveKeys, caseSensitiveSections, globalProperties, rootSections, interpolator, variablePattern, missingVariableMode);
        var matched = new StringBuilder();
        var multiline = false;
        String readAhead = null;
        String key = null;
        var buf = new StringBuilder();
        var nextComments = new ArrayList<String>();
        var pastFirstBlank = false;
        
        while ((line = lineReader.readLine()) != null) {
        	if(!multiline) {
        		buf.setLength(0);
        		key = null;
        	}
        	
            offset += line.length();

            if (continuation) {
                lineBuffer.append(' ');
                line = line.stripLeading();
                continuation = false;
            }

            if (!multiline && lineContinuations && isLineContinuation(line)) {
                line = line.substring(0, line.length() - 1);
                lineBuffer.append(line);
                continuation = true;
                continue;
            }

            lineBuffer.append(line);
            if (lineBuffer.length() == 0) {
            	pastFirstBlank = checkPastFirstBlank(section, ini, nextComments, pastFirstBlank);
                continue;
            }

            var fullLine = lineBuffer.toString();
            lineBuffer.setLength(0);
            var lineWithoutLeading = fullLine.stripLeading();

            if (!multiline && (lineWithoutLeading.length() == 0)) {
            	pastFirstBlank = checkPastFirstBlank(section, ini, nextComments, pastFirstBlank);
            	continue;
            }
            
			if (comments &&  lineWithoutLeading.length() > 0 && lineWithoutLeading.charAt(0) == commentCharacter) {
            	var ln = fullLine.trim();
            	while(ln.startsWith(String.valueOf(commentCharacter))) {
            		ln = ln.substring(1);
            	}
            	nextComments.add(ln.trim());
            	continue;
            }

            var lineChars = lineWithoutLeading.toCharArray();
            var escape = false;
            char quoted = '\0';
            var column = 0; 
            var skipToNext = false;
            var bufSizeAtStartOfLine = buf.length();
            var inlineComment = new StringBuilder();
            var assigned = false;
            
            while(column < lineChars.length) {
	            for (; column < lineChars.length; column++) {
	            	char ch;
	            	if(readAhead != null) {
	            		column--;
	            		ch = readAhead.charAt(0);
	            		readAhead = readAhead.substring(1);
	            	}
	            	else {
	            		ch = lineChars[column];
	            	}
	                if (escape) {
	                    switch (ch) {
	                    case '\\':
	                    case '\'':
	                    case '"':
	                    case '#':
	                    case ':':
	                        buf.append(ch);
	                        break;
	                    case '0':
	                        buf.append((char) 0);
	                        break;
	                    case 'a':
	                        buf.append((char) 7);
	                        break;
	                    case 'b':
	                        buf.append((char) 8);
	                        break;
	                    case 't':
	                        buf.append((char) 11);
	                        break;
	                    case 'n':
	                        buf.append((char) 10);
	                        break;
	                    case 'r':
	                        buf.append((char) 13);
	                        break;
	                    // TODO unicode escape
	                    default:
	                        if ((comments && ch == commentCharacter) || ch == valueSeparator)
	                            buf.append(ch);
	                        else {
	                            buf.append('\\');
	                            buf.append(ch);
	                        }
	                        break;
	                    }
	                    escape = false;
	                } else {
	                	try {
		                    if (ch == '\\' && (escapeMode == EscapeMode.ALWAYS || (escapeMode == EscapeMode.QUOTED && quoted != '\0'))) {
		                        escape = true;
		                    } else {
		                        if (quoted != '\0') {
		                            if (quoted == ch) {
		                                quoted = '\0';
		                                continue;
		                            } else
		                                buf.append(ch);
		                        } else {
		                        	if(multilineStrings && readAhead == null) {
		                        	   if(isQuote(ch)) {
		                        		   matched.append(ch);
		                        		   if(matched.length() == INI.MULTI_QUOTE_MATCHES) {
		                        			   /* Have multi-line string pattern. Ignore the remainder of the line */
		                        			   matched.setLength(0);
		                        			   
		                        			   if(multiline) {
		                        				   /* End multi-line doesn't end the row, as it may being used for a multi-line key */
		                        				   multiline = false;
		                        				   continue;
		                        			   }
		                        			   else {
		                        				   /* Starting multi-line always starts on next row */
			                        			   multiline = true;
			                        			   column = line.length();
			                        			   skipToNext = true;
		                        			   }
		                        			   break;
		                        		   }
		                        		   else {
		                        			   /* Don't yet have full multi-line, read the next 
		                        			    * character
		                        			    */
		                        			   continue;
		                        		   }
		                        	   }
		                        	   else {
		                        		   if(matched.length() == 0) {
		                        			   /* Nothing matched, continue as normal */
		                        		   }
		                        		   else {
		                        			   /* Matched < full pattern, feed back the characters
		                        			    * that did match
		                        			    */
			                        		   matched.append(ch);
		                        			   readAhead = matched.toString();
		                        			   matched.setLength(0);
		                        			   continue;
		                        		   }
		                        	   }
		                        	}
		                        		
		                        	if(multiline) {
		                        		if(buf.length() == bufSizeAtStartOfLine && buf.length() > 0) {
		                        			buf.append(lineSeparator);
		                        		}
		                                buf.append(ch);
		                        	}
		                        	else if (isQuote(ch)) {
		                                quoted = ch;
		                            } else if (ch == commentCharacter && comments && inlineComments) {
		                            	inlineComment.setLength(0);
		                            	column++;
		                            	while(column < lineChars.length) {
		                            		inlineComment.append(lineChars[column++]);
		                            	}
		                                break;
		                            } else if (key == null) {
		                                if (ch == valueSeparator) {
		                                    key = unescapeJavaString(buf.toString());
		                                    buf.setLength(0);
		                                    assigned = true;
		                                }
		//								else if(ch == ' ' || ch == '\t') {
		//									continue;
		//								}
		                                else
		                                    buf.append(ch);
		                            } else {
		                            	if(ch == multiValueSeparator && multiValueMode == MultiValueMode.SEPARATED) {
		                            		column++;
		                            		break;
		                            	}
		                                buf.append(ch);
		                            }
		                        }
		                    }
		                    
	                	} finally {
                 		   if("".equals(readAhead)) {
                 			   /* Finished reading from read ahead */
                 			   readAhead = null;
                 		   }
	                	}
	                }
	            }
	            
	            if(skipToNext)
	            	break;
	            
	            if (key == null) {
	                key = buf.toString();
	                buf.setLength(0);
	            }
	
	            if (valueSeparatorWhitespace)
	                key = key.stripTrailing();
	
	            if (key.startsWith("[")) {
	                var eidx = key.indexOf(']', 1);
	                if (eidx == -1) {
	                    if (parseExceptions) {
	                        throw new INIParseException(
                                    "Incorrect syntax for section name, no closing ']'.", offset, lineNo);
	                    }
	                } else {
	                    if (eidx != key.length() - 1) {
	                        if (parseExceptions) {
	                            throw new INIParseException(
	                                    "Incorrect syntax for section name, trailing content after closing ']'.",
                                        offset, lineNo);
	                        } else
	                            continue;
	                    }
	                    key = key.substring(1, eidx);
	
	                    String[] sectionPath;
	                    if (nestedSections) {
	                        sectionPath = tokenize(key, sectionPathSeparator);
	                    } else {
	                        sectionPath = new String[] { key };
	                    }
	
	                    var parent = rootSections;
	                    Data lastSection = null;
	
	                    for (int sectionIdx = 0; sectionIdx < sectionPath.length; sectionIdx++) {
	                        var sectionKey = sectionPath[sectionIdx];
	                        var last = sectionIdx == sectionPath.length - 1;
	
	                        var sectionsForKey = parent.get(sectionKey);
	                        var parentSection = lastSection == null ? ini : lastSection;
	
	                        if (last) {
								var newSection = new SectionImpl(emptyValues, preserveOrder, caseSensitiveKeys, caseSensitiveSections,
	                                    parentSection, sectionKey, interpolator, variablePattern, missingVariableMode);
								setCommentsForSection(nextComments, newSection, inlineComment);
	                            if (sectionsForKey == null) {
	                                /* Doesn't exist, just add */
	                                sectionsForKey = new Section[] { newSection };
	                                parent.put(sectionKey, sectionsForKey);
	                            } else {
	                                switch (duplicateSectionAction) {
	                                case ABORT:
	                                    throw new INIParseException(
	                                            MessageFormat.format("Duplicate section key {0}.", sectionKey),
                                                offset, lineNo);
	                                case REPLACE:
	                                    sectionsForKey = new Section[] { newSection };
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
	                                    sectionsForKey = new Section[] { newSection };
	                                    break;
	                                }
	                            }
	                        } else {
	                            if (sectionsForKey == null) {
	                            	
	                                /* Doesn't exist, just add */
	                            	var newSection = new SectionImpl(emptyValues, preserveOrder, caseSensitiveKeys,
	                                        caseSensitiveSections, parentSection, sectionKey, interpolator, variablePattern, missingVariableMode);
									setCommentsForSection(nextComments, newSection, inlineComment);
	                                sectionsForKey = new Section[] { newSection };
	                                parent.put(sectionKey, sectionsForKey);
	                            }
	                        }
	                        parent = sectionsForKey[sectionsForKey.length - 1].sections();
	                        lastSection = section = (SectionImpl)sectionsForKey[sectionsForKey.length - 1];
	                    }
	                }
	            } else if(!multiline) {

					setCommentsForKey(nextComments, section == null ? (globalSection ? ini : null) : section, key, inlineComment);
					
	                var val = buf.toString();
	                buf.setLength(0);
	                if (val.isEmpty() && !emptyValues) {
	                    if (parseExceptions)
	                        throw new INIParseException("Empty values are not allowed.", offset, lineNo);
	                } else {
	                    if (trimmedValue) {
	                        val = val.trim();
	                    } else if (valueSeparatorWhitespace) {
	                        val = val.stripLeading();
	                    }
	
	                    Map<String, String[]> sectionProperties;
	                    if (section == null) {
	                        if (globalSection) {
	                            sectionProperties = globalProperties;
	                        } else {
	                            if (parseExceptions)
	                                throw new INIParseException(
	                                        "Global properties are not allowed, all properties must be in a [Section].",
	                                        offset, lineNo);
	                            else
	                                continue;
	                        }
	                    } else {
	                        sectionProperties = section.values;
	                    }
	
	                    var oldValues = sectionProperties.get(key);
						if (oldValues != null 
								&& multiValueMode == MultiValueMode.SEPARATED
								&& duplicateKeysAction != DuplicateAction.ABORT
								&& duplicateKeysAction != DuplicateAction.IGNORE
                                && duplicateKeysAction != DuplicateAction.REPLACE) {
                            appendValue(key, sectionProperties, val, oldValues);
                            lastAppendedLine = lineNo;
	                    }
	                    else if (oldValues == null || 
	                    		 multiValueMode == MultiValueMode.OFF || 
	                    	   ( duplicateKeysAction == DuplicateAction.REPLACE && 
                    	   				oldValues != null && 
                    	   				lastAppendedLine != lineNo 
	                    	   	) ) {
	                        /* Doesn't exist, just add */
	                    	if(val.equals("") && !assigned)
	                    		sectionProperties.put(key, new String[0]);
	                    	else
	                    		sectionProperties.put(key, new String[] { val });
	                        lastAppendedLine = lineNo;
	                    } else {
	                        switch (duplicateKeysAction) {
	                        case ABORT:
	                            throw new INIParseException(
                                        MessageFormat.format("Duplicate property key {0}.", key), offset, lineNo);
	                        case IGNORE:
		                        lastAppendedLine = -1;
	                            continue;
	                        default:
	                            appendValue(key, sectionProperties, val, oldValues);
	                            lastAppendedLine = lineNo;
	                            break;
	                        }
	                    }
	                }
	            }
            }

            lineNo++;
        }
        return ini;
    }

	protected void appendValue(String key, Map<String, String[]> sectionProperties, String value,
			String[] valuesForKey) {
		var newValues = new String[valuesForKey.length + 1];
		System.arraycopy(valuesForKey, 0, newValues, 0, valuesForKey.length);
		newValues[valuesForKey.length] = value;
		sectionProperties.put(key, newValues);
	}

    private boolean isQuote(char ch) {
        for (var c : quoteCharacters)
            if (c == ch)
                return true;
        return false;
    }
    
    /**
     * Unescapes a string that contains standard Java escape sequences.
     * <ul>
     * <li><strong>&#92;b &#92;f &#92;n &#92;r &#92;t &#92;" &#92;'</strong> :
     * BS, FF, NL, CR, TAB, double and single quote.</li>
     * <li><strong>&#92;X &#92;XX &#92;XXX</strong> : Octal character
     * specification (0 - 377, 0x00 - 0xFF).</li>
     * <li><strong>&#92;uXXXX</strong> : Hexadecimal based Unicode character.</li>
     * </ul>
     * 
     * @param st
     *            A string optionally containing standard java escape sequences.
     * @return The translated string.
     */
    public String unescapeJavaString(String st) {

        StringBuilder sb = new StringBuilder(st.length());

        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st
                        .charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                            && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
                                && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                case '\\':
                    ch = '\\';
                    break;
                case 'b':
                    ch = '\b';
                    break;
                case 'f':
                    ch = '\f';
                    break;
                case 'n':
                    ch = '\n';
                    break;
                case 'r':
                    ch = '\r';
                    break;
                case 't':
                    ch = '\t';
                    break;
                case '\"':
                    ch = '\"';
                    break;
                case '\'':
                    ch = '\'';
                    break;
                // Hex Unicode: u????
                case 'u':
                    if (i >= st.length() - 5) {
                        ch = 'u';
                        break;
                    }
                    int code = Integer.parseInt(
                            "" + st.charAt(i + 2) + st.charAt(i + 3)
                                    + st.charAt(i + 4) + st.charAt(i + 5), 16);
                    sb.append(Character.toChars(code));
                    i += 5;
                    continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

	private boolean checkPastFirstBlank(SectionImpl section, DefaultINI ini, ArrayList<String> nextComments,
			boolean pastFirstBlank) {
		if(!pastFirstBlank) {
			if(nextComments.size() > 0) {
		        if (section == null && globalSection) {
		        	setCommentsForSection(nextComments, ini, null);;
		        } 
			}
			pastFirstBlank = true;	
		}
		return pastFirstBlank;
	}

	private void setCommentsForSection(List<String> nextComments, Data newSection, StringBuilder inlineComments) {
		if(newSection == null || ( nextComments.isEmpty() && (inlineComments == null || inlineComments.length() == 0)))
			return;
		
		newSection.setComments(getAllComments(nextComments, inlineComments).toArray(new String[0]));
	}

	private ArrayList<String> getAllComments(List<String> nextComments, StringBuilder inlineComments) {
		var allComments = new ArrayList<String>(nextComments);
		if(inlineComments != null && inlineComments.length() > 0) {
			var trimmed = inlineComments.toString().trim();
			if(trimmed.length() > 0) {
				allComments.add(trimmed);
			}
			inlineComments.setLength(0);
		}
		nextComments.clear();
		return allComments;
	}


	private void setCommentsForKey(List<String> nextComments, Data section, String key, StringBuilder inlineComments) {
		if(section == null || ( nextComments.isEmpty() && (inlineComments == null || inlineComments.length() == 0)))
			return;
		
		section.setKeyComments(key, getAllComments(nextComments, inlineComments).toArray(new String[0]));
	}

    boolean isLineContinuation(String line) {
        var contCount = 0;
        for (int i = line.length() - 1; i >= 0; i--) {
            var ch = line.charAt(i);
            if (ch != '\\')
                break;
            contCount++;
        }
        return contCount % 2 == 1;
    }

    static String[] tokenize(String val, char sep) {
        var tkns = new StringTokenizer(val, String.valueOf(sep));
        var arr = new String[tkns.countTokens()];
        for (var i = 0; tkns.hasMoreTokens(); i++) {
            arr[i] = tkns.nextToken();
        }
        return arr;
    }

    static String[] trim(String[] arr) {
        for (int i = 0; i < arr.length; i++)
            arr[i] = arr[i].trim();
        return arr;
    }

    static Map<String, Section[]> createSectionMap(boolean preserveOrder, boolean caseSensitiveSections) {
        if (preserveOrder) {
            if (caseSensitiveSections) {
                return new LinkedHashMap<>();
            } else {
                return new LinkedCaseInsensitiveMap<>();
            }
        } else {
            if (caseSensitiveSections) {
                return new HashMap<>();
            } else {
                return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            }
        }
    }

    static Map<String, String[]> createPropertyMap(boolean preserveOrder, boolean caseSensitiveKeys) {
        if (preserveOrder) {
            if (caseSensitiveKeys) {
                return new LinkedHashMap<>();
            } else {
                return new LinkedCaseInsensitiveMap<>();
            }
        } else {
            if (caseSensitiveKeys) {
                return new HashMap<>();
            } else {
                return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            }
        }
    }

}
