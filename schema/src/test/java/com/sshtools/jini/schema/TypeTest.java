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
