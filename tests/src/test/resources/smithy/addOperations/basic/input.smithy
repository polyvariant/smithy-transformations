$version: "2"

namespace example

use smithytransformations#addOperations

@addOperations([Another])
service MyService {
    operations: [A]
}

operation A {}

operation Another {}
