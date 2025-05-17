package com.sshtools.jini.schema;

public final class Arity {
	
	public static final Arity NO_MORE_THAN_ONE = new Arity(0, 1);
	public static final Arity ONE = new Arity(1, 1);
	public static final Arity AT_LEAST_ONE = new Arity(1, Integer.MAX_VALUE);
	public static final Arity ANY = new Arity(0, Integer.MAX_VALUE);
	
	private final int min;
	private final int max;
	
	public static Arity parse(String str) {
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
					return new Arity(v, v);
				else
					return new Arity(v, Integer.MAX_VALUE);
			}
			else if(els.length == 2) {
				return new Arity(v, Integer.parseInt(els[1]));
			}
			else
				throw new NumberFormatException();
		}
		catch(NumberFormatException nfe) {
			throw new IllegalArgumentException("Invalid format for arity. [<min>]..[<max>]");
		}
	}
	
	private Arity(int min, int max) {
		super();
		this.min = min;
		this.max = max;
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
}