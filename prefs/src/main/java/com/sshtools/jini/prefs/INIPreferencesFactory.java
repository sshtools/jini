package com.sshtools.jini.prefs;

import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

import com.sshtools.jini.prefs.INIPreferences.Scope;

public class INIPreferencesFactory implements PreferencesFactory {

	@Override
	public Preferences systemRoot() {
		synchronized (INIPreferences.scopedStores) {
			return INIPreferences.scoped(Scope.GLOBAL);
		}
	}

	@Override
	public Preferences userRoot() {
		synchronized (INIPreferences.scopedStores) {
			return INIPreferences.scoped(Scope.USER);
		}
	}
}