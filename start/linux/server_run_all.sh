#!/bin/bash

# Changing directory to single
cd "$(dirname "$0")/single"

echo "Starting center.json"
pm2 start center.json
sleep 2

echo "Starting external.json"
pm2 start external.json
sleep 2

echo "Starting global.json"
pm2 start global.json
sleep 2

echo "Starting game.json"
pm2 start game.json
sleep 2

echo "Starting http.json"
pm2 start http.json
sleep 2

echo "Starting gmback.json"
pm2 start gmback.json
sleep 2

echo "success"