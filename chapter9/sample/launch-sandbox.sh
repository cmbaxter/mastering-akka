#!/bin/bash
eval "$(docker-machine env default)"
sandbox run 1.1.8 --feature visualization --nr-of-containers 3 -e DOCKER_HOST_IP=$(docker machine ip default)
open http://192.168.99.100:9999