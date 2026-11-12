package creator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReceiptDeliveryService {
    private final InfraiClient client;
    private final String bucket;

    public ReceiptDeliveryService(InfraiClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public InfraiClient.StorageObject deliver(CreatorReceipt receipt, Path formPath) throws IOException, InterruptedException {
        if (!receipt.paid()) throw new IllegalStateException("receipt requires a paid subscription");
        String fields = "{\"creator_name\":\"" + quote(receipt.creatorName())
                + "\",\"product_name\":\"" + quote(receipt.productName())
                + "\",\"subscriber_id\":\"" + quote(receipt.subscriberId()) + "\"}";
        InfraiClient.PdfDocument filled = client.fillAndFlatten(InfraiClient.encodePdf(Files.readAllBytes(formPath)), fields,
                "receipt-" + receipt.subscriberId());
        return client.putObject(bucket, receipt.subscriberId() + "/receipt.pdf",
                InfraiClient.encodePdf(client.downloadPdf(filled)), "receipt-" + receipt.subscriberId());
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
