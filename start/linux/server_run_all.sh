#!/bin/bash

# Changing directory to single
cd "$(dirname "$0")/single"

echo "Starting center.json"
pm2 start center.json || { echo "Failed to start center, exiting"; exit 1; }
sleep 2

echo "Starting external.json"
pm2 start external.json || { echo "Failed to start external, exiting"; exit 1; }
sleep 2

echo "Starting global.json"
pm2 start global.json || { echo "Failed to start global, exiting"; exit 1; }
sleep 2

echo "Starting game.json"
pm2 start game.json || { echo "Failed to start game, exiting"; exit 1; }
sleep 2

echo "Starting http.json"
pm2 start http.json || { echo "Failed to start http, exiting"; exit 1; }
sleep 2

echo "Starting gmback.json"
pm2 start gmback.json || { echo "Failed to start gmback, exiting"; exit 1; }
sleep 2

echo "success"
