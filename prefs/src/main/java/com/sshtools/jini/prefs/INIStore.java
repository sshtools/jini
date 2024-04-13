package com.sshtools.jini.prefs;

import java.io.Closeable;
import java.util.prefs.Preferences;

import com.sshtools.jini.prefs.INIPreferences.Scope;

public interface INIStore extends Closeable {

	Preferences root();

	Scope scope();
	
}