package com.hwang.xsighting;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.Toolbar;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.LabelVisibilityMode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.hwang.xsighting.models.Sighting;
import com.hwang.xsighting.models.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private static FirebaseUser user;
  private static final int RC_SIGN_IN = 482;
  private static final String TAG = "MainActivity";
  private AllSightingsAdapter adapter;
  private RecyclerView recyclerView;
  private RecyclerView.LayoutManager layoutManager;
  private String deviceToken;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    user = FirebaseAuth.getInstance().getCurrentUser();
    if (user != null) {

      // User is signed in
      // unused
      setNavigation();
      Log.i(TAG, user.toString());
    } else {

      // No user signed in, direct them to Login
      // A little disorienting to make your user sign in immediately upon launching the app.
      // A bit nicer to at least give context first about why.
      Log.i(TAG, "About to launch sign in");

      // Choose authentication providers
      List<AuthUI.IdpConfig> providers = Arrays.asList(
              new AuthUI.IdpConfig.EmailBuilder().build());

      // Create and launch sign-in intent
      startActivityForResult(
              AuthUI.getInstance()
                      .createSignInIntentBuilder()
                      .setAvailableProviders(providers)
                      .build(),
              RC_SIGN_IN);

      Log.i(TAG, "Sign up intent Sent");
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == RC_SIGN_IN) {
      IdpResponse response = IdpResponse.fromResultIntent(data);

      if (resultCode == RESULT_OK) {

        // Successfully signed in
        createNewUserIfUserDoesNotExist(FirebaseAuth.getInstance().getUid());
        setNavigation();

      } else {
        // Sign in failed
        Log.i(TAG, "Login Failed");
        // more error handling would be good here!
      }
    }
  }

  // Creates new user document in Firebase if current user doesn't exist
  public void createNewUserIfUserDoesNotExist(final String loggedInUserId) {

    user = FirebaseAuth.getInstance().getCurrentUser();
    final FirebaseFirestore db = FirebaseFirestore.getInstance();
    DocumentReference docRef = db.collection("users").document(loggedInUserId);
    docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
      @Override
      public void onComplete(@NonNull Task<DocumentSnapshot> task) {
        if (task.isSuccessful()) {
          DocumentSnapshot document = task.getResult();
          if (document.exists()) {
            Log.d(TAG, "DocumentSnapshot data: " + document.getData());
            // Update their device token for notifications
            setDeviceToken();
          } else {
            Log.d(TAG, "No such document, creating user");

            // Saves user to Firebase
            db.collection("users").document(loggedInUserId)
                    // Save user with DeviceToken
                    .set(new User(deviceToken))
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                      @Override
                      public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");

                        // Update their device token for notifications
                        setDeviceToken();
                      }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                      }
                    });
          }
        } else {
          Log.d(TAG, "get failed with ", task.getException());
        }
      }
    });
  }

  // Since this method's job is adding the listener, I'd rather call this setUpRecyclerView or
  // similar, to avoid implying that calling this method will actually update what's shown.
  private void updateRecyclerView() {
    recyclerView = findViewById(R.id.recyclerview_allsightings);
    // nooo zombie code! get rid of it!
//    recyclerView.setHasFixedSize(true);

    // Creates a layout manager and assigns it to the recycler view
    layoutManager = new LinearLayoutManager(this);
    recyclerView.setLayoutManager(layoutManager);

    // Specifies which adapter the recycler view should use
    adapter = new AllSightingsAdapter(new ArrayList<Sighting>());
    recyclerView.setAdapter(adapter);

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    // Grab sighting and sort by created time
    db.collection("sighting").orderBy("createdTime", Query.Direction.ASCENDING)
            .addSnapshotListener(new EventListener<QuerySnapshot>() {
              @Override
              public void onEvent(@Nullable QuerySnapshot snapshots,
                                  @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                  Log.w(TAG, "listen:error", e);
                  return;
                }

                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                  switch (dc.getType()) {
                    case ADDED:
                      Log.d(TAG, "New sighting: " + dc.getDocument().getData());
                      adapter.add(dc.getDocument().toObject(Sighting.class), dc.getDocument().getId());
                      break;
                    case MODIFIED:
                      Log.d(TAG, "Modified sighting: " + dc.getDocument().getData());
                      break;
                    case REMOVED:
                      Log.d(TAG, "Removed sighting: " + dc.getDocument().getData());
                      adapter.remove(dc.getDocument().toObject(Sighting.class));
                      break;
                  }
                }

              }
            });
  }

  public void setNavigation(){

    setContentView(R.layout.activity_main);
    BottomNavigationView bottomNavigationView = (BottomNavigationView)
            findViewById(R.id.navigation);
    bottomNavigationView.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_LABELED);
    updateRecyclerView();
    bottomNavigationView.setOnNavigationItemSelectedListener(
            new BottomNavigationView.OnNavigationItemSelectedListener() {
              @Override
              public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                  case R.id.navigation_home:
                    // Similarly, you don't want to navigate home from the home page.
                    // This means the back button loses all relevance.
                    Intent homeIntent = new Intent(getBaseContext(), MainActivity.class);
                    startActivity(homeIntent);
                    overridePendingTransition(0, 0);
                    break;
                  case R.id.navigation_add_sighting:
                    Intent addSighting = new Intent(getBaseContext(), CreateSighting.class);
                    startActivity(addSighting);
                    overridePendingTransition(0, 0);
                    break;
                }
                return true;
              }
            });
  }

  public void setDeviceToken() {
    FirebaseInstanceId.getInstance().getInstanceId()
            .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
              @Override
              public void onComplete(@NonNull Task<InstanceIdResult> task) {
                if (!task.isSuccessful()) {
                  Log.w(TAG, "getInstanceId failed", task.getException());
                  return;
                }

                // Get new Instance ID token
                deviceToken = task.getResult().getToken();
                updateDeviceTokenInDatabase();

                // Log and toast
                Log.d(TAG, deviceToken);
              }
            });


  }

  private void updateDeviceTokenInDatabase() {
    final FirebaseFirestore db = FirebaseFirestore.getInstance();
    DocumentReference docRef = db.collection("users").document(user.getUid());
    docRef
            // I wish this stored multiple device tokens in a map! It's sad that I get notifications
            // on only one of my devices, not both.
            .update("deviceToken", deviceToken)
            .addOnSuccessListener(new OnSuccessListener<Void>() {
              @Override
              public void onSuccess(Void aVoid) {
                Log.d(TAG, "Update deviceToken");
              }
            })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Error updating deviceToken", e);
              }
            });
  }
}
