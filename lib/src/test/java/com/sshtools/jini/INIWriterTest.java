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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.INI.EscapeMode;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter.StringQuoteMode;

public class INIWriterTest {

    @Test
    public void testMultiValueModeRepeatedKey() throws Exception {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.APPEND).build().read(
                "Key1 = Val1\n" +
                "Key1 = Val2\n" + 
                "Key1 = Val3\n" +
                "Key1 = Val4\n");
        
        var wrtr = new INIWriter.Builder().
        		withMultiValueMode(MultiValueMode.REPEATED_KEY).
                build();
        
        assertEquals(
                "Key1 = Val1" + System.lineSeparator() +
                "Key1 = Val2" + System.lineSeparator() +
                "Key1 = Val3" + System.lineSeparator() +
                "Key1 = Val4" + System.lineSeparator(), wrtr.write(ini));
    }

    @Test
    public void testMultiValueModeSeparated() throws Exception {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.APPEND).build().read(
                "Key1 = Val1" + System.lineSeparator() +
                "Key1 = Val2" + System.lineSeparator() +
                "Key1 = Val3" + System.lineSeparator() +
                "Key1 = Val4" + System.lineSeparator());
        
        var wrtr = new INIWriter.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                build();
        
        assertEquals("Key1 = Val1, Val2, Val3, Val4"+ System.lineSeparator(), wrtr.write(ini));
    }


    @Test
    public void testMultiValueModeSeparatedWithoutSpace() throws Exception {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.APPEND).build().read(
                "Key1 = Val1" + System.lineSeparator()+
                "Key1 = Val2" + System.lineSeparator()+
                "Key1 = Val3" + System.lineSeparator()+
                "Key1 = Val4"+ System.lineSeparator());
        
        var wrtr = new INIWriter.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                withoutTrimmedValue().
                build();
        
        assertEquals("Key1 = Val1,Val2,Val3,Val4"+ System.lineSeparator(), wrtr.write(ini));
    }

    @Test
    public void testCreateMultipleSameNamedSectrions() throws IOException, ParseException {
        var ini = INI.create();
        var sec1a = ini.create("Section1");
        sec1a.put("Key1", "Val1");
        var sec1b = ini.create("Section1");
        sec1b.put("Key2", "Val2");
        sec1b.put("Key3", "Val3");
        
        var wtr = new INIWriter.Builder().
                withEmptyValues(false).
                withValueSeparatorWhitespace(false).
                withIndent(0).
                withStringQuoteMode(StringQuoteMode.NEVER).
                withMultiValueMode(MultiValueMode.SEPARATED).build();
        
        assertEquals(
        		"[Section1]" + System.lineSeparator() +
        		"Key1=Val1"  + System.lineSeparator() + System.lineSeparator() +
        		"[Section1]"  + System.lineSeparator() + 
        		"Key2=Val2" + System.lineSeparator() +
        		"Key3=Val3" + System.lineSeparator()
        		, wtr.write(ini));
    }

    @Test
    public void testSimpleComments() throws IOException, ParseException {
        var ini = INI.create();
        ini.setComments("This is the document comment");
        var sec1a = ini.create("Section1");
        sec1a.setComments("This is a couple of lines of", "comments for this section");
        sec1a.put("Key1", "Val1");
        var sec1b = ini.create("Section1");
        sec1b.put("Key2", "Val2");
        sec1b.setKeyComments("Key2", "And this is commments for a key,", "using default EOL");
        sec1b.put("Key3", "Val3");
        
        var wtr = new INIWriter.Builder().
                withEmptyValues(false).
                withCommentCharacter('#').
                withStringQuoteMode(StringQuoteMode.NEVER).
                withMultiValueMode(MultiValueMode.SEPARATED).build();
        
        assertEquals(
        		"# This is the document comment" + System.lineSeparator() +System.lineSeparator() +
        		"# This is a couple of lines of" + System.lineSeparator() +
        		"# comments for this section" + System.lineSeparator() +System.lineSeparator() +
        		"[Section1]" + System.lineSeparator() +
        		"  Key1 = Val1"  + System.lineSeparator() + System.lineSeparator() +
        		"[Section1]"  + System.lineSeparator() + 
        		"  Key2 = Val2 # And this is commments for a key, using default EOL" + System.lineSeparator() +
        		"  Key3 = Val3" + System.lineSeparator()
        		, wtr.write(ini));
    }

    @Test
    public void testBackslashesInSingleLine() throws IOException, ParseException {
    	var ini = INI.create();
	   	ini.put("AVal", "C:\\Window\\Path\\To\\Something");
	   	var wtr = new INIWriter.Builder().
		                withEscapeMode(EscapeMode.ALWAYS).
		                withStringQuoteMode(StringQuoteMode.NEVER).build();
        assertEquals("AVal = C:\\\\Window\\\\Path\\\\To\\\\Something" + System.lineSeparator(), wtr.write(ini));
    }

    @Test
    public void testStringSpanningLines() throws IOException, ParseException {
    	 var ini = INI.create();
    	 ini.put("AVal", "A value that spans"  + System.lineSeparator() + "more than one" + System.lineSeparator() + "line");
    	 var wtr = new INIWriter.Builder().
	                withEmptyValues(false).
	                withCommentCharacter('#').
	                withValueSeparatorWhitespace(false).
	                withIndent(0).
	                withEscapeMode(EscapeMode.ALWAYS).
	                withStringQuoteMode(StringQuoteMode.NEVER).
	                withoutMultilineStrings().
	                withMultiValueMode(MultiValueMode.SEPARATED).build();
    	 

         assertEquals("AVal=A value that spans\\n\\" + System.lineSeparator()
         		+ "more than one\\n\\" + System.lineSeparator()
         		+ "line" + System.lineSeparator()
         		+ "", wtr.write(ini));
    }
}
