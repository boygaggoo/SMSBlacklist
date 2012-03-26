package ca.tyrannosaur.SMSBlacklist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {

   private static final String TAG = Blacklist.class.getName();
   private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

   @Override
   public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "Examining new SMSes...");
      
      if (intent != null && SMS_RECEIVED.equals(intent.getAction()) && !intent.hasExtra(BlacklistService.EXTRA_SEEN)) {
            
         Log.d(TAG, "Sending new SMSes to service for examination");

         intent.setAction(BlacklistService.ACTION_START_AND_FILTER);
         intent.setComponent(null);
         
         try {
            context.getApplicationContext().startService(intent);
            abortBroadcast();
         }
         catch (SecurityException e) {
            Log.e(TAG, "Not allowed to start BlacklistService");
         }
         
      }
   }

}