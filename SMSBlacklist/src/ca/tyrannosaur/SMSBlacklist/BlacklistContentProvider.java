package ca.tyrannosaur.SMSBlacklist;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.UUID;

import org.json.simple.JSONObject;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;

/**
 * Abstracted creation and modification of blacklist filters and message logs.
 * 
 */
public class BlacklistContentProvider extends ContentProvider {

   /**
    * Aggregates additional SQL 'where' clauses into one clause, given an
    * initial where fragment and its associated parameters (if any).
    * 
    */
   public static class SQLiteWhereExtender {

      private LinkedList<String> parameters;
      private LinkedList<String> whereStatements;

      public SQLiteWhereExtender(String where, String[] whereParameters) {
         parameters = new LinkedList<String>();
         whereStatements = new LinkedList<String>();
         append(where, whereParameters);
      }

      public void append(String where, String[] whereParameters) {
         if (where != null && !TextUtils.isEmpty(where))
            whereStatements.add(where);

         if (whereParameters != null) {
            for (int i = 0; i < whereParameters.length; i++)
               parameters.add(whereParameters[i]);
         }
      }

      public String getWhere() {
         StringBuilder builder = new StringBuilder();
         Iterator<String> iter = whereStatements.iterator();

         while (iter.hasNext()) {
            builder.append("(");
            builder.append(iter.next());
            builder.append(")");

            if (!iter.hasNext()) {
               break;
            }
            builder.append(" AND ");
         }
         return builder.toString();
      }

      public String[] getParameters() {
         return parameters.toArray(new String[parameters.size()]);
      }
   }

  
   private static final String TAG = BlacklistContentProvider.class.getName();
   private static final String EXTERNAL_STORAGE_PATH = 
            "/Android/data/ca.tyrannosaur.SMSBlacklist/files/";
   
   private static final int 
            FILTERS = 1, 
            FILTER_ID = 2, 
            LOGS_FOR_FILTER = 3, 
            LOG_ID = 4, 
            LOGS = 5;

   private static final UriMatcher uriMatcher;

   static {
      uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

      // All filters
      uriMatcher.addURI(Contract.AUTHORITY, "filters", FILTERS);

      // A specific filter
      uriMatcher.addURI(Contract.AUTHORITY, "filters/#", FILTER_ID);

      // All logged messages. Only used for inserts and updates
      uriMatcher.addURI(Contract.AUTHORITY, "logs", LOGS);

      // All logged messages for a specific filter. Only used for querying
      uriMatcher.addURI(Contract.AUTHORITY, "logsForFilter/#", LOGS_FOR_FILTER);

      // A specific logged message. Only used for querying and deleting
      uriMatcher.addURI(Contract.AUTHORITY, "logs/#", LOG_ID);
   }
   
   private DatabaseHelper dbHelper;
   

   /*
    * Construct a JSONObject that represents the given filter and SMS 
    * information from the provided ContentValues. 
    */
   @SuppressWarnings("unchecked")
   private static final JSONObject getLogJSON(ContentValues values)  {
      
      String filterText = values.getAsString(Contract.Filters.FILTER_TEXT);
      String filterNote = values.getAsString(Contract.Filters.NOTE);
      String filterAffinity = values.getAsString(Contract.Filters.FILTER_MATCH_AFFINITY);
      long received = values.getAsLong(Contract.Logs.DATE_RECEIVED);
      
      SmsMessage message = SmsMessage.createFromPdu(
                                     values.getAsByteArray(Contract.Logs.MESSAGE_PDU));

      JSONObject filterObject = new JSONObject();
      JSONObject messageObject = new JSONObject();
      JSONObject rootObject = new JSONObject();

      filterObject.put("text", filterText);
      filterObject.put("note", filterNote);
      filterObject.put("affinity", filterAffinity);

      messageObject.put("sender", message.getDisplayOriginatingAddress());
      messageObject.put("body", message.getDisplayMessageBody());
      messageObject.put("sent", new Date(message.getTimestampMillis()).toGMTString());
      messageObject.put("received", new Date(received).toGMTString());

      rootObject.put("filter", filterObject);
      rootObject.put("message", messageObject);
      
      return rootObject;
   }   

   /*
    * Is the external storage writeable?
    */
   private static boolean isEternalStorageWriteable() {
      String state = Environment.getExternalStorageState();

      if (Environment.MEDIA_MOUNTED.equals(state))
         return true;      
      else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
         return false;      
      else
         return false;      
   }
   
   @Override
   public boolean onCreate() {
      dbHelper = new DatabaseHelper(getContext());
      return true;
   }

   @Override
   public String getType(Uri uri) {
      switch (uriMatcher.match(uri)) {
         case FILTERS:
            return Contract.Filters.CONTENT_TYPE;

         case FILTER_ID:
            return Contract.Filters.CONTENT_ITEM_TYPE;

         case LOGS_FOR_FILTER:
            return Contract.Logs.CONTENT_TYPE;

         case LOG_ID:
            return Contract.Logs.CONTENT_ITEM_TYPE;

         default:
            throw new IllegalArgumentException(String.format("Unkown URI %s", uri));
      }
   }

   @Override
   public int delete(Uri uri, String where, String[] whereArgs) {
      SQLiteDatabase db = dbHelper.getWritableDatabase();
      SQLiteWhereExtender whereExtender = new SQLiteWhereExtender(where, whereArgs);
      int count;

      switch (uriMatcher.match(uri)) {
         case FILTERS:
            {
               count = db.delete(DatabaseHelper.TABLE_FILTERS, where, whereArgs);
               break;
            }
         case FILTER_ID:
            {
               whereExtender.append(
                  String.format("%s = ?", Contract.Filters._ID),
                  new String[] {
                     uri.getPathSegments().get(1)
                  });

               count = db.delete(
                  DatabaseHelper.TABLE_FILTERS, 
                  whereExtender.getWhere(), 
                  whereExtender.getParameters());
               break;
            }
         case LOGS_FOR_FILTER:
            {
               whereExtender.append(
                  String.format("%s = ?", Contract.Filters._ID),
                  new String[] {
                     uri.getPathSegments().get(1)
                  });

               count = db.delete(
                  DatabaseHelper.TABLE_LOGS, 
                  whereExtender.getWhere(), 
                  whereExtender.getParameters());
               break;
            }
         case LOG_ID:
            {
               whereExtender.append(
                  String.format("%s = ?", Contract.Logs._ID),
                  new String[] {
                     uri.getPathSegments().get(1)
                  });

               count = db.delete(
                  DatabaseHelper.TABLE_LOGS, 
                  whereExtender.getWhere(), 
                  whereExtender.getParameters());
               break;
            }
         default:
            {
               throw new IllegalArgumentException(String.format("Unknown URI %s", uri));
            }
      }

      getContext().getContentResolver().notifyChange(uri, null);
      return count;
   }

   @Override
   public int bulkInsert(Uri uri, ContentValues[] allValues) {
      int match = uriMatcher.match(uri);

      switch (match) {
         case LOGS:
            {
               int c = 0;
               for (ContentValues values : allValues) {
                  insertLogForFilter(uri, values);
                  c += 1;
               }
               return c;
            }
         default:
            {
               throw new IllegalArgumentException(String.format("Unknown URI %s", uri));
            }
      }
   }

   @Override
   public Uri insert(Uri uri, ContentValues initialValues) {
      ContentValues values = (initialValues != null)
            ? new ContentValues(initialValues)
            : new ContentValues();

      SQLiteDatabase db = dbHelper.getWritableDatabase();
      int match = uriMatcher.match(uri);
      long rowId;
      Uri toReturn;

      switch (match) {
         case FILTERS:
            {
               rowId = db.insert(DatabaseHelper.TABLE_FILTERS, null, values);

               if (rowId > 0) {
                  toReturn = Contract.buildFilterUri(rowId);
                  getContext().getContentResolver().notifyChange(toReturn, null);
                  return toReturn;
               }
               else {
                  throw new SQLException(String.format("Failed to insert row into %s", uri));
               }
            }
         case LOGS_FOR_FILTER:
            {
               insertLogForFilter(uri, values);
            }
         default:
            {
               throw new IllegalArgumentException(String.format("Unknown URI %s", uri));
            }
      }
   }
   

   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
      int match = uriMatcher.match(uri);

      SQLiteDatabase db = dbHelper.getWritableDatabase();
      SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();

      switch (match) {
         case FILTERS:
            {
               qBuilder.setTables(DatabaseHelper.TABLE_FILTERS);
               break;
            }
         case FILTER_ID:
            {
               qBuilder.setTables(DatabaseHelper.TABLE_FILTERS);
               qBuilder.appendWhere(String.format("%s = ", Contract.Filters._ID));
               qBuilder.appendWhereEscapeString(uri.getPathSegments().get(1));
               break;
            }
         default:
            {
               throw new IllegalArgumentException(String.format("Unknown URI %s", uri));
            }
      }

      Cursor c = qBuilder.query(
         db,
         projection,
         selection,
         selectionArgs,
         null,
         null,
         sortOrder == null ? Contract.Filters.DEFAULT_SORT_ORDER : sortOrder);
      c.setNotificationUri(getContext().getContentResolver(), uri);
      return c;
   }

   /**
    * Updates are not supported for any data type at the current time.
    * 
    * @see android.content.ContentProvider#update(android.net.Uri,
    *      android.content.ContentValues, java.lang.String[],
    *      java.lang.String[])
    */
   @Override
   public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
      throw new UnsupportedOperationException();
   }

   /*
    * Insert a single log entry into the database, consisting of
    * the name of the file written to disk.
    */
   private Uri insertLogForFilter(Uri uri, ContentValues values) {
      Uri toReturn;
      
      SQLiteDatabase db = dbHelper.getWritableDatabase();            
      JSONObject logJSON = BlacklistContentProvider.getLogJSON(values);                  
      UUID uuid = UUID.randomUUID();
      
      File dataPath = new File(
               Environment.getExternalStorageDirectory(),
               EXTERNAL_STORAGE_PATH);
      File dataFile = new File(dataPath, String.format("%s.json", uuid.toString()));
                  
      if (!isEternalStorageWriteable()) {
         Log.e(TAG, "External storage was not writeable and a message could not be saved");         
      }
      else {
         if (dataPath != null)
            dataPath.mkdirs();
         
         try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile));
            writer.write(logJSON.toJSONString());
            writer.close();
         }
         catch (IOException e) {
            Log.e(TAG, "Could not write SMS JSON to external media");
         }
      }

      // Insert the location of the message file into the database
      String filterId = values.getAsString(Contract.Logs.FILTER_ID);

      ContentValues logValues = new ContentValues();
      logValues.put(Contract.Logs.FILTER_ID, filterId);
      logValues.put(Contract.Logs.PATH, dataFile.getName());

      long rowId = db.insert(DatabaseHelper.TABLE_LOGS, null, logValues);
      db.execSQL(
         String.format(
            "update %s set %s = %s + 1 where %s = ?",
            DatabaseHelper.TABLE_FILTERS,
            Contract.Filters.UNREAD,
            Contract.Filters.UNREAD,
            Contract.Filters._ID),
         new String[] {
            filterId
         });

      if (rowId > 0) {
         toReturn = Contract.buildLogsUri(rowId);
         getContext().getContentResolver().notifyChange(toReturn, null);
         getContext().getContentResolver().notifyChange(
            Contract.buildFilterUri(Long.valueOf(filterId)), null);
      }
      else {
         throw new SQLException(String.format("Failed to insert row into %s", uri));
      }

      return toReturn;
   }


}
