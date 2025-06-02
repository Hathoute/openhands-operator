FROM docker.all-hands.dev/all-hands-ai/runtime:0.39-nikolaik

COPY requirements.txt .
RUN pip3 install -r requirements.txt

COPY events_server.py .
COPY --chmod=0755 entrypoint.sh .