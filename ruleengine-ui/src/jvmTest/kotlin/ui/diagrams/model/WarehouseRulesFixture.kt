package ui.diagrams.model

/**
 * A representative slice of `ruleengine-core/src/test/resources/warehouse-shipments`, copied verbatim.
 *
 * Seven of the thirteen rules, chosen to cover the output shapes [OutcomeKey] has to bucket: a rule
 * emitting two actions, and rules whose outputs share a prefix but belong to separate families
 * (`assessment:transit:green` vs `assessment:transit:red`).
 *
 * Inlined rather than read from the core module: parsing needs no schema, `ruleengine-ui` has no
 * dependency on the core module's test resources, and the repository's UI tests already declare rule
 * DSL inline.
 */
internal val WAREHOUSE_RULES = """
    rule "premium-service-promise" {
      when
        shipment.customer.tier equals "gold"
        and shipment.service contains "express"

      then
        assessment "service:premium"
        reason "gold-customer-on-express-service"
    }

    rule "transit-within-promise" {
      when
        shipment.transitDays <= 2

      then
        assessment "transit:green"
        reason "delivered-within-two-days"
    }

    rule "transit-over-promise" {
      when
        shipment.transitDays > 2

      then
        assessment "transit:red"
        reason "more-than-two-days-in-transit"
    }

    rule "all-parcels-intact" {
      when
        count(parcels[damaged == true]) == 0

      then
        assessment "condition:green"
        reason "no-damaged-parcel-reported"
    }

    rule "fragile-load" {
      when
        sum(parcels[category == "fragile"].weightKg) > sum(parcels.weightKg) * 0.25

      then
        assessment "handling:fragile-load"
        reason "fragile-parcels-exceed-a-quarter-of-the-weight"
    }

    rule "consolidate-at-hamburg-hub" {
      when
        count(parcels[origin.hub == "HAM"]) >= 2

      then
        assessment "consolidation:hub-ham"
        reason "two-or-more-parcels-originate-in-hamburg"
    }

    rule "tracking-gap" {
      when
        count(checkpoints[scanned == false]) > 0

      then
        assessment "tracking:gap"
        reason "a-checkpoint-was-not-scanned"
    }
""".trimIndent()
