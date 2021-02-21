#!/bin/sh

cd build/nodes
for i in Seller Bank Organizer Buyer ; do
    cp ../../ticket-contracts/build/libs/*.jar $i/cordapps
    cp ../../ticket-flows/build/libs/*.jar $i/cordapps
done
