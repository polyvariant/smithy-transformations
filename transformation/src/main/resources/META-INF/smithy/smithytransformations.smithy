$version: "2"

namespace smithytransformations

/// Adds the listed operations to the service this trait is applied to.
@trait(selector: "service")
list addOperations {
    @idRef(failWhenMissing: true, selector: "operation")
    member: String
}

/// Adds the listed members to the aggregate (structure or union) this trait is applied to.
@trait(selector: ":is(structure, union)")
list addMembers {
    member: AddMembersEntry
}

@private
structure AddMembersEntry {
    /// Name of the new member.
    @required
    name: String

    /// Shape targeted by the new member.
    @required
    @idRef(failWhenMissing: true)
    target: String

    /// Traits to apply to the new member, keyed by trait shape id.
    traits: TraitMap
}

@private
map TraitMap {
    key: String
    value: Document
}
