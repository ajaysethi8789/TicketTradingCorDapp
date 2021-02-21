#!/bin/sh

for dir in Bank Seller Buyer Organizer Notary ; do
    cd build/nodes/$dir
    echo Starting $dir
    java -Xmx2G -jar corda.jar > std.out 2>&1 &
    sleep 5
    cd ../../..
done
