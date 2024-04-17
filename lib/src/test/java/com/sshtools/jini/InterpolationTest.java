package com.sshtools.jini;

import static com.sshtools.jini.Interpolation.compound;
import static com.sshtools.jini.Interpolation.environment;
import static com.sshtools.jini.Interpolation.systemProperties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class InterpolationTest {
	
	private InterpolationTest() {
	}
	
    @Test
    public void testInterpolateSystemProperties() throws Exception {
    	Assertions.assertEquals(
			"This text, XX, should be replaced, as should YY".
				replace("XX", System.getProperty("user.home")).
				replace("YY", System.getProperty("user.name")), 
			Interpolation.str(null, "This text, ${sys:user.home}, should be replaced, as should ${sys:user.name}", 
					systemProperties()));
    }
    
    @Test
    public void testInterpolateEnv() throws Exception {
    	Assertions.assertEquals(
			"This text, XX, should be replaced".
				replace("XX", System.getenv("HOME")), 
			Interpolation.str(null, "This text, ${env:HOME}, should be replaced", 
					environment()));
    }
    
    @Test
    public void testInterpolateCompound() throws Exception {
    	Assertions.assertEquals(
			"This text, XX, should be replaced, as should YY".
				replace("XX", System.getenv("HOME")).
				replace("YY", System.getProperty("user.name")), 
			Interpolation.str(null, "This text, ${env:HOME}, should be replaced, as should ${sys:user.name}",
					compound(
						systemProperties(),
						environment()
					)));
    }
    
    @Test
    public void testInterpolateMissingThrow() throws Exception {
    	Assertions.assertThrows(IllegalArgumentException.class, () ->  Interpolation.str(null, "Fail ${XXXXX} Tail",
			Interpolation.defaults()));
    }
    
    @Test
    public void testInterpolateMissingSkip() throws Exception {
    	Assertions.assertEquals("Fail ${XXXXX} Tail", Interpolation.str(null, "Fail ${XXXXX} Tail",
			compound(
				systemProperties(),
				environment()
			)));
    }
}
