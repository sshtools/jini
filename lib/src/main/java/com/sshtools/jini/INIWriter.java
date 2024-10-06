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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sshtools.jini.INI.AbstractIO;
import com.sshtools.jini.INI.AbstractIOBuilder;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader.MultiValueMode;

/**
 * Writes {@link INI} documents in the INI format.
 * <p>
 * This class should not be directly created, instead use {@link INIWriter.Builder}
 * and configure it accordingly before calling {@link INIWriter.Builder#build()}.
 */
public class INIWriter extends AbstractIO {

    /**
     * Use to configure when written string values are wrapped in quotes.
     */
    public enum StringQuoteMode {
        /**
         * Strings will never be quoted with the configured quote character.
         */
        NEVER,
        /**
         * Strings will always be quoted with the configured quote character.
         */
        ALWAYS,
        /**
         * Strings will be quoted if they contain any escaped characters, the
         * comment character, the value separator, the multi-value separator or whitespace 
         */
        AUTO,
        /**
         * Strings will be quoted if they contain any escape characters, the 
         * comment character, the value separator, or the multi-value separator.
         */
        SPECIAL
    }

    /**
     * Creates {@link INIWriter} instances. Builders may be re-used, once {@link #build()}
     * is used, any changes to the builder will not affect the created instance.
     */
    public final static class Builder extends AbstractIOBuilder<Builder> {

        private StringQuoteMode stringQuoteMode = StringQuoteMode.SPECIAL;
        
        private char quoteCharacter = '"';
        private boolean emptyValuesHaveSeparator = true;
        private int indent = 2;
        private char indentCharacter = ' ';
        
        /**
         * Which character to use for indenting. Only tab ('\t') and space (' ') is allowed.
         * 
         * @param identCharacter character 
         * @return this for chaining
         */
        public Builder withIndentCharacter(char indentCharacter) {
        	if(indentCharacter != ' ' && indentCharacter != '\t') {
        		throw new IllegalArgumentException();
        	}
            this.indentCharacter = indentCharacter;
            return this;
        }
        
        /** 
         * How many indent characters to use.
         * 
         * @param indent indent 
         * @return this for chaining
         */
        public Builder withIndent(int indent) {
            this.indent = indent;
            return this;
        }

        /**
         * When {@link #withEmptyValues(boolean)} is true (the default), this configures whether
         * the value separator ({@link #withValueSeparator(char)} will be written when the value is empty.
         * {@link #withValueSeparatorWhitespace(boolean)} also affects this, adding space either side of
         * the separator.
         * 
         * @param emptyValuesHaveSeparator empty values have separator.
         * @return this for chaining
         */
        public Builder withEmptyValuesHaveSeparator(boolean emptyValuesHaveSeparator) {
            this.emptyValuesHaveSeparator = emptyValuesHaveSeparator;
            return this;
        }

        /**
         * Configure how the write behaves when writing strings with regard to
         * quotes. See {@link StringQuoteMode}.
         * 
         * @param stringQuoteMode quote mode.
         * @return this for chaining
         */
        public Builder withStringQuoteMode(StringQuoteMode stringQuoteMode) {
            this.stringQuoteMode = stringQuoteMode;
            return this;
        }

        /**
         * Configure the quote character to use when quote mode is anything
         * other than {@link StringQuoteMode#NEVER}.
         * 
         * @param quoteCharacter quote character
         * @return this for chaining
         */
        public Builder withQuoteCharacter(char quoteCharacter) {
            this.quoteCharacter = quoteCharacter;
            return this;
        }

        /**
         * Create a new {@link INIWriter}.
         * 
         * @return writer
         */
        public final INIWriter build() {
            return new INIWriter(this);
        }
    }

    private final StringQuoteMode stringQuoteMode;
    private final char quoteCharacter;
    private final boolean emptyValuesHaveSeparator;
    private final String indent;

    INIWriter(Builder builder) {
        super(builder);
        this.stringQuoteMode = builder.stringQuoteMode;
        this.quoteCharacter = builder.quoteCharacter;
        this.emptyValuesHaveSeparator = builder.emptyValuesHaveSeparator;
        
        var indnt = new StringBuilder();
        for(int i = 0 ; i < builder.indent; i++) 
        	indnt.append(builder.indentCharacter);
        
        indent = indnt.toString();
    }

    /**
     * Write the {@link Data} as a string. This can either be any {@link Data}, including
     * the entire {@link INI} document, or a {@link Section}.
     * 
     * @param document document
     * @return string  content
     * @throws IOException
     */
    public String write(Data document) {
        var w = new StringWriter();
        write(document, w);
        return w.toString();
    }

    /**
     * Write the {@link Data} to a file. This can either be any {@link Data}, including
     * the entire {@link INI} document, or a {@link Section}.
     * 
     * @param document document
     * @param file file
     * @throws IOException on error
     */
    public void write(Data document, Path file) throws IOException {
        try(var out = Files.newBufferedWriter(file)) {
            write(document, out);
        }
    }

    /**
     * Write the {@link Data} as a stream. This can either be any {@link Data}, including
     * the entire {@link INI} document, or a {@link Section}.
     * 
     * @param document document
     * @param writer writer
     * @throws IOException on error
     */
    public void write(Data document, Writer writer) {
        var pw = new PrintWriter(writer);
        var values = document.values();
        var sections = document.sections();
        var path = new Stack<String>();

        values.forEach((k, v) -> writeProperty(0, pw, k, v));

        writeSections(0, pw, new AtomicBoolean(values.size() > 0), sections, path);

        pw.flush();
    }

    private void writeSections(int depth, PrintWriter pw, AtomicBoolean lf, Map<String, Section[]> sections, Stack<String> path) {
        if (sections.size() > 0) {
            for(var section : sections.entrySet()) {
                writeSection(depth, pw, path, section.getKey(), lf, section.getValue());
            }
        }
    }

    public String quote(int depth, String value) {
        switch (stringQuoteMode) {
        case NEVER:
            return escape(value, false);
        case ALWAYS:
            break;
        case SPECIAL:
            if (!needsSpecialQuote(value))
                return value;
            break;
        case AUTO:
            if (!needsAutoQuote(value))
                return value;
            break;
        }
        return quoteCharacter + lineContinuations(depth, escape(value, true)) + quoteCharacter;
    }

    public String[] quote(int depth, String[] values) {
    	var newVals = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            newVals[i] = quote(depth, values[i]);
        }
        return newVals;
    }
    
    private String lineContinuations(int depth, String value) {
    	var lines = value.split("\\\\n");
    	if(lines.length > 1) {
    		var buf = new StringBuffer();
    		for(int i = 0 ; i < lines.length; i++) {
    			if(i > 0) {
    				buf.append("\\n\\\n");
    				for(int j = 0 ; j < Math.min(2,  depth * 4); j++) {
    					buf.append(' ');
    				}
    			}
    			buf.append(lines[i]);
    		}
    		return buf.toString();
    	}
    	else
    		return value;
    }

    private boolean needsAutoQuote(String value) {
        for (var c : new char[] { ' ', '\t', commentCharacter, valueSeparator, multiValueSeparator, '\r', '\\', '\n', '\0', '\b' }) {
            if (value.indexOf(c) != -1)
                return true;
        }
        return false;
    }
    
    private boolean needsSpecialQuote(String value) {
        for (var c : new char[] { multiValueSeparator, '\t', valueSeparator, commentCharacter, '\r', '\\', '\n', '\0', '\b' }) {
            if (value.indexOf(c) != -1)
                return true;
        }
        return false;
    }


    private String escape(String value, boolean quoted) {
    	switch(escapeMode) {
    	case QUOTED:
    		if(!quoted)
    			return value;
    	case ALWAYS:
            value = value.replace("\\", "\\\\");
            value = value.replace("\r", "\\r");
            value = value.replace("\n", "\\n");
            value = value.replace("\0", "\\0");
            value = value.replace("\b", "\\b");
            switch (stringQuoteMode) {
            case NEVER:
            case ALWAYS:
                value = value.replace("\t", "\\t");
                break;
            default:
                break;
            }
            return value;
    	default:
    		return value;
    	}
    }

    private void writeSection(int depth, PrintWriter pw, Stack<String> path, String key, AtomicBoolean newline, Section... sections) {
        path.push(key);
        try {
            if (sections.length < 2) {
                if(newline.get())
                    pw.println();
                pw.format("%s[%s]%n", indent(depth), escape(String.join(String.valueOf(sectionPathSeparator), path), false));
                if (sections.length == 1) {
                    sections[0].rawValues().forEach((k, v) -> writeProperty(depth + 1, pw, k, v));
                    newline.set(true);
                    writeSections(depth + 1, pw, newline, sections[0].sections(), path);
                }
            } else {
                for (var sec : sections) {
                    if(newline.get())
                        pw.println();
                    
                    pw.format("%s[%s]%n", indent(depth), escape(String.join(String.valueOf(sectionPathSeparator), path), false));
                	sec.rawValues().forEach((k, v) -> writeProperty(depth + 1, pw, k, v));
                    newline.set(true);
                    writeSections(depth + 1, pw, newline, sec.sections(), path);
                    
//                    writeSection(depth + 1, pw, path, key, newline, v);
                }
            }
        } finally {
            path.pop();
        }
    }

    private void writeProperty(int depth, PrintWriter pw, String key, String... values) {
        if (values.length == 0) {
            if(emptyValues) {
                pw.format("%s%s", indent(depth), escape(key, false));
                if(emptyValuesHaveSeparator) {
                    if(valueSeparatorWhitespace) {
                        pw.println(" = ");
                    }
                    else {
                        pw.println("=");
                    }
                }
                else {
                    pw.println();
                }
            }
        } else if (values.length == 1) {
            writeOneProperty(depth, pw, key, quote(depth, values[0]));
        } else {
            if (multiValueMode == MultiValueMode.REPEATED_KEY) {
                for (var v : values)
                    writeProperty(depth, pw, key, v);
            } else {
                if (trimmedValue)
                    writeOneProperty(depth, pw, key, String.join(String.valueOf(multiValueSeparator) + " ", quote(depth, values)));
                else
                    writeOneProperty(depth, pw, key, String.join(String.valueOf(multiValueSeparator), quote(depth, values)));
            }
        }
    }
    
    private String indent(int depth) {
    	var b = new StringBuilder();
    	for(int i = 0 ; i< depth; i++)
    		b.append(indent);
    	return b.toString();
    }

    private void writeOneProperty(int depth, PrintWriter pw, String key, String value) {
        if (valueSeparatorWhitespace)
            pw.println(String.format("%s%s %s %s", indent(depth), escape(key, false), Character.valueOf(valueSeparator), value));
        else
            pw.println(String.format("%s%s%s%s", indent(depth), escape(key, false), Character.valueOf(valueSeparator), value));
    }

}
