#!/bin/bash
for i in 4.0 5.0 6.0 7.0 9.0 10.0 11.0 11.1 11.2
do
	VERSION="1.$i"
	curl -O "http://repo1.maven.org/maven2/com/madgag/bfg/$VERSION/bfg-$VERSION.jar"
done
