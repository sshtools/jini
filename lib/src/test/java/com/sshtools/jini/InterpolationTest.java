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
    	
    	var homeVar = getHomeVar();
    	
    	Assertions.assertEquals(
			"This text, XX, should be replaced".
				replace("XX", System.getenv(homeVar)), 
			Interpolation.str(null, "This text, ${env:" + homeVar + "}, should be replaced", 
					environment()));
    }

	private String getHomeVar() {
		/* Windows doesn't have HOME envar, but does have a HOMEPATH */
    	var home = System.getenv("HOME");
    	var homeVar = "HOME";
    	if(home == null) {
        	home = System.getenv("HOMEPATH");
        	homeVar = "HOMEPATH";
        	if(home == null)
        		throw new IllegalStateException("No suitable envvar for test.");
    	}
		return homeVar;
	}
    
    @Test
    public void testInterpolateCompound() throws Exception {
    	Assertions.assertEquals(
			"This text, XX, should be replaced, as should YY".
				replace("XX", System.getenv(getHomeVar())).
				replace("YY", System.getProperty("user.name")), 
			Interpolation.str(null, "This text, ${env:" + getHomeVar() + "}, should be replaced, as should ${sys:user.name}",
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
