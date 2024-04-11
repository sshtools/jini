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
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader.MultiValueMode;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes {@link INI} documents in the INI format.
 * <p>
 * This class should not be directly created, instead use {@link INIWriter.Builder}
 * and configure it accordingly before calling {@link INIWriter.Builder#build()}.
 */
public class INIWriter extends AbstractIO {

    /**
     * Use to configure when special characters in written string values are escaped.
     */
    public enum EscapeMode {
        /**
         * Special characters in strings will never be escaped with the configured escape character.
         */
        NEVER,
        /**
         * Special characters in strings will always be escaped with the configured escape character.
         */
        ALWAYS
    }

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
        * Strings will be quoted if they contain whitespace 
        */
        AUTO
    }

    /**
     * Creates {@link INIWriter} instances. Builders may be re-used, once {@link #build()}
     * is used, any changes to the builder will not affect the created instance.
     */
    public final static class Builder extends AbstractIOBuilder<Builder> {

        private StringQuoteMode stringQuoteMode = StringQuoteMode.AUTO;
        private EscapeMode escapeMode = EscapeMode.ALWAYS;
        
        private char quoteCharacter = '"';
        private boolean emptyValuesHaveSeparator = true;

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
         * Configure how the write behaves when writing special characters in strings 
         * with regard to escaping. See {@link EscapeQuoteMode}.
         * 
         * @param escapeMode escape mode.
         * @return this for chaining
         */
        public Builder withEscapeMode(EscapeMode escapeMode) {
            this.escapeMode = escapeMode;
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
    private final EscapeMode escapeMode;
    private final char quoteCharacter;
    private final boolean emptyValuesHaveSeparator;

    INIWriter(Builder builder) {
        super(builder);
        this.escapeMode = builder.escapeMode;
        this.stringQuoteMode = builder.stringQuoteMode;
        this.quoteCharacter = builder.quoteCharacter;
        this.emptyValuesHaveSeparator = builder.emptyValuesHaveSeparator;
    }

    /**
     * Write the {@link INI} document as a string.
     * 
     * @param document document
     * @return string  content
     * @throws IOException
     */
    public String write(INI document) {
        var w = new StringWriter();
        write(document, w);
        return w.toString();
    }

    /**
     * Write the {@link INI} document to a file.
     * 
     * @param document document
     * @param file file
     * @throws IOException on error
     */
    public void write(INI document, Path file) throws IOException {
        try(var out = Files.newBufferedWriter(file)) {
            write(document, out);
        }
    }

    /**
     * Write the {@link INI} document as a stream.
     * 
     * @param document document
     * @param writer writer
     * @throws IOException on error
     */
    public void write(INI document, Writer writer) {
        var pw = new PrintWriter(writer);
        var values = document.values();
        var sections = document.sections();
        var path = new Stack<String>();

        values.forEach((k, v) -> writeProperty(pw, k, v));

        writeSections(pw, new AtomicBoolean(values.size() > 0), sections, path);

        pw.flush();
    }

    private void writeSections(PrintWriter pw, AtomicBoolean lf, Map<String, Section[]> sections, Stack<String> path) {
        if (sections.size() > 0) {
            for(var section : sections.entrySet()) {
                writeSection(pw, path, section.getKey(), lf, section.getValue());
            }
        }
    }

    private String quote(String value) {
    	return quote(quoteCharacter, stringQuoteMode, escapeMode, value);
    }

    public static String quote(char quoteCharacter, StringQuoteMode stringQuoteMode, EscapeMode escapeMode, String value) {
        switch (stringQuoteMode) {
        case NEVER:
            return escape(stringQuoteMode, escapeMode, value);
        case ALWAYS:
            break;
        case AUTO:
            if (!needsQuote(value))
                return value;
        }
        return quoteCharacter + escape(stringQuoteMode, escapeMode, value) + quoteCharacter;
    }

    private String[] quote(String[] values) {
    	return quote(quoteCharacter, stringQuoteMode, escapeMode, values);
    }

    public static String[] quote(char quoteCharacter, StringQuoteMode stringQuoteMode, EscapeMode escapeMode, String[] values) {
    	var newVals = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            newVals[i] = quote(quoteCharacter, stringQuoteMode, escapeMode, values[i]);
        }
        return newVals;
    }

    public static boolean needsQuote(String value) {
        for (var c : new char[] { ' ', '\t', ';' }) {
            if (value.indexOf(c) != -1)
                return true;
        }
        return false;
    }


    private String escape(String value) {
    	return escape(stringQuoteMode, escapeMode, value);
    }

    private static String escape(StringQuoteMode stringQuoteMode, EscapeMode escapeMode, String value) {
    	switch(escapeMode) {
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

    private void writeSection(PrintWriter pw, Stack<String> path, String key, AtomicBoolean newline, Section... sections) {
        path.push(key);
        try {
            if (sections.length < 2) {
                if(newline.get())
                    pw.println();
                pw.format("[%s]%n", escape(key));
                if (sections.length == 1) {
                    sections[0].values().forEach((k, v) -> writeProperty(pw, k, v));
                    newline.set(true);
                    writeSections(pw, newline, sections[0].sections(), path);
                }
            } else {
                for (var v : sections) {
                    writeSection(pw, path, key, newline, v);
                }
            }
        } finally {
            path.pop();
        }
    }

    private void writeProperty(PrintWriter pw, String key, String... values) {
        if (values.length == 0) {
            if(emptyValues) {
                pw.print(escape(key));
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
            writeOneProperty(pw, key, quote(values[0]));
        } else {
            if (multiValueMode == MultiValueMode.REPEATED_KEY) {
                for (var v : values)
                    writeProperty(pw, key, v);
            } else {
                if (trimmedValue)
                    writeOneProperty(pw, key, String.join(String.valueOf(multiValueSeparator) + " ", quote(values)));
                else
                    writeOneProperty(pw, key, String.join(String.valueOf(multiValueSeparator), quote(values)));
            }
        }
    }

    private void writeOneProperty(PrintWriter pw, String key, String value) {
        if (valueSeparatorWhitespace)
            pw.println(String.format("%s %s %s", escape(key), Character.valueOf(valueSeparator), value));
        else
            pw.println(String.format("%s%s%s", escape(key), Character.valueOf(valueSeparator), value));
    }

}
