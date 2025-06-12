import json
import os
import requests
import socket
from flask import Flask, jsonify, request, Response


app = Flask(__name__)

OPENHANDS_FILE_STORE = "/tmp/openhands_file_store"
SHUTDOWN_FILE = "/tmp/reporter/SHUTDOWN"
EXIT_FILE = "/tmp/reporter/OH_EXIT"

OPENHANDS_SESSIONS = os.path.join(OPENHANDS_FILE_STORE, "sessions")

def get_events_dir():
    if not os.path.isdir(OPENHANDS_SESSIONS):
        return None

    sessions = next(os.walk(OPENHANDS_SESSIONS))[1]
    if len(sessions) == 0:
        return None
    elif len(sessions) > 1:
        raise RuntimeError("Expecting existing openhands sessions to be either zero or one")
    else:
        return os.path.join(OPENHANDS_SESSIONS, sessions[0], "events")


@app.route('/health', methods=['GET'])
def health():
    if not os.path.isfile(EXIT_FILE):
        return jsonify({'status': 'RUNNING'}), 200

    with open(EXIT_FILE, 'r', encoding='utf-8') as f:
        code = int(f.read())
        return jsonify({
            'status': 'STOPPED',
            'statusCode': code
        })


@app.route('/events', methods=['GET'])
def get_events():
    # TODO: implement pagination
    events = []
    events_dir = get_events_dir()

    # Ensure the directory exists
    if not events_dir:
        return jsonify([]), 200

    for filename in os.listdir(events_dir):
        if filename.endswith('.json'):
            filepath = os.path.join(events_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    events.append(data)
            except (json.JSONDecodeError, IOError) as e:
                return jsonify({'error': f'Error reading {filename}: {str(e)}'}), 500

    events.sort(key=lambda ev: ev["id"])
    return jsonify(events)


@app.route('/shutdown', methods=['POST'])
def shutdown():
    open(SHUTDOWN_FILE, 'w+b').close()
    return jsonify({'status': 'ok'}), 200


# Development server only
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
