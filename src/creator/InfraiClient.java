package creator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public final class InfraiClient {
    private final HttpClient http;
    private final String key;
    private final String baseUrl;

    public InfraiClient(String key, String baseUrl) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.key = key;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    public PdfDocument fillAndFlatten(String pdfBase64, String fieldsJson, String requestId)
            throws IOException, InterruptedException {
        String body = "{\"pdf\":\"" + pdfBase64 + "\",\"fields\":" + fieldsJson
                + ",\"flatten\":true,\"idempotency_key\":\"" + json(requestId) + "\"}";
        String data = send("POST", "/pdf/form/fill", body, requestId);
        return new PdfDocument(jsonValue(data, "pdf_id"), jsonValue(data, "url"));
    }

    public StorageObject putObject(String bucket, String objectKey, String dataBase64, String requestId)
            throws IOException, InterruptedException {
        String path = "/storage/object/put/" + encode(bucket) + "/" + encode(objectKey);
        String data = send("PUT", path, "{\"data_base64\":\"" + dataBase64
                + "\",\"idempotency_key\":\"" + json(requestId) + "\"}", requestId);
        return new StorageObject(jsonValue(data, "bucket_id"), jsonValue(data, "key"),
                Long.parseLong(jsonValue(data, "size_bytes")), jsonValue(data, "etag"),
                jsonValue(data, "created_at"));
    }

    public record PdfDocument(String pdfId, String url) {}
    public record StorageObject(String bucketId, String key, long sizeBytes, String etag, String createdAt) {}

    public byte[] downloadPdf(PdfDocument document) throws IOException, InterruptedException {
        String url = document.url();
        if (url == null || url.isBlank()) throw new IOException("PDF response has no URL");
        if (url.startsWith("data:application/pdf;base64,")) {
            return Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
        }
        URI base = URI.create(baseUrl + "/");
        URI target = base.resolve(url);
        HttpRequest.Builder request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30)).GET();
        // Never forward the API credential to a signed third-party download URL.
        if (base.getScheme().equals(target.getScheme()) && base.getAuthority().equals(target.getAuthority())) {
            request.header("Authorization", "Bearer " + key);
        }
        HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PDF download failed: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String send(String method, String path, String body, String requestId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429) {
                Thread.sleep((long) Math.pow(2, attempt) * 250L);
                continue;
            }
            if (jsonBoolean(envelope, "ok") == Boolean.FALSE) {
                throw new IOException("Infrai rejected request: " + jsonValue(envelope, "error"));
            }
            if (response.statusCode() >= 500) throw new IOException("remote service error");
            return jsonValue(envelope, "data");
        }
        throw new IOException("request retry budget exhausted");
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static Boolean jsonBoolean(String object, String key) {
        String value = jsonValue(object, key);
        return value == null ? null : Boolean.valueOf(value);
    }

    // Preserve structured JSON values; decode strings only when the value is a string.
    private static String jsonValue(String object, String key) {
        String marker = "\"" + key + "\"";
        int keyStart = object.indexOf(marker);
        if (keyStart < 0) return null;
        int colon = object.indexOf(':', keyStart + marker.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < object.length() && Character.isWhitespace(object.charAt(start))) start++;
        if (start >= object.length()) return null;
        if (object.charAt(start) != '\"') {
            int depth = 0;
            boolean quoted = false;
            boolean escaped = false;
            int end = start;
            for (; end < object.length(); end++) {
                char c = object.charAt(end);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') quoted = false;
                } else if (c == '"') quoted = true;
                else if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') {
                    if (depth == 0) break;
                    depth--;
                } else if (c == ',' && depth == 0) break;
            }
            return object.substring(start, end).trim();
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < object.length(); i++) {
            char c = object.charAt(i);
            if (escaped) {
                if (c == 'u') {
                    if (i + 4 >= object.length()) return null;
                    value.append((char) Integer.parseInt(object.substring(i + 1, i + 5), 16));
                    i += 4;
                } else value.append(c == 'n' ? '\n' : c == 'r' ? '\r' : c == 't' ? '\t' : c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '\"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }
    private static String encode(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
    public static String encodePdf(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
}
