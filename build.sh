#!/bin/sh
./gradlew build
touch tradebot
rm tradebot
echo "#! /usr/bin/env java -jar" > tradebot
cat ./build/libs/tradebot-all.jar >> tradebot
chmod +x tradebot
