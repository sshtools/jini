package com.sshtools.jini;

import java.text.MessageFormat;
import java.util.function.Function;
import java.util.regex.Pattern;

public class Interpolation {

	@SuppressWarnings("unchecked")
	public static Function<String, String> compound(Function<String, String>... sources)  {
		return (k) -> {
			for(var src : sources) {
				var v = src.apply(k);
				if(v != null)
					return v;
			}
			return null;
		};
	}
	
	public static Function<String, String> systemProperties()  {
		return (k) -> {
			if(k.startsWith("sys.")) {
				return System.getProperty(k.substring(4));
			}
			else if(k.startsWith("env.")) {
				return System.getenv(k.substring(4));
			}
			else
				return null;
		};
	}

	public static String str(String text, Function<String, String> source) {
		var pattern = Pattern.compile("\\$\\{(.*?)\\}");
		var matcher = pattern.matcher(text);
		var builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
			var variable = matcher.group(1);
			var replacement = source.apply(variable);
			builder.append(text.substring(i, matcher.start()));
			if (replacement == null) {
				throw new IllegalArgumentException(MessageFormat.format("Unknown string variable $\\{{0}\\}", variable));
			} else {
				builder.append(replacement);
			}
			i = matcher.end();

		}
		builder.append(text.substring(i, text.length()));
		text = builder.toString();
		return text;
	}
}
