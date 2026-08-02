package ui.samples

import ui.samples.model.SampleCategory
import ui.samples.model.SampleDescriptor

/**
 * The bundled sample projects.
 *
 * `ruleResPaths` must list a sample's rule files in the order its `manifest.yaml` does — that order is
 * what the engine evaluates in, and `set` and `stop` both depend on it. `SampleRegistryTest` compares the
 * two so a sample cannot ship with the gallery loading it in a different order from the manifest.
 */
object SampleRegistry {

    val all: List<SampleDescriptor> = listOf(
        SampleDescriptor(
            id = "financial-transactions",
            name = "Financial Transactions",
            description = "Classify bank payments by purpose, detect fraud signals, " +
                    "and identify VIP customers based on amount and tags.",
            category = SampleCategory.FINANCE,
            manifestResPath = "files/samples/financial-transactions/manifest.yaml",
            schemaResPath = "files/samples/financial-transactions/schema.yaml",
            actionsResPath = "files/samples/financial-transactions/actions.yaml",
            ruleResPaths = listOf(
                "files/samples/financial-transactions/rules/rent-payment.rule",
                "files/samples/financial-transactions/rules/fraud-detection.rule",
                "files/samples/financial-transactions/rules/vip-customer.rule",
                "files/samples/financial-transactions/rules/transaction-aggregates.rule",
                "files/samples/financial-transactions/rules/recurring-payments.rule",
            ),
        ),
        SampleDescriptor(
            id = "kyc-onboarding",
            name = "KYC Onboarding (Germany)",
            description = "Customer due diligence on a German business customer under the GwG: two " +
                "guards that stop the run, and requirement checks that deliberately do not, so one " +
                "run reports every outstanding document at once.",
            category = SampleCategory.COMPLIANCE,
            manifestResPath = "files/samples/kyc-onboarding/manifest.yaml",
            schemaResPath = "files/samples/kyc-onboarding/schema.yaml",
            actionsResPath = "files/samples/kyc-onboarding/actions.yaml",
            ruleResPaths = listOf(
                // First two: the guards. Everything after them reports independently.
                "files/samples/kyc-onboarding/rules/application-gate.rule",
                "files/samples/kyc-onboarding/rules/sanctions-screening.rule",
                "files/samples/kyc-onboarding/rules/company-identification.rule",
                "files/samples/kyc-onboarding/rules/people-identification.rule",
                "files/samples/kyc-onboarding/rules/business-details.rule",
                "files/samples/kyc-onboarding/rules/risk-and-completeness.rule",
            ),
        ),
        SampleDescriptor(
            id = "loan-decisioning",
            name = "Loan Decisioning",
            description = "Underwrite a retail loan application: rule variables derive debt-to-income, " +
                "disposable income and loan-to-value once, and the policy and pricing rules read them back.",
            category = SampleCategory.FINANCE,
            manifestResPath = "files/samples/loan-decisioning/manifest.yaml",
            schemaResPath = "files/samples/loan-decisioning/schema.yaml",
            actionsResPath = "files/samples/loan-decisioning/actions.yaml",
            ruleResPaths = listOf(
                // First: the rules that follow are written in the ratios it publishes.
                "files/samples/loan-decisioning/rules/applicant-ratios.rule",
                "files/samples/loan-decisioning/rules/underwriting-decision.rule",
                "files/samples/loan-decisioning/rules/risk-pricing.rule",
            ),
        ),
        SampleDescriptor(
            id = "log-filter",
            name = "Log Filter",
            description = "Route, suppress, and escalate application log events by severity level, " +
                    "response time, and originating service.",
            category = SampleCategory.LOGGING,
            manifestResPath = "files/samples/log-filter/manifest.yaml",
            schemaResPath = "files/samples/log-filter/schema.yaml",
            actionsResPath = "files/samples/log-filter/actions.yaml",
            ruleResPaths = listOf(
                // First: the suppression rule, which ends the run for a discarded entry.
                "files/samples/log-filter/rules/error-severity.rule",
                "files/samples/log-filter/rules/slow-request.rule",
                "files/samples/log-filter/rules/service-alert.rule",
            ),
        ),
        SampleDescriptor(
            id = "product-recommendation",
            name = "Product Recommendation",
            description = "Boost, badge, and discount products for recommendation engines " +
                    "based on category, price, rating, and inventory.",
            category = SampleCategory.ECOMMERCE,
            manifestResPath = "files/samples/product-recommendation/manifest.yaml",
            schemaResPath = "files/samples/product-recommendation/schema.yaml",
            actionsResPath = "files/samples/product-recommendation/actions.yaml",
            ruleResPaths = listOf(
                // First: the stock check, which ends the run for a product that cannot be sold.
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
            manifestResPath = "files/samples/warehouse-shipments/manifest.yaml",
            schemaResPath = "files/samples/warehouse-shipments/schema.yaml",
            actionsResPath = "files/samples/warehouse-shipments/actions.yaml",
            ruleResPaths = listOf(
                // First: the rules that follow read the variables it publishes.
                "files/samples/warehouse-shipments/rules/shipment-totals.rule",
                "files/samples/warehouse-shipments/rules/delivery-quality.rule",
                "files/samples/warehouse-shipments/rules/parcel-condition.rule",
                "files/samples/warehouse-shipments/rules/route-risk.rule",
            ),
        ),
        SampleDescriptor(
            id = "access-control",
            name = "Access Control",
            description = "Enforce role-based permissions, IP allowlist/blocklist rules, " +
                    "and time-window restrictions on API routes.",
            category = SampleCategory.SECURITY,
            manifestResPath = "files/samples/access-control/manifest.yaml",
            schemaResPath = "files/samples/access-control/schema.yaml",
            actionsResPath = "files/samples/access-control/actions.yaml",
            ruleResPaths = listOf(
                // First: the block list, which ends the run before any role can grant access.
                "files/samples/access-control/rules/ip-filter.rule",
                "files/samples/access-control/rules/role-check.rule",
                "files/samples/access-control/rules/time-restriction.rule",
            ),
        ),
    )
}
