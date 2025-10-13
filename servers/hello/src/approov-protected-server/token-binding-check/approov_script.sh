#!/usr/bin/env bash

echo "Starting Approov check..."
sleep 4

approov
sleep 4

echo "Checking git status..."
git status