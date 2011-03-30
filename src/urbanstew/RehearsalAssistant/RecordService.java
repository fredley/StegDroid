package urbanstew.RehearsalAssistant;

import java.io.File;

import uk.ac.cam.tfmw2.stegdroid.IRecordService;
import uk.ac.cam.tfmw2.stegdroid.IRecordService.Stub;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder.AudioSource;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;


public class RecordService extends Service
{

	public enum State { INITIALIZING, READY, STARTED, RECORDING };

	private final static int[] sampleRates = {44100, 22050, 11025, 8000};

	@Override
	public void onCreate()
	{
		mState = State.STARTED;
	}

	@Override
	public void onDestroy()
	{
		if(mState == State.RECORDING)
		{
			stopRecording();
		}
	}

	@Override
	public void onStart(Intent intent, int startId)
	{
		if(mState == State.RECORDING)
		{
			stopRecording();
		}
		else
		{
			startRecording();
		}
	}

	void setSession(long sessionId)
	{
	}


	void toggleRecording(long sessionId)
	{		
		if(mState == State.STARTED)
			startRecording();
		else
			stopRecording();
	}

	void startRecording()
	{		
		// make sure the SD card is present for the recording
		if(android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
		{
			// create the directory
			File external = Environment.getExternalStorageDirectory();
			File audio = new File(external.getAbsolutePath() + "/stegDroid"); 
			audio.mkdirs();
			Log.w("Rehearsal Assistant", "writing to directory " + audio.getAbsolutePath());


			// set to uncompressed
			boolean uncompressed = true;

			// construct file name
			mOutputFile =
				audio.getAbsolutePath() + "/unencoded"
				+ (uncompressed ? ".wav" : ".3gp");
			Log.w("Rehearsal Assistant", "writing to file " + mOutputFile);

			// start the recording
			if(!uncompressed)
			{
				mRecorder = new RehearsalAudioRecorder(false, 0, 0, 0, 0);
			}
			else
			{
				int i=0;
				do
				{
					if (mRecorder != null)
						mRecorder.release();
					mRecorder = new RehearsalAudioRecorder(true, AudioSource.MIC, sampleRates[i], AudioFormat.CHANNEL_CONFIGURATION_MONO,
							AudioFormat.ENCODING_PCM_16BIT);
				} while((++i<sampleRates.length) & !(mRecorder.getState() == RehearsalAudioRecorder.State.INITIALIZING));
			}
			mRecorder.setOutputFile(mOutputFile);
			mRecorder.prepare();
			mRecorder.start(); // Recording is now started

		}
		mState = State.RECORDING;
	}

	void stopRecording()
	{
		// state must be RECORDING
		if(mState != State.RECORDING)
			return;

		if(mRecorder != null)
		{
			mRecorder.stop();
			mRecorder.release();
		}

		mState = State.STARTED;
	}


	long timeInRecording()
	{
		if(mState != State.RECORDING)
			return 0;
		return System.currentTimeMillis() - mTimeAtAnnotationStart;
	}

	long timeInSession()
	{
		if(mState == State.INITIALIZING)
			return 0;
		return System.currentTimeMillis() - mTimeAtStart;
	}

	int getMaxAmplitude()
	{
		if(mRecorder == null || mState != State.RECORDING)
			return 0;
		return mRecorder.getMaxAmplitude();
	}


	@Override
	public IBinder onBind(Intent arg0)
	{
		return mBinder;
	}
	State mState;
	long mTimeAtStart;
	long mRecordedAnnotationId;
	RehearsalAudioRecorder mRecorder = null;

	long mTimeAtAnnotationStart;
	String mOutputFile;
	String mTitle;


	private final IRecordService.Stub mBinder = new IRecordService.Stub() {
		@Override
		public long getTimeInRecording() throws RemoteException
		{
			return timeInRecording();
		}
		@Override
		public long getTimeInSession() throws RemoteException
		{
			return timeInSession();
		}
		@Override
		public void stopRecording() throws RemoteException
		{
			RecordService.this.stopRecording();
		}
		@Override
		public int getState() throws RemoteException
		{
			return mState.ordinal();
		}
		@Override
		public void toggleRecording(long sessionId) throws RemoteException
		{
			RecordService.this.toggleRecording(sessionId);
		}
		@Override
		public int getMaxAmplitude() throws RemoteException
		{
			return RecordService.this.getMaxAmplitude();
		}
		@Override
		public void setSession(long sessionId) throws RemoteException
		{
			RecordService.this.setSession(sessionId);
		}
		@Override
		public void startRecording() throws RemoteException
		{
			RecordService.this.startRecording();			
		}
	};

}