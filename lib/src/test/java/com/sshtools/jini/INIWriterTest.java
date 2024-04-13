package com.sshtools.jini;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;

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
}
