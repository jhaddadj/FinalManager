package com.example.manager.admin.ui;

import android.app.Dialog;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.manager.R;
import com.example.manager.admin.adapter.ResourceAdapter;
import com.example.manager.admin.model.Resource;
import com.example.manager.databinding.ActivityResourceManagementBinding;
import com.example.manager.databinding.DialogAddEditResourceBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.net.DatagramSocketImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class ResourceManagementActivity extends AppCompatActivity {
    private ActivityResourceManagementBinding binding;
    private ResourceAdapter adapter;
    private List<Resource> resourceList = new ArrayList<>();
    private DatabaseReference databaseReference;
    private String adminId;
    private GoogleMap mMap;
    private String selectedLocation = "";

    private  SupportMapFragment mapFragment;
    private Dialog mapDialog;
    private SupportMapFragment mapFragments;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityResourceManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        adminId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        databaseReference = FirebaseDatabase.getInstance().getReference("resources");


        setupRecyclerView();

        // Floating Action Button listener
        binding.addResourceButton.setOnClickListener(v -> showAddEditDialog(null));

    }

    private void setupRecyclerView() {
        adapter = new ResourceAdapter(ResourceManagementActivity.this,resourceList, this::showAddEditDialog, this::deleteResource);
        binding.resourceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.resourceRecyclerView.setAdapter(adapter);

        // Load resources (you can fetch this from a database instead)
        loadResources();
    }

    private void loadResources() {
        binding.progressBar.setVisibility(View.VISIBLE);
        databaseReference.orderByChild("adminId").equalTo(adminId)
                        .addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                binding.progressBar.setVisibility(View.INVISIBLE);
                                resourceList.clear();
                                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                                    Resource resource = dataSnapshot.getValue(Resource.class);
                                    resourceList.add(resource);
                                }
                                adapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                binding.progressBar.setVisibility(View.INVISIBLE);

                                Toast.makeText(ResourceManagementActivity.this, "Failed to load resources.", Toast.LENGTH_SHORT).show();

                            }
                        });


    }
    private void showAddEditDialog(Resource resourceToEdit) {
        DialogAddEditResourceBinding dialogBinding = DialogAddEditResourceBinding.inflate(LayoutInflater.from(this));
        ArrayAdapter<Integer> capacityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                getCapacityRange(1, 100)
        );
        capacityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.capacitySpinner.setAdapter(capacityAdapter);

        if (resourceToEdit != null) {
            dialogBinding.nameTextInputLayout.getEditText().setText(resourceToEdit.getName());
            String type = resourceToEdit.getType();
            if (type.equals("Room")) {
                dialogBinding.typeSpinner.setSelection(0);
            } else if (type.equals("Facility")) {
                dialogBinding.typeSpinner.setSelection(1);
            }
            int capacity = Integer.parseInt(resourceToEdit.getCapacity());
            dialogBinding.capacitySpinner.setSelection(capacity - 1);
            selectedLocation = resourceToEdit.getLocation();
            dialogBinding.locationText.setText(resourceToEdit.getLocation());

            dialogBinding.availabilitySwitch.setChecked("yes".equalsIgnoreCase(resourceToEdit.getIsAvailable()));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(resourceToEdit == null ? "Add Resource" : "Edit Resource")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", null) // Set to null so we can override it later
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = dialogBinding.nameTextInputLayout.getEditText().getText().toString().trim();
                String type = dialogBinding.typeSpinner.getSelectedItem().toString();
                String capacity = dialogBinding.capacitySpinner.getSelectedItem().toString();
                boolean isAvailable = dialogBinding.availabilitySwitch.isChecked();
                String availability = isAvailable ? "yes" : "no";
                String location = selectedLocation.trim();

                if (name.isEmpty()) {
                    dialogBinding.nameTextInputLayout.setError("Name cannot be empty");
                    return;
                }

                if (type.isEmpty()) {
                    Toast.makeText(ResourceManagementActivity.this, "Please select a resource type", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (capacity.isEmpty()) {
                    Toast.makeText(ResourceManagementActivity.this, "Please select a capacity", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (location.isEmpty()) {
                    Toast.makeText(ResourceManagementActivity.this, "Location cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (resourceToEdit == null) {
                    checkDuplicateAndAdd(name, type, capacity, location, isAvailable, dialog);
                } else {
                    resourceToEdit.setName(name);
                    resourceToEdit.setType(type);
                    resourceToEdit.setCapacity(capacity);
                    resourceToEdit.setIsAvailable(availability);
                    resourceToEdit.setLocation(location);
                    databaseReference.child(resourceToEdit.getId()).setValue(resourceToEdit)
                            .addOnSuccessListener(unused -> dialog.dismiss());
                }
            });
        });

        showMapDialog(dialogBinding, dialogBinding.locationText, selectedLocation);
        dialog.setOnDismissListener(dialogInterface -> {
            if (mapFragments != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(mapFragments);
                transaction.commit();
            }
        });

        dialog.show();
    }

    private void checkDuplicateAndAdd(String name, String type, String capacity, String location, boolean isAvailable, AlertDialog dialog) {
        databaseReference.orderByChild("name").equalTo(name)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Toast.makeText(ResourceManagementActivity.this, "Resource with the same name already exists.", Toast.LENGTH_SHORT).show();
                        } else {
                            String availableStr = isAvailable ? "yes" : "no";
                            String id = databaseReference.push().getKey();
                            Resource newResource = new Resource(id, name, type, capacity, adminId, location, availableStr);
                            databaseReference.child(id).setValue(newResource)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(ResourceManagementActivity.this, "Resource added successfully.", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ResourceManagementActivity.this, "Failed to add resource.", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void showMapDialog(DialogAddEditResourceBinding dialogBinding,AppCompatTextView locationText, String existingLocation) {

        FragmentManager fragmentManager = getSupportFragmentManager();

        mapFragments = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);

        if (mapFragments == null) {
            mapFragment = new SupportMapFragment();
            fragmentManager.beginTransaction()
                    .replace(R.id.mapFragment, mapFragments)
                    .commit();
        }

        mapFragments.getMapAsync(googleMap -> {
            //mMap = googleMap;
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            googleMap.getUiSettings().setZoomControlsEnabled(true);

            LatLng defaultLocation = new LatLng(-34, 151);
            if (selectedLocation != null && !selectedLocation.isEmpty()) {
                String[] latLngParts = selectedLocation.split(", ");
                if (latLngParts.length == 2) {
                    try {
                        double lat = Double.parseDouble(latLngParts[0]);
                        double lng = Double.parseDouble(latLngParts[1]);
                        defaultLocation = new LatLng(lat, lng);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10));

            googleMap.setOnMapClickListener(latLng -> {
                googleMap.clear();
                googleMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
                selectedLocation = latLng.latitude + ", " + latLng.longitude;

                // Convert LatLng to Address
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    if (!addresses.isEmpty()) {
                        selectedLocation = addresses.get(0).getAddressLine(0);
                        dialogBinding.locationText.setText(selectedLocation);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                dialogBinding.confirmLocationButton.setOnClickListener(v -> {
//                    dialogBinding.mapLayout.setVisibility(View.GONE);
//                    dialogBinding.locationText.setText(selectedLocation);
//                });

            });
        });


    }


    private List<Integer> getCapacityRange(int start, int end) {
        List<Integer> range = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            range.add(i);
        }
        return range;
    }



    private void deleteResource(Resource resource) {
        databaseReference.child(resource.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    resourceList.remove(resource);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Resource deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete resource.", Toast.LENGTH_SHORT).show());
    }


}