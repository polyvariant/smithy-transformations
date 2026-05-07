$version: "2"

namespace example

use smithytransformations#addOperations

@addOperations([Another])
service MyService {
    operations: [A]
}

apply MyService @addOperations([Third])

operation A {}

operation Another {}

operation Third {}
