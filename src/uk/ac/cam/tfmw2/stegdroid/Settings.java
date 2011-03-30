package uk.ac.cam.tfmw2.stegdroid;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Settings extends PreferenceActivity{

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    addPreferencesFromResource(R.xml.settings);
	}
	
	
	
}
