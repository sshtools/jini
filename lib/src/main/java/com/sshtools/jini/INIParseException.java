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

import java.text.ParseException;

/**
 * A parsing error caused by invalid INI format. Contains context like the error offset and line number where
 * the problem occurred.
 *
 */
public final class INIParseException extends ParseException {

    private final int lineNumber;

    /**
     * Constructs an exception with the following details
     *
     * @param message         the detail message
     * @param errorOffset the position where the error is found while parsing.
     * @param lineNumber  the line number where the error occurred, starting from 0
     */
    public INIParseException(String message, int errorOffset, int lineNumber) {
        super(message, errorOffset);
        this.lineNumber = lineNumber;
    }

    /**
     * Gets the line number where the parse error occurred. The line numbers are indexed starting from 0.
     *
     * @return the line number, starting at 0
     */
    public int getLineNumber() {
        return lineNumber;
    }
}
