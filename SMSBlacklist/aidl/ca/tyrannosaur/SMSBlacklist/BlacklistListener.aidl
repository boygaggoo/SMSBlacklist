package ca.tyrannosaur.SMSBlacklist;

interface BlacklistListener {
   void handleBlacklistEnabled();
   void handleBlacklistDisabled();
}