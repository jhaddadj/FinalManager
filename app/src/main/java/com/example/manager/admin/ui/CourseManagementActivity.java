package com.example.manager.admin.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.adapter.CourseAdapter;
import com.example.manager.admin.model.CourseItem;
import com.example.manager.admin.model.Resource;
import com.example.manager.model.User;
import com.example.manager.databinding.ActivityCourseManagementBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CourseManagementActivity provides administrators with a comprehensive interface
 * for managing courses within the institution.
 *
 * This activity implements full CRUD (Create, Read, Update, Delete) functionality for courses:
 * - Create: Administrators can add new courses with details like name, code, department, etc.
 * - Read: Displays a list of all courses managed by the current administrator
 * - Update: Allows editing of existing course details
 * - Delete: Enables removal of courses that are no longer needed
 */
public class CourseManagementActivity extends AppCompatActivity implements CourseAdapter.OnCourseClickListener {
    private static final String TAG = "CourseManagementAct";
    private ActivityCourseManagementBinding binding;
    private CourseAdapter adapter;
    private List<CourseItem> courseList = new ArrayList<>();
    private DatabaseReference databaseReference;
    private String adminId;
    
    // For lecturer spinner
    private Map<String, String> lecturerMap = new HashMap<>();
    private List<String> lecturerNames = new ArrayList<>();
    private List<String> lecturerIds = new ArrayList<>();
    
    // For resource (room) spinner
    private Map<String, String> resourceMap = new HashMap<>();
    private List<String> resourceNames = new ArrayList<>();
    private List<String> resourceIds = new ArrayList<>();
    
    // Department options for spinner
    private final List<String> departmentOptions = new ArrayList<>(
        Arrays.asList(
            "Computer Science", 
            "Information Technology",
            "Engineering",
            "Business"
        )
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCourseManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get current admin ID for filtering courses in the database
        adminId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Initialize Firebase database reference for courses
        databaseReference = FirebaseDatabase.getInstance().getReference("courses");

        // Set up the RecyclerView
        setupRecyclerView();
        
        // Load the lecturer data for the spinner
        loadLecturerData();
        
        // Load the resource data for the spinner
        loadResourceData();

        // Set up the Floating Action Button to add new courses
        binding.addCourseButton.setOnClickListener(v -> showAddOrEditCourseDialog(null));

        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Load courses
        loadCourses();
    }

    /**
     * Set up the RecyclerView for displaying courses.
     */
    private void setupRecyclerView() {
        adapter = new CourseAdapter(courseList, this);
        binding.recyclerViewCourses.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewCourses.setAdapter(adapter);
    }

    /**
     * Loads courses from Firebase that belong to the current administrator.
     */
    private void loadCourses() {
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        
        // Query Firebase for courses belonging to the current admin
        databaseReference.orderByChild("adminId").equalTo(adminId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        courseList.clear();
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            CourseItem course = snapshot.getValue(CourseItem.class);
                            if (course != null) {
                                courseList.add(course);
                            }
                        }
                        
                        adapter.setCourseList(courseList);
                        
                        // Hide loading indicator
                        binding.progressBar.setVisibility(View.GONE);
                        
                        // Show empty view if no courses
                        if (courseList.isEmpty()) {
                            binding.emptyView.setVisibility(View.VISIBLE);
                        } else {
                            binding.emptyView.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // Hide loading indicator
                        binding.progressBar.setVisibility(View.GONE);
                        
                        Log.e(TAG, "Error loading courses", databaseError.toException());
                        Toast.makeText(CourseManagementActivity.this, 
                                "Failed to load courses: " + databaseError.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * Load lecturer data for the spinner in add/edit dialog.
     */
    private void loadLecturerData() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
        usersRef.orderByChild("role").equalTo("lecture")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        lecturerNames.clear();
                        lecturerIds.clear();
                        lecturerMap.clear();
                        
                        // Add a "None" option
                        lecturerNames.add("None (No lecturer assigned)");
                        lecturerIds.add("");
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            User user = snapshot.getValue(User.class);
                            if (user != null && "accepted".equals(user.getStatus())) {
                                lecturerNames.add(user.getName());
                                lecturerIds.add(user.getId());
                                lecturerMap.put(user.getId(), user.getName());
                            }
                        }
                        
                        if (lecturerNames.size() <= 1) {
                            // Only "None" is in the list
                            Log.w(TAG, "No lecturers found for assigning to courses");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading lecturers", databaseError.toException());
                    }
                });
    }

    /**
     * Load resource data for the spinner in add/edit dialog.
     */
    private void loadResourceData() {
        DatabaseReference resourcesRef = FirebaseDatabase.getInstance().getReference("resources");
        resourcesRef.orderByChild("adminId").equalTo(adminId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        resourceNames.clear();
                        resourceIds.clear();
                        resourceMap.clear();
                        
                        // Add a "None" option
                        resourceNames.add("None (No room assigned)");
                        resourceIds.add("");
                        
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Resource resource = snapshot.getValue(Resource.class);
                            if (resource != null && "yes".equals(resource.getIsAvailable())) {
                                String displayName = resource.getName() + " (" + resource.getType() + ", Capacity: " + resource.getCapacity() + ")";
                                resourceNames.add(displayName);
                                resourceIds.add(resource.getId());
                                resourceMap.put(resource.getId(), displayName);
                            }
                        }
                        
                        // Update the adapter with the resource map
                        if (adapter != null) {
                            adapter.setResourceMap(resourceMap);
                        }
                        
                        if (resourceNames.size() <= 1) {
                            // Only "None" is in the list
                            Log.w(TAG, "No resources found for assigning to courses");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Error loading resources", databaseError.toException());
                    }
                });
    }

    /**
     * Shows dialog for adding a new course or editing an existing one.
     *
     * @param course The course to edit, or null if adding a new course
     */
    private void showAddOrEditCourseDialog(final CourseItem course) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_course, null);
        builder.setView(dialogView);
        
        // Set title
        builder.setTitle(course == null ? "Add New Course" : "Edit Course");
        
        // Set up form fields
        EditText nameEditText = dialogView.findViewById(R.id.courseName);
        EditText codeEditText = dialogView.findViewById(R.id.courseCode);
        EditText lecturesEditText = dialogView.findViewById(R.id.courseLectures);
        Spinner lecturerSpinner = dialogView.findViewById(R.id.lecturerSpinner);
        Spinner resourceSpinner = dialogView.findViewById(R.id.resourceSpinner);
        Spinner departmentSpinner = dialogView.findViewById(R.id.departmentSpinner);
        
        // Set up lecturer spinner
        ArrayAdapter<String> lecturerAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, lecturerNames);
        lecturerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lecturerSpinner.setAdapter(lecturerAdapter);
        
        // Set up resource spinner
        ArrayAdapter<String> resourceAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, resourceNames);
        resourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resourceSpinner.setAdapter(resourceAdapter);
        
        // Set up department spinner
        ArrayAdapter<String> departmentAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, departmentOptions);
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        departmentSpinner.setAdapter(departmentAdapter);
        
        // If editing existing course, fill in the fields
        if (course != null) {
            nameEditText.setText(course.getName());
            codeEditText.setText(course.getCode());
            lecturesEditText.setText(String.valueOf(course.getNumberOfLectures()));
            
            // Set selected department
            String department = course.getDepartment();
            if (department != null && !department.isEmpty()) {
                int departmentIndex = departmentOptions.indexOf(department);
                if (departmentIndex >= 0) {
                    departmentSpinner.setSelection(departmentIndex);
                } else {
                    // If the department is not in the predefined list, add it temporarily
                    departmentOptions.add(department);
                    departmentAdapter.notifyDataSetChanged();
                    departmentSpinner.setSelection(departmentOptions.size() - 1);
                }
            }
            
            // Set selected lecturer
            String lecturerId = course.getAssignedLecturerId();
            if (lecturerId != null && !lecturerId.isEmpty()) {
                for (int i = 0; i < lecturerIds.size(); i++) {
                    if (lecturerId.equals(lecturerIds.get(i))) {
                        lecturerSpinner.setSelection(i);
                        break;
                    }
                }
            }
            
            // Set selected resource
            String resourceId = course.getAssignedResourceId();
            if (resourceId != null && !resourceId.isEmpty()) {
                for (int i = 0; i < resourceIds.size(); i++) {
                    if (resourceId.equals(resourceIds.get(i))) {
                        resourceSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
        
        // Set up dialog buttons
        builder.setPositiveButton("Save", null); // Will override this below
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Override the positive button click listener to handle validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Validate input fields
            String name = nameEditText.getText().toString().trim();
            String code = codeEditText.getText().toString().trim();
            String lecturesStr = lecturesEditText.getText().toString().trim();
            
            // Get selected department from spinner
            String department = departmentSpinner.getSelectedItem().toString();
            
            if (name.isEmpty() || code.isEmpty() || lecturesStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse number of lectures 
            int lectures;
            
            try {
                lectures = Integer.parseInt(lecturesStr);
                if (lectures <= 0) {
                    Toast.makeText(this, "Number of lectures must be at least 1", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number for lectures", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Get selected lecturer
            String lecturerId = "";
            int selectedLecturerPosition = lecturerSpinner.getSelectedItemPosition();
            if (selectedLecturerPosition > 0 && selectedLecturerPosition < lecturerIds.size()) {
                lecturerId = lecturerIds.get(selectedLecturerPosition);
            }
            
            // Get selected resource
            String resourceId = "";
            int selectedResourcePosition = resourceSpinner.getSelectedItemPosition();
            if (selectedResourcePosition > 0 && selectedResourcePosition < resourceIds.size()) {
                resourceId = resourceIds.get(selectedResourcePosition);
            }
            
            // Get admin ID
            String adminId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            
            // Set durationHours as 1 (each class is 1 hour)
            int durationHours = 1;
            
            // Set labs to 0 by default
            int labs = 0;
            
            if (course == null) {
                // Adding new course
                String id = databaseReference.push().getKey();
                if (id != null) {
                    CourseItem newCourse = new CourseItem(id, name, code, durationHours, department, 
                            lectures, labs, adminId, lecturerId, resourceId);
                    databaseReference.child(id).setValue(newCourse)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CourseManagementActivity.this, "Course added successfully", 
                                        Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> Toast.makeText(CourseManagementActivity.this, 
                                    "Error adding course: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            } else {
                // Updating existing course
                course.setName(name);
                course.setCode(code);
                course.setDepartment(department);
                course.setNumberOfLectures(lectures);
                course.setNumberOfLabs(labs);
                course.setAssignedLecturerId(lecturerId);
                course.setAssignedResourceId(resourceId);
                
                databaseReference.child(course.getId()).setValue(course)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(CourseManagementActivity.this, "Course updated successfully", 
                                    Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(CourseManagementActivity.this, 
                                "Error updating course: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onCourseClick(CourseItem course, int position) {
        // Show course details or other actions
        Toast.makeText(this, "Course: " + course.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCourseEditClick(CourseItem course, int position) {
        showAddOrEditCourseDialog(course);
    }

    @Override
    public void onCourseDeleteClick(CourseItem course, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Course")
                .setMessage("Are you sure you want to delete " + course.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    databaseReference.child(course.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> 
                                    Toast.makeText(this, "Course deleted successfully", 
                                            Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> 
                                    Toast.makeText(this, "Failed to delete course: " + e.getMessage(), 
                                            Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
