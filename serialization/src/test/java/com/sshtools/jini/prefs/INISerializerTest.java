package com.sshtools.jini.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.serialization.INISerializer;

public class INISerializerTest extends AbstractSerializerTest  {

	@Test
	public void testBasics() {
		assertEquals(INI_TEXT, INISerializer.toINI(createPerson()).asString());
	}
}
