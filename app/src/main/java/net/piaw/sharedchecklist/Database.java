package net.piaw.sharedchecklist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import static com.facebook.FacebookSdk.getApplicationContext;

/**
 * Created by piaw on 9/23/2016.
 */

public class Database {
    @SuppressLint("StaticFieldLeak")
    private static Database mDB = null;
    private final String Tag = "Database";
    private boolean mShowOnFetch;
    private DatabaseReference mUserDB;
    private DatabaseReference mChecklistDB;
    private Activity mActivity;
    private String mEmail;
    private User mUser;

    Database(String email, Activity activity, boolean showOnFetch) {
        Log.v(Tag, "instantiating database");
        mShowOnFetch = showOnFetch;
        mActivity = activity;
        mUserDB = FirebaseDatabase.getInstance().getReference().child("users");
        mChecklistDB = FirebaseDatabase.getInstance().getReference().child("checklists");
        mEmail = email;
        ValueEventListener userListener = new UserListener();
        Log.d(Tag, "AddingValueEventListener for user");
        mUserDB.child(mEmail).addListenerForSingleValueEvent(userListener);
    }

    public static Database getDB() {
        return mDB;
    }

    public static void setDB(Database db) {
        mDB = db;
    }

    public static String unEscapeEmailAddress(String email) {
        // Replace ',' (not allowed in a Firebase key) with '.' (not allowed in an email address)
        return email.toLowerCase().replaceAll(",", "\\.");
    }

    public static String escapeEmailAddress(String email) {
        // Replace '.' (not allowed in a Firebase key) with ',' (not allowed in an email address)
        return email.toLowerCase().replaceAll("\\.", ",");
    }

    DatabaseReference getChecklistDB() {
        return mChecklistDB;
    }

    DatabaseReference getUserDB() {
        return mUserDB;
    }

    public User getUser() {
        return mUser;
    }

    public String getEmail() {
        return mEmail;
    }

    public void setDefaultChecklist(Checklist cl) {
        if (cl.getId().equals("")) {
            Log.e(Tag, "checklist id is null!");
            Toast.makeText(getApplicationContext(),
                    "Checklist is corrupt", Toast.LENGTH_LONG).show();
            return;
        }
        // assert mUser is valid!
        mUser.setDefault_checklist(cl.getId());
        mUserDB.child(mEmail).setValue(mUser);
    }

    public void fetchChecklistFromURI(Uri uri, FetchChecklistCallback cb) {
        Log.d(Tag, "fetchChecklistfromURI:" + uri.toString());
        String url = uri.toString();
        String[] parts = url.split("/");
        // the tail is the checklistID
        String checklistId = parts[parts.length - 1];
        Log.d(Tag, "fetching:" + checklistId);
        FetchChecklist(cb, checklistId);
    }

    public void FetchChecklist(FetchChecklistCallback cb, String checklistId) {
        Log.v(Tag, "Fetching checklist:" + checklistId);
        mChecklistDB.child(checklistId)
                .addListenerForSingleValueEvent(new FetchChecklistCallbackListener(cb));
    }

    public void UpdateUser() {
        mUserDB.child(mEmail).setValue(mUser);
    }

    public void UpdateUser(User user) {
        mUserDB.child(user.getEmail()).setValue(user);
    }

    public void DeleteChecklist(Checklist cl) {
        mChecklistDB.child(cl.getId()).removeValue();
    }

    private Checklist createDefaultChecklist() {
        Checklist checklist = new Checklist();
        checklist.setCreator(mUser.getEmail());
        checklist.setOwner(mUser.getEmail());
        checklist.setItems(new ArrayList<ChecklistItem>());
        checklist.addAcl(mEmail);

        checklist = CreateChecklist(checklist);
        UpdateDefaultChecklist(checklist);
        return checklist;
    }

    public void UpdateDefaultChecklist(Checklist cl) {
        mUser.setDefault_checklist(cl.getId());
        mUserDB.child(mEmail).setValue(mUser);
    }

    @NonNull
    public Checklist CreateChecklist(Checklist checklist) {
        String checklist_id = mChecklistDB.push().getKey();
        checklist.setId(checklist_id);
        mChecklistDB.child(checklist_id).setValue(checklist);
        mUser.addChecklist(checklist_id);
        mUserDB.child(mEmail).setValue(mUser);
        return checklist;
    }

    public void SharedChecklistNotificationOn(SharedChecklistCallback cb) {
        mUserDB.child(mEmail).child("pending_checklists").
                addChildEventListener(new SharedChecklistEventListener((cb)));
    }

    private void ShowChecklist(Checklist checklist) {
        if (!mShowOnFetch) return;
        Log.d(Tag, "Showing checklist:" + checklist.getId());
        Intent intent = new Intent(getApplicationContext(),
                ChecklistDisplay.class);
        intent.putExtra("checklist", checklist);
        mActivity.startActivity(intent);
    }

    public void AddChecklistItem(Checklist cl, ChecklistItem item) {
        Log.d(Tag, "Adding checklist item:" + item.getLabel() + " to:" + cl.getId());
        cl.addItem(item);
        mChecklistDB.child(cl.getId()).setValue(cl);
    }

    public void UpdateChecklist(Checklist cl) {
        Log.d(Tag, "Updating checklist:" + cl.getId());
        mChecklistDB.child(cl.getId()).setValue(cl);
    }

    public void FetchUser(FetchUserCallback cb, String email) {
        mUserDB.child(email).addListenerForSingleValueEvent(new FetchUserCallbackListener(cb));
    }

    public interface SharedChecklistCallback {
        void onSharedChecklist(String clId);
    }

    public interface FetchUserCallback {
        void onUserLoaded(User user);
    }

    public interface FetchChecklistCallback {
        void onChecklistLoaded(Checklist cl);
    }

    class SharedChecklistEventListener implements ChildEventListener {
        SharedChecklistCallback mCB;

        SharedChecklistEventListener(SharedChecklistCallback cb) {
            mCB = cb;
        }

        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String prevChildName) {
            String checklist_id = dataSnapshot.getValue(String.class);
            mCB.onSharedChecklist(checklist_id);
        }

        @Override
        public void onCancelled(DatabaseError err) {
            // ignore
        }

        public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            // ignore
        }

        public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            // ignore
        }

        public void onChildRemoved(DataSnapshot snapshot) {
            // ignore
        }
    }

    private class FetchUserCallbackListener implements ValueEventListener {
        FetchUserCallback mCB;

        FetchUserCallbackListener(FetchUserCallback cb) {
            mCB = cb;
        }

        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            User user = dataSnapshot.getValue(User.class);
            mCB.onUserLoaded(user);
        }

        @Override
        public void onCancelled(DatabaseError dberr) {
            mCB.onUserLoaded(null);
        }
    }

    private class FetchChecklistCallbackListener implements
            ValueEventListener {
        FetchChecklistCallback mCB;

        FetchChecklistCallbackListener(FetchChecklistCallback cb) {
            mCB = cb;
        }

        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Log.v(Tag, "FetchCLCB:onDataChange");
            Checklist checklist = dataSnapshot.getValue(Checklist.class);
            mCB.onChecklistLoaded(checklist);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            Log.w(Tag, "FetchChecklist:cancelled!");
            mCB.onChecklistLoaded(null);
        }
    }

    class UserListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(Tag, "UserListener:onDataChange");
            if (dataSnapshot.exists()) {
                Log.d(Tag, "User exists!");
                mUser = dataSnapshot.getValue(User.class);

                // now retrieve default checklist
                if (!mUser.getDefault_checklist().equals("")) {
                    Log.d(Tag, "User has default checklist!");
                    ValueEventListener cl_listener = new ChecklistListener();
                    mChecklistDB.child(mUser.getDefault_checklist()).
                            addListenerForSingleValueEvent(cl_listener);

                } else {
                    Log.d(Tag, "Creating default checklist for existing user");
                    ShowChecklist(createDefaultChecklist());
                }
            } else {
                Log.d(Tag, "Creating new user");
                // user doesn't exist, now create new user
                mUser = new User();
                mUser.setEmail(mEmail);
                // create new checklist as the default checklist
                // note that createDefaultChecklist() also writes to the UserDB
                // so we don't have to do it
                ShowChecklist(createDefaultChecklist());
            }
        }

        @Override
        public void onCancelled(DatabaseError dbErr) {
            Log.w(Tag, "User retrieval failed", dbErr.toException());
            Toast.makeText(getApplicationContext(), "Failed to load user",
                    Toast.LENGTH_SHORT).show();
        }
    }

    class ChecklistListener implements ValueEventListener {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(Tag, "ChecklistListener: onDataChange");
            if (dataSnapshot.exists()) {
                Log.d(Tag, "Checklist exists!");
                Checklist checklist = dataSnapshot.getValue(Checklist.class);
                ShowChecklist(checklist);
            } else {
                Log.d(Tag, "No default checklist --- creating");
                // no existing checklist! create it. For now, just stick it into the
                // Default checklist
                ShowChecklist(createDefaultChecklist());
            }
        }

        @Override
        public void onCancelled(DatabaseError dbErr) {
            Log.w(Tag, "User retrieval failed", dbErr.toException());
            Toast.makeText(getApplicationContext(), "Failed to load checklist",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
