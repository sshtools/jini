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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sshtools.jini.INIReader.DuplicateAction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

public class INITest {
    
    @Test
    public void testAllSections() throws IOException, ParseException {
        var ini = new INIReader.Builder().withDuplicateSectionAction(DuplicateAction.APPEND)
                .build().read("""
                        [Section1]
                        S1aK1 = V1
                        [Section1]
                        S1bK1 = V2
                        [Section1]
                        S1cK1 = V3
                        """);
         assertEquals(Arrays.asList("Section1", "Section1", "Section1"), Arrays.asList(ini.allSections("Section1")).stream().map(s -> s.key()).toList());
         assertTrue(ini.sectionOr("SectionX").isEmpty());
         assertTrue(ini.allSectionsOr("SectionX").isEmpty());
    }

    @Test
    public void testTypes() throws IOException, ParseException {
        var ini = new INIReader.Builder().withoutNestedSections().withDuplicateKeysAction(DuplicateAction.APPEND)
                .build().read("""
                        A_String = HelloWorld!                        
                        A_Boolean = TRUE
                        An_Integer = 12
                        A_Float = 456.789
                        A_Long = 12345678901234
                        A_Double = 12345678901234.12345678901234
                        A_Short = 345
                        A_Boolean_ARRAY = TRUE
                        A_Boolean_ARRAY = false
                        A_Boolean_ARRAY = True
                        A_Boolean_ARRAY = FALSE
                        An_Integer_ARRAY = 1
                        An_Integer_ARRAY = 2
                        An_Integer_ARRAY = 3
                        An_Integer_ARRAY = 4
                        An_Integer_ARRAY = 5
                        A_Float_ARRAY = 111.222
                        A_Float_ARRAY = 777.333
                        A_Float_ARRAY = 898.676
                        A_Long_ARRAY = 345457856321234
                        A_Long_ARRAY = 923234568524587
                        A_Long_ARRAY = 675621354569053
                        A_Long_ARRAY = 679452356886031
                        A_Double_ARRAY = 34436346234235.2323423423525
                        A_Double_ARRAY = 789784563235345.589344583458
                        A_Double_ARRAY = 91298234782345.13264136
                        A_Short_ARRAY = 789
                        A_Short_ARRAY = 2121
                        A_Short_ARRAY = 343
                        A_Short_ARRAY = 4346
                        A_Short_ARRAY = 5111
                        A_String_ARRAY = ABCDEFGHIJKLM
                        A_String_ARRAY = NOPQRSTUVWXYZ
                        A_String_ARRAY = abcdefghijklm
                        A_String_ARRAY = nopqrstuvwxyz
                        """);

        assertEquals("HelloWorld!", ini.get("A_String"));
        assertTrue(ini.getOr("A_Missing_String").isEmpty());
        assertEquals("ZZZZZ", ini.getOr("A_Missing_String", "ZZZZZ"));

        assertEquals(true, ini.getBoolean("A_Boolean"));
        assertTrue(ini.getBooleanOr("A_Missing_Boolean").isEmpty());
        assertFalse(ini.getBooleanOr("A_Missing_Boolean", false));

        assertEquals(12, ini.getInt("An_Integer"));
        assertTrue(ini.getIntOr("A_Missing_Integer").isEmpty());
        assertEquals(456, ini.getIntOr("A_Missing_Integer", 456));

        assertEquals(456.789f, ini.getFloat("A_Float"));
        assertTrue(ini.getFloatOr("A_Missing_Float").isEmpty());
        assertEquals(123.987f, ini.getFloatOr("A_Missing_Float", 123.987f));

        assertEquals(12345678901234l, ini.getLong("A_Long"));
        assertTrue(ini.getLongOr("A_Missing_Long").isEmpty());
        assertEquals(7895634635223l, ini.getLongOr("A_Missing_Long", 7895634635223l));

        assertEquals(12345678901234.12345678901234, ini.getDouble("A_Double"));
        assertTrue(ini.getDoubleOr("A_Missing_Double").isEmpty());
        assertEquals(67894562342134.123112342345d, ini.getDoubleOr("A_Missing_Double", 67894562342134.123112342345d));

        assertEquals(345, ini.getShort("A_Short"));
        assertTrue(ini.getShortOr("A_Missing_Short").isEmpty());
        assertEquals(111, ini.getShortOr("A_Missing_Short", (short) 111));

        assertArrayEquals(new boolean[] { true, false, true, false }, ini.getAllBoolean("A_Boolean_ARRAY"));
        assertTrue(ini.getAllBooleanOr("A_Missing_Boolean_ARRAY").isEmpty());
        assertArrayEquals(new boolean[] { false, true },
                ini.getAllBooleanOr("A_Missing_Boolean_ARRAY", new boolean[] { false, true }));

        assertArrayEquals(new int[] { 1, 2, 3, 4, 5 }, ini.getAllInt("An_Integer_ARRAY"));
        assertTrue(ini.getAllIntOr("A_Missing_Integer_ARRAY").isEmpty());
        assertArrayEquals(new int[] { 9, 8, 7 }, ini.getAllIntOr("A_Missing_Integer_ARRAY", new int[] { 9, 8, 7 }));

        assertArrayEquals(new float[] { 111.222f, 777.333f, 898.676f }, ini.getAllFloat("A_Float_ARRAY"));
        assertTrue(ini.getAllFloatOr("A_Missing_Float_ARRAY").isEmpty());
        assertArrayEquals(new float[] { 9.234f, 8.6543f, 7.2198f },
                ini.getAllFloatOr("A_Missing_Float_ARRAY", new float[] { 9.234f, 8.6543f, 7.2198f }));

        assertArrayEquals(new long[] { 345457856321234l, 923234568524587l, 675621354569053l, 679452356886031l },
                ini.getAllLong("A_Long_ARRAY"));
        assertTrue(ini.getAllIntOr("A_Missing_Long_ARRAY").isEmpty());
        assertArrayEquals(new long[] { 23346457352453649l, 3434457923678l, 22345680047l }, ini
                .getAllLongOr("A_Missing_Long_ARRAY", new long[] { 23346457352453649l, 3434457923678l, 22345680047l }));

        assertArrayEquals(
                new double[] { 34436346234235.2323423423525, 789784563235345.589344583458, 91298234782345.13264136 },
                ini.getAllDouble("A_Double_ARRAY"));
        assertTrue(ini.getAllDoubleOr("A_Missing_Double_ARRAY").isEmpty());
        assertArrayEquals(new double[] { 5678346345.344563462345, 23237233467.232323523476 }, ini.getAllDoubleOr(
                "A_Missing_Double_ARRAY", new double[] { 5678346345.344563462345, 23237233467.232323523476 }));

        assertArrayEquals(new short[] { 789, 2121, 343, 4346, 5111 }, ini.getAllShort("A_Short_ARRAY"));
        assertTrue(ini.getAllShortOr("A_Missing_Short_ARRAY").isEmpty());
        assertArrayEquals(new short[] { 912, 87, 333 },
                ini.getAllShortOr("A_Missing_Short_ARRAY", new short[] { 912, 87, 333 }));

        assertArrayEquals(new String[] { "ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "abcdefghijklm", "nopqrstuvwxyz" },
                ini.getAll("A_String_ARRAY"));
        assertTrue(ini.getAllOr("A_Missing_String_ARRAY").isEmpty());
        assertArrayEquals(new String[] { "zz", "xx" },
                ini.getAllOr("A_Missing_String_ARRAY", new String[] { "zz", "xx" }));

    }
}
