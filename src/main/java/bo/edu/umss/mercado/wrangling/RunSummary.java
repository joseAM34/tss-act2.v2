package bo.edu.umss.mercado.wrangling;

public record RunSummary(String id, String origin, String mode, String createdAt,
                         int inputCount, int includedCount, String logicalHash) {}
