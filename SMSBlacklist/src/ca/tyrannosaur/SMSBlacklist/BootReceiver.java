package ca.tyrannosaur.SMSBlacklist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

   @Override
   public void onReceive(Context context, Intent intent) {
      Intent startServiceIntent = new Intent(context, BlacklistService.class);
      context.startService(startServiceIntent);
   }

}
