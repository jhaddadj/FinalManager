package com.example.manager.admin.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.manager.R;
import com.example.manager.admin.model.Resource;

import java.util.List;

public class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.ResourceViewHolder> {

    private final List<Resource> resourceList;
    private final OnEditListener onEditListener;
    private final OnDeleteListener onDeleteListener;
    private Context context;

    public interface OnEditListener {
        void onEdit(Resource resource);
    }

    public interface OnDeleteListener {
        void onDelete(Resource resource);
    }

    public ResourceAdapter(Context context, List<Resource> resourceList, OnEditListener onEditListener, OnDeleteListener onDeleteListener) {
        this.context=context;
        this.resourceList = resourceList;
        this.onEditListener = onEditListener;
        this.onDeleteListener = onDeleteListener;
    }

    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resource, parent, false);
        return new ResourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        Resource resource = resourceList.get(position);
        holder.nameTextView.setText(resource.getName());
        holder.typeTextView.setText(resource.getType());
        holder.capacityTextView.setText(String.valueOf(resource.getCapacity()));
        if (resource.getIsAvailable().equalsIgnoreCase("yes")) {
            holder.availabilityTextView.setText("Available");
            holder.availabilityTextView.setTextColor(context.getResources().getColor(R.color.green)); // Replace with your green color resource
        } else {
            holder.availabilityTextView.setText("Not Available");
            holder.availabilityTextView.setTextColor(context.getResources().getColor(R.color.red)); // Replace with your red color resource
        }
        holder.itemView.setOnClickListener(v -> onEditListener.onEdit(resource));
        holder.itemView.setOnLongClickListener(v -> {
            onDeleteListener.onDelete(resource);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return resourceList.size();
    }

    public static class ResourceViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, typeTextView, capacityTextView, availabilityTextView;

        public ResourceViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.valueNameTextView);
            typeTextView = itemView.findViewById(R.id.valueTypeTextView);
            capacityTextView = itemView.findViewById(R.id.valueCapacityTextView);
            availabilityTextView = itemView.findViewById(R.id.valueAvailabilityTextView);
        }
    }
}
