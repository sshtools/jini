package com.sshtools.jini;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.text.ParseException;

import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;
import com.sshtools.jini.INIWriter.StringQuoteMode;

import org.junit.jupiter.api.Test;

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
                "Key1 = Val1\n" +
                "Key1 = Val2\n" +
                "Key1 = Val3\n" +
                "Key1 = Val4\n", wrtr.write(ini));
    }

    @Test
    public void testMultiValueModeSeparated() throws Exception {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.APPEND).build().read(
                "Key1 = Val1\n" +
                "Key1 = Val2\n" +
                "Key1 = Val3\n" +
                "Key1 = Val4\n");
        
        var wrtr = new INIWriter.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                build();
        
        assertEquals("Key1 = Val1, Val2, Val3, Val4\n", wrtr.write(ini));
    }


    @Test
    public void testMultiValueModeSeparatedWithoutSpace() throws Exception {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.APPEND).build().read(
                "Key1 = Val1\n" +
                "Key1 = Val2\n" +
                "Key1 = Val3\n" +
                "Key1 = Val4\n");
        
        var wrtr = new INIWriter.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                withoutTrimmedValue().
                build();
        
        assertEquals("Key1 = Val1,Val2,Val3,Val4\n", wrtr.write(ini));
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
                withCommentCharacter('#').
                withValueSeparatorWhitespace(false).
                withIndent(0).
                withStringQuoteMode(StringQuoteMode.NEVER).
                withMultiValueMode(MultiValueMode.SEPARATED).build();
        
        assertEquals(
        		"[Section1]\n" +
        		"Key1=Val1\n\n" +
        		"[Section1]\n" +
        		"Key2=Val2\n" +
        		"Key3=Val3\n"
        		, wtr.write(ini));
    }
}
