package com.example.gutapp.session.Requests;

import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.AlertSyncResponses;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SyncAlertPush — upload the user's locally-changed (dirty) alerts so the server can apply
 * them last-write-wins. Mirrors {@code SyncChartPush}.
 *
 * Payload: [2B count] then per row
 *   [2B uuidLen][uuid][1B deleted][8B updated_at][4B payloadLen][payload]
 * Big-endian throughout; updated_at is epoch millis; payload is the alert JSON.
 *
 * On success the pushed rows are returned via {@link DataType#ALERT_SYNC_PUSHED} so the
 * caller can clear their dirty flags.
 */
public class SyncAlertPush extends AsyncRequest {

    private final List<AlertDBHelper.SyncRow> rows;

    public SyncAlertPush(List<AlertDBHelper.SyncRow> rows, SessionCallback caller) {
        super(caller);
        this.rows = rows;
    }

    public List<AlertDBHelper.SyncRow> getRows() { return rows; }

    @Override
    public byte[] getBytes() {
        // Pre-encode uuids/payloads and size the buffer exactly.
        int n = rows.size();
        byte[][] uuidB = new byte[n][];
        byte[][] payB  = new byte[n][];
        int rowsBytes = 0;
        for (int i = 0; i < n; i++) {
            AlertDBHelper.SyncRow r = rows.get(i);
            uuidB[i] = r.uuid != null ? r.uuid.getBytes(StandardCharsets.UTF_8) : new byte[0];
            payB[i]  = r.payload != null ? r.payload.getBytes(StandardCharsets.UTF_8) : new byte[0];
            rowsBytes += 2 + uuidB[i].length + 1 + 8 + 4 + payB[i].length;
        }

        int payloadLen = 2 + rowsBytes;      // [2B count] + rows
        int length     = 6 + payloadLen;     // Flag(1) + Type(1) + ReqId(4) + payload

        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.SYNC_ALERT_PUSH.value);
        buffer.put(reqId);
        buffer.putShort((short) n);
        for (int i = 0; i < n; i++) {
            AlertDBHelper.SyncRow r = rows.get(i);
            buffer.putShort((short) uuidB[i].length);
            buffer.put(uuidB[i]);
            buffer.put((byte) (r.deleted ? 1 : 0));
            buffer.putLong(r.updatedAt);
            buffer.putInt(payB[i].length);
            buffer.put(payB[i]);
        }
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        this.isDone = true;   // one-shot — evict from pendingRequests after handling
        if (caller == null) return;
        AlertSyncResponses.PushResult res = (AlertSyncResponses.PushResult) response;
        if (res.status == 0) {
            caller.onDataReceived(DataType.ALERT_SYNC_PUSHED, rows);
        } else {
            caller.onDataReceived(DataType.ALERT_SYNC_ERROR, "Alert push failed: " + res.status);
        }
    }
}
