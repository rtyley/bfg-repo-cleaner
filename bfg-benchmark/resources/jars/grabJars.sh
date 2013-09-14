#!/bin/bash
for i in 4 5 6 7 9 10
do
	VERSION="1.$i.0"
	curl -O "http://repo1.maven.org/maven2/com/madgag/bfg/$VERSION/bfg-$VERSION.jar"
done