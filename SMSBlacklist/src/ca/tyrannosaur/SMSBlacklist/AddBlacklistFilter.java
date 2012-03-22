package ca.tyrannosaur.SMSBlacklist;

import java.util.regex.PatternSyntaxException;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

public class AddBlacklistFilter extends Activity implements TextWatcher {

   /**
    * Affinity text labels (in order)
    */
   private final int[] affinityLabels = {
         R.string.affinity_right,
         R.string.affinity_left,
         R.string.affinity_exact,
         R.string.affinity_substr,
         R.string.affinity_regex
   };

   /**
    * Affinity values (in order). A right-affinity is assumed to be the most
    * likely
    */
   private final String[] affinityValues = {
         Contract.Filters.AFFINITY_RIGHT,
         Contract.Filters.AFFINITY_LEFT,
         Contract.Filters.AFFINITY_EXACT,
         Contract.Filters.AFFINITY_SUBSTR,
         Contract.Filters.AFFINITY_REGEX
   };

   private EditText filterText;
   private EditText noteText;
   private Spinner filterAffinity;
   private EditText filterPreview;
   private ImageView filterPreviewMatch;
   private Button saveButton;

   private FilterPreview filterPreviewHelper;
   
   private int prevAffinityPosition = Spinner.INVALID_POSITION;
   
   private final class UserInput {
      public String filterText;
      public String noteText;
   }

   @Override
   protected void onCreate(Bundle bundle) {
      super.onCreate(bundle);
      setContentView(R.layout.activity_add_filter);

      buildOrInitUI();

      final Intent intent = getIntent();
      final String action = intent.getAction();

      if (Intent.ACTION_INSERT.equals(action)) {
         final UserInput savedData = (UserInput) getLastNonConfigurationInstance();
         if (savedData != null) {
            filterText.setText(savedData.filterText);
            noteText.setText(savedData.noteText);
         }
      }
      else if (Intent.ACTION_EDIT.equals(action)) {
         // Editing is not supported yet, as it would render any saved logs inconsistent.
         finish();
         return;
      }
      else {
         finish();
         return;
      }
   }

   private void buildOrInitUI() {
      filterText = (EditText) findViewById(R.id.filterText);
      noteText = (EditText) findViewById(R.id.filterNote);
      saveButton = (Button) findViewById(R.id.addFilter);
      filterAffinity = (Spinner) findViewById(R.id.filterAffinity);
      filterPreview = (EditText) findViewById(R.id.filterPreview);
      filterPreviewMatch = (ImageView) findViewById(R.id.filterPreviewMatch);

      // Make this activity the listener for when the filterText and noteText
      // EditTexts have their contents changed.

      filterText.addTextChangedListener(this);
      noteText.addTextChangedListener(this);

      filterPreviewHelper = new FilterPreview(filterPreview, filterPreviewMatch);

      saveButton.setOnClickListener(new Button.OnClickListener() {
         public void onClick(View v) {
            if (saveFilter()) {
               setResult(RESULT_OK);
               finish();
            }
         }
      });

      // Populate the affinity Spinner with affinity String values.
      // The affinity of a filter is the position in the phone number
      // string from which a the filter text is matched.
      //
      // See the SMSReceiver class for the regular expressions used.
      String[] labels = new String[affinityLabels.length];
      for (int i = 0; i < affinityLabels.length; i++)
         labels[i] = getString(affinityLabels[i]);

      ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);

      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      filterAffinity.setAdapter(adapter);
      filterAffinity.setOnItemSelectedListener(new OnItemSelectedListener() {
         private void clearFilterPattern(int oldPos, int newPos) {
            if (oldPos == Spinner.INVALID_POSITION || newPos == Spinner.INVALID_POSITION)
               return;
            
            String prevAffinity = affinityValues[oldPos];
            String newAffinity = affinityValues[newPos];
            
            // Clear the filter text of any regular expression if
            // the affinity is not a regular expression anymore
            if (Contract.Filters.AFFINITY_REGEX.equals(prevAffinity) 
                  && !Contract.Filters.AFFINITY_REGEX.equals(newAffinity)) {
               filterText.setText("");               
            }            
         }
         
         @Override
         public void onItemSelected(AdapterView<?> arg0, View arg1, int item, long arg3) {
            clearFilterPattern(prevAffinityPosition, item);
            updateFilterPreviewPattern(item);
            prevAffinityPosition = item;
         }

         @Override
         public void onNothingSelected(AdapterView<?> arg0) {
            clearFilterPattern(prevAffinityPosition, Spinner.INVALID_POSITION);
            filterPreviewHelper.clearFilterPreviewPattern();
            prevAffinityPosition = Spinner.INVALID_POSITION;
         }
      });
   }

   /*
    * Update the regular expression used to test numbers.
    */

   private void updateFilterPreviewPattern() {
      updateFilterPreviewPattern(filterAffinity.getSelectedItemPosition());
   }
   
   private void updateFilterPreviewPattern(int affinityPosition) {
      String filter = getCleanFilterText();      
      String affinity = affinityValues[affinityPosition];

      if (Contract.Filters.AFFINITY_REGEX.equals(affinity)) {
         filterText.setInputType(InputType.TYPE_CLASS_TEXT);
      }
      else {
         filterText.setInputType(InputType.TYPE_CLASS_PHONE);
      }
      
      filterPreviewHelper.updateFilterPreviewPattern(filter, affinity);
      filterPreviewHelper.testFilter();      
   }


   /*
    * Return the temporary values the user has entered.
    * 
    * @see android.app.Activity#onRetainNonConfigurationInstance()
    */
   @Override
   public Object onRetainNonConfigurationInstance() {
      final UserInput data = new UserInput();

      data.filterText = filterText.toString();
      data.noteText = noteText.toString();

      return data;
   }
 
   private String getCleanFilterText() {
      String text = filterText.getText().toString();
      String affinity = affinityValues[filterAffinity.getSelectedItemPosition()];
      
      if (Contract.Filters.AFFINITY_REGEX.equals(affinity)) {
         return text; 
      }
      else {
         return text.replaceAll("[^0-9]", "");
      }
   }

   private String getCleanNoteText() {
      String text = noteText.getText().toString();
      return text;
   }

   /*
    * Whether the filter is complete and can be saved. Checking this depends on
    * the length of the text entered.
    */
   private boolean isSaveable() {
      String filter = getCleanFilterText();
      String note = getCleanNoteText();
      String affinity = affinityValues[filterAffinity.getSelectedItemPosition()];

      // Try to compile this. If it's invalid, an error is thrown and we don't
      // save. Inelegant but whatever.
      try {
         if (Contract.Filters.AFFINITY_REGEX.equals(affinity))
            Contract.buildFilterPattern(filter, affinity);         
      }
      catch (PatternSyntaxException e) {
         return false;
      }

      return !TextUtils.isEmpty(filter) && !TextUtils.isEmpty(note);
   }

   /*
    * Save the filter to the {@link ConentProvider}.
    */
   private boolean saveFilter() {
      String filter = getCleanFilterText();
      String note = getCleanNoteText();
      int affinityIndex = filterAffinity.getSelectedItemPosition();

      if (affinityIndex == Spinner.INVALID_POSITION)
         return false;

      if (isFinishing())
         return false;

      if (!isSaveable())
         return false;

      // Add a new filter
      ContentValues values = new ContentValues();
      values.put(Contract.Filters.FILTER_TEXT, filter);
      values.put(Contract.Filters.NOTE, note);
      values.put(Contract.Filters.FILTER_MATCH_AFFINITY, affinityValues[affinityIndex]);

      Uri filterUri = getContentResolver().insert(getIntent().getData(), values);

      if (filterUri == null) {
         Toast.makeText(this, R.string.toast_filterInvalid, Toast.LENGTH_SHORT);
         return false;
      }
      else {
         setResult(RESULT_OK, (new Intent()).setAction(filterUri.toString()));
         Toast.makeText(this, R.string.toast_filterAdded, Toast.LENGTH_SHORT).show();
         return true;
      }
   }

   public void afterTextChanged(Editable s) {
      saveButton.setEnabled(isSaveable());
      updateFilterPreviewPattern();
   }

   public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      // Do nothing
   }

   public void onTextChanged(CharSequence s, int start, int before, int count) {
      // Do nothing
   }
}
