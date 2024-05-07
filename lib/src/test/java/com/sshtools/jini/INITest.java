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

import static com.sshtools.jini.Interpolation.compound;
import static com.sshtools.jini.Interpolation.environment;
import static com.sshtools.jini.Interpolation.systemProperties;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.INI.MissingVariableMode;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader.DuplicateAction;

public class INITest {

    @Test
    public void testEmptyValue() throws Exception {
        var ini = INI.create();
        ini.put("Key1", (String)null);
        ini.putAll("Key2", new String[0]);
        assertTrue(ini.getOr("Key1").isEmpty());
        assertTrue(ini.getOr("Key2").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ini.get("Key1"));
        assertThrows(IllegalArgumentException.class, () -> ini.get("Key2"));
    }
    
    @Test
    public void testWithoutEmptyValue() throws Exception {
        var ini = new INI.Builder().withoutEmptyValues().build();

        assertThrows(IllegalArgumentException.class, () -> ini.put("Key1", (String) null));
        assertThrows(IllegalArgumentException.class, () -> ini.putAll("Key1", new String[0]));
    }

    @Test
    public void testWithoutPreserveOrder() throws Exception {
        /* Just for coverage, no point in testing if order is bad, it might not be! */
        new INI.Builder().withoutPreserveOrder().build();
    }

    @Test
    public void testCaseSensitive() throws Exception {
        var ini = new INIReader.Builder().withCaseSensitiveKeys().withCaseSensitiveSections().build()
                .read(getBasicIni());

        doTestCaseSensitive(ini);
    }

    @Test
    public void testCaseSensitiveAndUnordered() throws Exception {
        var ini = new INI.Builder().withoutPreserveOrder().withCaseSensitiveKeys().withCaseSensitiveSections().build();

        doTestCaseSensitive(ini);
    }

    @Test
    public void testReadFromString() throws Exception {
        var ini = INI.fromString(getBasicIni());
        assertBasic(ini);
        assertBasicOrder(ini);
        assertBasicInsensitive(ini);
    }

    @Test
    public void testCommaInValue() throws Exception {
        assertEquals("Some,val", INI.fromString("Key1='Some,val'").get("Key1"));
    }

    @Test
    public void testDeleteKeys() throws Exception {
        var ini = INI.fromString(getBasicIni());

        assertTrue(ini.remove("Root 1"));
        assertEquals(5, ini.values().size());
        assertFalse(ini.remove("Root 1"));
    }

    @Test
    public void testInterpolate() throws Exception {
        var ini = new INIReader.Builder().
        		withInterpolator(Interpolation.defaults()).
        		build().read(getVariablesIni());
		assertVariables(ini);
		assertThrows(IllegalArgumentException.class, () -> {
	        ini.get("Error 1");
		});
		assertThrows(IllegalArgumentException.class, () -> {
	        ini.section("Section1").get("Error 1");
		});
    }

    @Test
    public void testInterpolateSkipMissing() throws Exception {
        var ini = new INIReader.Builder().
        		withInterpolator(compound(
    				systemProperties(),
    				environment())
        		).
        		withMissingVariableMode(MissingVariableMode.SKIP).
        		build().read(getVariablesIni());
		assertVariables(ini);
		assertEquals("${xxxxx}", ini.get("Error 1"));
		assertEquals("${xxxxx}", ini.section("Section1").get("Error 1"));
    }

    @Test
    public void testInterpolateBlankMissing() throws Exception {
        var ini = new INIReader.Builder().
        		withInterpolator(compound(
    				systemProperties(),
    				environment())
        		).
        		withMissingVariableMode(MissingVariableMode.BLANK).
        		build().read(getVariablesIni());
		assertVariables(ini);
		assertEquals("", ini.get("Error 1"));
		assertEquals("", ini.section("Section1").get("Error 1"));
    }

    @Test
    public void testInterpolateAltPattern() throws Exception {
        var ini = new INIReader.Builder().
        		withVariablePattern("%(.*)%").
        		withInterpolator(Interpolation.defaults()).
        		build().read(getWinVariablesIni());
        
		assertVariables(ini);
		assertThrows(IllegalArgumentException.class, () -> {
	        ini.get("Error 1");
		});
		assertThrows(IllegalArgumentException.class, () -> {
	        ini.section("Section1").get("Error 1");
		});
    }

    @Test
    public void testReadFromBadString() throws Exception {
        assertThrows(IllegalStateException.class, () -> INI.fromString(getBadIni()));
    }

    @Test
    public void testReadFromFile() throws Exception {
        var tf = Files.createTempFile("jini", ".ini");
        try (var out = Files.newOutputStream(tf)) {
            out.write(getBasicIni().getBytes());
        }
        var ini = INI.fromFile(tf);
        assertBasic(ini);
        assertBasicOrder(ini);
        assertBasicInsensitive(ini);
    }

    @Test
    public void testReadFromStream() throws Exception {
        var ini = INI.fromInput(new ByteArrayInputStream(getBasicIni().getBytes()));
        assertBasic(ini);
        assertBasicOrder(ini);
        assertBasicInsensitive(ini);
    }

    @Test
    public void testReadOnly() throws Exception {
    	assertThrows(UnsupportedOperationException.class, () -> {
            var ini = INI.fromString(getBasicIni()).readOnly();
            ini.put("abc", true);
    	});
    }

    @Test
    public void testEmpty() throws Exception {
    	assertThrows(UnsupportedOperationException.class, () -> {
            var ini = INI.blank();
            assertEquals(0, ini.keys().size());
            assertEquals(0, ini.sections().size());
            ini.put("abc", true);
    	});
    }

    @Test
    public void testFailReadFromFile() throws Exception {
        assertThrows(UncheckedIOException.class, () -> INI.fromFile(Paths.get(UUID.randomUUID().toString())));
    }

    @Test
    public void testFailReadFromReader() throws Exception {
        assertThrows(UncheckedIOException.class, () -> {
            INI.fromReader(new Reader() {

                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    throw new IOException("Bang");
                }

                @Override
                public void close() throws IOException {
                }
            });
        });
    }

    @Test
    public void testCreateAndDeleteSections() throws IOException, ParseException {
        var ini = INI.create();

        var sec1 = ini.create("Section1");
        assertTrue(ini.containsSection("Section1"));
        assertEquals(1, ini.sections().size());
        assertEquals("Section1", sec1.key());
        assertEquals("Section1", ini.section("Section1").key());
        assertEquals(ini, sec1.document());
        assertThrows(IllegalStateException.class, () -> sec1.parent());

        var sec2 = sec1.create("Section2");
        assertTrue(sec1.containsSection("Section2"));
        assertEquals(1, ini.sections().size());
        assertEquals(1, sec1.sections().size());
        assertEquals("Section2", sec2.key());
        assertEquals("Section2", sec1.section("Section2").key());

        var sec3 = ini.create("Section1", "Section2", "Section3");
        assertTrue(ini.containsSection("Section1"));
        assertEquals(1, ini.sections().size());
        assertEquals(1, sec1.sections().size());
        assertEquals("Section3", sec3.key());
        assertEquals("Section3", sec2.section("Section3").key());
        var sec3a = sec2.create("Section3a");
        assertEquals(ini, sec3a.document());
        assertEquals(2, sec2.sections().size());

        var sec4 = ini.create("Section1a", "Section2", "Section4");
        assertEquals(2, ini.sections().size());
        assertEquals(2, sec2.sections().size());
        assertEquals("Section4", sec4.key());

        assertEquals(sec4, ini.section("Section1a", "Section2", "Section4"));
        assertArrayEquals(new Section[] { ini.section("Section1a", "Section2"), ini.section("Section1a") },
                sec4.parents());
        assertArrayEquals(new String[] { "Section1a", "Section2", "Section4" }, sec4.path());

        sec4.remove();
        var sec4sec2 = ini.section("Section1a", "Section2");
        assertEquals(0, sec4sec2.sections().size());
        sec4sec2.remove();
        var sec4sec1a = ini.section("Section1a");
        assertEquals(0, sec4sec2.sections().size());
        sec4sec1a.remove();
        assertEquals(1, ini.sections().size());
    }

    @Test
    public void testAllSections() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.APPEND).build().read(
                "[Section1]\n" +
                "S1aK1 = V1\n" +
                "[Section1]\n" +
                "S1bK1 = V2\n" +
                "[Section1]\n" +
                "S1cK1 = V3\n");
        assertEquals(Arrays.asList("Section1", "Section1", "Section1"),
                Arrays.asList(ini.allSections("Section1")).stream().map(s -> s.key()).collect(Collectors.toList()));
        assertTrue(ini.sectionOr("SectionX").isEmpty());
        assertTrue(ini.allSectionsOr("SectionX").isEmpty());
    }

    @Test
    public void testPutTypes() {
        var ini = INI.create();
        ini.put("A_String", "HelloWorld!");
        ini.put("A_Boolean", true);
        ini.put("An_Integer", 12);
        ini.put("A_Float", 456.789f);
        ini.put("A_Long", 12345678901234l);
        ini.put("A_Double", 12345678901234.12345678901234);
        ini.put("A_Short", (short) 345);
        ini.putAll("A_Boolean_ARRAY", true, false, true, false);
        ini.putAll("An_Integer_ARRAY", 1, 2, 3, 4, 5);
        ini.putAll("A_Float_ARRAY", 111.222f, 777.333f, 898.676f);
        ini.putAll("A_Long_ARRAY", 345457856321234l, 923234568524587l, 675621354569053l, 679452356886031l);
        ini.putAll("A_Double_ARRAY", 34436346234235.2323423423525, 789784563235345.589344583458,
                91298234782345.13264136);
        ini.putAll("A_Short_ARRAY", (short) 789, (short) 2121, (short) 343, (short) 4346, (short) 5111);
        ini.putAll("A_String_ARRAY", "ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "abcdefghijklm", "nopqrstuvwxyz");
        assertTypes(ini);

        /* For coverage */
        ini.putBoolean("A_Boolean_ARRAY", Arrays.asList(true, false, true, false));
        ini.putInt("An_Integer_ARRAY", Arrays.asList(1, 2, 3, 4, 5));
        ini.putFloat("A_Float_ARRAY", Arrays.asList(111.222f, 777.333f, 898.676f));
        ini.putLong("A_Long_ARRAY",
                Arrays.asList(345457856321234l, 923234568524587l, 675621354569053l, 679452356886031l));
        ini.putDouble("A_Double_ARRAY",
                Arrays.asList(34436346234235.2323423423525, 789784563235345.589344583458, 91298234782345.13264136));
        ini.putShort("A_Short_ARRAY",
                Arrays.asList((short) 789, (short) 2121, (short) 343, (short) 4346, (short) 5111));
        ini.put("A_String_ARRAY", Arrays.asList("ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "abcdefghijklm", "nopqrstuvwxyz"));
        assertTypes(ini);
    }

    @Test
    public void testGetTypes() throws IOException, ParseException {
        var ini = new INIReader.Builder().withoutNestedSections().withDuplicateKeysAction(DuplicateAction.APPEND)
                .build().read(
                        "A_String = HelloWorld!\n" + 
                        "A_Boolean = TRUE\n" + 
                        "An_Integer = 12\n" + 
                        "A_Float = 456.789\n" + 
                        "A_Long = 12345678901234\n" + 
                        "A_Double = 12345678901234.12345678901234\n" + 
                        "A_Short = 345\n" + 
                        "A_Boolean_ARRAY = TRUE\n" + 
                        "A_Boolean_ARRAY = false\n" + 
                        "A_Boolean_ARRAY = True\n" + 
                        "A_Boolean_ARRAY = FALSE\n" + 
                        "An_Integer_ARRAY = 1\n" + 
                        "An_Integer_ARRAY = 2\n" + 
                        "An_Integer_ARRAY = 3\n" + 
                        "An_Integer_ARRAY = 4\n" + 
                        "An_Integer_ARRAY = 5\n" + 
                        "A_Float_ARRAY = 111.222\n" + 
                        "A_Float_ARRAY = 777.333\n" + 
                        "A_Float_ARRAY = 898.676\n" + 
                        "A_Long_ARRAY = 345457856321234\n" + 
                        "A_Long_ARRAY = 923234568524587\n" + 
                        "A_Long_ARRAY = 675621354569053\n" + 
                        "A_Long_ARRAY = 679452356886031\n" + 
                        "A_Double_ARRAY = 34436346234235.2323423423525\n" + 
                        "A_Double_ARRAY = 789784563235345.589344583458\n" + 
                        "A_Double_ARRAY = 91298234782345.13264136\n" + 
                        "A_Short_ARRAY = 789\n" + 
                        "A_Short_ARRAY = 2121\n" + 
                        "A_Short_ARRAY = 343\n" + 
                        "A_Short_ARRAY = 4346\n" + 
                        "A_Short_ARRAY = 5111\n" + 
                        "A_String_ARRAY = ABCDEFGHIJKLM\n" + 
                        "A_String_ARRAY = NOPQRSTUVWXYZ\n" + 
                        "A_String_ARRAY = abcdefghijklm\n" +
                        "A_String_ARRAY = nopqrstuvwxyz\n");

        assertTypes(ini);

    }

    protected void doTestCaseSensitive(INI ini) {
        ini.put("Key1", "Val1");
        ini.put("Key2", "Val2");
        ini.put("Key3", "Val3");
        ini.create("Section1");
        assertFalse(ini.contains("key1"));
        assertFalse(ini.contains("key2"));
        assertFalse(ini.contains("key3"));
        assertFalse(ini.containsSection("section1"));
        assertTrue(ini.contains("Key1"));
        assertTrue(ini.contains("Key2"));
        assertTrue(ini.contains("Key3"));
        assertTrue(ini.containsSection("Section1"));
        assertTrue(ini.getOr("key1").isEmpty());
        assertTrue(ini.getOr("key2").isEmpty());
        assertTrue(ini.getOr("key3").isEmpty());
        assertEquals("Val1", ini.get("Key1"));
        assertEquals("Val2", ini.get("Key2"));
        assertEquals("Val3", ini.get("Key3"));
    }

    protected void assertTypes(INI ini) {
        assertTrue(ini.contains("A_String"));
        assertEquals("HelloWorld!", ini.get("A_String"));
        assertTrue(ini.getOr("A_Missing_String").isEmpty());
        assertEquals("ZZZZZ", ini.get("A_Missing_String", "ZZZZZ"));

        assertTrue(ini.contains("A_Boolean"));
        assertEquals(true, ini.getBoolean("A_Boolean"));
        assertTrue(ini.getBooleanOr("A_Missing_Boolean").isEmpty());
        assertFalse(ini.getBoolean("A_Missing_Boolean", false));

        assertTrue(ini.contains("An_Integer"));
        assertEquals(12, ini.getInt("An_Integer"));
        assertTrue(ini.getIntOr("A_Missing_Integer").isEmpty());
        assertEquals(456, ini.getInt("A_Missing_Integer", 456));

        assertTrue(ini.contains("A_Float"));
        assertEquals(456.789f, ini.getFloat("A_Float"));
        assertTrue(ini.getFloatOr("A_Missing_Float").isEmpty());
        assertEquals(123.987f, ini.getFloat("A_Missing_Float", 123.987f));

        assertTrue(ini.contains("A_Long"));
        assertEquals(12345678901234l, ini.getLong("A_Long"));
        assertTrue(ini.getLongOr("A_Missing_Long").isEmpty());
        assertEquals(7895634635223l, ini.getLong("A_Missing_Long", 7895634635223l));

        assertTrue(ini.contains("A_Double"));
        assertEquals(12345678901234.12345678901234, ini.getDouble("A_Double"));
        assertTrue(ini.getDoubleOr("A_Missing_Double").isEmpty());
        assertEquals(67894562342134.123112342345d, ini.getDouble("A_Missing_Double", 67894562342134.123112342345d));

        assertTrue(ini.contains("A_Short"));
        assertEquals(345, ini.getShort("A_Short"));
        assertTrue(ini.getShortOr("A_Missing_Short").isEmpty());
        assertEquals(111, ini.getShort("A_Missing_Short", (short) 111));

        assertTrue(ini.contains("A_Boolean_ARRAY"));
        assertArrayEquals(new boolean[] { true, false, true, false }, ini.getAllBoolean("A_Boolean_ARRAY"));
        assertTrue(ini.getAllBooleanOr("A_Missing_Boolean_ARRAY").isEmpty());
        assertArrayEquals(new boolean[] { false, true },
                ini.getAllBooleanElse("A_Missing_Boolean_ARRAY", new boolean[] { false, true }));

        assertTrue(ini.contains("An_Integer_ARRAY"));
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5 }, ini.getAllInt("An_Integer_ARRAY"));
        assertTrue(ini.getAllIntOr("A_Missing_Integer_ARRAY").isEmpty());
        assertArrayEquals(new int[] { 9, 8, 7 }, ini.getAllIntElse("A_Missing_Integer_ARRAY", new int[] { 9, 8, 7 }));

        assertTrue(ini.contains("A_Float_ARRAY"));
        assertArrayEquals(new float[] { 111.222f, 777.333f, 898.676f }, ini.getAllFloat("A_Float_ARRAY"));
        assertTrue(ini.getAllFloatOr("A_Missing_Float_ARRAY").isEmpty());
        assertArrayEquals(new float[] { 9.234f, 8.6543f, 7.2198f },
                ini.getAllFloatElse("A_Missing_Float_ARRAY", new float[] { 9.234f, 8.6543f, 7.2198f }));

        assertTrue(ini.contains("A_Long_ARRAY"));
        assertArrayEquals(new long[] { 345457856321234l, 923234568524587l, 675621354569053l, 679452356886031l },
                ini.getAllLong("A_Long_ARRAY"));
        assertTrue(ini.getAllIntOr("A_Missing_Long_ARRAY").isEmpty());
        assertArrayEquals(new long[] { 23346457352453649l, 3434457923678l, 22345680047l }, ini
                .getAllLongElse("A_Missing_Long_ARRAY", new long[] { 23346457352453649l, 3434457923678l, 22345680047l }));

        assertTrue(ini.contains("A_Double_ARRAY"));
        assertArrayEquals(
                new double[] { 34436346234235.2323423423525, 789784563235345.589344583458, 91298234782345.13264136 },
                ini.getAllDouble("A_Double_ARRAY"));
        assertTrue(ini.getAllDoubleOr("A_Missing_Double_ARRAY").isEmpty());
        assertArrayEquals(new double[] { 5678346345.344563462345, 23237233467.232323523476 }, ini.getAllDoubleElse(
                "A_Missing_Double_ARRAY", new double[] { 5678346345.344563462345, 23237233467.232323523476 }));

        assertTrue(ini.contains("A_Short_ARRAY"));
        assertArrayEquals(new short[] { 789, 2121, 343, 4346, 5111 }, ini.getAllShort("A_Short_ARRAY"));
        assertTrue(ini.getAllShortOr("A_Missing_Short_ARRAY").isEmpty());
        assertArrayEquals(new short[] { 912, 87, 333 },
                ini.getAllShortElse("A_Missing_Short_ARRAY", new short[] { 912, 87, 333 }));

        assertTrue(ini.contains("A_String_ARRAY"));
        assertArrayEquals(new String[] { "ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "abcdefghijklm", "nopqrstuvwxyz" },
                ini.getAll("A_String_ARRAY"));
        assertTrue(ini.getAllOr("A_Missing_String_ARRAY").isEmpty());
        assertArrayEquals(new String[] { "zz", "xx" },
                ini.getAllElse("A_Missing_String_ARRAY", new String[] { "zz", "xx" }));
    }

    static void assertBasicCaseSensitive(INI ini) {
        assertFalse(ini.sections().containsKey("section1"));
        var sec1 = ini.section("Section1");
        assertFalse(sec1.values().containsKey("key1"));
        assertFalse(sec1.values().containsKey("key2"));
        assertFalse(sec1.values().containsKey("key 3"));
        assertFalse(sec1.values().containsKey("key4"));
    }

    static void assertBasicInsensitive(INI ini) {
        var sec1 = ini.section("Section1");
        assertEquals("Value1", sec1.get("key1"));
        assertEquals("Value2", sec1.get("key 2"));
        assertEquals("Value 3", sec1.get("key 3"));
        assertEquals("Value 4", sec1.get("key4"));
        assertTrue(ini.sections().containsKey("section1"));
    }

    static void assertBasic(INI ini) {
        assertEquals(6, ini.values().size());
        assertEquals("RootVal1", ini.get("Root 1"));
        assertEquals("RootVal2", ini.get("Root2"));
        assertEquals("Root Val 3", ini.get("Root 3"));
        assertEquals("Root Val 4", ini.get("Root 4"));
        assertEquals("Root Val 5", ini.get("Root 5"));
        assertEquals("Root,Val,6", ini.get("Root 6"));
        assertEquals(3, ini.sections().size());
        assertTrue(ini.sections().containsKey("Section1"));
        assertTrue(ini.sections().containsKey("Section2"));
        assertTrue(ini.sections().containsKey("Section3"));
        var sec1 = ini.section("Section1");
        assertEquals("Value1", sec1.get("Key1"));
        assertEquals("Value2", sec1.get("Key 2"));
        assertEquals("Value 3", sec1.get("Key 3"));
        assertEquals("Value 4", sec1.get("Key4"));
        assertEquals("Value,5", sec1.get("Key5"));
        var sec2 = ini.section("Section2");
        assertEquals("Value1-2", sec2.get("Key1-2"));
        assertEquals("Value2-2", sec2.get("Key 2-2"));
        assertEquals("Value 3-2", sec2.get("Key 3-2"));
        assertEquals("Value 4-2", sec2.get("Key4-2"));
        var sec3 = ini.section("Section3");
        assertEquals("Value1-3", sec3.get("Key1-3"));
        assertEquals("Value2-3", sec3.get("Key 2-3"));
        assertEquals("Value 3-3", sec3.get("Key 3-3"));
        assertEquals("Value 4-3", sec3.get("Key4-3"));
    }

    static String getBadIni() {
        return "\nKey1 = Val 1\n[Sec1\n";
    }

    static String getVariablesIni() {
        return "; Some Comment\n" +
               "Path 1 = ${env:HOME}\n" +
               "User 1 = ${sys:user.name}\n" +
               "Key 1 = Val 1\n" +
               "Error 1 = ${xxxxx}\n" +
               "\n" +
               "; Another Comment\n" +
               "[Section1]\n" +
               "Path 1 = ${env:HOME}\n" +
               "User 1 = ${sys:user.name}\n" +
               "Key 1 = Val 1\n" +
               "Error 1 = ${xxxxx}\n" +
               "\n";
    }

    static String getWinVariablesIni() {
        return "; Some Comment\n" +
               "Path 1 = %env:HOME%\n" +
               "User 1 = %sys:user.name%\n" +
               "Key 1 = Val 1\n" +
               "Error 1 = %xxxxx%\n" +
               "\n" +
               "; Another Comment\n" +
               "[Section1]\n" +
               "Path 1 = %env:HOME%\n" +
               "User 1 = %sys:user.name%\n" +
               "Key 1 = Val 1\n" +
               "Error 1 = %xxxxx%\n" +
               "\n";
    }


    static String getBasicIni() {
        return "; Some Comment\n" +
               "Root 1 = RootVal1\n" +
               "Root2 = RootVal2\n" +
               "Root 3 = Root Val 3\n" +
               "Root 4 = 'Root Val 4'\n" +
               "Root 5 = \"Root Val 5\"\n" +
               "Root 6 = 'Root,Val,6'\n" +
               "\n" +
               "; Another Comment\n" +
               "[Section1]\n" +
               "Key1 = Value1\n" +
               "Key 2 = Value2\n" +
               "Key 3 = 'Value 3'\n" +
               "Key4=\"Value 4\"\n" +
               "Key5=\"Value,5\"\n" +
               "\n" +
               "; Yet Another Comment\n" +
               "[Section2]\n" +
               "Key1-2 = Value1-2\n" +
               "Key 2-2 = Value2-2\n" +
               "Key 3-2 = 'Value 3-2'\n" +
               "Key4-2=\"Value 4-2\"\n" +
               "\n" +
               "; Yet Another Comment\n" +
               "[Section3]\n" +
               "Key1-3 = Value1-3\n" +
               "Key 2-3 = Value2-3\n" +
               "Key 3-3 = 'Value 3-3'\n" +
               "Key4-3=\"Value 4-3\"\n";
    }

	static void assertVariables(INI ini) {
		assertEquals(4, ini.size());
        assertEquals("Val 1", ini.get("Key 1"));
        assertEquals(System.getenv("HOME"), ini.get("Path 1"));
        assertEquals(System.getProperty("user.name"), ini.get("User 1"));
        var sec = ini.section("Section1");
        assertEquals(4, sec.size());
        assertEquals("Val 1", sec.get("Key 1"));
        assertEquals(System.getenv("HOME"), sec.get("Path 1"));
        assertEquals(System.getProperty("user.name"), sec.get("User 1"));
	}

    static void assertBasicOrder(INI ini) {
        assertEquals("Root 1,Root2,Root 3,Root 4,Root 5,Root 6",
                String.join(",", ini.values().keySet().toArray(new String[0])));
        assertEquals("Key1,Key 2,Key 3,Key4,Key5",
                String.join(",", ini.section("Section1").values().keySet().toArray(new String[0])));
        assertEquals("Key1-2,Key 2-2,Key 3-2,Key4-2",
                String.join(",", ini.section("Section2").values().keySet().toArray(new String[0])));
        assertEquals("Key1-3,Key 2-3,Key 3-3,Key4-3",
                String.join(",", ini.section("Section3").values().keySet().toArray(new String[0])));
        assertEquals("Section1,Section2,Section3", String.join(",", ini.sections().keySet().toArray(new String[0])));
    }
}
