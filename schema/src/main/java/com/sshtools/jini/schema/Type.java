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

public enum Type {
	SECTION, ENUM, BOOLEAN, TEXT, NUMBER, LIST;

	public Discriminator discriminator(String discriminator) {
		switch(this) {
		case TEXT:
			return TextDiscriminator.valueOf(discriminator);
		case NUMBER:
			return NumberDiscriminator.valueOf(discriminator);
		default:
			throw new UnsupportedOperationException("The type '" + name() + "' does not support the discriminator '" + discriminator + "'");
		}
	}
}