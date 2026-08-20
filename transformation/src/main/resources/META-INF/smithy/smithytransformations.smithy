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

/// Selectors matching the trait definition shapes whose traits should be stripped off
/// every shape in the model, e.g. `[trait|trait][id|namespace = 'smithy.rules']`.
@metadata(key: "smithytransformations#removeTraits")
list RemoveTraits {
    member: RemoveTraitsSelector
}

@private
@length(min: 1)
string RemoveTraitsSelector

/// Adds the listed error shapes to the operation or service this trait is applied to.
@trait(selector: ":is(operation, service)")
list addErrors {
    @idRef(failWhenMissing: true, selector: "structure[trait|error]")
    member: String
}

/// Removes the listed error shapes from the operation or service this trait is applied to.
///
/// Errors that aren't attached to the shape are ignored.
@trait(selector: ":is(operation, service)")
list removeErrors {
    @idRef(failWhenMissing: true, selector: "structure[trait|error]")
    member: String
}

/// Removes the listed operations from the service this trait is applied to.
///
/// Operations that aren't attached to the service are ignored. The operation shapes
/// themselves are left in the model; only the service's `operations` list is affected.
@trait(selector: "service")
list removeOperations {
    @idRef(failWhenMissing: true, selector: "operation")
    member: String
}

/// Removes the named members from the aggregate (structure or union) this trait is applied to.
///
/// Members that don't exist on the shape are ignored.
@trait(selector: ":is(structure, union)")
list removeMembers {
    member: RemoveMembersEntry
}

@private
@length(min: 1)
string RemoveMembersEntry
