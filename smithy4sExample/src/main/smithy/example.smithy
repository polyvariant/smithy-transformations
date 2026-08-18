$version: "2"

// every trait in the `custom` namespace is stripped from the model before codegen runs
metadata "smithytransformations#removeTraits" = ["[trait|trait][id|namespace = 'custom']"]

namespace example

use smithytransformations#addOperations
use custom#internal

@addOperations([Another])
service MyService {
    operations: [A]
}

@internal
operation A {}

operation Another {}
