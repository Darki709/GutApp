package com.example.gutapp.session.Requests;

import com.example.gutapp.database.ChartStateDao;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.ChartSyncResponses;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SyncChartPush — upload the user's locally-changed (dirty) chart-state rows so the
 * server can apply them last-write-wins. Mirrors {@code ModifyWatchlistItems}.
 *
 * Payload: [2B count] then per row
 *   [1B kindCode][2B keyLen][key][1B deleted][8B updated_at][4B payloadLen][payload]
 * kindCode: 0 = drawings, 1 = indicators, 2 = preset. Big-endian throughout.
 *
 * On success the pushed rows are returned via {@link DataType#CHART_SYNC_PUSHED} so the
 * caller can clear their dirty flags.
 */
public class SyncChartPush extends AsyncRequest {

    private final List<ChartStateDao.Row> rows;

    public SyncChartPush(List<ChartStateDao.Row> rows, SessionCallback caller) {
        super(caller);
        this.rows = rows;
    }

    public List<ChartStateDao.Row> getRows() { return rows; }

    private static byte kindCode(String kind) {
        if (ChartStateDao.KIND_INDICATORS.equals(kind)) return 1;
        if (ChartStateDao.KIND_PRESET.equals(kind))     return 2;
        return 0; // drawings
    }

    @Override
    public byte[] getBytes() {
        // Pre-encode keys/payloads and size the buffer exactly.
        int n = rows.size();
        byte[][] keyB = new byte[n][];
        byte[][] payB = new byte[n][];
        int rowsBytes = 0;
        for (int i = 0; i < n; i++) {
            ChartStateDao.Row r = rows.get(i);
            keyB[i] = r.key != null ? r.key.getBytes(StandardCharsets.UTF_8) : new byte[0];
            payB[i] = r.payload != null ? r.payload.getBytes(StandardCharsets.UTF_8) : new byte[0];
            rowsBytes += 1 + 2 + keyB[i].length + 1 + 8 + 4 + payB[i].length;
        }

        int payloadLen = 2 + rowsBytes;      // [2B count] + rows
        int length     = 6 + payloadLen;     // Flag(1) + Type(1) + ReqId(4) + payload

        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.SYNC_CHART_PUSH.value);
        buffer.put(reqId);
        buffer.putShort((short) n);
        for (int i = 0; i < n; i++) {
            ChartStateDao.Row r = rows.get(i);
            buffer.put(kindCode(r.kind));
            buffer.putShort((short) keyB[i].length);
            buffer.put(keyB[i]);
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
        ChartSyncResponses.PushResult res = (ChartSyncResponses.PushResult) response;
        if (res.status == 0) {
            caller.onDataReceived(DataType.CHART_SYNC_PUSHED, rows);
        } else {
            caller.onDataReceived(DataType.CHART_SYNC_ERROR, "Chart push failed: " + res.status);
        }
    }
}
