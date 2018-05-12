# fatwheel

Run Clojure tools when files change.
Fatwheel reloads code, runs tests, linters, and custom tasks when you save a file.

![Fatwheel](fatwheel.jpg)


## Usage

No release is provided (yet!)

Clone this repo and build the jar:

    lein install

Then add it to your project dev dependencies:

    :profiles {:dev {:dependencies [[fatwheel "0.1.0-SNAPSHOT"]]}}

And create an alias to invoke it:

    :aliases {"fatwheel" ["run" "-m" "fatwheel.core/-main"]}
    
And now start it running:

    lein fatwheel
    
Edit your source code, when you save it the code is reloaded, tests are run, followed by eastwood and kibit.


## TODO

* Run a local webpage that serves a better view of all the output
* Format clickable console links
  - in IDEA IntelliJ .(file.clj:8) [but this does not give namespace differentiation]
  - in normal console use file:///... or similar?
* Make the console only show one actionable thing at a time (maybe have navigation keys)
* Minimize the amount of work done by diffing
* Reload libraries from deps.edn


## Contributing

Pull requests welcome.


## License

Copyright © 2018 Timothy Pratley

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
