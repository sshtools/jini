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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sshtools.jini.serialization.INISerialization.INIField;
import com.sshtools.jini.serialization.INISerializer;

public class INISerializerTest extends AbstractSerializerTest  {
	
	public final static class ReferenceTest {
		@INIField(reference = "obj123")
		private ReferenceTest referred;

		private ReferenceTest referred2;
	}
	
	public final static class ReferredTest {
		private final String otherData;
		
		ReferredTest(String otherData) {
			this.otherData = otherData;
		}
	}
	
	private final static ReferredTest REFERRED = new ReferredTest("Some data");
	private final static ReferredTest REFERRED_2 = new ReferredTest("Some other data");
	

	@Test
	public void testBasics() {
		assertEquals(INI_TEXT, INISerializer.toINI(createPerson()).asString());
	}
	
	@Test
	public void testReference() {
		assertEquals(INI_TEXT, INISerializer.toINI(createPerson()).asString());
	}
}
