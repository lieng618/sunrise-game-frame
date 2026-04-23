#!/bin/bash

# Changing directory to single
cd "$(dirname "$0")/single"

echo "Starting runallone.json"
pm2 start runallone.json || { echo "Failed to start runallone, exiting"; exit 1; }
sleep 2

echo "success"
