BFG Repo-Cleaner 
================

[![CI](https://github.com/rtyley/bfg-repo-cleaner/actions/workflows/ci.yml/badge.svg)](https://github.com/rtyley/bfg-repo-cleaner/actions/workflows/ci.yml)
[![Release](https://github.com/rtyley/bfg-repo-cleaner/actions/workflows/release.yml/badge.svg)](https://github.com/rtyley/bfg-repo-cleaner/actions/workflows/release.yml)

_Removes large or troublesome blobs like git-filter-branch does, but faster - and written in Scala_ - [Fund the BFG](https://j.mp/fund-bfg)

```
$ bfg --strip-blobs-bigger-than 1M --replace-text banned.txt repo.git
```

The BFG is a simpler, faster ([10 - 720x](https://docs.google.com/spreadsheet/ccc?key=0AsR1d5Zpes8HdER3VGU1a3dOcmVHMmtzT2dsS2xNenc) faster)
alternative to `git-filter-branch` for cleansing bad data out of your Git repository:

* Removing **Crazy Big Files**
* Removing **Passwords, Credentials** & other **Private data**

Main documentation for The BFG is here : **https://rtyley.github.io/bfg-repo-cleaner/**
