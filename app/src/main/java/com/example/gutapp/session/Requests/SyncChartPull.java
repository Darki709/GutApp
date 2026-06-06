package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.ChartSyncResponses;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;

/**
 * SyncChartPull — request the full set of the user's chart-state rows (drawings,
 * indicators, presets) from the server. No payload. Mirrors {@code GetWatchlists}.
 *
 * On success the parsed rows are delivered via {@link DataType#CHART_SYNC_PULLED}
 * (the caller — ChartSyncManager — merges them into the local cache last-write-wins).
 */
public class SyncChartPull extends AsyncRequest {

    public SyncChartPull(SessionCallback caller) {
        super(caller);
    }

    @Override
    public byte[] getBytes() {
        // length = Flag(1) + Type(1) + ReqId(4) = 6
        ByteBuffer buffer = ByteBuffer.allocate(4 + 6);
        buffer.putInt(6);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.SYNC_CHART_PULL.value);
        buffer.put(reqId);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        this.isDone = true;   // one-shot — evict from pendingRequests after handling
        if (caller == null) return;
        ChartSyncResponses.PullResult res = (ChartSyncResponses.PullResult) response;
        if (res.status == 0) {
            caller.onDataReceived(DataType.CHART_SYNC_PULLED, res.rows);
        } else {
            caller.onDataReceived(DataType.CHART_SYNC_ERROR, "Chart pull failed: " + res.status);
        }
    }
}
