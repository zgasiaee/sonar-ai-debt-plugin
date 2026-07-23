
FROM python:3.11-slim


RUN apt-get update && apt-get install -y \
    git \
 && rm -rf /var/lib/apt/lists/*


WORKDIR /app

ENTRYPOINT ["python", "specDetect4ai.py"]

COPY . .


RUN pip install --upgrade pip
RUN pip install -r requirements.txt


CMD ["python", "specDetect4ai.py", "--help"]
