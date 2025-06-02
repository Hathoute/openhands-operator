#!/bin/bash

set -euo pipefail

# First argument must be the prompt
OH_PROMPT=$1

HOST="0.0.0.0"
PORT=${REPORTER_LISTEN_PORT}
APP_MODULE="events_server:app"
WORKERS=1
SHUTDOWN_FILE="/tmp/reporter/SHUTDOWN"
EXITCODE_FILE="/tmp/reporter/OH_EXIT"

# Remove any existing reporter file
rm -rf "/tmp/reporter"
mkdir -p "/tmp/reporter"

# Start Gunicorn in the background
echo "Starting Gunicorn with $WORKERS workers on port $PORT..."
gunicorn -w $WORKERS -b $HOST:$PORT "$APP_MODULE" &
GUNICORN_PID=$!
echo "Gunicorn started with PID $GUNICORN_PID"

# Disable exit on error, since we want to keep the Gunicorn server running
# to serve the events even after openhands failure
set +e

# Start OpenHands with provided prompt
echo "Will run OpenHands with prompt: $OH_PROMPT"
poetry run python -m openhands.core.main -t "$OH_PROMPT"
OH_EXIT_CODE=$?
echo "OpenHands exited with code $OH_EXIT_CODE"

# Re-enable exit on error
set -e

# Save the exit code to a file
touch $EXITCODE_FILE
echo "$OH_EXIT_CODE" > $EXITCODE_FILE

# Wait for the operator to initiate a shutdown for this server
echo "Waiting for $SHUTDOWN_FILE to terminate the server..."
while true; do
    # If shutdown file is created, break the loop
    if [ -f "$SHUTDOWN_FILE" ]; then
        echo "$SHUTDOWN_FILE detected. Stopping Gunicorn..."
        kill "$GUNICORN_PID"
        rm -f "$SHUTDOWN_FILE"
        echo "Gunicorn stopped."
        exit $OH_EXIT_CODE
    fi

    # If the process is no longer running (error?), we do not want to keep waiting forever, exit
    if ! kill -0 "$GUNICORN_PID" 2>/dev/null; then
        echo "Gunicorn process $GUNICORN_PID is no longer running. Exiting."
        exit 1
    fi

    sleep 1
done
