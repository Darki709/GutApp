package com.example.gutapp.session.Responses;

import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responses for the alert-sync API.
 *
 * Wire layout (after the 1B msgType + 4B reqId header stripped by the framing):
 *  PushResult (MsgType 21): [1B status]
 *  PullResult (MsgType 20): [1B status][2B count] then `count` rows of
 *      [2B uuidLen][uuid][1B deleted][8B updated_at][4B payloadLen][payload]
 *
 * All multi-byte integers are big-endian (network order) — matches ByteBuffer's default.
 * The pull parse is defensive: a truncated/oversized field aborts cleanly instead of
 * throwing past the buffer, so a malformed response can never crash the receive loop.
 */
public class AlertSyncResponses {

    /** Result of a PUSH (upload of local dirty rows). */
    public static class PushResult extends AsyncResponse {
        public final byte status;
        public PushResult(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            this.status = response.length > 5 ? response[5] : (byte) 4;
        }
    }

    /** Result of a PULL — the authoritative alerts for the user. */
    public static class PullResult extends AsyncResponse {
        public final byte status;
        public final List<AlertDBHelper.SyncRow> rows = new ArrayList<>();

        public PullResult(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length - 5);
            this.status = buffer.get();
            if (this.status != 0) return;

            if (buffer.remaining() < 2) return;
            int count = buffer.getShort() & 0xFFFF;
            for (int i = 0; i < count; i++) {
                if (buffer.remaining() < 2) break;
                int uuidLen = buffer.getShort() & 0xFFFF;
                if (buffer.remaining() < uuidLen) break;
                byte[] uuidBytes = new byte[uuidLen];
                buffer.get(uuidBytes);

                if (buffer.remaining() < 1 + 8 + 4) break;
                boolean deleted = (buffer.get() & 0xFF) == 1;
                long updatedAt  = buffer.getLong();
                int payLen      = buffer.getInt();
                if (payLen < 0 || buffer.remaining() < payLen) break;
                byte[] payBytes = new byte[payLen];
                buffer.get(payBytes);

                rows.add(new AlertDBHelper.SyncRow(
                        new String(uuidBytes, StandardCharsets.UTF_8),
                        new String(payBytes, StandardCharsets.UTF_8),
                        updatedAt, deleted));
            }
        }
    }
}
