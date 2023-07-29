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

import static com.sshtools.jini.INITest.assertBasic;
import static com.sshtools.jini.INITest.assertBasicCaseSensitive;
import static com.sshtools.jini.INITest.assertBasicInsensitive;
import static com.sshtools.jini.INITest.assertBasicOrder;
import static com.sshtools.jini.INITest.getBasicIni;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;

public class INIReaderTest {

    @Test
    public void testReadFromString() throws Exception {
        var ini = new INIReader.Builder().build().read(INITest.getBasicIni());
        assertBasic(ini);
        assertBasicOrder(ini);
        assertBasicInsensitive(ini);
    }

    @Test
    public void testReadFromFile() throws Exception {
        var tf = Files.createTempFile("jini", ".ini");
        try(var out = Files.newOutputStream(tf)) {
            out.write(INITest.getBasicIni().getBytes());
        }
        var ini = new INIReader.Builder().build().read(tf);
        assertBasic(ini);
        assertBasicOrder(ini);
        assertBasicInsensitive(ini);
    }
    
    @Test
    public void testAbortDuplicateSection() throws IOException, ParseException {
        assertThrows(ParseException.class, () -> {
            new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.ABORT)
                    .build().read("""
                            [Section1]
                            S1aK1 = V1
                            [Section1]
                            S1bK1 = V2
                            """); 
        });
    }
    
    @Test
    public void testAbortDuplicateKeys() throws IOException, ParseException {
        assertThrows(ParseException.class, () -> {
            new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.ABORT)
                    .build().read("""
                            S1aK1 = V1
                            S1bK1 = V2
                            S1aK1 = V2
                            """); 
        });
    }
    
    @Test
    public void testIgnoreDuplicateKeys() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.IGNORE)
                .build().read("""
                        S1aK1 = V1
                        S1bK1 = V2
                        S1aK1 = V3
                        """); 
        assertEquals(2, ini.values().size());
        assertEquals("V1", ini.get("S1aK1"));
        assertEquals("V2", ini.get("S1bK1"));
    }
    
    @Test
    public void testReplaceDuplicateKeys() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.REPLACE)
                .build().read("""
                        S1aK1 = V1
                        S1bK1 = V2
                        S1aK1 = V3
                        """); 
        assertEquals(2, ini.values().size());
        assertEquals("V3", ini.get("S1aK1"));
        assertEquals("V2", ini.get("S1bK1"));
    }
    
    @Test
    public void testMergeDuplicateSection() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.MERGE)
                .build().read("""
                        [Section1]
                        S1aK1 = V1
                        [Section1]
                        S1bK1 = V2
                        [Section1]
                        S1cK1 = V3
                        """); 
        assertEquals(1, ini.sections().size());
        var sec1 = ini.section("Section1");
        assertEquals(3, sec1.values().size());
        assertEquals("V1", sec1.get("S1aK1"));
        assertEquals("V2", sec1.get("S1bK1"));
        assertEquals("V3", sec1.get("S1cK1"));
    }
    
    @Test
    public void testMergeDuplicateKeys() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateKeysAction(DuplicateAction.MERGE)
                .build().read("""
                        S1aK1 = V1
                        S1aK1 = V2
                        S1aK1 = V3
                        """); 
        assertEquals(1, ini.values().size());
        assertArrayEquals(new String[] { "V1", "V2", "V3" }, ini.getAll("S1aK1"));
    }
    
    @Test
    public void testAppendDuplicateSection() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.APPEND)
                .build().read("""
                        [Section1]
                        S1aK1 = V1
                        [Section1]
                        S1bK1 = V2
                        [Section1]
                        S1cK1 = V3
                        """); 
        assertEquals(1, ini.sections().size());
        var sec1 = ini.section("Section1");
        assertEquals(3, sec1.values().size());
        assertEquals("V1", sec1.get("S1aK1"));
        assertEquals("V2", sec1.get("S1bK1"));
        assertEquals("V3", sec1.get("S1cK1"));
    }
    
    @Test
    public void testReplaceDuplicateSection() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.REPLACE)
                .build().read("""
                        [Section1]
                        S1aK1 = V1
                        [Section1]
                        S1bK1 = V2
                        [Section1]
                        S1cK1 = V3
                        """); 
        assertEquals(1, ini.sections().size());
        var sec1 = ini.section("Section1");
        assertEquals(1, sec1.values().size());
        assertEquals("V3", sec1.get("S1cK1"));
    }
    
    @Test
    public void testIgnoreDuplicateSection() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.IGNORE)
                .build().read("""
                        [Section1]
                        S1aK1 = V1
                        [Section1]
                        S1bK1 = V2
                        [Section1]
                        S1cK1 = V3
                        """); 
        assertEquals(1, ini.sections().size());
        var sec1 = ini.section("Section1");
        assertEquals("V1", sec1.get("S1aK1"));
        assertEquals("V2", sec1.get("S1bK1"));
        assertEquals("V3", sec1.get("S1cK1"));
    }

    @Test
    public void testNestedSections() throws Exception {
        var ini = new INIReader.Builder().
                build().read("""
                Key1 = Val1
                [Section1]
                S1Key1 = S1Val1
                [Section1.Section2]
                S1S2Key1 = S1S2Val1
                """);
        assertEquals("Val1", ini.get("Key1"));
        assertEquals(1, ini.sections().size());
        assertEquals(1, ini.values().size());
        var sec1 = ini.section("Section1");
        assertEquals(1, sec1.values().size());
        assertEquals("S1Val1", sec1.get("S1Key1"));
        assertEquals(1, sec1.sections().size());
        var sec2 = sec1.section("Section2");
        assertEquals(1, sec2.values().size());
        assertEquals("S1S2Val1", sec2.get("S1S2Key1"));
        assertEquals(0, sec2.sections().size());
        System.out.println(ini);
    }

    @Test
    public void testSeparatedMultiValueMode() throws Exception {
        var ini = new INIReader.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                build().read("""
                Key1 = Val1, Val2, Val3,Val4
                """);
        assertEquals(1, ini.values().size());
        assertArrayEquals(new String[] {"Val1", "Val2", "Val3", "Val4"}, ini.getAll("Key1"));
    }

    @Test
    public void testSeparatedMultiValueModeReplaces() throws Exception {
        var ini = new INIReader.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                build().read("""
                Key1 = Val1, Val2, Val3,Val4
                Key1 = Val5
                """);
        assertEquals(1, ini.values().size());
        assertArrayEquals(new String[] {"Val5"}, ini.getAll("Key1"));
    }

    @Test
    public void testSeparatedMultiValueModeAltSeparator() throws Exception {
        var ini = new INIReader.Builder().
                withMultiValueMode(MultiValueMode.SEPARATED).
                withMultiValueSeparator('/').
                build().read("""
                Key1 = Val1/ Val2/ Val3/Val4
                """);
        assertEquals(1, ini.values().size());
        assertArrayEquals(new String[] {"Val1", "Val2", "Val3", "Val4"}, ini.getAll("Key1"));
    }

    @Test
    public void testWithoutPreserveOrder() throws Exception {
        var ini = new INIReader.Builder().
                withoutPreserveOrder().
                build().read(getBasicIni());
        assertBasic(ini);
    }

    @Test
    public void testCaseSensitive() throws Exception {
        var ini = new INIReader.Builder().
                withCaseSensitiveKeys().
                withCaseSensitiveSections().
                build().read(getBasicIni());
        assertBasic(ini);
        assertBasicCaseSensitive(ini);
        assertBasicOrder(ini);
    }

    @Test
    public void testCaseSensitiveAndUnordered() throws Exception {
        var ini = new INIReader.Builder().
                withoutPreserveOrder().
                withCaseSensitiveKeys().
                withCaseSensitiveSections().
                build().read(getBasicIni());
        assertBasic(ini);
        assertBasicCaseSensitive(ini);
    }

    @Test
    public void testWithoutComments() throws Exception {
        var ini = new INIReader.Builder().
                withoutComments().
                build().read("""
                ;Key1 = Val1
                Key2 = Val2
                ;Key3 = Val3
                """);
        assertEquals(3, ini.values().size());
        assertEquals("Val1", ini.get(";Key1"));
        assertEquals("Val2", ini.get("Key2"));
        assertEquals("Val3", ini.get(";Key3"));
    }

    @Test
    public void testCommentCharacter() throws Exception {
        var ini = new INIReader.Builder().
                withCommentCharacter('#').
                build().read("""
                #Key1 = Val1
                Key2 = Val2
                ;Key3 = Val3
                #Key4 = Val4
                """);
        assertEquals(2, ini.values().size());
        assertEquals("Val2", ini.get("Key2"));
        assertEquals("Val3", ini.get(";Key3"));
    }

    @Test
    public void testValueSeparator() throws Exception {
        var ini = new INIReader.Builder().
                withValueSeparator(':').
                build().read("""
                Key1= : =Val1
                """);
        assertEquals(1, ini.values().size());
        assertEquals("=Val1", ini.get("Key1="));
    }

    @Test
    public void testSectionPathSeparator() throws Exception {
        var ini = new INIReader.Builder().
                withSectionPathSeparator('/').
                build().read("""
                Key1 = Val1
                [Section1]
                S1Key1 = S1Val1
                [Section1/Section2]
                S1S2Key1 = S1S2Val1
                """);
        assertEquals(1, ini.sections().size());
        assertEquals("Section1", ini.sections().values().iterator().next()[0].key());
        assertEquals("Section2", ini.section("Section1").sections().values().iterator().next()[0].key());
    }

    @Test
    public void testWithoutNestedSections() throws Exception {
        var ini = new INIReader.Builder().
                withoutNestedSections().
                build().read("""
                Key1 = Val1
                [Section1]
                S1Key1 = S1Val1
                [Section1.Section2]
                S1S2Key1 = S1S2Val1
                """);
        assertEquals(2, ini.sections().size());
    }

    @Test
    public void testWithoutStringQuoting() throws Exception {
        var ini = new INIReader.Builder().withoutStringQuoting().build().read("""
                \"Val 1\" = \"Root Val 1\"
                'Val 2' = 'Root Val 2'
                """);
        assertEquals("\"Root Val 1\"", ini.get("\"Val 1\""));
        assertEquals("'Root Val 2'", ini.get("'Val 2'"));
    }

    @Test
    public void testWithoutValueSeparatorWhitespace() throws Exception {
        var ini = new INIReader.Builder().withoutValueSeparatorWhitespace().build().read("""
                Key1=Val1
                Key2 = Val2
                """);
        assertEquals("Val1", ini.get("Key1"));
        assertEquals("Val2", ini.get("Key2 "));
    }

    @Test
    public void testWithoutTrimmedValue() throws Exception {
        var ini = new INIReader.Builder().withoutTrimmedValue().withoutValueSeparatorWhitespace().build().read("Key1=Val1\nKey2= Val2 ");
        assertEquals("Val1", ini.get("Key1"));
        assertEquals(" Val2 ", ini.get("Key2"));
    }

    @Test
    public void testQuoteCharacters() throws Exception {
        var ini = new INIReader.Builder().withoutStringQuoting().build().read("""
                `Val 1` = `Root Val 1`
                """);
        assertEquals("`Root Val 1`", ini.get("`Val 1`"));
    }

    @Test
    public void testParseExceptionsEmptyVal() throws Exception {
        assertEquals("Empty values are not allowed.", assertThrows(ParseException.class, () -> {
            new INIReader.Builder().withoutEmptyValues().build().read("""
                    Val 1 =
                    """);
        }).getMessage());
    }

    @Test
    public void testWithoutParseExceptionsEmptyVal() throws Exception {
        var ini = new INIReader.Builder().withoutEmptyValues().withoutParseExceptions().build().read("""
                Val 1 =
                """);
        assertFalse(ini.values().containsKey("Val 1"));
    }

    @Test
    public void testParseExceptionsBadSectionSyntax() throws Exception {
        assertEquals("Incorrect syntax for section name, no closing ']'.", assertThrows(ParseException.class, () -> {
            new INIReader.Builder().build().read("""
                    [Sec1
                    Val 1 = Abcd
                    """);
        }).getMessage());
    }

    @Test
    public void testWithoutParseExceptionsBadSectionSyntax() throws Exception {
        var ini = new INIReader.Builder().withoutParseExceptions().build().read("""
                [Sec1
                Val 1 = Abcd
                """);
        assertTrue(ini.values().containsKey("Val 1"));
    }

    @Test
    public void testParseExceptionsTrailingSectionNameContent() throws Exception {
        assertEquals("Incorrect syntax for section name, trailing content after closing ']'.",
                assertThrows(ParseException.class, () -> {
                    new INIReader.Builder().build().read("""
                            [Sec1]XXX
                            Val 1 = Abcd
                            """);
                }).getMessage());
    }

    @Test
    public void testWithoutParseExceptionsTrailingSectionNameContent() throws Exception {
        var ini = new INIReader.Builder().withoutParseExceptions().build().read("""
                [Sec1]XXX
                Val 1 = Abcd
                """);
        assertTrue(ini.values().containsKey("Val 1"));
    }

    @Test
    public void testParseExceptionsNoGlobalSection() throws Exception {
        assertEquals("Global properties are not allowed, all properties must be in a [Section].",
                assertThrows(ParseException.class, () -> {
                    new INIReader.Builder().withoutGlobalProperties().build().read("""
                            Val 1 = Abcd
                            """);
                }).getMessage());
    }

    @Test
    public void testInlineComments() throws Exception {
        var ini = new INIReader.Builder().build().read("""
                Key 1 = Val 1 ; Some comment
                """);
        assertEquals("Val 1", ini.get("Key 1"));
    }

    @Test
    public void testWithoutInlineComments() throws Exception {
        var ini = new INIReader.Builder().withoutInlineComments().build().read("""
                Key 1 = Val 1 ; Some comment
                """);
        assertEquals("Val 1 ; Some comment", ini.get("Key 1"));
    }

    @Test
    public void testLineContinuation() throws Exception {
        var ini = new INIReader.Builder().build().read("Key 0 = Val 0\n" +
                "Key 1 = Val 1\\\n" + 
                "Key 2 = Val 2 \\\n" +
                "Key 3 = Val 3\n" +
                "Key 4 = Val 4\n");
        System.out.println(ini);
        assertEquals(3, ini.values().size());
        assertEquals("Val 0", ini.get("Key 0"));
        assertEquals("Val 1 Key 2 = Val 2  Key 3 = Val 3", ini.get("Key 1"));
        assertEquals("Val 4", ini.get("Key 4"));
    }

    @Test
    public void testEscaped() throws Exception {
        var ini = new INIReader.Builder().build().read("Key 0 = \\\\Abc\\a1\\b2\\t4\\nY\\r\\;Hello\\=Argh!\\W\\0");
        System.out.println(ini);
        assertEquals(1, ini.values().size());
        assertEquals("\\Abc" + (char)7 + "1" + (char)8 + "2" + (char)11 + "4\nY\r;Hello=Argh!\\W", ini.get("Key 0"));
    }

    @Test
    public void testWithoutLineContinuation() throws Exception {
        var ini = new INIReader.Builder().withoutLineContinuations().build().read("Key 0 = Val 0\n" +
                "Key 1 = Val 1\\\n" + 
                "Key 2 = Val 2 \\\n" +
                "Key 3 = Val 3\n" +
                "Key 4 = Val 4\n");
        System.out.println(ini);
        assertEquals(5, ini.values().size());
        assertEquals("Val 0", ini.get("Key 0"));
        assertEquals("Val 1", ini.get("Key 1"));
        assertEquals("Val 2", ini.get("Key 2"));
        assertEquals("Val 3", ini.get("Key 3"));
        assertEquals("Val 4", ini.get("Key 4"));
    }

   
}
