The BFG is written in Scala, a modern functional language that runs on the JVM - so it
can run anywhere Java can.

Here's a rough set of instructions for building the BFG, if you don't want to use the
pre-built [downloads](http://rtyley.github.io/bfg-repo-cleaner/#download):

* Install Java JDK 11 or above
* Install [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)
* `git clone git@github.com:rtyley/bfg-repo-cleaner.git`
* `cd bfg-repo-cleaner`
* `sbt`<- start the sbt console
* `bfg/assembly` <- download dependencies, run the tests, build the jar

To find the jar once it's built, just look at the last few lines of output from the
`assembly` task - it'll say something like this:

```
[info] Packaging /Users/roberto/development/bfg-repo-cleaner/bfg/target/bfg-1.11.9-SNAPSHOT-master-21d2115.jar ...
[info] Done packaging.
[success] Total time: 19 s, completed 26-Sep-2014 16:05:11
```

If you're going to make changes to the Scala code, you may want to use IntelliJ and it's Scala
plugin to help with the Scala syntax...!

If you use [Eclipse IDE](http://www.eclipse.org/), you can set-up your development environment by following these instructions:

* Install `sbt` and build as-above
* Install [Scala IDE for Eclipse](http://scala-ide.org/) into your Eclipse installation if not already installed
* Add the `sbteclipse-plugin` to your set of local sbt plugins:

```
mkdir -p ~/.sbt/1.0/plugins && tee ~/.sbt/1.0/plugins/plugins.sbt <<EOF
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.2")
EOF
```

* `sbt`<- start the sbt console
* `eclipse` <- first-time only setup of the Eclipse plugin
* `eclipse` <- again, generate Eclipse project files (note that these are `.gitignore`d)
* In Eclipse, `File -> Import -> Existing Projects into Workspace`, browse to your `bfg` working-copy, and ensure that you select `Search for nested projects`
* You should now have the 4 `sbt` projects imported into your Eclipse workspace.

I personally found Coursera's [online Scala course](https://www.coursera.org/course/progfun) very helpful in
learning Scala, YMMV.
