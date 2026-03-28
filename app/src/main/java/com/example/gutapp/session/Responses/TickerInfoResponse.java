package com.example.gutapp.session.Responses;

import android.util.Log;

import com.example.gutapp.data.models.TickerInformation;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TickerInfoResponse extends AsyncResponse {
    TickerInformation information;
    public TickerInfoResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length - 5);
        int name_len = buffer.get() & 0xFF;
        if(name_len == 0){
            information = null;
        }
        else {
            byte[] nameBytes = new byte[name_len];
            buffer.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.US_ASCII);
            int exchange_len = buffer.get() & 0xFF;
            byte[] exchangeBytes = new byte[exchange_len];
            buffer.get(exchangeBytes);
            String exchange = new String(exchangeBytes, StandardCharsets.US_ASCII);
            byte typeByte = buffer.get();
            TickerInformation.AssetType type = TickerInformation.AssetType.fromByte(typeByte);
            int sector_len = buffer.get() & 0xFF;
            byte[] sectorBytes = new byte[sector_len];
            buffer.get(sectorBytes);
            String sector = new String(sectorBytes, StandardCharsets.US_ASCII);
            information = new TickerInformation(name, exchange, type, sector);
        }
    }

    public TickerInformation getInformation() throws NullPointerException{
        return information;
    }
}
