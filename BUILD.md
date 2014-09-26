The BFG is written in Scala, a modern functional language that runs on the JVM - so it
can run anywhere Java can.

Here's a rough set of instructions for building the BFG, if you don't want to use the
pre-built [downloads](http://rtyley.github.io/bfg-repo-cleaner/#download):

* Install Java 6 or above
* Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html#installing-sbt)
* `git clone git@github.com:rtyley/bfg-repo-cleaner.git`
* `cd bfg-repo-cleaner`
* `sbt`<- start the sbt console
* `assembly` <- download dependencies, run the tests, build the jar

You may want to use IntelliJ and it's Scala plugin to help with the Scala syntax...!

I personally found Coursera's [online Scale course](https://www.coursera.org/course/progfun) very helpful in
learning Scala, YMMV.
