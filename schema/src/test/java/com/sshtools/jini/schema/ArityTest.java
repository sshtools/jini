package com.sshtools.jini.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ArityTest {

	@Test
	public void testParseAny() {
		var a1 = Arity.parse("ANY");
		assertEquals(0, a1.min());
		assertEquals(Integer.MAX_VALUE, a1.max());
		assertTrue(a1.validate(0));
		assertTrue(a1.validate(Integer.MAX_VALUE));
	}

	@Test
	public void testParseOne() {
		var a1 = Arity.parse("ONE");
		assertEquals(1, a1.min());
		assertEquals(1, a1.max());
		assertTrue(a1.validate(1));
		assertFalse(a1.validate(0));
	}

	@Test
	public void testParseAtLeastOne() {
		var a1 = Arity.parse("AT_LEAST_ONE");
		assertEquals(1, a1.min());
		assertEquals(Integer.MAX_VALUE, a1.max());
		assertTrue(a1.validate(1));
		assertTrue(a1.validate(2));
		assertFalse(a1.validate(0));
	}

	@Test
	public void testParseNoMoreThanOne() {
		var a1 = Arity.parse("NO_MORE_THAN_ONE");
		assertEquals(0, a1.min());
		assertEquals(1, a1.max());
		assertTrue(a1.validate(1));
		assertTrue(a1.validate(0));
		assertFalse(a1.validate(2));
	}

	@Test
	public void testExactly() {
		var a1 = Arity.parse("99");
		assertEquals(99, a1.min());
		assertEquals(99, a1.max());
		assertTrue(a1.validate(99));
		assertFalse(a1.validate(98));
		assertFalse(a1.validate(100));
	}

	@Test
	public void testFromFixedNumberToIndefinite() {
		var a1 = Arity.parse("51..");
		assertEquals(51, a1.min());
		assertEquals(Integer.MAX_VALUE, a1.max());
		assertTrue(a1.validate(51));
		assertTrue(a1.validate(52));
		assertTrue(a1.validate(Integer.MAX_VALUE));
		assertFalse(a1.validate(2));
	}

	@Test
	public void testFromZeroToFixedNumber() {
		var a1 = Arity.parse("..43");
		assertEquals(0, a1.min());
		assertEquals(43, a1.max());
		assertTrue(a1.validate(43));
		assertTrue(a1.validate(0));
		assertFalse(a1.validate(Integer.MAX_VALUE));
		assertFalse(a1.validate(44));
	}

	@Test
	public void testFromFixedNumberToFixedNumber() {
		var a1 = Arity.parse("17..21");
		assertEquals(17, a1.min());
		assertEquals(21, a1.max());
		assertTrue(a1.validate(17));
		assertTrue(a1.validate(21));
		assertFalse(a1.validate(Integer.MAX_VALUE));
		assertFalse(a1.validate(22));
		assertFalse(a1.validate(16));
		assertFalse(a1.validate(0));
	}

	@Test
	public void testBadKeyword() {
		assertThrows(IllegalArgumentException.class, () -> Arity.parse("PANTS"));
	}

	@Test
	public void testEmptyKeyword() {
		assertThrows(IllegalArgumentException.class, () -> Arity.parse(""));
	}

	@Test
	public void testInvalidRange() {
		assertThrows(IllegalArgumentException.class, () -> Arity.parse("1..2..3"));
	}

	@Test
	public void testToString() {
		assertEquals("1", Arity.ONE.toString());
		assertEquals("..", Arity.ANY.toString());
		assertEquals("1..", Arity.AT_LEAST_ONE.toString());
		assertEquals("..1", Arity.NO_MORE_THAN_ONE.toString());
		assertEquals("123..456", Arity.parse("123..456").toString());
	}
}