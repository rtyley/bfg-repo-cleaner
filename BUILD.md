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

To find the jar once it's built, just look at the last few lines of output from the
`assembly` task - it'll say something like this:

```
[info] Packaging /Users/roberto/development/bfg-repo-cleaner/bfg/target/bfg-1.11.9-SNAPSHOT-master-21d2115.jar ...
[info] Done packaging.
[success] Total time: 19 s, completed 26-Sep-2014 16:05:11
```

If you're going to make changes to the Scala code, you may want to use IntelliJ and it's Scala
plugin to help with the Scala syntax...!

I personally found Coursera's [online Scale course](https://www.coursera.org/course/progfun) very helpful in
learning Scala, YMMV.
