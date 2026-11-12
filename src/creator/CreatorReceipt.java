package creator;

public record CreatorReceipt(String subscriberId, String creatorName, String productName, boolean paid) {
    public CreatorReceipt {
        if (subscriberId.isBlank() || creatorName.isBlank() || productName.isBlank()) {
            throw new IllegalArgumentException("receipt identity fields are required");
        }
    }
}
