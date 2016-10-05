#!/bin/bash
export CONDUCTR_IP=$(docker-machine ip default)
export HELLOWORLD_ENDPOINT=helloworld_ep
http GET $CONDUCTR_IP:9000/$HELLOWORLD_ENDPOINT/helloworld
http POST $CONDUCTR_IP:9000/$HELLOWORLD_ENDPOINT/person name=John age:=35