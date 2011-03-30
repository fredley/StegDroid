package uk.ac.cam.tfmw2.stegdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class FAQ extends Activity{
	
	private class FaqItem extends LinearLayout{

		public FaqItem(Context context, String question) {
			super(context);
			this.setPadding(15, 15, 15, 15);
			TextView item = new TextView(context);
			item.setTextColor(0xffffffff);
			item.setTextSize(15);
			item.setText(question);
			View line = new View(context);
			line.setBackgroundColor(0xffdddddd);
			this.addView(item,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		}
		
	}
	
	private class FaqListener implements View.OnClickListener{

		private String answer;
		
		public FaqListener(String message){
			this.answer = message;
		}
		
		@Override
		public void onClick(View v) {
			//create dialog containing answer
			AlertDialog.Builder builder = new AlertDialog.Builder(FAQ.this);
			builder.setMessage(answer)
			       .setCancelable(false)
			    
			       .setPositiveButton("OK",  new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			builder.create().show();
			
		}
		
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//create layout - lits of questions, tapping reveals answer
		ScrollView scroller = new ScrollView(this);
		LinearLayout mainView = new LinearLayout(this);
		scroller.addView(mainView);
		mainView.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		
		//title
		TextView title = new TextView(this);
		title.setText("Help");
		title.setTextColor(0xffffffff);
		title.setGravity(Gravity.CENTER);
		title.setTextSize(30);
		
		mainView.addView(title,params);
		
		//buttons
		String[] questions = {	"What is Steganography?",
								"What does StegDroid do?",
								"What is Paranoid Mode?",
								"How do I make my messages secure?",
								"Who can read my messages?",
								"Is using StegDroid a safe way to send sensitive data?"};
		
		String[] answers = { 	"Steganography is the art and science of writing hidden messages in such a way that no one, apart from the sender and intended recipient, suspects the existence of the message, a form of security through obscurity.", 
								"StegDroid hides text messages in an audio file using a steganography process that uses echoes. The human ear cannot detect the difference between an audio file with hidden data and one without, it requires expensive computation to determine if an audio file even has anything embedded in it.",
								"Paranoid mode wipes all data including files, messages and your key from StegDroid whenever it is closed of minimized. Enable this if you fear they may break down your door at any minute.",
								"You can enable encryption (using AES) for your messages in settings. This will ensure only someone with the right password can read your messages.",
								"Only someone with StegDroid, and if you've encrypted your messages, only someone with StegDroid and the correct password.",
								"That's a judegement for you to make. As the app is still in development, it will leave slight traces on your phone, which could in theory be detected by another malicious app. StegDroid is not designed to handle data that is in any way sensitive."};
		for(int i = 0;i < questions.length;i++){
			FaqItem fi = new FaqItem(this,questions[i]);
			fi.setFocusable(true);
			fi.setClickable(true);
			fi.setOnClickListener(new FaqListener(answers[i]));
			mainView.addView(fi,params);
			
		}
		
		
		this.setContentView(scroller);
		
		//create toast to instruct user to tap buttons
		Toast.makeText(this, "Click a question for more information.", Toast.LENGTH_LONG).show();
	}
}
