#!/bin/bash
for i in 4.0 5.0 6.0 7.0 12.0 13.0 13.1 13.2
do
	VERSION="1.$i"
	curl -O "https://repo1.maven.org/maven2/com/madgag/bfg/$VERSION/bfg-$VERSION.jar"
done
