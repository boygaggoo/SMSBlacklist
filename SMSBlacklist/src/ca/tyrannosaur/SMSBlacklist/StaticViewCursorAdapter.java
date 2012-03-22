package ca.tyrannosaur.SMSBlacklist;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/**
 * Adds a static view to either the top or bottom of a cursor-backed list.
 * 
 * @author charlie
 * 
 */
public class StaticViewCursorAdapter extends SimpleCursorAdapter {

   public final static int POSITION_BOTTOM = 0, 
                           POSITION_TOP = 1;
   
   public final static int INVALID_ID = -1;

   public final static int TYPE_STATIC_VIEW = 0;

   private Context context;

   private View staticButtonView;
   private int staticButtonPosition;
   private int staticButtonCount = 0;

   private int layoutResId;

   static class ViewHolder {
      TextView filterAndCount;
      TextView note;
      ImageView filterAffinity;
   }

   public StaticViewCursorAdapter(Context context, int layout, Cursor cursor, String[] columns, int[] ids) {
      super(context, layout, cursor, columns, ids);
      this.layoutResId = layout;
      this.context = context;
   }

   /**
    * Add a view's layout (once) at the given {@code position} in this adapter.
    * Trying to change the view results in a an {@link IllegalStateException},
    * since you never know what side-effects exist in the Android API.
    * 
    * @param layout
    * @param position
    */
   public void addView(int layout, int position) {
      if (staticButtonView != null)
         throw new IllegalStateException("A view has already been added");

      switch (position) {
         case POSITION_BOTTOM:
         case POSITION_TOP:
            staticButtonPosition = position;
            break;
         default:
            throw new IllegalArgumentException("Invalid button position");
      }

      LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      staticButtonView = inflater.inflate(layout, null);
      staticButtonCount = 1;
   }

   @Override
   public int getCount() {
      return super.getCount() + staticButtonCount;
   }

   @Override
   public int getViewTypeCount() {
      return super.getViewTypeCount() + staticButtonCount;
   }

   @Override
   public int getItemViewType(int position) {
      if (position == 0 && staticButtonPosition == POSITION_TOP)
         return TYPE_STATIC_VIEW;
      else if (position == super.getCount() && staticButtonPosition == POSITION_BOTTOM)
         return TYPE_STATIC_VIEW;
      else
         return super.getItemViewType(position) + 1;
   }

   public boolean areAllItemsSelectable() {
      return true;
   }

   public boolean isEnabled(int position) {
      return true;
   }

   @Override
   public Object getItem(int position) {
      if (getItemViewType(position) == TYPE_STATIC_VIEW)
         return null;

      switch (staticButtonPosition) {
         case POSITION_TOP:
            return super.getItem(position - 1);
         case POSITION_BOTTOM:
            return super.getItem(position);
         default:
            return null;
      }
   }

   @Override
   public long getItemId(int position) {
      if (getItemViewType(position) == TYPE_STATIC_VIEW)
         return INVALID_ID;

      switch (staticButtonPosition) {
         case POSITION_TOP:
            return super.getItemId(position - 1);
         case POSITION_BOTTOM:
            return super.getItemId(position);
         default:
            return INVALID_ID;
      }
   }

   @Override
   public View getView(int position, View convertView, ViewGroup parent) {
      if (getItemViewType(position) == TYPE_STATIC_VIEW)
         return staticButtonView;

      switch (staticButtonPosition) {
         case POSITION_TOP:
            return getCustomView(position - 1, convertView, parent);
         case POSITION_BOTTOM:
            return getCustomView(position, convertView, parent);
         default:
            return null;
      }
   }

   /*
    * A custom view specifically for the Blacklist ListView. TODO: Move this to
    * a new class.
    */
   private View getCustomView(int position, View convertView, ViewGroup parent) {
      View row = convertView;
      ViewHolder holder = null;

      if (position == -1)
         return null;

      if (row == null) {
         LayoutInflater inflater = ((Activity) context).getLayoutInflater();
         row = inflater.inflate(layoutResId, parent, false);

         holder = new ViewHolder();
         holder.filterAndCount = (TextView) row.findViewById(android.R.id.text1);
         holder.note = (TextView) row.findViewById(android.R.id.text2);
         holder.filterAffinity = (ImageView) row.findViewById(android.R.id.icon);

         row.setTag(holder);
      }
      else {
         holder = (ViewHolder) row.getTag();
      }

      Cursor c = this.getCursor();
      c.moveToPosition(position);

      String filterText = c.getString(c.getColumnIndexOrThrow(Contract.Filters.FILTER_TEXT));
      String affinity = c.getString(c.getColumnIndexOrThrow(Contract.Filters.FILTER_MATCH_AFFINITY));
      String note = c.getString(c.getColumnIndexOrThrow(Contract.Filters.NOTE));
      int unread = c.getInt(c.getColumnIndexOrThrow(Contract.Filters.UNREAD));

      if (unread > 1)         
         holder.filterAndCount.setText(Html.fromHtml(String.format("<b>%s</b> (%d)", filterText, unread)));
      else
         holder.filterAndCount.setText(Html.fromHtml(String.format("<b>%s</b>", filterText)));

      holder.note.setText(note);

      if (Contract.Filters.AFFINITY_EXACT.equals(affinity))
         holder.filterAffinity.setImageResource(R.drawable.list_affinity_exact);
      else if (Contract.Filters.AFFINITY_LEFT.equals(affinity))
         holder.filterAffinity.setImageResource(R.drawable.list_affinity_left);
      else if (Contract.Filters.AFFINITY_RIGHT.equals(affinity))
         holder.filterAffinity.setImageResource(R.drawable.list_affinity_right);
      else if (Contract.Filters.AFFINITY_SUBSTR.equals(affinity))
         holder.filterAffinity.setImageResource(R.drawable.list_affinity_substr);
      else if (Contract.Filters.AFFINITY_REGEX.equals(affinity))
         holder.filterAffinity.setImageResource(R.drawable.list_affinity_regexp);
      else
         holder.filterAffinity.setImageResource(R.drawable.list_affinity_unknown);

      return row;
   }

}
