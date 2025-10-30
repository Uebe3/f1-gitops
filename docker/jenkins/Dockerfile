# F1 Data Platform Dockerfile
FROM python:3.9-slim

LABEL maintainer="F1 Data Platform Team"
LABEL version="1.0"
LABEL description="F1 Data Platform - Multi-cloud data processing and analytics"

# Build arguments
ARG BUILD_VERSION=latest
ARG GIT_COMMIT=unknown

# Environment variables
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV BUILD_VERSION=${BUILD_VERSION}
ENV GIT_COMMIT=${GIT_COMMIT}

# Create app directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better Docker layer caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY f1_data_platform/ ./f1_data_platform/
COPY config/ ./config/
COPY run_f1_data_platform.py .
COPY run_local_data_pipeline.py .

# Create non-root user for security
RUN groupadd -r f1user && useradd -r -g f1user f1user
RUN chown -R f1user:f1user /app
USER f1user

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD python -c "import f1_data_platform; print('OK')" || exit 1

# Expose port (if needed for API endpoints)
EXPOSE 8000

# Default command
CMD ["python", "run_f1_data_platform.py"]