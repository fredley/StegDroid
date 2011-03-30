package uk.ac.cam.tfmw2.stegdroid;

interface IRecordService {
	
	long getTimeInRecording();
	long getTimeInSession();
	void stopRecording();
	int  getState();
	void toggleRecording(long sessionId);
	int  getMaxAmplitude();
	void setSession(long sessionId);
	void startRecording();

}

