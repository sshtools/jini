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

import java.text.MessageFormat;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public final class Interpolation {
	
	public static final String DEFAULT_VARIABLE_PATTERN = "\\$\\{(.*?)\\}";

	@FunctionalInterface
	public interface Interpolator extends BiFunction<Data, String, String> {
		
	}
	
	private Interpolation() {
	}

	public static Interpolator throwException()  {
		return (data, k) -> {
			throw new IllegalArgumentException(MessageFormat.format("Unknown string variable `{0}`'", k));
		};
	}
	
	public static Interpolator compound(Interpolator... sources)  {
		return (data, k) -> {
			for(var src : sources) {
				var v = src.apply(data, k);
				if(v != null)
					return v;
			}
			return null;
		};
	}
	
	public static Interpolator systemProperties()  {
		return (data, k) -> {
			if(k.startsWith("sys:")) {
				return System.getProperty(k.substring(4));
			}
			else
				return null;
		};
	}
	
	public static Interpolator environment()  {
		return (data, k) -> {
			if(k.startsWith("env:")) {
				return System.getenv(k.substring(4));
			}
			else
				return null;
		};
	}
	
	public static Interpolator self()  {
		return (data, k) -> {
			if(k.startsWith("this:")) {
				var kdata = data(k, data);
				if(kdata != null)
					return kdata.get(k.substring(5), key(k));
			}
			return null;
		};
	}
	
	public static Interpolator document()  {
		return (data, k) -> {
			if(k.startsWith("doc:")) {
				var kdata = data(k, data.document());
				if(kdata != null)
					return kdata.get(k.substring(4), key(k));
			}
			return null;
		};
	}

	private static String key(String key) {
		var idx = key.indexOf('.');
		return idx == -1 ? key : key.substring(idx + 1);
		
	}
	private static Data data(String key, Data parent) {
		var idx = key.indexOf('.');
		return idx == -1 ? parent : parent.section(key.substring(0, idx).split("\\."));
	}
	
	public static String str(Data data, String text, Interpolator source) {
		return str(data, DEFAULT_VARIABLE_PATTERN, text, source);
	}
	
	public static String str(Data data, String pattern, String text, Interpolator source) {
		return str(data, Pattern.compile(pattern), text, source);
	}

	public static String str(Data data, Pattern pattern, String text, Interpolator source) {
		var matcher = pattern.matcher(text);
		var builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
			var variable = matcher.group(1);
			var replacement = source.apply(data, variable);
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
			throwException(),
			self(),
			document()
		);
	}
}
