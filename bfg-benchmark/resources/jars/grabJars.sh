#!/bin/bash
for i in {3..10}
do
	VERSION="1.$i.0"
	curl -O "http://repo1.maven.org/maven2/com/madgag/bfg/$VERSION/bfg-$VERSION.jar"
done