package uk.ac.cam.tfmw2.stegdroid;



import imp.javax.sound.sampled.AudioFileFormat.Type;
import imp.javax.sound.sampled.AudioFormat;
import imp.javax.sound.sampled.AudioInputStream;
import imp.javax.sound.sampled.UnsupportedAudioFileException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.sun.media.sound.WaveFileReader;
import com.sun.media.sound.WaveFileWriter;

public class StegDroid extends Activity {

	public static final String SETTINGS_ENCRYPTION = "use_crypto";
	public static final String SETTINGS_PARANOIA = "paranoia";
	public static final String SETTINGS_ENCRYPTION_KEY = "key";
	
	private static final int DISPLAY_MESSAGE = 0;
	private static final int EMBED_SUCCEEDED = 1;
	private static final int EMBED_FAILED = 2;
	private static final int DECRYPTION_FAIL = 3;
	private static final int ERROR = 4;
	
	private static final int MENU_TEST = 0;
	private static final int MENU_SETTINGS = 1;
	private static final int MENU_FEEDBACK = 2;
	private static final int MENU_DECODE = 3;
	private static final int MENU_HELP = 4;
	private static final int MENU_TEST_ITEM = 5; //TODO remove
	
	private static final int SOURCE_FILE = 0;
	private static final int SOURCE_INTENT_WAV = 1;
	private static final int SOURCE_INTENT_OGG = 2;
	
	
	private static final String TAG = "StegDroid";
	
	public static final String DECRYPTION_FAIL_MESSAGE = "Decryption Failed";
	
	private String rootDir;
	public static final String unencodedFilePath = "unencoded.wav";
	public static final String encodedFilePath = "encoded.wav";
	public static final String shareableFilePath = "share.ogg";
	
	private boolean isPlaying = false;
	private boolean isRecordingLongEnough = false;
	private boolean sendIntent = false;

	private int source;
	private int page = 1;
	private String messageString = "";
	
	private Button startStop;
	private Button playUnencoded;
	private Button playEncoded;
	private Button extractData;
	private Button next;
	private Button back;
	private Button share;
	private Button multicast;
	
	private TextView charCount;
	private TextView timeCount;
	private TextView counterText;
	private EditText messageBox;

	private Uri sourceUri;
	private ProgressDialog loadingDialog;
	private MediaPlayer mediaPlayer = new MediaPlayer();
	private IRecordService recordService = null;
	private SharedPreferences settings;	
	private StegTimer timer;
	private PowerManager.WakeLock wakeLock;
	
	private class StegTimer extends CountDownTimer{
		
		// CountDownTimer to update views when recording
		
		public StegTimer(int seconds) {
			// Simplify constructor to just deal with a number of seconds
			super(seconds * 1000, 1000);
		}
		@Override
		public void onTick(long millisLeft){
			// update timeCount every second
			timeCount.setText(Long.toString(millisLeft/1000));
		}
		@Override
		public void onFinish(){
			// if the counter reaches the end, then the recordig is long enough 
			isRecordingLongEnough = true;
			timeCount.setText(R.string.counter_finished);
			counterText.setText(R.string.counter_text_finished);
		}
	};

	private int secondsNeeded(int chars){
		//if crypto is set round up to 16 byte blocks
		if(settings.getBoolean(SETTINGS_ENCRYPTION, false)) chars = (((chars / 16) + 1) * 16);
		
		//data rate is ~18.8 bits/s so this is a safe number
		return (chars / 2) + 1;
	}
	
	private MediaPlayer.OnCompletionListener FinishedPlaying = new MediaPlayer.OnCompletionListener() {

		@Override
		public void onCompletion(MediaPlayer mp) {
			isPlaying = false;
			mp.stop();
			mp.reset();
			
			Button b = (Button) findViewById(R.id.play);
			// some action is different depending on the page we're on.
			if(page == 2){
				b.setText(R.string.play_unencoded);
				startStop.setEnabled(true);
				next.setEnabled(isRecordingLongEnough);
			}else if(page == 3) {
				b.setText(R.string.play_encoded);
				share.setEnabled(true);
				extractData.setEnabled(true);
			}
			b.setBackgroundResource(android.R.drawable.btn_default);
			b.setCompoundDrawablesWithIntrinsicBounds(R.drawable.play, 0, 0, 0);
			back.setEnabled(true);
		}
	};
	private Handler messageHandler = new Handler(){
		
		@Override
		public void handleMessage(final Message m){
			switch(m.what){
			case DISPLAY_MESSAGE:
				String message = (String) m.obj;
				AlertDialog.Builder builder = new AlertDialog.Builder(StegDroid.this);
				builder.setMessage(message)
				.setTitle("Extracted Message:")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
				builder.create().show();

				//re-enable button
				extractData.setEnabled(true);
				break;
			case EMBED_SUCCEEDED:
				updatePage(3);
				break;
			case EMBED_FAILED:
				AlertDialog.Builder failBuilder = new AlertDialog.Builder(StegDroid.this);
				failBuilder.setMessage(R.string.embed_fail_message)
				.setCancelable(false)
				.setTitle("Decrypted Message")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
				failBuilder.create().show();
				break;
			case DECRYPTION_FAIL:
				//decryption failed, incorrect password. Get a new key from the user or cancel
				//unfortunately, this View can't be expanded from XML, so it must be created manually
				EditTextDialogView dialogLayout = new EditTextDialogView(StegDroid.this,R.string.decryption_fail_message,R.string.decryption_fail_hint);
				final EditText dialogResult = dialogLayout.getEditText();

				AlertDialog.Builder keyBuilder = new AlertDialog.Builder(StegDroid.this);
				keyBuilder
				.setCancelable(false)
				.setView(dialogLayout)
				.setTitle("Decryption Failed")
				.setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						//set new key in settings
						settings.edit().putString(SETTINGS_ENCRYPTION_KEY,dialogResult.getText().toString()).commit();
						//retry
						if(source == SOURCE_INTENT_WAV || source == SOURCE_INTENT_OGG){
							try {

								InputStream input = getContentResolver().openInputStream(sourceUri);
								extractThread et = new extractThread(input,(source == SOURCE_INTENT_OGG));
								loadingDialog = ProgressDialog.show(StegDroid.this,"",getString(R.string.loader_extracting_data),true);
								et.start();
							} catch (FileNotFoundException e) {
								showError("File not found, please email tfmw2@cam.ac.uk");
							};
						}else if(source == SOURCE_FILE){
							try {
								InputStream input = new FileInputStream(new File(rootDir + shareableFilePath));extractThread et = new extractThread(input,true);
								loadingDialog = ProgressDialog.show(StegDroid.this,"",getString(R.string.loader_extracting_data),true);
								et.start();
							} catch (FileNotFoundException e) {
								showError("File not found, please email tfmw2@cam.ac.uk");
							}
						}
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						//re-enable button
						extractData.setEnabled(true);
						dialog.cancel();
					}
				});
				keyBuilder.create().show();
				break;
			case ERROR:
				showError((String) m.obj);
				break;
			}
				
				
			
		}
	};
	private void runUnitTests() {
		// run each unit test and show a dialog with results
		boolean stringResult = Tests.bitStreamTest();
		String message = "StringBitStream: ";
		message += (stringResult) ? "PASS" : "FAIL"; 
		boolean complexLogResult = Tests.complexLogTest();
		message += "\nComplexLog: ";
		message += (complexLogResult) ? "PASS" : "FAIL"; 
		boolean cryptoTestResult = Tests.cryptoTest();
		message += "\nCrypto: ";
		message += (cryptoTestResult) ? "PASS" : "FAIL"; 
		boolean longCharTestResult = Tests.bitStreamLongCharsTest();
		message += "\nLongChars: ";
		message += (longCharTestResult) ? "PASS" : "FAIL"; 
		AlertDialog.Builder builder = new AlertDialog.Builder(StegDroid.this);
		builder.setMessage(message)
		       .setCancelable(false)
		       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		builder.create().show();
	}
	
	private class embedThread extends Thread{
		
		public void run(){

			try {
				WaveFileReader wfr = new WaveFileReader();

				File waveFile = new File(rootDir + unencodedFilePath);
				File saveFile = new File(rootDir + encodedFilePath);

				AudioInputStream stream = wfr.getAudioInputStream(waveFile);
				AudioFormat format = stream.getFormat();
				Log.v(TAG,"Format info: "+format.getEncoding().toString()+format.getSampleRate()+" "+format.getSampleSizeInBits()+" "+format.getChannels()+" "+format.getFrameSize()+" "+format.getFrameRate()+" "+(format.isBigEndian() ? "true" : "false"));
				int length = (int) (stream.getFrameLength() * format.getFrameSize());
				Log.v(TAG,"Encode Length: "+Integer.toString(length));
				
				// read the entire stream
				byte[] samples = new byte[length];
				DataInputStream dis = new DataInputStream(stream);
				try {
					// This could be improved by using a ByteBuffer
					//TODO
					dis.readFully(samples);
				} catch (IOException ex) {
					//TODO
					ex.printStackTrace();
				}
				BitStream inStream;
				Log.v(TAG,"checking use_crypto: "+(settings.getBoolean(SETTINGS_ENCRYPTION, false) ? "true" : "false"));
				if(settings.getBoolean(SETTINGS_ENCRYPTION, false)){
					//encrypted
					inStream = new BitStream(messageString,settings.getString(SETTINGS_ENCRYPTION_KEY, ""));
				}else{
					//unencrypted
					inStream = new BitStream(messageString);
				}
				EchoStegFile esf = new EchoStegFile(inStream);
				
				//filter the whole thing in one go
				byte[] newSamples = esf.embed(samples, 0, length);
				ByteArrayInputStream bais = new ByteArrayInputStream(newSamples);
				if(!inStream.endOfBitStream()){
					Message m = Message.obtain();
					m.what = EMBED_FAILED;
					messageHandler.sendMessage(m);
					loadingDialog.dismiss();
				}
				WaveFileWriter wfw = new WaveFileWriter();
				AudioInputStream oStream = new AudioInputStream(bais,format,newSamples.length / 2);

				wfw.write(oStream, Type.WAVE, saveFile);
				
				// Encode file as ogg
				Vorbis ve = new Vorbis();
				ve.encode(new FileInputStream(saveFile),new File(rootDir + shareableFilePath));

			} catch (UnsupportedAudioFileException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			//update views
			Message m = Message.obtain();
			m.what = EMBED_SUCCEEDED;
			messageHandler.sendMessage(m);
			loadingDialog.dismiss();
			
		}
	}
	private TextWatcher messageWatcher = new TextWatcher(){

		@Override
		public void afterTextChanged(Editable arg0) {			
		}

		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {			
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			updateCounters(s.toString());
		}

		
		
	};
	
	private void updateCounters(String s) {
		// Update the counters underneath the message box
		int stringLength = s.length();
		int bytesLength = s.getBytes().length;
		
		charCount.setText(stringLength+"/120 characters");
		timeCount.setText(secondsNeeded(bytesLength)+"s of audio required");
		
	}
	private void updatePage(int n){
		/* Fetch page layout from XML and assign buttons etc.
		 * Only layout happens here, anything else is dealt with in 
		 * nextListener and previousListener
		 */
		switch(n){
		case 1:
			setContentView(R.layout.page1);
			next = (Button) findViewById(R.id.p1Next);
			next.setOnClickListener(nextListener);
			messageBox = (EditText) findViewById(R.id.message);
			messageBox.addTextChangedListener(messageWatcher);
			//reload previous message if there is one
			if(messageString.length() > 0) messageBox.setText(messageString);
			charCount = (TextView) findViewById(R.id.char_counter);
			timeCount = (TextView) findViewById(R.id.counter);
			page = 1;
			//make the messageBox focus automatically, and launch the soft keyboard if neccesary
			messageBox.requestFocus();
			InputMethodManager imm = (InputMethodManager)getSystemService(
				    Context.INPUT_METHOD_SERVICE);
				imm.showSoftInput(messageBox, 0);
			break;
		case 2:
			setContentView(R.layout.page2);
			next = (Button) findViewById(R.id.p2Next);
			back = (Button) findViewById(R.id.p2Back);
			startStop = (Button) findViewById(R.id.record);
			playUnencoded = (Button) findViewById(R.id.play);
			timeCount = (TextView) findViewById(R.id.counter);
			counterText = (TextView) findViewById(R.id.counterText);
			timeCount.setText(Integer.toString(secondsNeeded(messageString.length())));
			next.setOnClickListener(nextListener);
			back.setOnClickListener(backListener);
			startStop.setOnClickListener(RecordListener);
			playUnencoded.setOnClickListener(PlayUnencodedListener);
			page = 2;
			break;
		case 3:
			setContentView(R.layout.page3);
			back = (Button) findViewById(R.id.p3Back);
			share = (Button) findViewById(R.id.share);
			multicast = (Button) findViewById(R.id.multicast);
			playEncoded = (Button) findViewById(R.id.play);
			extractData = (Button) findViewById(R.id.extract);
			back.setOnClickListener(backListener);
			playEncoded.setOnClickListener(PlayEncodedListener);
			extractData.setOnClickListener(ExtractDataListener);
			share.setOnClickListener(shareListener);
			multicast.setOnClickListener(multicastListener);
			page = 3;
			break;
		}
		//Finally update the status bar.
		updateStatusBar();
	}
	
	private void updateStatusBar() {
		//Update the status bar with details of encryption & paranoia status
		//Separate from other layout functionality as it must be called from other places
		
		TextView paranoiaStatus = (TextView) findViewById(R.id.status_paranoia);
		paranoiaStatus.setText(settings.getBoolean(SETTINGS_PARANOIA,false) ? "Paranoid mode: ON" : "Paranoid mode: OFF");
		TextView encryptionStatus = (TextView) findViewById(R.id.status_encryption);
		encryptionStatus.setText(settings.getBoolean(SETTINGS_ENCRYPTION,false) ? "Encryption: ON" : "Encryption: OFF");
		//TextView progress = (TextView) findViewById(R.id.progress);
		//progress.setText("Step "+page+"/"+3);
	}

	private class extractThread extends Thread{
		
		//Thread to perform extraction from a stream.
		
		private InputStream from;
		private boolean ogg;
		
		public extractThread(InputStream input, boolean isOgg){
			from = input;
			ogg = isOgg;
		}
		
		private void error(String s){
			
			Message m = Message.obtain();
			
			m.what = ERROR;
			m.obj = s;
			messageHandler.sendMessage(m);
		}
		
		public void run(){
			
			//get the return message here in case there's an error
			Message m = Message.obtain();
			
			if(ogg){
				//transcode ogg file to wav
				Vorbis v = new Vorbis();
				try {
					File outputFile = new File(rootDir + encodedFilePath);
					v.decode(from,outputFile);
					from = new FileInputStream(outputFile);
					Log.v(TAG,"Decoded Ogg file, trying to extract from wav.");
				} catch (IOException e) {
					error("There was an IO Exception (Code 3), your message could not be read. Please email tfmw2@cam.ac.uk!");
					e.printStackTrace();
					return;
				}
			}
			
			WaveFileReader wfr = new WaveFileReader();
			AudioInputStream stream;
			
			try {
				stream = wfr.getAudioInputStream(from);
			} catch (UnsupportedAudioFileException e) {
				//this should never happen, only a .wav file can get this far
				return;
			} catch (IOException e) {
				error("There was an IO Exception (Code 1), your message could not be read. Please email tfmw2@cam.ac.uk!");
				return;
			}
			
			//get the format information and initialize buffers
			AudioFormat format = stream.getFormat();
			int length = (int) (stream.getFrameLength() * format.getFrameSize());
			
			// read the entire stream
			byte[] samples = new byte[length];
			DataInputStream dis = new DataInputStream(stream);
			
			
			try {
				dis.readFully(samples);
			} catch (IOException ex) {
				//This actually happens, not sure what's causing it...
				//try ignoring for now? TODO
				//error("There was an IO Exception (Code 2), your message could not be read. Please email tfmw2@cam.ac.uk!");
				//return;
			}
			//Set up BitStream, EchoStegFile, Message and String for output
			BitStream sbs = new BitStream();
			EchoStegFile esf = new EchoStegFile();
			String result;
			
			//Perform extraction
			esf.extract(samples, sbs);
			
			//check if it's encrypted
			if(sbs.isEncrypted()){
				//decrypt using stored key. If it fails, send message to prompt user, otherwise display result
				result = sbs.decryptString(settings.getString(SETTINGS_ENCRYPTION_KEY,""));
				if(result == DECRYPTION_FAIL_MESSAGE){
					m.what = DECRYPTION_FAIL;
					m.obj = from;
					messageHandler.sendMessage(m);
				}else{
					m.what = DISPLAY_MESSAGE;
					m.obj = result;
					messageHandler.sendMessage(m);
				}
			}else{
				//display result
				result = sbs.getString();
				m.what = DISPLAY_MESSAGE;
				m.obj = result;
				messageHandler.sendMessage(m);
			}
			//dismiss the loading spinner
			loadingDialog.dismiss();
		}


	}
	private void showError(String message) {
		try{
			loadingDialog.dismiss();
		}catch(NullPointerException e){
			//ignore, this is just incase loading Dialog isn't showing
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(StegDroid.this);
		builder.setMessage(message)
		.setCancelable(false)
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		builder.create().show();

		}
	private OnClickListener ExtractDataListener = new OnClickListener(){

		@Override
		public void onClick(View v) {
			//disable button to prevent accidental double clicks
			extractData.setEnabled(false);
			//extract data from encoded file and display in dialog
			File inputFile = new File(rootDir + shareableFilePath);
			extractThread et;
			try {
				et = new extractThread(new FileInputStream(inputFile),true);
			} catch (FileNotFoundException e) {
				showError("Could not find the file to extract from.");
				return;
			}
			et.start();
			loadingDialog = ProgressDialog.show(StegDroid.this,"",getString(R.string.loader_extracting_data),true);
		}
		
	};
	private OnClickListener PlayEncodedListener = new OnClickListener() {

		@Override
		public void onClick(View v){
			//play unencoded file
			if(isPlaying){
				isPlaying = false;
				mediaPlayer.stop();
				mediaPlayer.reset();
				playEncoded.setText(R.string.play_encoded);
				playEncoded.setBackgroundResource(android.R.drawable.btn_default);
				playEncoded.setCompoundDrawablesWithIntrinsicBounds(R.drawable.play, 0, 0, 0);
				back.setEnabled(true);
				share.setEnabled(true);
				extractData.setEnabled(true);
			}else{
				isPlaying = true;
				try {
					mediaPlayer.setOnCompletionListener(FinishedPlaying);
					mediaPlayer.setDataSource(rootDir + encodedFilePath);
					mediaPlayer.prepare(); 
					mediaPlayer.start();
					playEncoded.setText(R.string.stop);
					playEncoded.setBackgroundResource(R.drawable.btn_default_green);
					playEncoded.setCompoundDrawablesWithIntrinsicBounds(R.drawable.stop, 0, 0, 0);
					back.setEnabled(false);
					share.setEnabled(false);
					extractData.setEnabled(false);
				} catch (IllegalArgumentException e) {
					//Should never happen
				} catch (IllegalStateException e) {
					//Should never happen
				} catch (IOException e) {
					showError("Could not find the file to play");
				}
			}
		}
	};
	private OnClickListener PlayUnencodedListener = new OnClickListener() {

		@Override
		public void onClick(View v){
			//play unencoded file
			if(isPlaying){
				isPlaying = false;
				mediaPlayer.stop();
				mediaPlayer.reset();
				playUnencoded.setText(R.string.play_unencoded);
				playUnencoded.setCompoundDrawablesWithIntrinsicBounds(R.drawable.play, 0, 0, 0);
				playUnencoded.setBackgroundResource(android.R.drawable.btn_default);
				startStop.setEnabled(true);
				next.setEnabled(isRecordingLongEnough);
				back.setEnabled(true);
			}else{
				isPlaying = true;
				try {
					mediaPlayer.setOnCompletionListener(FinishedPlaying);
					mediaPlayer.setDataSource(rootDir + unencodedFilePath);
					mediaPlayer.prepare();
					mediaPlayer.start();
					playUnencoded.setText(R.string.stop);
					playUnencoded.setBackgroundResource(R.drawable.btn_default_green);
					playUnencoded.setCompoundDrawablesWithIntrinsicBounds(R.drawable.stop, 0, 0, 0);
					startStop.setEnabled(false);
					next.setEnabled(false);
					back.setEnabled(false);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	};

	

	private OnClickListener RecordListener = new OnClickListener(){

		@Override
		public void onClick(View v) {
			try
			{
				switch(urbanstew.RehearsalAssistant.RecordService.State.values()[recordService.getState()])
				{
				case STARTED:
					timer = new StegTimer(secondsNeeded(messageString.length()));
					counterText.setText(R.string.counter_text);
					recordService.startRecording();
					timer.start();
					//stop the screen turning off
					wakeLock.acquire();
					//Make the button red while recording
					startStop.setBackgroundResource(R.drawable.btn_default_red);
					startStop.setText(R.string.stop);
					startStop.setCompoundDrawablesWithIntrinsicBounds (R.drawable.stop,0,0,0);
					playUnencoded.setEnabled(false);
					return;
				default:
					timer.cancel();
					recordService.stopRecording();
					//allow the screen to turn off
					wakeLock.release();
					//Make the button the default colour again
					//startStop.setBackgroundResource(R.drawable.btn_default_normal);
					startStop.invalidate();
					startStop.setBackgroundResource(android.R.drawable.btn_default);
					startStop.setText(R.string.rerecord);
					startStop.setCompoundDrawablesWithIntrinsicBounds (R.drawable.record,0,0,0);
					//check if recording is long enough
					if(!isRecordingLongEnough){
						//set message
						Toast.makeText(StegDroid.this, R.string.short_recording_toast, Toast.LENGTH_SHORT).show();
						next.setEnabled(false);
					}else{
						//enable next
						next.setEnabled(true);
					}
					playUnencoded.setEnabled(true);
				}
			} catch (RemoteException e)
			{
			}

		}

	};
	private OnClickListener nextListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			switch(v.getId()){
				case R.id.p1Next:
					messageString = messageBox.getText().toString();
					//Hide the keyboard
					InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(messageBox.getWindowToken(), 0);
					updatePage(2);
					break;
				case R.id.p2Next:
					//rapid presses can cause problems, so disable the button immediately
					next.setEnabled(false);
					source = SOURCE_FILE;
					//embed
					embedThread et = new embedThread();
					et.start();
					loadingDialog = ProgressDialog.show(StegDroid.this,"",getString(R.string.loader_embedding_data),true);
					//if successful, update view, otherwise show message
			}
		}
	};
	private OnClickListener backListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			switch(v.getId()){
			case R.id.p2Back:
				// cancel the timer, to prevent it updating counter
				if(timer != null)timer.cancel();
				updatePage(1);
				updateCounters(messageString);
				break;
			case R.id.p3Back:
				updatePage(2);
			}
		}
	};
	private OnClickListener shareListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			sendIntent = true;
			File toShare = new File(rootDir + shareableFilePath);
			Uri uri = Uri.fromFile(toShare);   
			Intent it = new Intent(Intent.ACTION_SEND);   
			it.putExtra(Intent.EXTRA_STREAM, uri);   
			it.setType("audio/ogg");   
			startActivity(it); 
		}
	};
	private OnClickListener multicastListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			sendIntent = true;
			Intent i = new Intent(getApplicationContext(), MultiSend.class);
			i.putExtra("file_path", rootDir + shareableFilePath);
			startActivity(i);
		}
	};
	/**
	 * Class for interacting with the secondary interface of the service.
	 */
	private ServiceConnection recordServiceConnection = new ServiceConnection()
	{

		@Override
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			recordService = IRecordService.Stub.asInterface(service);
			try
			{
				recordService.setSession(0);
				
			} catch (RemoteException e)
			{
				Log.v("Service","RemoteException");
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			recordService = null;
			startStop.setEnabled(false);
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		rootDir = Environment.getExternalStorageDirectory().getAbsolutePath()+"/StegDroid/";
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Wake Lock");
		
		//Check if app was launch with data, i.e. from an email attachment
		Uri uri = getIntent() != null ? getIntent().getData() : null;
		if(uri != null){
			InputStream is;
			BufferedInputStream in;
			try {
				is = getContentResolver().openInputStream(uri);
				in = new BufferedInputStream(is);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return;
			}
			Log.v(TAG,"Extra Info: "+getIntent().getType()+" "+getIntent().resolveType(this));
			
			/* Some apps (notably Gmail) do not report the correct mimetype.
			 * Gmail reports a type of audio/wav for an ogg file
			 * To get around this, read the first 4 bytes of the file
			 * An ogg file will have the header OggS, a wave file RIFF
			 */
			
			byte[] top4 = new byte[4];
			String header = "";
			try {
				in.mark(5);
				in.read(top4);
				header = new String(top4);
				Log.v(TAG,"header: "+header+((header.equals("OggS")) ? "true" : "false"));
				// Reset to the start after reading
				in.reset();
			} catch (IOException e) {
				showError("File not found, please contact tfmw2@cam.ac.uk");
			}
			source = (getIntent().getType().equals("audio/ogg") || header.equals("OggS")) ? SOURCE_INTENT_OGG : SOURCE_INTENT_WAV; 
			sourceUri = uri;
			extractThread et = new extractThread(in,(source == SOURCE_INTENT_OGG));
			updatePage(3);
			loadingDialog = ProgressDialog.show(StegDroid.this,"",getString(R.string.loader_extracting_data),true);
			et.start();
		}else{
			source = SOURCE_FILE;
		}
		//setContentView(new StegView(this));
		bindService(new Intent(IRecordService.class.getName()),
				recordServiceConnection, Context.BIND_AUTO_CREATE);
		updatePage(1);
	}
	public boolean onCreateOptionsMenu(Menu m){
		m.add(0, MENU_TEST, 0, "Run Tests").setIcon(R.drawable.ic_menu_test);
		m.add(0, MENU_SETTINGS, 0, "Settings").setIcon(R.drawable.ic_menu_settings);
		m.add(0, MENU_FEEDBACK, 0, "Send Feedback Email").setIcon(R.drawable.ic_menu_feedback);
		m.add(0, MENU_DECODE, 0, "Decode a message").setIcon(R.drawable.ic_menu_extract);
		m.add(0, MENU_HELP, 0, "Help").setIcon(R.drawable.ic_menu_help);
		//m.add(0, MENU_TEST_ITEM, 0, "Multisend");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_TEST:
			runUnitTests();
			break;
		case MENU_SETTINGS:
			Intent settingsIntent = new Intent(this, Settings.class);
			startActivity(settingsIntent);
			break;
		case MENU_FEEDBACK:
			Intent emailIntent = new Intent(Intent.ACTION_SEND);
			emailIntent.setType("text/plain");
			emailIntent.putExtra(Intent.EXTRA_EMAIL  , new String[]{"tfmw2@cam.ac.uk"});
			emailIntent.putExtra(Intent.EXTRA_SUBJECT, "StegDroid Feedback");
		    startActivity(Intent.createChooser(emailIntent, "Send feedback with:"));
		    break;
		case MENU_DECODE:
			Toast.makeText(StegDroid.this, R.string.decode_instructions, Toast.LENGTH_LONG).show();
			break;
		case MENU_HELP:
			Intent helpIntent = new Intent(this, FAQ.class);
			startActivity(helpIntent);
			break;
		case MENU_TEST_ITEM:
			Intent msIntent = new Intent(this, MultiSend.class);
			startActivity(msIntent);
//			float result1 = Tests.stegReliabilityTest(rootDir, unencodedFilePath);
//			Log.v(TAG,"Test Result: "+result1);
//			float result2 = Tests.vorbisTest(rootDir, "test.wav");
//			Log.v(TAG,"Test Result: "+result2);
//			break;
			
		}
		return false;
	}
	@Override
	public void onPause(){
		//unbind service
		unbindService(recordServiceConnection);
		if(settings.getBoolean(SETTINGS_PARANOIA, false) && !sendIntent){
			Log.v(TAG,"paranoia ON");
			//get rid of all data
			messageString = "";
			File dir = new File(rootDir);
			File unencoded = new File(rootDir+unencodedFilePath);
			File encoded = new File(rootDir+encodedFilePath);
			File shared = new File(rootDir+shareableFilePath);
			//delete files
			unencoded.delete();
			encoded.delete();
			shared.delete();
			if(dir.exists()){
				if( dir.listFiles().length > 0){
					//can't delete dir, has other files in
					Log.v(TAG,dir.listFiles()[0].getAbsolutePath());
				}else{
					dir.delete();
				}
			}
			//remove key
			settings.edit().putString(SETTINGS_ENCRYPTION_KEY, "").commit();
			updatePage(1);
		}else if(settings.getBoolean(SETTINGS_PARANOIA, false) && sendIntent){
			sendIntent = false;
		}
		
		super.onPause();
	}
	@Override
	public void onResume(){


		if(page == 1) updateCounters(messageString);
		
		bindService(new Intent(IRecordService.class.getName()),
				recordServiceConnection, Context.BIND_AUTO_CREATE);
		super.onResume();
		updateStatusBar();
	}
	@Override
	public void onBackPressed(){
		switch(page){
		case 3:
			updatePage(2);
			break;
		case 2:
			updatePage(1);
			updateCounters(messageString);
			break;
		default:
			super.onBackPressed();
		}
	}
}