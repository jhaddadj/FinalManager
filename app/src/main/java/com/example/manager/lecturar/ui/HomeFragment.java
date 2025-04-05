package com.example.manager.lecturar.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.manager.R;
import com.example.manager.admin.adapter.TimetableAdapter;
import com.example.manager.admin.model.TimetableEntry;
import com.example.manager.admin.ui.AddScheduleActivity;
import com.example.manager.admin.ui.TimetableInitializationActivity;
import com.example.manager.lecturar.ui.ViewScheduleActivity;
import com.example.manager.databinding.FragmentHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private DatabaseReference databaseReferenceUser;
    private FirebaseAuth auth;
    private DatabaseReference databaseReference;
    private List<TimetableEntry> timetableEntries = new ArrayList<>();
    private TimetableAdapter adapter;
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // Fragment parameters for navigation
    private String mParam1;
    private String mParam2;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new instance of the HomeFragment with specified parameters
     */
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        auth = FirebaseAuth.getInstance();
        databaseReferenceUser = FirebaseDatabase.getInstance().getReference("Users");
        databaseReference = FirebaseDatabase.getInstance().getReference("timetables");

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Setup RecyclerView
        binding.timetableRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new TimetableAdapter(timetableEntries, this::onItemClicked, this::onItemLongClicked);
        binding.timetableRecyclerView.setAdapter(adapter);
        binding.textView.setVisibility(View.GONE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAccountStatus();
    }

    private void checkAccountStatus() {
        String userId = auth.getCurrentUser().getUid();

        // Fetch user data from Realtime Database
        databaseReferenceUser.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);

                    if ("rejected".equalsIgnoreCase(status)) {
                        showRejectionLayout();
                    } else if ("pending".equalsIgnoreCase(status)) {
                        showPendingLayout();
                    } else if ("accepted".equalsIgnoreCase(status)) {
                        // Hide overlay if status is accepted
                        binding.overlayLayout.setVisibility(View.GONE);
                        binding.timetableRecyclerView.setVisibility(View.VISIBLE);
                        loadTimetableData();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database error
            }
        });
    }

    private void loadTimetableData() {
        String userId = auth.getCurrentUser().getUid();
        binding.progressBar.setVisibility(View.VISIBLE);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                timetableEntries.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    TimetableEntry entry = data.getValue(TimetableEntry.class);
                    if (entry != null) {
                        binding.progressBar.setVisibility(View.INVISIBLE);
                        if (userId.equalsIgnoreCase(entry.getLecturerId())) {
                            entry.setCourseId(data.getKey());

                            timetableEntries.add(entry);
                        }
                    }else {

                        binding.progressBar.setVisibility(View.INVISIBLE);
                        binding.textView.setVisibility(View.VISIBLE);

                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                binding.progressBar.setVisibility(View.INVISIBLE);
                Toast.makeText(getActivity(), "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onItemClicked(TimetableEntry entry) {
//        Intent intent = new Intent(this, AddScheduleActivity.class);
        Intent intent = new Intent(getActivity(), ViewScheduleActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }
    private void onItemLongClicked(TimetableEntry entry) {
        Intent intent = new Intent(getActivity(), ViewScheduleActivity.class);

        intent.putExtra("courseId", entry.getCourseId());
        intent.putExtra("name", entry.getLecturerName());
        intent.putExtra("lecId", entry.getLecturerId());
        startActivity(intent);
    }
    private void showRejectionLayout() {
        binding.overlayLayout.setVisibility(View.VISIBLE);
        binding.overlayMessageTextView.setText("Your account has been rejected by the admin. Please re-upload your correct ID and contract with clear images. If the issue continues, contact the admin department.");
    }

    private void showPendingLayout() {
        binding.overlayLayout.setVisibility(View.VISIBLE);
        binding.overlayMessageTextView.setText("Your account is waiting for admin approval. Please wait until further notification.");
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Set binding to null to avoid memory leaks
        binding = null;
    }
}