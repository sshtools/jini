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
package com.sshtools.jini.maven;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.sshtools.jini.schema.INISchema;

@Mojo(name = "generate-defaults", threadSafe = true)
public class GenerateDefaultsMojo extends AbstractMojo {
	@Parameter(property = "jini.generate-defaults.filenamePattern", defaultValue = "glob:*.schema.ini")
	private String schemaPattern = "glob:*.schema.ini";
	
	@Parameter(property = "jini.generate-defaults.outputDirectory", defaultValue = "${project.build.directory}/generate-inis")
	private Path outputDirectory;
	
	@Parameter
	private List<Path> sourceDirectories = new ArrayList<>();
	
	@Parameter
	private Map<String, String> nameMap = new HashMap<>();

	@Parameter(required = true, readonly = true, property = "project")
	protected MavenProject project;

	public void execute() throws MojoExecutionException {
		getLog().info("Generating defaults");

		var fs = FileSystems.getDefault();
		var matcher = fs.getPathMatcher(schemaPattern);

		try {
			for (var res : project.getResources()) {
				doPath(matcher, Paths.get(res.getDirectory()));
			}
			
			for(var path : sourceDirectories) {
				doPath(matcher, path);
			}
			
		} catch (IOException ioe) {
			throw new MojoExecutionException("Failed to generate defaults.", ioe);
		}
	}

	private void doPath(PathMatcher matcher, Path resRoot) throws IOException {
		Files.walkFileTree(resRoot, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) throws IOException {
				var name = file.getFileName();
				if (matcher.matches(name)) {
					
					var schema = new INISchema.Builder().
							fromFile(file).
							build();
					
					var outputName = resourceToIniName(file.getFileName().toString());

					getLog().info(MessageFormat.format("{0} -> {1}", file.getFileName(), outputName));
					
					schema.writeDefaults(outputDirectory.resolve(outputName));
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	private String resourceToIniName(String resourceName) {
		if(nameMap.containsKey(resourceName)) {
			return nameMap.get(resourceName);
		}
		
		var idx = resourceName.lastIndexOf('.');
		if(idx != -1) {
			idx = resourceName.lastIndexOf('.', idx - 1);
			if(idx != -1) {
				resourceName = resourceName.substring(0, idx);
			}
		}
		var sb = new StringBuilder();
		var wasLower = false;
		for(var ch : resourceName.toCharArray()) {
			var lower = Character.isLowerCase(ch);
			if(!lower && wasLower) {
				sb.append("-");
			}
			sb.append(Character.toLowerCase(ch));
			wasLower = lower;
		}
		sb.append(".ini");
		return sb.toString();
	}
}
