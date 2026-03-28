package com.example.gutapp.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.gutapp.R;
import com.example.gutapp.data.models.TickerInfo;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
    private List<TickerInfo> mData = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TickerInfo result);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<TickerInfo> newData) {
        this.mData = newData;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TickerInfo item = mData.get(position);

        // Set the Ticker (e.g., AAPL)
        holder.symbolTv.setText(item.symbol);

        // Set the Full Name (e.g., Apple Inc.)
        holder.nameTv.setText(item.name);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() { return mData.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView symbolTv, nameTv;
        public ViewHolder(View itemView) {
            super(itemView);
            symbolTv = itemView.findViewById(R.id.ticker_symbol);
            nameTv = itemView.findViewById(R.id.ticker_name); // Make sure this ID exists in your item XML
        }
    }
}
