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

/**
 * This package contains classes for reading, querying, building and writing
 * <a href="https://en.wikipedia.org/wiki/INI_file">INI</a> files.
 * <h2>Usage</h2>
 * <p>
 * The general pattern for reading an INI document is ..
 * <ul>
 * <li>Create a configured {@link com.sshtools.jini.INIReader} via {@link com.sshtools.jini.INIReader.Builder}.</li>
 * <li>Get an {@link com.sshtools.jini.INI} instance using {@link com.sshtools.jini.INIReader#read(String)} and other read methods.</li>
 * <li>Query the {@link com.sshtools.jini.INI} instance for sections, properties etc.</li>
 * </ul>
 * <p>
 * And for writing an INI document ..
 * <ul>
 * <li>Either use an {@link com.sshtools.jini.INI} document you have obtained from an {@link com.sshtools.jini.INIReader}, create a
 * default document (order preserved, case insensitive keys) using {@link com.sshtools.jini.INI#create()},
 * or use {@link com.sshtools.jini.INI.Builder} to configure behaviour.</li>
 * <li>Create a configured {@link com.sshtools.jini.INIWriter} via {@link com.sshtools.jini.INIWriter.Builder}.</li>
 * <li>Write the instance to some target using {@link com.sshtools.jini.INIWriter#write(INI)} and other write methods.</li>
 * </ul>
 * <h2>Examples</h2>
 * <h3>Write A New INI Document</h3>
 * <pre>{@code 
 * var ini = INI.create();
 * ini.put("Name", "Alice");
 * ini.put("Age", 34);
 * ini.put("Registered", false);
 *   
 * var sec = ini.create("Address");
 * sec.put("Street", "15 Stone Lane");
 * sec.put("Area", "");
 * sec.put("City", "Arbington");
 * sec.put("County", "Inishire");
 * sec.put("PostCode", "ABC 123");
 *   
 * var wrt = new INIWriter.Builder().build();
 *   
 * try(var out = Files.newBufferedWriter(Paths.get("data.ini"))) {
 *     wrt.write(ini, out);
 * }
 * }</pre>
 * <h3>Read An INI Document From A File</h3>
 * <pre>{@code 
 * var ini = INI.fromFile(Paths.get("data.ini"));
 * System.out.format("Name: %s%n". ini.get("Name")); 
 * System.out.format("Age: %d%n". ini.getInt("Age"));
 * if(ini.getBoolean("Registered"))
 *     System.out.println("Is registered%n");
 *   
 * ini.sectionOr("Address").ifPresent(s -> {  
 *     System.out.println("Address");
 *     System.out.format("  Street: %s%n". s.get("Street"));
 *     System.out.format("  Area: %s%n". s.get("Area"));
 *     System.out.format("  City: %s%n". s.get("City"));
 *     System.out.format("  County: %s%n". s.get("County"));
 *     System.out.format("  PostCode: %s%n". s.get("PostCode"));
 *     System.out.format("  Tel: %s%n". s.getOr("PostCode", "N/A"));
 * });
 * }</pre>
 */
package com.sshtools.jini;