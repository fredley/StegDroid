/*
 *  Author:
 *      Stjepan Rajko
 *      urbanSTEW
 *
 *  Copyright 2008,2009 Stjepan Rajko.
 *
 *  This file is part of the Android version of Rehearsal Assistant.
 *
 *  Rehearsal Assistant is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  Rehearsal Assistant is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Rehearsal Assistant.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package urbanstew.RehearsalAssistant;

import java.io.File;
import java.util.HashMap;

import urbanstew.RehearsalAssistant.Rehearsal.Annotations;
import urbanstew.RehearsalAssistant.Rehearsal.AppData;
import urbanstew.RehearsalAssistant.Rehearsal.Projects;
import urbanstew.RehearsalAssistant.Rehearsal.Sessions;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public class RehearsalData extends ContentProvider {

	@Override
	public boolean onCreate()
	{
		// Access the database.
		mOpenHelper = new DatabaseHelper(getContext());
		
		return true;
	}
	
	static ContentValues valuesForMemoProject(Context context)
	{
		ContentValues values = new ContentValues();
		values.put(Projects.TITLE, "stegdroid");
		values.put(Projects.IDENTIFIER, "memo_project");
		values.put(Projects.TYPE, Projects.TYPE_SIMPLE);
		return values;
	}

	static void addSimpleProject(SQLiteDatabase db, Context context)
	{
		db.insert("projects", "identifier", valuesForMemoProject(context));
	}
	
	static void addSessionProject(SQLiteDatabase db, Context context)
	{
		ContentValues values = new ContentValues();
		values.put(Projects.TITLE,"stegdroid");
		values.put(Projects.IDENTIFIER, "session_project");
		values.put(Projects.TYPE, Projects.TYPE_SESSION);
		db.insert("projects", "identifier", values);
	}

	enum Project { _ID, TITLE, IDENTIFIER, TYPE }

	enum Session { _ID, PROJECT_ID, TITLE, IDENTIFIER, START_TIME, END_TIME }
	
	enum Annotation { _ID, RUN_ID, START_TIME, END_TIME, FILE_NAME }

	private static class DatabaseHelper extends SQLiteOpenHelper
	{
		DatabaseHelper(Context context)
		{
			super(context, "rehearsal_assistant.db", null, 9);
			mContext = context;
		}

		public void onCreate(SQLiteDatabase db)
		{
			createProjectsTable(db);
			createSessionsTable(db);
			createAnnotationsTable(db);
		}
				
		void createProjectsTable(SQLiteDatabase db)
		{
			db.execSQL("CREATE TABLE " + Projects.TABLE_NAME + "("
					+ Projects._ID + " INTEGER PRIMARY KEY,"
					+ Projects.TITLE + " TEXT,"
					+ Projects.IDENTIFIER + " TEXT,"
					+ Projects.TYPE + " INTEGER DEFAULT " + Projects.TYPE_SESSION 
					+ ");");

			addSimpleProject(db, mContext);
			addSessionProject(db, mContext);
		}
		
		void createSessionsTable(SQLiteDatabase db)
		{
			db.execSQL("CREATE TABLE " + Sessions.TABLE_NAME + "("
					+ Sessions._ID + " INTEGER PRIMARY KEY,"
					+ Sessions.PROJECT_ID + " INTEGER,"
					+ Sessions.TITLE + " TEXT,"
					+ Sessions.IDENTIFIER + " TEXT,"
					+ Sessions.START_TIME + " INTEGER,"
					+ Sessions.END_TIME + " INTEGER"
					+ ");");
		}
		
		void createAnnotationsTable(SQLiteDatabase db)
		{
			db.execSQL("CREATE TABLE " + Annotations.TABLE_NAME + "("
					+ Annotations._ID + " INTEGER PRIMARY KEY,"
					+ Annotations.SESSION_ID + " INTEGER,"
					+ Annotations.START_TIME + " INTEGER,"
					+ Annotations.END_TIME + " INTEGER,"
					+ Annotations.FILE_NAME + " TEXT,"
					+ Annotations.VIEWED + " BOOLEAN DEFAULT FALSE,"
					+ Annotations.LABEL + " TEXT DEFAULT ''"
					+ ");");
		}

		@SuppressWarnings("unused")
		void migrateTable(SQLiteDatabase db, String name)
		{
			String backupName = name + "_backup";
			db.execSQL("DROP TABLE IF EXISTS " + backupName + ";");
			db.execSQL("ALTER TABLE " + name + " RENAME TO " + backupName + ";");
			if(name.equals(Sessions.TABLE_NAME))
				createSessionsTable(db);
			else
				createAnnotationsTable(db);
			db.execSQL("INSERT INTO " + name + " SELECT * FROM " + backupName + ";");
			db.execSQL("DROP TABLE IF EXISTS " + backupName + ";");
		}
		void upgrade5to6(SQLiteDatabase db)
		{
			db.execSQL("CREATE TABLE " + AppData.TABLE_NAME + "("
					+ AppData._ID + " INTEGER PRIMARY KEY"
					+ ");");
		}
		void upgrade6to7(SQLiteDatabase db)
		{
			db.execSQL("ALTER TABLE " + Annotations.TABLE_NAME + " ADD COLUMN " + Annotations.LABEL + " TEXT DEFAULT ''");
		}
		void upgrade7to8(SQLiteDatabase db)
		{
			db.execSQL("ALTER TABLE " + Projects.TABLE_NAME + " ADD COLUMN " + Projects.TYPE + " INTEGER DEFAULT " + Projects.TYPE_SESSION);
		}
		void upgrade8to9(SQLiteDatabase db)
		{
		    // Display license if this is the first time running this version.
		    String[] appDataProjection =
		    {
		    	AppData._ID,
		        AppData.KEY,
		    	AppData.VALUE
		    };
		    
	    	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
	    	SharedPreferences.Editor editor = preferences.edit();
	    	
	    	// get the current project information from AppData and move to preferences
	    	Cursor project = db.query(AppData.TABLE_NAME, appDataProjection, AppData.KEY + "=" + "'current_project_id'", null, null, null, AppData.DEFAULT_SORT_ORDER, null);
	    	if(project.getCount()>0)
	    	{
	    		project.moveToFirst();
	    		editor.putLong("current_project_id", project.getLong(2));
	    	}
	    	project.close();
	    	
	    	Cursor visited_version = db.query(AppData.TABLE_NAME, appDataProjection, AppData.KEY + "=" + "'app_visited_version'", null, null, null, AppData.DEFAULT_SORT_ORDER, null);
	    	if(visited_version.getCount()>0)
	    	{
	    		visited_version.moveToFirst();
	    		editor.putFloat("app_visited_version", Float.valueOf(visited_version.getString(2)));
	    	}
	    	
	    	editor.commit();
	    	
	    	db.execSQL("DROP TABLE " + AppData.TABLE_NAME);
		}
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            Log.w("RehearsalAssistant", "Upgrading database from version " + oldVersion + " to " + newVersion);
            switch(oldVersion)
            {
            case 5:
				upgrade5to6(db);
            case 6:
				upgrade6to7(db);
            case 7:
				upgrade7to8(db);
            case 8:
            	upgrade8to9(db);
            	break;
            default:
	            Log.w("RehearsalAssistant", "Reinitializing database tables");
            	// drop tables that existed prior to 5
	            db.execSQL("DROP TABLE IF EXISTS " + Projects.TABLE_NAME);
	            db.execSQL("DROP TABLE IF EXISTS " + Sessions.TABLE_NAME);
	            db.execSQL("DROP TABLE IF EXISTS " + Annotations.TABLE_NAME);
	            db.execSQL("DROP TABLE IF EXISTS runs");
	            onCreate(db);
			}
        }
		
		Context mContext;
	}

    private static final int APPDATA = 1;
    private static final int APPDATA_ID = 2;
    private static final int PROJECTS = 3;
    private static final int PROJECT_ID = 4;
    private static final int SESSIONS = 5;
    private static final int SESSION_ID = 6;
    private static final int ANNOTATIONS = 7;
    private static final int ANNOTATION_ID = 8;

    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> sAppDataProjectionMap;
    private static HashMap<String, String> sProjectsProjectionMap;
    private static HashMap<String, String> sSessionsProjectionMap;
    private static HashMap<String, String> sAnnotationsProjectionMap;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "appdata", APPDATA);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "appdata/#", APPDATA_ID);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "projects", PROJECTS);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "projects/#", PROJECT_ID);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "sessions", SESSIONS);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "sessions/#", SESSION_ID);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "annotations", ANNOTATIONS);
        sUriMatcher.addURI(Rehearsal.AUTHORITY, "annotations/#", ANNOTATION_ID);

        sAppDataProjectionMap = new HashMap<String, String>();
        sAppDataProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sAppDataProjectionMap.put(AppData.KEY, AppData.KEY);
        sAppDataProjectionMap.put(AppData.VALUE, AppData.VALUE);

        sProjectsProjectionMap = new HashMap<String, String>();
        sProjectsProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sProjectsProjectionMap.put(Projects.TITLE, Projects.TITLE);
        sProjectsProjectionMap.put(Projects.IDENTIFIER, Projects.IDENTIFIER);
        sProjectsProjectionMap.put(Projects.TYPE, Projects.TYPE);

        sSessionsProjectionMap = new HashMap<String, String>();
        sSessionsProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sSessionsProjectionMap.put(Sessions.PROJECT_ID, Sessions.PROJECT_ID);
        sSessionsProjectionMap.put(Sessions.TITLE, Sessions.TITLE);
        sSessionsProjectionMap.put(Sessions.IDENTIFIER, Sessions.IDENTIFIER);
        sSessionsProjectionMap.put(Sessions.START_TIME, Sessions.START_TIME);
        sSessionsProjectionMap.put(Sessions.END_TIME, Sessions.END_TIME);

        sAnnotationsProjectionMap = new HashMap<String, String>();
        sAnnotationsProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sAnnotationsProjectionMap.put(Annotations.SESSION_ID, Annotations.SESSION_ID);
        sAnnotationsProjectionMap.put(Annotations.START_TIME, Annotations.START_TIME);
        sAnnotationsProjectionMap.put(Annotations.END_TIME, Annotations.END_TIME);
        sAnnotationsProjectionMap.put(Annotations.FILE_NAME, Annotations.FILE_NAME);
        sAnnotationsProjectionMap.put(Annotations.LABEL, Annotations.LABEL);
        sAnnotationsProjectionMap.put(Annotations.VIEWED, Annotations.VIEWED);
    }

	private DatabaseHelper mOpenHelper;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case SESSIONS:
        	Cursor c = db.query(Sessions.TABLE_NAME, new String[] {Sessions._ID}, selection, selectionArgs, null, null, null);
        	count = 0;
        	for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext())
        		count += delete(ContentUris.withAppendedId(Sessions.CONTENT_URI, c.getLong(0)), null, null);
            break;

        case SESSION_ID:
            String sessionId = uri.getPathSegments().get(1);
            count = db.delete(Sessions.TABLE_NAME, Sessions._ID + "=" + sessionId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            if(count>0)
            	deleteAnnotations(db, Annotations.SESSION_ID + "=" + sessionId, null);
            break;

        case ANNOTATIONS:
            count = deleteAnnotations(db, selection, selectionArgs);
            break;

        case ANNOTATION_ID:
            String annotationId = uri.getPathSegments().get(1);
            count = deleteAnnotations(db, Annotations._ID + "=" + annotationId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
            
        case PROJECT_ID:
        	String projectId = uri.getPathSegments().get(1);
            count = db.delete(Projects.TABLE_NAME, Projects._ID + "=" + projectId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            if(count>0)
            	delete(Sessions.CONTENT_URI, Sessions.PROJECT_ID + "=" + projectId, null);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
	}
	
	int deleteAnnotations(SQLiteDatabase db, String selection, String[] selectionArgs)
	{
		// query and erase files
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		
        qb.setTables(Annotations.TABLE_NAME);
        qb.setProjectionMap(sAnnotationsProjectionMap);

        String[] projection =
        {
        	Annotations.FILE_NAME        	
        };
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, null);

        for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext())
        {
        	if(c.getString(0)!=null)
        	{
        		Log.w("Rehearsal Assistant erasing", c.getString(0));
        		(new File(c.getString(0))).delete();
        	}
        }
		// delete
		int result = db.delete(Annotations.TABLE_NAME, selection, selectionArgs);
		c.close();
		return result;
	}

	@Override
	public String getType(Uri uri)
	{
        switch (sUriMatcher.match(uri)) {
        case APPDATA:
            return AppData.CONTENT_TYPE;

        case APPDATA_ID:
            return AppData.CONTENT_ITEM_TYPE;

        case PROJECTS:
            return Projects.CONTENT_TYPE;

        case PROJECT_ID:
            return Projects.CONTENT_ITEM_TYPE;

        case SESSIONS:
            return Sessions.CONTENT_TYPE;

        case SESSION_ID:
            return Sessions.CONTENT_ITEM_TYPE;
            
        case ANNOTATIONS:
        	return Annotations.CONTENT_TYPE;
        
        case ANNOTATION_ID:
        	return Annotations.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues)
	{
        ContentValues values;
        if (initialValues != null)
            values = new ContentValues(initialValues);
        else
            values = new ContentValues();
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        
        long rowId=0;
        Uri contentURI;

		switch(sUriMatcher.match(uri))
		{
			case APPDATA:
			{
		        rowId = db.insert(AppData.TABLE_NAME, AppData.KEY, values);
		        contentURI = AppData.CONTENT_URI;
		        break;
			}
			case SESSIONS:
			{
		    	if(!values.containsKey(Sessions.IDENTIFIER))
		    		values.put(Sessions.IDENTIFIER, values.getAsString(Sessions.TITLE).toLowerCase().replace(" ", "_"));
		 
		        rowId = db.insert(Sessions.TABLE_NAME, Sessions.TITLE, values);
		        contentURI = Sessions.CONTENT_URI;
		        break;
			}
			case ANNOTATIONS:
			{
				rowId = db.insert(Annotations.TABLE_NAME, Annotations.FILE_NAME, values);
				contentURI = Annotations.CONTENT_URI;
		        break;
			}
			case PROJECTS:
			{
				rowId = db.insert(Projects.TABLE_NAME, Projects.TITLE, values);
				contentURI = Projects.CONTENT_URI;
				break;
			}
			default:
		        throw new IllegalArgumentException("Unknown URI " + uri);
		}
        if (rowId >= 0) {
            Uri noteUri = ContentUris.withAppendedId(contentURI, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder)
	{
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (sUriMatcher.match(uri)) {
        case APPDATA:
            qb.setTables(AppData.TABLE_NAME);
            qb.setProjectionMap(sAppDataProjectionMap);
            break;

        case APPDATA_ID:
            qb.setTables(AppData.TABLE_NAME);
            qb.setProjectionMap(sAppDataProjectionMap);
            qb.appendWhere(AppData._ID + "=" + uri.getPathSegments().get(1));
            break;

        case PROJECTS:
            qb.setTables(Projects.TABLE_NAME);
            qb.setProjectionMap(sProjectsProjectionMap);
            break;

        case PROJECT_ID:
            qb.setTables(Projects.TABLE_NAME);
            qb.setProjectionMap(sProjectsProjectionMap);
            qb.appendWhere(Projects._ID + "=" + uri.getPathSegments().get(1));
            break;

        case SESSIONS:
            qb.setTables(Sessions.TABLE_NAME);
            qb.setProjectionMap(sSessionsProjectionMap);
            break;

        case SESSION_ID:
            qb.setTables(Sessions.TABLE_NAME);
            qb.setProjectionMap(sSessionsProjectionMap);
            qb.appendWhere(Sessions._ID + "=" + uri.getPathSegments().get(1));
            break;
            
        case ANNOTATIONS:
            qb.setTables(Annotations.TABLE_NAME);
            qb.setProjectionMap(sAnnotationsProjectionMap);
            break;

        case ANNOTATION_ID:
            qb.setTables(Annotations.TABLE_NAME);
            qb.setProjectionMap(sAnnotationsProjectionMap);
            qb.appendWhere(Annotations._ID + "=" + uri.getPathSegments().get(1));
            break;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Rehearsal.Sessions.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
	}

	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs)
	{
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        if(uri.getPathSegments().size()>1)
        	selection = BaseColumns._ID + "=" + uri.getPathSegments().get(1)
        		+ (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
        
        switch (sUriMatcher.match(uri)) {
        case APPDATA:
        	count = db.update(AppData.TABLE_NAME, values, selection, selectionArgs);
        	break;
        case PROJECT_ID:
        	count = db.update(Projects.TABLE_NAME, values, selection, selectionArgs);
        	break;
        case SESSION_ID:
            count = db.update(Sessions.TABLE_NAME, values, selection, selectionArgs);
            break;
        case ANNOTATION_ID:
        	count = db.update(Annotations.TABLE_NAME, values, selection, selectionArgs);
        	break;
        	
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
	}
}