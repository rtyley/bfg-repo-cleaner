BFG Repo-Cleaner
================

Removes large or troublesome blobs like git-filter-branch does, but faster. And written in Scala

```
$ bfg --strip-biggest-blobs 500 some-big-repo.git
```

The BFG isn't a full replacement for the `git-filter-branch` tool: `git-filter-branch` is enormously powerful and can do things that the BFG can't - _however_ BFG is *much* better at these core use-cases:

* Removing big files from the history of your project, making it smaller - often a problem when someone commits a ridiculously large file by mistake.
* Removing passwords/private credentials from the history of your project
* Fixing incorrect author & committer details

For these use-cases, the BFG is a much better choice, because:

* Faster
* Simpler
* Beautiful - If you need to, you can use the beautiful Scala language to customise the BFG. Which has got to be better than Bash scripting at least some of the time.
