package com.sshtools.jini.schema;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TypeTest {

	@Test
	public void testTextDiscrim() {
		assertTrue(Type.TEXT.discriminator("COLOR").equals(TextDiscriminator.COLOR));
	}

	@Test
	public void testNumberDiscrim() {
		assertTrue(Type.NUMBER.discriminator("DOUBLE").equals(NumberDiscriminator.DOUBLE));
	}

	@Test
	public void testBadDiscrim() {
		assertThrows(UnsupportedOperationException.class, () -> Type.BOOLEAN.discriminator("BLERGH"));
	}
	
}
