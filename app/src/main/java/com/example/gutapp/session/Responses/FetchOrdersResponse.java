package com.example.gutapp.session.Responses;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;
import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.util.Log;

import com.example.gutapp.data.models.Order;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lombok.Getter;

public class FetchOrdersResponse extends AsyncResponse {
    @Getter
    private final ArrayList<Order> orders;
    public FetchOrdersResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length-5);

        int count = 0;
        if (buffer.remaining() >= 4) {
            count = buffer.getInt();
        }

        this.orders = new ArrayList<>(count);

        try {
            while (buffer.hasRemaining() && this.orders.size() < count) {
                // Uses the Order.fromBuffer logic we defined earlier
                this.orders.add(Order.fromBuffer(buffer));
            }
        } catch (Exception e) {
            // Log parsing errors (e.g., if the buffer is truncated)
           Log.e("NETWORK", "Error parsing order batch: " + e.getMessage());
           throw e;
        }
        Log.d(NETWORK_LOG_TAG, String.format("amount orders received: %d", count));
    }

    public static void debugPrintOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            Log.d(CHART_LOG_TAG, "Order list is empty or null.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Log.d(CHART_LOG_TAG, "--- Printing " + orders.size() + " Orders ---");

        for (int i = 0; i < orders.size(); i++) {
            Order o = orders.get(i);
            StringBuilder sb = new StringBuilder();

            sb.append(String.format("[%d] ID:%-5d | %-6s | %-5s | Qty:%-4d | Price:%.2f | Entry:%s",
                    i, o.getOrder_id(), o.getSymbol(), o.getType(),
                    o.getQuantity(), o.getEntry_price(), sdf.format(new Date(o.getEntry_ts()))));

            if (o.isActive()) {
                sb.append(" | STATUS: ACTIVE");
            } else {
                // Handle Optional values for closed orders
                String endP = o.getEnd_price().map(p -> String.format("%.2f", p)).orElse("N/A");
                String endT = o.getEnd_ts().map(ts -> sdf.format(new Date(ts))).orElse("N/A");

                sb.append(String.format(" | STATUS: CLOSED | EndPrice:%s | EndTime:%s", endP, endT));
            }

            Log.d(CHART_LOG_TAG, sb.toString());
        }
        Log.d(CHART_LOG_TAG, "------------------------------------------");
}
}
