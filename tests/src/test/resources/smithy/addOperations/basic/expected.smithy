$version: "2"

namespace example

use smithytransformations#addOperations

@addOperations([Another])
service MyService {
    operations: [A, Another]
}

operation A {}

operation Another {}
