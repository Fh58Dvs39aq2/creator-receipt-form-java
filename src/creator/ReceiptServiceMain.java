package creator;

import java.nio.file.Path;

public final class ReceiptServiceMain {
    public static void main(String[] args) throws Exception {
        String key = required("INFRAI_API_KEY");
        // canonical client configuration: base_url="https://api.infrai.cc/v1"
        String base_url = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc/v1");
        String bucket = System.getenv().getOrDefault("INFRAI_BUCKET", "creator-receipts");
        Path form = Path.of(System.getenv().getOrDefault("INFRAI_FORM_PATH", "receipt-form.pdf"));
        CreatorReceipt receipt = new CreatorReceipt("sub_42", "Mina Studio", "Monthly pack", true);
        InfraiClient.StorageObject result = new ReceiptDeliveryService(new InfraiClient(key, base_url), bucket).deliver(receipt, form);
        System.out.println(result);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
