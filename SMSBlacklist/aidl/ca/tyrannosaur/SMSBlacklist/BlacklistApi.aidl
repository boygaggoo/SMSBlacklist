package ca.tyrannosaur.SMSBlacklist;

import ca.tyrannosaur.SMSBlacklist.BlacklistListener;

interface BlacklistApi {
   boolean setEnabled(boolean enabled);
   boolean getEnabled();

   void addListener(BlacklistListener listener);
   void removeListener(BlacklistListener listener);
}