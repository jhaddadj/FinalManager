package com.example.manager.stduent.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.manager.R;
import com.example.manager.databinding.ActivityStudentRegistrationBinding;
import com.example.manager.model.User;
import com.example.manager.ui.LoginActivity;
import com.example.manager.ui.SelectActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class StudentRegistrationActivity extends AppCompatActivity {
    private ActivityStudentRegistrationBinding binding;
    private FirebaseAuth auth;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;

    private Uri idImageUri;
    private Uri contractImageUri;
    private String roles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityStudentRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        storageReference = FirebaseStorage.getInstance().getReference("uploads");
        roles = getIntent().getStringExtra("role");

        // Set listeners for image selection
        binding.selectIdImageButton.setOnClickListener(v -> pickImage(idImagePickerLauncher));
        binding.selectContractImageButton.setOnClickListener(v -> pickImage(contractImagePickerLauncher));

        binding.registerButton.setOnClickListener(v -> registerUser());

    }

    private final ActivityResultLauncher<String> idImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    idImageUri = uri;
                    binding.idImageView.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<String> contractImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    contractImageUri = uri;
                    binding.contractImageView.setImageURI(uri);
                }
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!result.containsValue(false)) {
                    Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Please grant permissions to continue", Toast.LENGTH_SHORT).show();
                }
            });

    private void pickImage(ActivityResultLauncher<String> launcher) {
        permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        launcher.launch("image/*");
    }

    private void registerUser() {

        String name = binding.nameEditText.getText().toString().trim();
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || idImageUri == null || contractImageUri == null) {
            Toast.makeText(this, "Please fill all fields and select both images", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.registerButton.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = auth.getCurrentUser().getUid();
                        uploadImageToStorage("id_images/" + userId, idImageUri, idImageUrl -> {
                            uploadImageToStorage("contract_images/" + userId, contractImageUri, contractImageUrl -> {
                                saveUserToDatabase(userId, name, email, idImageUrl.toString(), contractImageUrl.toString(), userId);
                            });
                        });
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.registerButton.setEnabled(true);

                        Toast.makeText(StudentRegistrationActivity.this, "Failed to Create Account: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadImageToStorage(String path, Uri imageUri, OnImageUploadedCallback callback) {
        StorageReference fileRef = storageReference.child(path);
        fileRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(callback::onImageUploaded))
                .addOnFailureListener(e -> Toast.makeText(StudentRegistrationActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveUserToDatabase(String id, String name, String email, String idImageUrl, String contractImageUrl, String userId) {
        User user = new User(id, name, email,"", "student", idImageUrl, contractImageUrl, "pending");

        databaseReference.child(userId).setValue(user)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        Toast.makeText(StudentRegistrationActivity.this, "Account Created Successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(StudentRegistrationActivity.this, LoginActivity.class);
                        intent.putExtra("role", "3");

                        startActivity(intent);
                        finish();
                    } else {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.registerButton.setEnabled(true);

                        Toast.makeText(StudentRegistrationActivity.this, "Failed to Save Data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private interface OnImageUploadedCallback {
        void onImageUploaded(Uri downloadUrl);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(StudentRegistrationActivity.this, SelectActivity.class);
        startActivity(intent);
        finish();
    }
}