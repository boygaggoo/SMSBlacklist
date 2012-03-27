package ca.tyrannosaur.SMSBlacklist;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class BlacklistService extends Service {

   public static final String ACTION_START_SERVICE = "ca.tyrannosaur.SMSBlacklist.BlacklistService.START_SERVICE";
   public static final String ACTION_START_AND_FILTER = "ca.tyrannosaur.SMSBlacklist.BlacklistService.START_AND_FILTER";

   public static final String EXTRA_SEEN = "ca.tyrannosaur.SMSBlacklist.BlacklistService.EXTRA_SEEN";
   public static final String EXTRA_ORIGINAL_INTENT_ACTION = "ca.tyrannosaur.SMSBlacklist.BlacklistService.EXTRA_ORIGINAL_INTENT_ACTION";   
   
   private static final String TAG = Blacklist.class.getName();
   private static final int ONGOING_ID = 0;
   private static final String PREFS_BLACKLIST_ENABLED_KEY = "blacklistEnabled";
   private static boolean DEFAULT = true;

   private SharedPreferences preferences;

   private List<BlacklistListener> listeners = new ArrayList<BlacklistListener>();

   private BlacklistApi.Stub apiEndpoint = new BlacklistApi.Stub() {

      @Override
      public synchronized boolean getEnabled() throws RemoteException {
         if (!preferences.contains(PREFS_BLACKLIST_ENABLED_KEY)) {
            Editor edit = preferences.edit();
            edit.putBoolean(PREFS_BLACKLIST_ENABLED_KEY, DEFAULT).commit();
         }

         return preferences.getBoolean(PREFS_BLACKLIST_ENABLED_KEY, DEFAULT);
      }

      @Override
      public synchronized boolean setEnabled(boolean enabled) throws RemoteException {
         boolean old = getEnabled();

         Editor edit = preferences.edit();
         edit.putBoolean(PREFS_BLACKLIST_ENABLED_KEY, enabled).commit();

         if (enabled)
            showNotification();
         else
            hideNotification();

         return old;
      }

      @Override
      public synchronized void addListener(BlacklistListener listener) throws RemoteException {
         listeners.add(listener);
      }

      @Override
      public synchronized void removeListener(BlacklistListener listener) throws RemoteException {
         listeners.remove(listener);
      }

   };

   @Override
   public IBinder onBind(Intent intent) {
      if (ACTION_START_SERVICE.equals(intent.getAction())) {
         return apiEndpoint;
      }
      else {
         return null;
      }
   }

   @Override
   public void onCreate() {
      super.onCreate();

      preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

      try {
         if (apiEndpoint.getEnabled())
            showNotification();
         else
            hideNotification();
      }
      catch (RemoteException e) {
      }

      Log.d(TAG, "Started service");
   }

   @Override
   public void onDestroy() {
      super.onDestroy();
      hideNotification();

      Log.d(TAG, "Stopped service");
   }

   private void showNotification() {
      NotificationManager nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

      Notification notification = new Notification(
         R.drawable.ic_stat_skull, 
         getString(R.string.notification_blacklistEnabled), 
         0);

      notification.flags |= Notification.FLAG_NO_CLEAR;
      notification.flags |= Notification.FLAG_ONGOING_EVENT;

      // Clicking on the notification opens up the application proper
      Intent notificationIntent = new Intent(Intent.ACTION_MAIN, null, this, Blacklist.class);
      PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

      notification.setLatestEventInfo(
         this, 
         getString(R.string.app_name), 
         getString(R.string.notification_blacklistEnabled), 
         contentIntent);

      nmanager.notify(ONGOING_ID, notification);
   }

   private void hideNotification() {
      NotificationManager nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      nmanager.cancel(ONGOING_ID);
   }

   private Pattern buildFilterPattern(Cursor c) {      
      String affinity = c.getString(
                           c.getColumnIndexOrThrow(
                                 Contract.Filters.FILTER_MATCH_AFFINITY));

      String text = c.getString(
                        c.getColumnIndexOrThrow(
                                 Contract.Filters.FILTER_TEXT));

      return Contract.buildFilterPattern(
               text,
               affinity);
   }

   private int filterBlacklistedMessages(Context context, Intent intent) {
      Bundle bundle = intent.getExtras();
      ContentResolver contentResolver = context.getContentResolver();
      int cValid = 0;
      
      if (bundle != null) {
         // Get a list of filters
         Cursor filters = contentResolver.query(
                  Contract.buildFiltersListUri(),
                  null,
                  null,
                  null,
                  Contract.Filters.DEFAULT_SORT_ORDER);

         // The raw SMS data
         Object[] pdus = (Object[]) bundle.get("pdus");

         // The SMS data to put back into the intent
         byte[][] validPdus = new byte[pdus.length][];

         Log.i(TAG, String.format("Examining %d SMSes...", pdus.length));

         // The invalid messages to write to the log
         ArrayList<ContentValues> invalidMessages = new ArrayList<ContentValues>();
         
         for (int i = 0; i < pdus.length; i++) {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdus[i]);
            Pattern filterPattern;
            Matcher filterMatcher;
            boolean invalidFound = false;

            filters.moveToFirst();
            while (filters.isAfterLast() == false) {
               try {
                  filterPattern = buildFilterPattern(filters);
                  filterMatcher = filterPattern.matcher(message.getOriginatingAddress());

                  // Stuff the invalid message in an array for later
                  if (filterMatcher.matches()) {
                     ContentValues row = new ContentValues();                     
                     row.put(
                              Contract.Filters.FILTER_TEXT,
                              filters.getString(filters.getColumnIndexOrThrow(Contract.Filters.FILTER_TEXT)));
                     row.put(
                              Contract.Filters.FILTER_MATCH_AFFINITY,
                              filters.getString(filters.getColumnIndexOrThrow(Contract.Filters.FILTER_MATCH_AFFINITY)));
                     row.put(
                              Contract.Filters.NOTE,
                              filters.getString(filters.getColumnIndexOrThrow(Contract.Filters.NOTE)));
                     row.put(
                              Contract.Logs.DATE_RECEIVED,
                              message.getTimestampMillis());
                     row.put(
                              Contract.Logs.FILTER_ID,
                              filters.getString(filters.getColumnIndexOrThrow(Contract.Filters._ID)));
                     row.put(
                              Contract.Logs.MESSAGE_PDU,
                              message.getPdu().clone());

                     invalidMessages.add(row);
                     invalidFound = true;
                     break;
                  }
               }
               catch (PatternSyntaxException e) {
                  Log.w(
                           TAG,
                           "An invalid pattern was inserted in the database",
                           e);
               }

               filters.moveToNext();

            }

            if (!invalidFound)
               validPdus[cValid++] = (byte[]) pdus[i];
         }

         filters.close();

         // Stuff the valid pdus back in the intent
         if (cValid < pdus.length) {
            byte[][] trimmed = new byte[cValid][];
            for (int i = 0; i < cValid; i++)
               trimmed[i] = validPdus[i].clone();
            bundle.putSerializable(
                     "pdus",
                     trimmed);
         }

         // Write the messages to the log
         ContentValues[] cv = invalidMessages.toArray(new ContentValues[invalidMessages.size()]);
         contentResolver.bulkInsert(
                  Contract.buildLogsListUri(),
                  cv);
         
         Log.i(TAG, String.format("Filtered %d SMS(es)", pdus.length - cValid));         
      }

      return cValid;
   }   
   
   private void rebroadcast(Intent intent) {
      intent.setAction("android.provider.Telephony.SMS_RECEIVED");
      intent.putExtra(EXTRA_SEEN, true);
      intent.setClassName("com.android.mms", "com.android.mms.transaction.SmsReceiverService");                  
      getApplicationContext().startService(intent);
   }
   
   /*
    * Starts the service and possibly filters an SMS at the same time.
    * Re-broadcasts the original Intent with the new SMSes.
    * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
    */
   public int onStartCommand(Intent intent, int flags, int startId)  {
      Context context =  getApplicationContext();
      boolean shouldRebroadcast = true;
  
      if (intent != null && ACTION_START_AND_FILTER.equals(intent.getAction())) {         
         try {
            if (apiEndpoint.getEnabled()) {               
               shouldRebroadcast = filterBlacklistedMessages(context, intent) > 0;                             
            }            
         }
         catch (RemoteException e) {
            Log.d(TAG, "Could not connect to API");
         }
         catch (NullPointerException e) {
            Log.d(TAG, "API not ready");
         }
         finally {         
            if (shouldRebroadcast) {
               Log.d(TAG, "Rebroadcasting SMS");
               rebroadcast(intent);
            }
            else {
               Log.d(TAG, "Not rebroadcasting SMS");
            }
         }
      }
      
      return super.onStartCommand(intent, flags, startId);
   }
   
}
