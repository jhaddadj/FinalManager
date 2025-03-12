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


public class SplashScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splashscreen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    private void redirectToMain() {



            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(FirebaseAuth.getInstance().getCurrentUser().getUid());

            userRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DataSnapshot snapshot = task.getResult();
                    if (snapshot.exists()) {
                        String role = snapshot.child("role").getValue(String.class);
                        if (role.equalsIgnoreCase("admin")) {
                            redirectToAdmin();
                        } else if (role.equalsIgnoreCase("lecture")) {
                            redirectToLecturer();
                        } else if (role.equalsIgnoreCase("student")) {
                            redirectToStudent();
                        } else {
                            Toast.makeText(SplashScreenActivity.this, "User Data not Found", Toast.LENGTH_SHORT).
                            show();
                        }
                    } else {
                        Toast.makeText(SplashScreenActivity.this, "User Data not Found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SplashScreenActivity.this,
                            "Error Fetching Data" + task.getException().getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });

    }


    private void redirectToAdmin() {
        Intent intent = new Intent(SplashScreenActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    private void redirectToLecturer() {
        Intent intent = new Intent(SplashScreenActivity.this, LecturerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    private void redirectToStudent() {
        Intent intent = new Intent(SplashScreenActivity.this, StudentMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }
    @Override
    protected void onStart() {
        super.onStart();
        if (FirebaseAuth.getInstance().getCurrentUser()!=null){
            redirectToMain();
        }else {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(SplashScreenActivity.this, SelectActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                startActivity(intent);
                finish();
            }, 3000);
        }
    }

}