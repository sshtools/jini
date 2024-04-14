package com.sshtools.jini;

import java.text.MessageFormat;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Interpolation {
	
	public static final String DEFAULT_VARIABLE_PATTERN = "\\$\\{(.*?)\\}";

	@FunctionalInterface
	public interface Interpolator extends Function<String, String> {
		
	}
	
	private Interpolation() {
	}

	public static Interpolator throwException()  {
		return (k) -> {
			throw new IllegalArgumentException(MessageFormat.format("Unknown string variable ''{0}'''", k));
		};
	}
	
	public static Interpolator compound(Interpolator... sources)  {
		return (k) -> {
			for(var src : sources) {
				var v = src.apply(k);
				if(v != null)
					return v;
			}
			return null;
		};
	}
	
	public static Interpolator systemProperties()  {
		return (k) -> {
			if(k.startsWith("sys:")) {
				return System.getProperty(k.substring(4));
			}
			else
				return null;
		};
	}
	
	public static Interpolator environment()  {
		return (k) -> {
			if(k.startsWith("env:")) {
				return System.getenv(k.substring(4));
			}
			else
				return null;
		};
	}
	
	public static String str(String text, Interpolator source) {
		return str(DEFAULT_VARIABLE_PATTERN, text, source);
	}
	
	public static String str(String pattern, String text, Interpolator source) {
		return str(Pattern.compile(pattern), text, source);
	}

	public static String str(Pattern pattern, String text, Interpolator source) {
		var matcher = pattern.matcher(text);
		var builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
			var variable = matcher.group(1);
			var replacement = source.apply(variable);
			if(replacement == null) {
				builder.append(text.substring(i, matcher.end()));
			}
			else {
				builder.append(text.substring(i, matcher.start()));
				builder.append(replacement);
			}
						
			i = matcher.end();

		}
		builder.append(text.substring(i, text.length()));
		text = builder.toString();
		return text;
	}

	public static Interpolator defaults() {
		return compound(
			systemProperties(),
			environment(),
			throwException()
		);
	}
}
