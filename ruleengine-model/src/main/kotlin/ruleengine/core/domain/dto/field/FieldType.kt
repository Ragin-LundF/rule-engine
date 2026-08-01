package ruleengine.core.domain.dto.field

enum class FieldType {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    STRING_SET,
    DATE,

    /** A calendar date with a time of day. Unlike [DATE], the time component is kept and compared. */
    DATE_TIME,

    /** A list of elements, navigable with dotted paths and filters (e.g. `orders[status == "paid"].total`). */
    COLLECTION,

    /** A single nested object, navigable with dotted paths (e.g. `customer.address.city`). */
    OBJECT
}
