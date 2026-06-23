package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.AlertSyncResponses;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;

/**
 * SyncAlertPull — request the full set of the user's alerts from the server. No payload.
 * Mirrors {@code SyncChartPull}.
 *
 * On success the parsed rows are delivered via {@link DataType#ALERT_SYNC_PULLED}
 * (the caller — AlertSyncManager — merges them into the local store last-write-wins).
 */
public class SyncAlertPull extends AsyncRequest {

    public SyncAlertPull(SessionCallback caller) {
        super(caller);
    }

    @Override
    public byte[] getBytes() {
        // length = Flag(1) + Type(1) + ReqId(4) = 6
        ByteBuffer buffer = ByteBuffer.allocate(4 + 6);
        buffer.putInt(6);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.SYNC_ALERT_PULL.value);
        buffer.put(reqId);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        this.isDone = true;   // one-shot — evict from pendingRequests after handling
        if (caller == null) return;
        AlertSyncResponses.PullResult res = (AlertSyncResponses.PullResult) response;
        if (res.status == 0) {
            caller.onDataReceived(DataType.ALERT_SYNC_PULLED, res.rows);
        } else {
            caller.onDataReceived(DataType.ALERT_SYNC_ERROR, "Alert pull failed: " + res.status);
        }
    }
}
