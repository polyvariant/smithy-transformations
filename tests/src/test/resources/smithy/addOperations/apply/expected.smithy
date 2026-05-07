$version: "2"

namespace example

use smithytransformations#addOperations

@addOperations([Another, Third])
service MyService {
    operations: [A, Another, Third]
}

operation A {}

operation Another {}

operation Third {}
