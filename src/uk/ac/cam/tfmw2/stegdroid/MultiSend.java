package uk.ac.cam.tfmw2.stegdroid;

import java.io.File;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Data;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

public class MultiSend extends ListActivity{
	private String[] addresses;
	private String[] ids;
	private String TAG = "MultiSend";
	private String filePath;
	private OnClickListener backListener = new OnClickListener(){

		@Override
		public void onClick(View v) {
			MultiSend.this.finish();
			
		}
		
	};
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//get state info
		
		Bundle extras = getIntent().getExtras(); 
		if(extras !=null) {
		    filePath = extras.getString("file_path");
		}

		//Get email addresses
		String order = "lower(" + ContactsContract.Groups.TITLE + ") ASC";
		String[] groupProjection = new String[] {
				ContactsContract.Groups.TITLE,
				ContactsContract.Groups._ID
		};

		Cursor groups = managedQuery(
				ContactsContract.Groups.CONTENT_SUMMARY_URI,
				groupProjection, 
				null,
				null, 
				order);

		groups.moveToFirst();
		int nameColumn = groups.getColumnIndex(ContactsContract.Groups.TITLE);
		
		addresses = new String[groups.getCount()];
		ids = new String[addresses.length];
		int i = 0;
		do {
			ids[i] = groups.getString(groups.getColumnIndex(ContactsContract.Groups._ID));
			addresses[i++] = String.format("%s", groups.getString(nameColumn).replace("System Group: ", ""));
		} while (groups.moveToNext());

		//LayoutInflater li = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		setContentView(R.layout.list_view);
		
		Button back = (Button) findViewById(R.id.back);
		back.setOnClickListener(backListener);
		
		ListView lv = (ListView) findViewById(android.R.id.list);
		//lv.addHeaderView(li.inflate(R.layout.list_header,lv));
		
		setListAdapter(new ArrayAdapter<String>(this, R.layout.list_item, addresses));
		
		lv.setTextFilterEnabled(true);

		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				//Get ID
				String gid = ids[position];
				//get all people in this group
				String[] projection = new String[]{
						ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID
				};
				Cursor contacts = getContentResolver().query(
						Data.CONTENT_URI,
						projection,
						ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + "=" + gid,
						null,
						null
				);
				startManagingCursor(contacts);
				String[] emailArray = new String[contacts.getCount()];
				int i = 0;
				if (contacts.moveToFirst()) {
					do {
						int cid = contacts.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID);
						//get email addresses

						String[] nameAndEmailProjection = new String[] {
								Email.DATA };

						Cursor emails = managedQuery(
								Email.CONTENT_URI,
								nameAndEmailProjection, Email.CONTACT_ID + "=" + contacts.getString(cid), null, null);

						emails.moveToFirst();
						int emailColumn = emails.getColumnIndex(
								ContactsContract.CommonDataKinds.Email.DATA);
						do {
							try{
								String address = emails.getString(emailColumn);
								emailArray[i++] = address;
								Log.v(TAG,address);
							}catch (CursorIndexOutOfBoundsException e){
								//ignore, just means that this contact has no email address.
							}
						} while (emails.moveToNext());


					} while (contacts.moveToNext());
				}
				// When clicked, show a toast with the TextView text
				
				File toShare = new File(filePath);
				Uri uri = Uri.fromFile(toShare);   
				Intent it = new Intent(Intent.ACTION_SEND);   
				it.putExtra(Intent.EXTRA_STREAM, uri);   
				it.putExtra(Intent.EXTRA_EMAIL, emailArray);
				it.setType("audio/ogg");
				
				startActivity(it); 
				
			}
		});
	}
}
