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
        * Strings will be quoted if they contain whitespace 
        */
        AUTO
    }

    /**
     * Creates {@link INIWriter} instances.
     */
    public final static class Builder extends AbstractIOBuilder<Builder> {

        private StringQuoteMode stringQuoteMode = StringQuoteMode.AUTO;
        private char quoteCharacter = '"';

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

    INIWriter(Builder builder) {
        super(builder);
        this.stringQuoteMode = builder.stringQuoteMode;
        this.quoteCharacter = builder.quoteCharacter;
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

        writeSections(pw, values.size() > 0, sections, path);

        pw.flush();
    }

    private void writeSections(PrintWriter pw, boolean lf, Map<String, Section[]> sections, Stack<String> path) {
        if (sections.size() > 0) {
            if (lf)
                pw.println();
            sections.forEach((k, v) -> writeSection(pw, path, k, v));
        }
    }

    private String quote(String value) {
        switch (stringQuoteMode) {
        case NEVER:
            return escape(value);
        case ALWAYS:
            break;
        case AUTO:
            if (!needsQuote(value))
                return value;
        }
        return quoteCharacter + escape(value) + quoteCharacter;
    }

    private String[] quote(String[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = quote(values[i]);
        }
        return values;
    }

    boolean needsQuote(String value) {
        for (var c : new char[] { ' ', '\t', ';' }) {
            if (value.indexOf(c) != -1)
                return true;
        }
        return false;
    }

    private String escape(String value) {
        // TODO unicode
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
    }

    private void writeSection(PrintWriter pw, Stack<String> path, String key, Section... values) {
        path.push(key);
        try {
            if (values.length < 2) {
                pw.println("[" + escape(key) + "]");
                if (values.length == 1) {
                    values[0].values().forEach((k, v) -> writeProperty(pw, k, v));
                    var sections = values[0].sections();
                    writeSections(pw, true, sections, path);
                }
            } else {
                for (var v : values)
                    writeSection(pw, path, key, v);
            }
        } finally {
            path.pop();
        }
    }

    private void writeProperty(PrintWriter pw, String key, String... values) {
        if (values.length == 0) {
            pw.println(escape(key));
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
