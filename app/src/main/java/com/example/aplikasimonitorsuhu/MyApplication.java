package com.example.aplikasimonitorsuhu;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase First
        FirebaseApp.initializeApp(this);
        
        // Initialize App Check with Play Integrity
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
    }
}
