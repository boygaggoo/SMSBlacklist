package ca.tyrannosaur.SMSBlacklist;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

public class FilterPreview implements TextWatcher {
   private List<FilterPreviewMatchListener> filterMatchListeners;
   
   private EditText filterPreview;
   private ImageView filterPreviewMatch;
   
   private Pattern filterPreviewPattern;
   
   public FilterPreview(EditText filterPreview, ImageView filterPreviewMatch) {
      this.filterPreview = filterPreview;
      this.filterPreviewMatch = filterPreviewMatch;
      
      this.filterMatchListeners = new LinkedList<FilterPreviewMatchListener>();
      
      // Format anything in the preview as a phone number in the current locale.
      filterPreview.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
      
      // When the preview text changes, update.
      filterPreview.addTextChangedListener(this);     
   }

   public void clearFilterPreviewPattern() {
      filterPreviewPattern = null;
      toggleFilterPreviewMatchVisibility(false);
      updateListeners(false); 
   }
   
   public void addFilterPreviewMatchListener(FilterPreviewMatchListener l) {
      filterMatchListeners.add(l);
   }
   
   public void removeFilterPreviewMatchListener(FilterPreviewMatchListener l) {
      filterMatchListeners.remove(l);
   }
   
   /*
    * Tests the internal filter pattern against the text in the filterPreview and updates the UI 
    * to reflect whether the test was successful. 
    */
   public boolean testFilter() {
      boolean matched = false;
           
      String testText = filterPreview.getText().toString();
      testText = testText.replaceAll("[^0-9]+", "");
   
      if (!TextUtils.isEmpty(testText) && filterPreviewPattern != null)
         matched = filterPreviewPattern.matcher(testText).matches();   
  
      toggleFilterPreviewMatchVisibility(matched);
      updateListeners(matched);      
      return matched;
   }

   /*
    * Updates the internal filter pattern.
    * If either the filter text or the affinity is empty, does not update.
    */
   public void updateFilterPreviewPattern(String filterText, String affinity) {            
      if (!TextUtils.isEmpty(filterText) && !TextUtils.isEmpty(affinity)) {
         try {
            filterPreviewPattern = Contract.buildFilterPattern(filterText, affinity);            
         }
         catch (PatternSyntaxException e) {
            // Don't update yet
            clearFilterPreviewPattern();
         }       
      }
   }

   public void updateFilterPreviewPattern(Pattern p) {
      if (p != null)
         filterPreviewPattern = p;
   }
   
   /*
    * Sets the visibility of an image. If the filter matches the the preview
    * number, then the image is displayed, otherwise it isn't.
    */
   private void toggleFilterPreviewMatchVisibility(boolean visible) {
      if (visible)
         filterPreviewMatch.setVisibility(View.VISIBLE);
      else
         filterPreviewMatch.setVisibility(View.INVISIBLE);
   }

   private void updateListeners(boolean matched) {      
      Iterator<FilterPreviewMatchListener> i = filterMatchListeners.iterator();
      while (i.hasNext())
         i.next().onMatch(matched);            
   }
   
   @Override
   public void afterTextChanged(Editable s) {
      // do nothing
   }

   @Override
   public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      // do nothing
   }

   @Override
   public void onTextChanged(CharSequence s, int start, int before, int count) {
      testFilter();
   }   
}
