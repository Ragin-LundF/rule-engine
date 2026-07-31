package ui.samples

object SampleRegistry {

    val all: List<SampleDescriptor> = listOf(
        SampleDescriptor(
            id = "financial-transactions",
            name = "Financial Transactions",
            description = "Classify bank payments by purpose, detect fraud signals, and identify VIP customers based on amount and tags.",
            category = SampleCategory.FINANCE,
            schemaResPath = "files/samples/financial-transactions/schema.yaml",
            actionsResPath = "files/samples/financial-transactions/actions.yaml",
            ruleResPaths = listOf(
                "files/samples/financial-transactions/rules/rent-payment.rule",
                "files/samples/financial-transactions/rules/fraud-detection.rule",
                "files/samples/financial-transactions/rules/vip-customer.rule",
            ),
        ),
        SampleDescriptor(
            id = "log-filter",
            name = "Log Filter",
            description = "Route, suppress, and escalate application log events by severity level, response time, and originating service.",
            category = SampleCategory.LOGGING,
            schemaResPath = "files/samples/log-filter/schema.yaml",
            actionsResPath = "files/samples/log-filter/actions.yaml",
            ruleResPaths = listOf(
                "files/samples/log-filter/rules/error-severity.rule",
                "files/samples/log-filter/rules/slow-request.rule",
                "files/samples/log-filter/rules/service-alert.rule",
            ),
        ),
        SampleDescriptor(
            id = "product-recommendation",
            name = "Product Recommendation",
            description = "Boost, badge, and discount products for recommendation engines based on category, price, rating, and inventory.",
            category = SampleCategory.ECOMMERCE,
            schemaResPath = "files/samples/product-recommendation/schema.yaml",
            actionsResPath = "files/samples/product-recommendation/actions.yaml",
            ruleResPaths = listOf(
                "files/samples/product-recommendation/rules/premium-product.rule",
                "files/samples/product-recommendation/rules/category-boost.rule",
                "files/samples/product-recommendation/rules/discount-eligibility.rule",
            ),
        ),
        SampleDescriptor(
            id = "warehouse-shipments",
            name = "Warehouse Shipments",
            description = "Assess parcel deliveries from nested shipment data: dotted paths into the " +
                "shipment record, plus aggregates and filters over the parcel and checkpoint collections.",
            category = SampleCategory.LOGISTICS,
            schemaResPath = "files/samples/warehouse-shipments/schema.yaml",
            actionsResPath = "files/samples/warehouse-shipments/actions.yaml",
            ruleResPaths = listOf(
                "files/samples/warehouse-shipments/rules/delivery-quality.rule",
                "files/samples/warehouse-shipments/rules/parcel-condition.rule",
                "files/samples/warehouse-shipments/rules/route-risk.rule",
            ),
        ),
        SampleDescriptor(
            id = "access-control",
            name = "Access Control",
            description = "Enforce role-based permissions, IP allowlist/blocklist rules, and time-window restrictions on API routes.",
            category = SampleCategory.SECURITY,
            schemaResPath = "files/samples/access-control/schema.yaml",
            actionsResPath = "files/samples/access-control/actions.yaml",
            ruleResPaths = listOf(
                "files/samples/access-control/rules/role-check.rule",
                "files/samples/access-control/rules/ip-filter.rule",
                "files/samples/access-control/rules/time-restriction.rule",
            ),
        ),
    )
}
