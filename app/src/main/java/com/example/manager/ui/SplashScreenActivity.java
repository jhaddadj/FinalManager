package com.example.manager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.admin.ui.AdminMainActivity;
import com.example.manager.lecturar.ui.LecturerMainActivity;
import com.example.manager.stduent.ui.StudentMainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * SplashScreenActivity is the launch screen of the FinalManager application.
 * It displays a splash screen for a set duration, during which it checks if a user
 * is already authenticated, and then redirects to the appropriate screen based on
 * authentication status and user role.
 * 
 * If the user is authenticated, they're directed to their role-specific main activity.
 * If not, they're directed to the role selection screen.
 */
public class SplashScreenActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 2000; // 2 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Enable edge-to-edge display for modern UI appearance
        setContentView(R.layout.activity_splashscreen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Add a delay before redirecting to show the splash screen
        new Handler(Looper.getMainLooper()).postDelayed(this::redirectToMain, SPLASH_DELAY);
    }

    /**
     * Redirects the user to the main activity based on their role.
     * If the user is not authenticated, redirects to the role selection screen.
     */
    private void redirectToMain() {
        // Check if the user is authenticated
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            // User is authenticated, fetch their role and redirect accordingly
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(FirebaseAuth.getInstance().getCurrentUser().getUid());

            userRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DataSnapshot snapshot = task.getResult();
                    if (snapshot.exists()) {
                        String role = snapshot.child("role").getValue(String.class);

                        if ("admin".equals(role)) {
                            // Redirect to admin main activity
                            redirectToAdmin();
                        } else if ("lecture".equals(role)) {
                            // Redirect to lecturer main activity
                            redirectToLecturer();
                        } else if ("student".equals(role)) {
                            // Redirect to student main activity
                            redirectToStudent();
                        } else {
                            // Role not recognized, display error message and go to selection
                            Toast.makeText(SplashScreenActivity.this, "User role not recognized", Toast.LENGTH_SHORT).show();
                            redirectToSelection();
                        }
                    } else {
                        // User data not found, display error message and go to selection
                        Toast.makeText(SplashScreenActivity.this, "User data not found", Toast.LENGTH_SHORT).show();
                        redirectToSelection();
                    }
                } else {
                    // Error fetching user data, display error message and go to selection
                    Toast.makeText(SplashScreenActivity.this,
                            "Error fetching data: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"),
                            Toast.LENGTH_LONG).show();
                    redirectToSelection();
                }
            });
        } else {
            // No authenticated user, redirect to selection screen
            redirectToSelection();
        }
    }
    
    /**
     * Redirects the user to the selection activity.
     */
    private void redirectToSelection() {
        Intent intent = new Intent(SplashScreenActivity.this, SelectActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Redirects the user to the admin main activity.
     */
    private void redirectToAdmin() {
        Intent intent = new Intent(SplashScreenActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Redirects the user to the lecturer main activity.
     */
    private void redirectToLecturer() {
        Intent intent = new Intent(SplashScreenActivity.this, LecturerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * Redirects the user to the student main activity.
     */
    private void redirectToStudent() {
        Intent intent = new Intent(SplashScreenActivity.this, StudentMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    // We don't need onStart redirection since we're now handling it in onCreate
    // Removing this removes the potential for double redirection
}