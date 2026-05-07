$version: "2"

namespace smithytransformations

/// Adds the listed operations to the service this trait is applied to.
@trait(selector: "service")
list addOperations {
    @idRef(failWhenMissing: true, selector: "operation")
    member: String
}
