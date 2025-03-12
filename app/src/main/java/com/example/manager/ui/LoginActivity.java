package com.example.manager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.admin.ui.AdminMainActivity;
import com.example.manager.admin.ui.AdminRegistrationActivity;
import com.example.manager.databinding.ActivityLoginBinding;
import com.example.manager.R;

import com.example.manager.lecturar.ui.LecturarRegistrationActivity;
import com.example.manager.lecturar.ui.LecturerMainActivity;
import com.example.manager.stduent.ui.StudentMainActivity;
import com.example.manager.stduent.ui.StudentRegistrationActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private String roles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        auth = FirebaseAuth.getInstance();
        roles = getIntent().getStringExtra("role");
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.loginButton.setOnClickListener(v -> loginUser());
        binding.registerText.setOnClickListener(v -> {
            if ("1".equals(roles)) {
                Intent intent = new Intent(LoginActivity.this, AdminRegistrationActivity.class);
                intent.putExtra("role", "1");
                startActivity(intent);
            } else if ("2".equals(roles)) {
                Intent intent = new Intent(LoginActivity.this, LecturarRegistrationActivity.class);
                intent.putExtra("role", "2");

                startActivity(intent);
            } else if ("3".equals(roles)) {
                Intent intent = new Intent(LoginActivity.this, StudentRegistrationActivity.class);
                intent.putExtra("role", "3");

                startActivity(intent);
            }

        });

        binding.forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog());

    }
    private void showForgotPasswordDialog() {
        // Create a dialog box
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.forgot_password);

        // Create a TextInputLayout for email input
        final com.google.android.material.textfield.TextInputLayout emailInputLayout = new com.google.android.material.textfield.TextInputLayout(this);
        emailInputLayout.setHint(getString(R.string.email));

        final com.google.android.material.textfield.TextInputEditText emailEditText = new com.google.android.material.textfield.TextInputEditText(this);
        emailEditText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInputLayout.addView(emailEditText);

        builder.setView(emailInputLayout);

        // Add buttons
        builder.setPositiveButton(R.string.send_recovery_email, null); // Set null initially for custom handling
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Set custom behavior for the positive button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT).show();
            } else {
                sendPasswordRecoveryEmail(email, dialog);
            }
        });
    }

    private void sendPasswordRecoveryEmail(String email, AlertDialog dialog) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Recovery email sent successfully!", Toast.LENGTH_LONG).show();
                        dialog.dismiss(); // Dismiss the dialog on success
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Error sending recovery email. Please try again.";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                });
    }




    private void loginUser() {

        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,"Please fill all the fields", Toast.LENGTH_SHORT).show();

            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.loginButton.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        fetchUserDataAndSave();
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this,
                                "Login Failed" + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void fetchUserDataAndSave() {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(auth.getCurrentUser().getUid());

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot.exists()) {
                    String role = snapshot.child("role").getValue(String.class);

                    if ("admin".equals(role)) {
                        if ("1".equals(roles)) {

                            redirectToAdmin();
                        }else {
                            Toast.makeText(LoginActivity.this, "Invalid User Type", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.loginButton.setEnabled(true);
                            FirebaseAuth.getInstance().signOut();

                        }
                    } else if ("lecture".equals(role)) {
                        if ("2".equals(roles)) {

                            redirectToLecturer();
                        }else {
                            Toast.makeText(LoginActivity.this, "Invalid User Type", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.loginButton.setEnabled(true);
                            FirebaseAuth.getInstance().signOut();
                        }
                    } else if ("student".equals(role)) {
                        if ("3".equals(roles)) {

                            redirectToStudent();
                        }else {
                            Toast.makeText(LoginActivity.this, "Invalid User Type", Toast.LENGTH_SHORT).show();
                            binding.progressBar.setVisibility(View.INVISIBLE);
                            binding.loginButton.setEnabled(true);
                            FirebaseAuth.getInstance().signOut();

                        }
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.loginButton.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "User Data not Found", Toast.LENGTH_SHORT).
                        show();
                        FirebaseAuth.getInstance().signOut();

                    }
                } else {
                    binding.progressBar.setVisibility(View.INVISIBLE);
                    binding.loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "User data not Found", Toast.LENGTH_SHORT).show();
                }
            } else {
                binding.progressBar.setVisibility(View.INVISIBLE);
                binding.loginButton.setEnabled(true);
                Toast.makeText(LoginActivity.this,
                        "Error Fetching Data" + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                FirebaseAuth.getInstance().signOut();

            }
        });
    }


    private void redirectToAdmin() {
        Intent intent = new Intent(LoginActivity.this, AdminMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    private void redirectToLecturer() {
        Intent intent = new Intent(LoginActivity.this, LecturerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }

    private void redirectToStudent() {
        Intent intent = new Intent(LoginActivity.this, StudentMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(intent);
        finish();
    }
}