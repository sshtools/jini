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