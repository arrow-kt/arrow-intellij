# Arrow IntelliJ plug-in

Different helpers related to [Arrow](https://arrow-kt.io/) libraries.

## Inspections

This is a list of implemented and planned inspections.
When possible, the plug-in also offers a fix for the problem.

_Related to `Raise`_:
- [X] Missing `bind` and `bindAll`
- [X] Different contexts, and missing `withError`
- [X] Idiomatic usage of `ensure` and `ensureNotNull`

_Potentially wrong usages_:

- [X] Potentially wrong escape of `Raise` context
- [ ] Incorrect usage of `Atomic` with primitive types
- [ ] Matching on `Eval` instances directly

## License

    Copyright (C) 2024 The Arrow Authors

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.