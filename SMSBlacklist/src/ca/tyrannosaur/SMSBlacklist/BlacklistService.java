package ca.tyrannosaur.SMSBlacklist;

import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class BlacklistService extends Service {

   public static final String ACTION_START_SERVICE = "ca.tyrannosaur.SMSBlacklist.BlacklistService.START_SERVICE";

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

      Notification notification = new Notification(R.drawable.ic_stat_skull, getString(R.string.notification_blacklistEnabled), 0);

      notification.flags |= Notification.FLAG_NO_CLEAR;
      notification.flags |= Notification.FLAG_ONGOING_EVENT;

      // Clicking on the notification opens up the application proper
      Intent notificationIntent = new Intent(Intent.ACTION_MAIN, null, this, Blacklist.class);
      PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

      notification.setLatestEventInfo(this, getString(R.string.app_name), getString(R.string.notification_blacklistEnabled), contentIntent);

      nmanager.notify(ONGOING_ID, notification);
   }

   private void hideNotification() {
      NotificationManager nmanager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      nmanager.cancel(ONGOING_ID);
   }

}
