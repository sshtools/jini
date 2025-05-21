package com.sshtools.jini.schema;

public final class Multiplicity {
	
	public static final Multiplicity NO_MORE_THAN_ONE = new Multiplicity(0, 1);
	public static final Multiplicity ONE = new Multiplicity(1, 1);
	public static final Multiplicity AT_LEAST_ONE = new Multiplicity(1, Integer.MAX_VALUE);
	public static final Multiplicity ANY = new Multiplicity(0, Integer.MAX_VALUE);
	
	private final int min;
	private final int max;
	
	public static Multiplicity parse(String str) {
		try {
			var els = str.split("\\.\\.");
			
			if(els.length == 1) {
				if(els[0].equals("ANY")) 
					return ANY;
				else if(els[0].equals("ONE")) 
					return ONE;
				else if(els[0].equals("NO_MORE_THAN_ONE")) 
					return NO_MORE_THAN_ONE;
				else if(els[0].equals("AT_LEAST_ONE")) 
					return AT_LEAST_ONE;
				else if(els[0].equals(""))
					throw new NumberFormatException();
			}
			
			var v = els[0].equals("") ? 0 : Integer.parseInt(els[0]);
			if(els.length == 1) {
				if(els[0].length() == str.length())
					return new Multiplicity(v, v);
				else
					return new Multiplicity(v, Integer.MAX_VALUE);
			}
			else if(els.length == 2) {
				return new Multiplicity(v, Integer.parseInt(els[1]));
			}
			else
				throw new NumberFormatException();
		}
		catch(NumberFormatException nfe) {
			throw new IllegalArgumentException("Invalid format for arity. [<min>]..[<max>]");
		}
	}
	
	private Multiplicity(int min, int max) {
		super();
		this.min = min;
		this.max = max;
		if(min > max) {
			throw new IllegalArgumentException("Min may not be more than max.");
		}
	}
	
	public int min() {
		return min;
	}
	
	public int max() {
		return max;
	}
	
	public boolean validate(int items) {
		return items >= min && items <= max;
	}

	@Override
	public String toString() {
		if(min == max)
			return String.valueOf(min);
		else
			return ( min == 0 ? "" : min ) + ".." + ( max == Integer.MAX_VALUE ? "" : max );
	}

	public boolean required() {
		return min > 0;
	}

	public boolean multiple() {
		return max > 1;
	}

	public boolean once() {
		return max == min && max == 1;
	}
}