FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --default-timeout=300 --retries 5 -r requirements.txt

COPY app/ ./app/

RUN python -m app.train

EXPOSE 5000

# Single worker + bounded timeouts for predictable CPU use in compose
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "5000", "--timeout-keep-alive", "65", "--timeout-graceful-shutdown", "10"]
