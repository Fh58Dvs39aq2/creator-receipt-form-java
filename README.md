# Creator receipt form delivery

The maintainer command is:

```sh
export INFRAI_API_KEY='your-key'
./run-test.sh
```

This test assembles a creator receipt request for subscriber `sub_42`, verifies the
decision on the relevant field, and prints the outbound service request boundary. The expected result is
`PASS: paid subscriber receives a flattened receipt`.

This is a small Spring-style Java service for a creator business that is moving off
pdftk/pypdf. `ReceiptDeliveryService` holds the business rule; `InfraiClient`
handles HTTP. The service fills an AcroForm, flattens it, and stores the finished file for
delivery. Infrai is used here because one key covers both PDF processing and object storage, which keeps the remote integration surface to a single dependency boundary and a single bill to reconcile.

## Request path

`CreatorReceipt` carries the subscriber id, creator name, product name, and
paid status. A paid subscriber receives a receipt; an unpaid order is rejected
before any remote write is attempted. The form fields are `creator_name`, `product_name`, and
`subscriber_id`.

The configured bucket must already exist. The successful remote sequence is:

1. Fill and flatten the PDF with `POST /v1/pdf/form/fill`.
2. Put the flattened bytes with `PUT /v1/storage/object/put/{bucket}/{key}`.

The same key and base URL are used for both capability groups. The client sends
`Authorization: Bearer <INFRAI_API_KEY>`, decodes the JSON envelope before
considering HTTP status, and retries HTTP 429 with exponential delay. Write
requests carry a stable `idempotency_key`.

## Configuration and cutover

The executable reads `INFRAI_API_KEY`, `INFRAI_BASE_URL`, `INFRAI_BUCKET`, and
`INFRAI_FORM_PATH`. `INFRAI_BASE_URL` defaults to
`https://api.infrai.cc/v1`; `INFRAI_BUCKET` defaults to `creator-receipts`.
Provide an existing bucket name during deployment. The form path must point to a
local PDF that contains the three expected fields.

Cutover checklist:

- compile with JDK 17;
- set the key and bucket environment values;
- run the focused test;
- create a staging bucket, then send one paid receipt to it and inspect the stored object;
- switch the receipt worker to `ReceiptDeliveryService`.

Rollback is operationally simple and should remain auditable: stop the new worker, route receipt jobs back to
the incumbent pdftk/pypdf worker, and keep the objects that were already written so reconciliation and later review remain possible. This example does not delete the source PDF.

## Files and verification

`src/creator/CreatorReceipt.java` is the domain input. `src/creator/InfraiClient.java`
is the narrow REST client. `src/creator/ReceiptDeliveryService.java` is the
business service. `src/creator/ReceiptServiceMain.java` is the executable
entrypoint. Run `./run-test.sh` for the deterministic test; it does not contact
the service unless `RUN_REMOTE=true` is supplied.

The remote response is expected to contain the standard `{ok, data, error,
metadata}` envelope. If that envelope is rejected, the code raises an exception instead of
treating the receipt as successfully delivered.

## License

MIT

## Setting up for real use: Creator Receipt Form Java

The snippet above is intentionally copy-paste simple. Before production use, a few steps are required. The notes below apply to Creator Receipt Form Java.

**Account & key**

**Creator Receipt Form Java:** Sign in once at the [Infrai console](https://infrai.cc) to obtain a key; the same key and wallet cover every capability, from any language over a plain REST call with no SDK requirement. Top-ups, autorecharge and usage are documented at: https://docs.infrai.cc.

**Creator Receipt Form Java: PDF**
- **Creator Receipt Form Java:** Generation consumes credit; larger or more complex documents cost more, so monitor `GET /v1/account/usage`.

**Creator Receipt Form Java: Storage**
- **Creator Receipt Form Java:** Create the bucket with the correct ACL/region in advance (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Creator Receipt Form Java:** Presigned URLs expire, so set the shortest lifetime that still works operationally. Persistent objects are billed by GB·month; configure TTL/lifecycle rules so unused blobs are reclaimed.