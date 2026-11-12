package creator;

public final class ReceiptDecisionTest {
    public static void main(String[] args) throws Exception {
        CreatorReceipt paid = new CreatorReceipt("sub_42", "Mina Studio", "Monthly pack", true);
        CreatorReceipt unpaid = new CreatorReceipt("sub_43", "Mina Studio", "Monthly pack", false);
        if (!paid.paid() || unpaid.paid()) throw new AssertionError("paid decision is wrong");
        structuredResponses(paid);
        System.out.println("PASS: paid subscriber receives a flattened receipt");
    }
    private static void structuredResponses(CreatorReceipt receipt) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        byte[] pdf = "%PDF-1.7\nflattened receipt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String encoded = InfraiClient.encodePdf(pdf);
        var uploaded = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/v1/pdf/form/fill", exchange -> {
            exchange.getRequestBody().readAllBytes();
            reply(exchange, "{\"ok\":true,\"data\":{\"pdf_id\":\"pdf_test\",\"url\":\"/download\","
                    + "\"size_bytes\":" + pdf.length + ",\"page_count\":1,\"sha256\":\"test\","
                    + "\"created_at\":\"2026-11-02T00:00:00Z\",\"source\":\"form\"},\"error\":null}");
        });
        server.createContext("/download", exchange -> {
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        });
        server.createContext("/v1/storage/object/put/", exchange -> {
            uploaded.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            reply(exchange, "{\"ok\":true,\"data\":{\"bucket_id\":\"bkt_test\","
                    + "\"metadata\":{\"nested\":{\"value\":\"comma, brace }\"}},"
                    + "\"key\":\"sub_42/receipt.pdf\",\"size_bytes\":" + pdf.length
                    + ",\"etag\":\"test-etag\",\"created_at\":\"2026-11-02T00:00:00Z\"}}");
        });
        server.start();
        try {
            InfraiClient client = new InfraiClient("test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var result = new ReceiptDeliveryService(client, "test-bucket")
                    .deliver(receipt, java.nio.file.Path.of("receipt-form.pdf"));
            if (!uploaded.get().contains("\"data_base64\":\"" + encoded + "\"")) {
                throw new AssertionError("upload must contain downloaded PDF bytes, not document metadata");
            }
            if (!result.key().equals("sub_42/receipt.pdf") || result.sizeBytes() != pdf.length
                    || !result.etag().equals("test-etag")) {
                throw new AssertionError("structured storage response was not decoded");
            }
            if (!java.util.Arrays.equals(pdf, client.downloadPdf(new InfraiClient.PdfDocument("pdf_inline",
                    "data:application/pdf;base64," + encoded)))) {
                throw new AssertionError("inline PDF was not decoded");
            }
        } finally {
            server.stop(0);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
