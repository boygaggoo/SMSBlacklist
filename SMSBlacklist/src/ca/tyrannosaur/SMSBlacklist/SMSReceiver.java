package ca.tyrannosaur.SMSBlacklist;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

   /**
    * Build a regular expression {@link Pattern} to match a filter and affinity.
    * Affinities determine which side (if any) of the string contain a wildcard.
    * 
    * {@link AFFINITY_EXACT} matches a filter exactly. {@link AFFINITY_LEFT}
    * produces a regular expression with a wildcard to the right.
    * {@link AFFINITY_RIGHT} produces a regular expression with a wildcard to
    * the left.
    * 
    * For instance, a right affinity will match filter {@code 1234} to
    * {@code 00001234}, but not match {@code 12340000}
    * 
    * 
    * @param c
    *           a {@link Cursor} containing filters and affinities
    * @return a {@link Pattern} used to test a phone number
    */
   private Pattern buildFilterPattern(Cursor c) {
      String affinity = c.getString(c.getColumnIndexOrThrow(BlacklistContract.Filters.FILTER_MATCH_AFFINITY));
      String text = c.getString(c.getColumnIndexOrThrow(BlacklistContract.Filters.FILTER_TEXT));

      return BlacklistContract.buildFilterPattern(text, affinity);
   }

   private int filterBlacklistedMessages(Context context, Intent intent) {
      Bundle bundle = intent.getExtras();
      ContentResolver contentResolver = context.getContentResolver();
      int invalidCount = 0;

      if (bundle != null) {
         // Get a list of filters
         Cursor filters = contentResolver.query(BlacklistContract.buildFiltersListUri(), null, null, null, BlacklistContract.Filters.DEFAULT_SORT_ORDER);

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
               filterPattern = buildFilterPattern(filters);
               filterMatcher = filterPattern.matcher(message.getOriginatingAddress());

               // Stuff the invalid message in an array for later
               if (filterMatcher.matches()) {
                  ContentValues row = new ContentValues();
                  row.put(BlacklistContract.Filters.FILTER_TEXT, filters.getString(filters.getColumnIndexOrThrow(BlacklistContract.Filters.FILTER_TEXT)));
                  row.put(BlacklistContract.Filters.FILTER_MATCH_AFFINITY, filters.getString(filters.getColumnIndexOrThrow(BlacklistContract.Filters.FILTER_MATCH_AFFINITY)));
                  row.put(BlacklistContract.Filters.NOTE, filters.getString(filters.getColumnIndexOrThrow(BlacklistContract.Filters.NOTE)));
                  row.put(BlacklistContract.Logs.DATE_RECEIVED, message.getTimestampMillis());
                  row.put(BlacklistContract.Logs.FILTER_ID, filters.getString(filters.getColumnIndexOrThrow(BlacklistContract.Filters._ID)));
                  row.put(BlacklistContract.Logs.MESSAGE_PDU, message.getPdu().clone());

                  invalidMessages.add(row);
                  invalidFound = true;
                  break;
               }
               else {
                  filters.moveToNext();
               }
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
            bundle.putSerializable("pdus", trimmed);
         }

         // Write the messages to the log
         ContentValues[] cv = invalidMessages.toArray(new ContentValues[invalidMessages.size()]);
         contentResolver.bulkInsert(BlacklistContract.buildLogsListUri(), cv);

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

         // Bind the already-running service to the api. Since the service is
         // using
         // IPC, all calls will not be blocking.
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