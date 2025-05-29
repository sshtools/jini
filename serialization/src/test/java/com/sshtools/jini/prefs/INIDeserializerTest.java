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
package com.sshtools.jini.prefs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.serialization.INIDeserializer;

public class INIDeserializerTest extends AbstractSerializerTest  {

	@Test
	public void testBasics() {
		var m = INIDeserializer.fromString(INI_TEXT, Person.class);
		var p = createPerson();
		
		/* Test each individually (help debug if goes wrong) */
		assertEquals(p.address, m.address);
		assertEquals(p.age, m.age);
		assertEquals(p.favouriteColour, m.favouriteColour);
		assertEquals(p.items, m.items);
		assertEquals(p.name, m.name);
		assertEquals(p.payload, m.payload);
		assertEquals(p.props, m.props);
		assertEquals(p.sendSpam, m.sendSpam);
		assertArrayEquals(p.signature, m.signature);
		assertArrayEquals(p.telephones, m.telephones);
		
		/* And the overall object */
		assertEquals(p, m);
	}
}
