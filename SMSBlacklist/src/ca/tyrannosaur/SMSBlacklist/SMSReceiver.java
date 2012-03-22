package ca.tyrannosaur.SMSBlacklist;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {

   private static final String TAG = Blacklist.class.getName();
   private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

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
      int invalidCount = 0;

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

         int cValid = 0;
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

         // Prevent this broadcast from propagating if there are no valid
         // messages
         if (cValid == 0)
            abortBroadcast();

         invalidCount = invalidMessages.size();
      }

      return invalidCount;
   }

   @Override
   public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "Examining new SMSes...");
      Intent startService = new Intent(BlacklistService.ACTION_START_SERVICE);

      try {
         // Start the service if it's been killed for some reason
         context.startService(startService);

         // Bind the already-running service to the API. Since the service is
         // using IPC, all calls will not be blocking.
         IBinder service = peekService(context, startService);

         BlacklistApi api = BlacklistApi.Stub.asInterface(service);

         if (api == null)
            Log.e(TAG, "The service wasn't started in time, so no SMSes are being filtered for safety");

         if (api != null && api.getEnabled() && SMS_RECEIVED.equals(intent.getAction())) {
            int invalidCount = filterBlacklistedMessages(context, intent);
            Log.i(TAG, String.format("Filtered %d SMS(es)", invalidCount));
         }
      }
      catch (RemoteException e) {
         Log.e(TAG, "Couldn't contact BlacklistService");
      }
      catch (SecurityException e) {
         Log.e(TAG, "Not allowed to start BlacklistService");
      }

      Log.i(TAG, "Finished examining new SMSes");
   }

}