#!/bin/sh
cd "$(dirname "$0")"
chmod +x gradlew
./gradlew build && cp build/libs/vaporous-0.1.0.jar . && echo "Done! vaporous-0.1.0.jar is in this folder."
