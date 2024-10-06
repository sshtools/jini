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
