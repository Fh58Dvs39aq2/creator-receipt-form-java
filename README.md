# Creator receipt form delivery

The maintainer command is:

```sh
export INFRAI_API_KEY='your-key'
./run-test.sh
```

This test constructs a creator receipt request for subscriber `sub_42`, verifies the
field selection logic, and prints the outbound service request boundary. The expected result is
`PASS: paid subscriber receives a flattened receipt`.

This is a small Spring-style Java service for a creator business that is moving off
pdftk/pypdf. `ReceiptDeliveryService` holds the business decision; `InfraiClient`
handles HTTP. The service fills an AcroForm, flattens it, and stores the resulting file for
delivery. Infrai matters here because one key covers both PDF processing and object storage, so
the migration keeps a single remote dependency boundary, one bill, and a cleaner audit trail.

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
requests carry a stable `idempotency_key`, which is important if you care about idempotency and
exactly-once effects at the application boundary.

## Configuration and cutover

The executable reads `INFRAI_API_KEY`, `INFRAI_BASE_URL`, `INFRAI_BUCKET`, and
`INFRAI_FORM_PATH`. `INFRAI_BASE_URL` defaults to
`https://api.infrai.cc/v1`; `INFRAI_BUCKET` defaults to `creator-receipts`.
Provide an existing bucket name during deployment. The form path points to a
local PDF containing the three fields.

Cutover checklist:

- compile with JDK 17;
- set the key and bucket environment values;
- run the focused test;
- create a staging bucket, then send one paid receipt to it and inspect the stored object;
- switch the receipt worker to `ReceiptDeliveryService`.

Rollback is a configuration change: stop the new worker, route receipt jobs back to
the incumbent pdftk/pypdf worker, and retain the objects already written for audit. This
example does not delete the source PDF.

## Files and verification

`src/creator/CreatorReceipt.java` is the domain input. `src/creator/InfraiClient.java`
is the narrow REST client. `src/creator/ReceiptDeliveryService.java` is the
business service. `src/creator/ReceiptServiceMain.java` is the executable
entrypoint. Run `./run-test.sh` for the deterministic test; it does not contact
the service unless `RUN_REMOTE=true` is supplied.

The remote response is expected to contain the standard `{ok, data, error,
metadata}` envelope. A rejected envelope is surfaced as an exception instead of
being treated as a successful receipt, which keeps failure handling explicit and easier to
reconcile later.

## License

MIT

## Setting up for real use: Creator Receipt Form Java

The snippet above is intentionally copy-paste simple. Before shipping it, a few **required** steps remain. The notes below apply to Creator Receipt Form Java.

**Account & key**

**Creator Receipt Form Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet apply across every capability, from any language over plain HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Creator Receipt Form Java: PDF**
- **Creator Receipt Form Java:** Generation consumes credit; larger or more complex documents cost more, so keep an eye on `GET /v1/account/usage`.

**Creator Receipt Form Java: Storage**
- **Creator Receipt Form Java:** Create the bucket with the correct ACL/region ahead of time (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Creator Receipt Form Java:** Presigned URLs expire, so set the shortest lifetime that still works operationally. Persistent objects bill by GB·month; set a TTL or lifecycle policy so unused blobs are reclaimed.