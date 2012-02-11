package ca.tyrannosaur.SMSBlacklist;

import java.util.regex.Pattern;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
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
   private final int[] affinityLabels = { R.string.affinity_right, R.string.affinity_left, R.string.affinity_exact, R.string.affinity_substr, };

   /**
    * Affinity values (in order). A right-affinity is assumed to be the most
    * likely
    */
   private final String[] affinityValues = { BlacklistContract.Filters.AFFINITY_RIGHT, BlacklistContract.Filters.AFFINITY_LEFT, BlacklistContract.Filters.AFFINITY_EXACT,
         BlacklistContract.Filters.AFFINITY_SUBSTR, };

   private EditText filterText;
   private EditText noteText;
   private Spinner filterAffinity;
   private EditText filterPreview;
   private ImageView filterPreviewMatch;
   private Button saveButton;

   private Pattern filterPreviewPattern;

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
         // Editing is not supported yet
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
      // EditTexts
      // have their contents changed.

      filterText.addTextChangedListener(this);
      noteText.addTextChangedListener(this);

      filterPreview.addTextChangedListener(new TextWatcher() {

         @Override
         public void afterTextChanged(Editable s) {
         }

         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateFilterPreviewPattern();
         }
      });

      filterPreview.addTextChangedListener(new PhoneNumberFormattingTextWatcher());

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

      // See the SMSReceiver class for the regular expressions used.

      String[] labels = new String[affinityLabels.length];
      for (int i = 0; i < affinityLabels.length; i++)
         labels[i] = getString(affinityLabels[i]);

      ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);

      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      filterAffinity.setAdapter(adapter);
      filterAffinity.setOnItemSelectedListener(new OnItemSelectedListener() {
         @Override
         public void onItemSelected(AdapterView<?> arg0, View arg1, int item, long arg3) {
            updateFilterPreviewPattern(item);
         }

         @Override
         public void onNothingSelected(AdapterView<?> arg0) {
            updateFilterPreviewPattern(true);
         }
      });
   }

   /*
    * Update the regular expression used to test numbers.
    */

   private void updateFilterPreviewPattern() {
      updateFilterPreviewPattern(filterAffinity.getSelectedItemPosition(), false);
   }

   private void updateFilterPreviewPattern(int affinityPosition) {
      updateFilterPreviewPattern(affinityPosition, false);
   }

   private void updateFilterPreviewPattern(boolean clearPattern) {
      updateFilterPreviewPattern(filterAffinity.getSelectedItemPosition(), clearPattern);
   }

   private void updateFilterPreviewPattern(int affinityPosition, boolean clearPattern) {
      String filter = getCleanFilterText();
      String preview = getCleanPreviewText();
      String affinity = affinityValues[affinityPosition];

      boolean matches = false;

      if (!TextUtils.isEmpty(preview) && !TextUtils.isEmpty(filter) && !clearPattern) {
         filterPreviewPattern = BlacklistContract.buildFilterPattern(filter, affinity);
         matches = filterPreviewPattern.matcher(preview).matches();
      }
      else {
         filterPreviewPattern = null;
      }

      toggleFilterPreviewMatchVisibility(matches);
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

   private String getCleanPreviewText() {
      String text = filterPreview.getText().toString();
      return text.replaceAll("[^0-9]", "");
   }

   private String getCleanFilterText() {
      String text = filterText.getText().toString();
      return text.replaceAll("[^0-9]", "");
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
      values.put(BlacklistContract.Filters.FILTER_TEXT, filter);
      values.put(BlacklistContract.Filters.NOTE, note);
      values.put(BlacklistContract.Filters.FILTER_MATCH_AFFINITY, affinityValues[affinityIndex]);

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
