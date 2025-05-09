# Arrow plug-in for IntelliJ-based IDEs

Different helpers related to [Arrow](https://arrow-kt.io/) libraries.

<a href="https://plugins.jetbrains.com/plugin/24550-arrow"><img height="48px" src="media/marketplace.png" /></a>


## Inspections

This is a list of implemented and planned inspections.
When possible, the plug-in also offers a fix for the problem.

_Related to `Raise`_:

- Missing `bind` and `bindAll`
- Different contexts, and missing `withError`
- Idiomatic usage of `ensure` and `ensureNotNull`
- Potential escape of `Raise` context
- Gutter icons for calls using `Raise`

_Related to `Eval`_:
- Matching on `Eval` values directly
- Gutter icons for `later` and `always`

_Related to atomics_:

- Incorrect usage of `Atomic` with primitive types

_Related to serialization_:

- Missing serializer for an Arrow type

![Video of bind](media/bind.gif)

## Running the development version

Open the project in IntelliJ, or run `./gradlew runIde` from the command line.

On the first run, an entire IntelliJ IDEA is downloaded, so it can take quite some time.

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