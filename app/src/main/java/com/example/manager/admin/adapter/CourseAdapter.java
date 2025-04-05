package com.example.manager.admin.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.model.CourseItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the Course RecyclerView.
 * This adapter handles displaying the course items and managing interactions with them.
 */
public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.CourseViewHolder> {
    private List<CourseItem> courseList;
    private OnCourseClickListener listener;
    private Map<String, String> resourceMap; // Map of resource IDs to names

    /**
     * Constructor for the CourseAdapter.
     *
     * @param courseList List of courses to display
     * @param listener Click listener for course items
     */
    public CourseAdapter(List<CourseItem> courseList, OnCourseClickListener listener) {
        this.courseList = courseList;
        this.listener = listener;
        this.resourceMap = new HashMap<>();
    }

    /**
     * Updates the course list and notifies the adapter of the change.
     *
     * @param courseList The new list of courses
     */
    public void setCourseList(List<CourseItem> courseList) {
        this.courseList = courseList;
        notifyDataSetChanged();
    }

    /**
     * Sets the resource map for looking up resource names by their IDs.
     * 
     * @param resourceMap Map of resource IDs to display names
     */
    public void setResourceMap(Map<String, String> resourceMap) {
        this.resourceMap = resourceMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course, parent, false);
        return new CourseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        CourseItem course = courseList.get(position);
        
        holder.nameTextView.setText(course.getName());
        holder.codeTextView.setText(course.getCode());
        holder.departmentTextView.setText(course.getDepartment());
        holder.creditsTextView.setText(String.format("Department: %s | Duration: %d hours", 
                course.getDepartment(), course.getDurationHours()));
        holder.sessionsTextView.setText(String.format("Sessions: %d lectures, %d labs", 
                course.getNumberOfLectures(), course.getNumberOfLabs()));
        
        // Set room information
        String resourceId = course.getAssignedResourceId();
        if (resourceId != null && !resourceId.isEmpty() && resourceMap.containsKey(resourceId)) {
            holder.roomTextView.setVisibility(View.VISIBLE);
            holder.roomTextView.setText("Room: " + resourceMap.get(resourceId));
        } else {
            holder.roomTextView.setVisibility(View.VISIBLE);
            holder.roomTextView.setText("Room: Not assigned");
        }
        
        // Set up item click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCourseClick(course, holder.getAdapterPosition());
            }
        });
        
        // Set up edit button click listener
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCourseEditClick(course, holder.getAdapterPosition());
            }
        });
        
        // Set up delete button click listener
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCourseDeleteClick(course, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return courseList == null ? 0 : courseList.size();
    }

    /**
     * ViewHolder for course items.
     */
    static class CourseViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        TextView codeTextView;
        TextView departmentTextView;
        TextView creditsTextView;
        TextView sessionsTextView;
        TextView roomTextView;
        ImageView editButton;
        ImageView deleteButton;

        CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.courseName);
            codeTextView = itemView.findViewById(R.id.courseCode);
            departmentTextView = itemView.findViewById(R.id.courseDepartment);
            creditsTextView = itemView.findViewById(R.id.courseCredits);
            sessionsTextView = itemView.findViewById(R.id.courseSessions);
            roomTextView = itemView.findViewById(R.id.courseRoom);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    /**
     * Interface for course item click events.
     */
    public interface OnCourseClickListener {
        void onCourseClick(CourseItem course, int position);
        void onCourseEditClick(CourseItem course, int position);
        void onCourseDeleteClick(CourseItem course, int position);
    }
}
