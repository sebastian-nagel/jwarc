/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2020-2022 National Library of Australia and the jwarc contributors
 */

package org.netpreserve.jwarc.tools;

import org.netpreserve.jwarc.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ExtractTool {

    private enum ExtractAction { RECORD, HEADERS, PAYLOAD }

    private static void writeWarcHeaders(WritableByteChannel out, WarcRecord record) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(record.version().toString()).append("\r\n");
        record.headers().appendTo(sb);
        sb.append("\r\n");
        out.write(ByteBuffer.wrap(sb.toString().getBytes(UTF_8)));
    }

    private static void writeHttpHeaders(WritableByteChannel out, WarcRecord record) throws IOException {
        if (record instanceof WarcResponse) {
            HttpResponse response = ((WarcResponse) record).http();
            out.write(ByteBuffer.wrap(response.serializeHeader()));
        } else if (record instanceof WarcRequest) {
            HttpRequest request = ((WarcRequest) record).http();
            out.write(ByteBuffer.wrap(request.serializeHeader()));
        }
    }

    private static void writePayload(WritableByteChannel out, WarcRecord record) throws IOException {
        try {
            MessageBody payload;
            if (record instanceof WarcResponse) {
                if (record.contentType().base().equals(MediaType.HTTP)) {
                    HttpResponse response = ((WarcResponse) record).http();
                    payload = response.bodyDecoded();
                } else {
                    payload = ((WarcResponse) record).payload().map(WarcPayload::body).orElse(MessageBody.empty());
                }
            } else if (record instanceof WarcRequest) {
                if (record.contentType().base().equals(MediaType.HTTP)) {
                    HttpRequest request = ((WarcRequest) record).http();
                    payload = request.bodyDecoded();
                } else {
                    payload = ((WarcRequest) record).payload().map(WarcPayload::body).orElse(MessageBody.empty());
                }
            } else {
                payload = record.body();
            }
            writeBody(out, payload);
        } catch (IOException e) {
            System.err.println("Failed to read payload: " + e.getMessage());
        }
    }

    private static void writeBody(WritableByteChannel out, ReadableByteChannel body) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        while (body.read(buffer) > -1) {
            buffer.flip();
            out.write(buffer);
            buffer.compact();
        }
    }

    private static void usage(int exitValue) {
        System.err.println();
        System.err.println("ExtractTool [-h] [--payload | --headers] filename offset ...");
        System.err.println();
        System.err.println("Options:");
        System.err.println();
        System.err.println(" --headers\toutput only record (and HTTP) headers");
        System.err.println(" --payload\toutput only record payload, if necessary");
        System.err.println("          \tdecode transfer and/or content encoding");
        System.exit(exitValue);
    }

    public static void main(String[] args) throws IOException {
        ExtractAction action = ExtractAction.RECORD;
        Path warcFile = null;
        List<Long> offsets = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
            case "-h":
            case "--help":
                usage(0);
            case "--headers":
                action = ExtractAction.HEADERS;
                break;
            case "--payload":
                action = ExtractAction.PAYLOAD;
                break;
            default:
                if (warcFile == null) {
                    warcFile = Paths.get(arg);
                    if (!warcFile.toFile().canRead()) {
                        System.err.println("Cannot read WARC file: " + warcFile);
                        usage(1);
                    }
                } else if (arg.startsWith("-")) {
                    System.err.println("Unknown argument: " + arg);
                    usage(1);
                } else {
                    try {
                        offsets.add(Long.parseLong(arg));
                    } catch (NumberFormatException e) {
                        System.err.println(e.getMessage());
                        usage(1);
                    }
                }
            }
        }
        if (warcFile == null || offsets.isEmpty()) {
            usage(1);
        }
        for (long offset : offsets) {
            try (FileChannel channel = FileChannel.open(warcFile);
                 WarcReader reader = new WarcReader(channel.position(offset))) {
                Optional<WarcRecord> record = reader.next();
                if (!record.isPresent()) {
                    System.err.println("No record found at position " + offset);
                    System.exit(1);
                }
                WritableByteChannel out = Channels.newChannel(System.out);
                switch (action) {
                    case RECORD:
                        writeWarcHeaders(out, record.get());
                        writeBody(out, record.get().body());
                        out.write(ByteBuffer.wrap("\r\n\r\n".getBytes(US_ASCII)));
                        break;
                    case HEADERS:
                        writeWarcHeaders(out, record.get());
                        writeHttpHeaders(out, record.get());
                        break;
                    case PAYLOAD:
                        writePayload(out, record.get());
                        break;
                }
            }
        }
    }
}
