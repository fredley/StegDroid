package uk.ac.cam.tfmw2.stegdroid;

import android.content.Context;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EditTextDialogView extends LinearLayout{

	// Dialog Views must be generated programatically. This class provides a simple
	// way of creating an EditTextDialog with a programable message and hint
	
	private EditText dialogResult;
	
	public EditTextDialogView(Context context,int messageRes,int hintRes) {
		super(context);
		setOrientation(LinearLayout.VERTICAL);
		setPadding(10,10,10,10);
		TextView dialogMessage = new TextView(context);
		dialogMessage.setText(messageRes);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.setMargins(0, 0, 0, 15);
		addView(dialogMessage,params);

		dialogResult = new EditText(context);
		dialogResult.setGravity(Gravity.TOP);
		dialogResult.setHint(hintRes);
		addView(dialogResult,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		
	}
	
	public EditText getEditText(){
		return dialogResult;
	}

}
