/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2018 National Library of Australia and the jwarc contributors
 */

package org.netpreserve.jwarc;

import java.io.IOException;
import java.util.Optional;

public class WarcResource extends WarcCaptureRecord {
    WarcResource(ProtocolVersion version, Headers headers, BodyChannel body) {
        super(version, headers, body);
    }

    public Optional<WarcPayload> payload() throws IOException {
        return Optional.of(new WarcPayload(body()) {
            @Override
            MediaType type() {
                return contentType();
            }

            @Override
            Optional<MediaType> identifiedType() {
                return Optional.empty();
            }

            @Override
            Optional<Digest> digest() {
                Optional<Digest> payloadDigest = payloadDigest();
                return payloadDigest.isPresent() ? payloadDigest : blockDigest();
            }
        });
    }

    public static class Builder extends WarcCaptureRecord.Builder<WarcResource, Builder> {
        protected Builder() {
            super("resource");
        }

        @Override
        public WarcResource build() {
            return build(WarcResource::new);
        }
    }
}
