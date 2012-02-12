package ca.tyrannosaur.SMSBlacklist;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

   public static final String
         TABLE_FILTERS = "filters",
         TABLE_LOGS = "logs";

   private static final String DB_NAME = "SMSBlacklist.db3";
   private static final int DB_VERSION = 10;

   public DatabaseHelper(Context context) {
      super(context, DB_NAME, null, DB_VERSION);
   }

   private void createFilters(SQLiteDatabase database) {
      database.execSQL(
              String.format("create table if not exists %s (%s integer primary key autoincrement, %s text not null," +
                    "%s text not null, %s text not null, %s integer not null default 0);",
                 TABLE_FILTERS,
                 BlacklistContract.Filters._ID,
                 BlacklistContract.Filters.FILTER_TEXT,
                 BlacklistContract.Filters.NOTE,
                 BlacklistContract.Filters.FILTER_MATCH_AFFINITY,
                 BlacklistContract.Filters.UNREAD));
   }

   private void createLogs(SQLiteDatabase database) {
      database.execSQL(String.format("create table if not exists %s (%s integer primary key autoincrement, %s integer," +
            "%s text, foreign key (%s) references %s(%s) ON DELETE CASCADE);",
         TABLE_LOGS,
         BlacklistContract.Logs._ID,
         BlacklistContract.Logs.FILTER_ID,
         BlacklistContract.Logs.PATH,
         BlacklistContract.Logs.FILTER_ID,
         TABLE_FILTERS,
         BlacklistContract.Filters._ID));
   }

   @Override
   public void onCreate(SQLiteDatabase database) {
      createFilters(database);
      createLogs(database);
   }

   @Override
   public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
      Log.w(
         DatabaseHelper.class.getName(),
         String.format("Updgrading database from version %d to %d. This will not destroy data.",
            oldVersion,
            newVersion));

      /*
       * database.execSQL(String.format("drop table if exists %s",
       * TABLE_FILTERS));
       * database.execSQL(String.format("drop table if exists %s", TABLE_LOGS));
       */

      onCreate(database);
   }
}
