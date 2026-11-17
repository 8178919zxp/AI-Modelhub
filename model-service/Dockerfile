# =============================================================================
# AI ModelHub — Model Service (FastAPI + 10 ML models)
# Image: python:3.11-slim
# Port: 8001
#
# All model weights are pre-downloaded during build.
# Runtime is fully offline — no network required.
# =============================================================================

FROM python:3.11-slim-bookworm

WORKDIR /app

# ── System dependencies ─────────────────────────────────────────────
# libgomp1  → PyTorch OpenMP runtime
# libgl1 / libglib2.0 → OpenCV (ultralytics YOLOv8)
# libsm6 / libxext6 / libxrender1 → OpenCV image I/O
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libgcc-s1 \
    && rm -rf /var/lib/apt/lists/*

# ── Python dependencies ─────────────────────────────────────────────
# Strip the Tsinghua mirror line (China-only) so pip uses default PyPI
COPY requirements.txt .
RUN grep -v '^--index-url' requirements.txt > requirements-pypi.txt && \
    pip install --no-cache-dir -r requirements-pypi.txt && \
    pip install --no-cache-dir \
        tensorflow==2.15.0 \
        rembg \
        onnxruntime \
        pandas \
        joblib \
        opencv-python-headless \
    && rm requirements-pypi.txt

# ── Download model weights (network required ONLY in this step) ─────
# Pre-downloads ResNet-50 (~100 MB), BERT (~270 MB), YOLOv8 (~6 MB)
# to /app/weights/ so runtime can load them fully offline.
COPY download_weights.py .
RUN python download_weights.py && rm download_weights.py

# ── Copy local weights (ONNX, Keras h5, scikit-learn pkl, etc.) ─────
COPY weights/ /app/weights/

# ── Application code ────────────────────────────────────────────────
COPY . .

# Pre-create __pycache__ dirs to avoid first-request permission issues
RUN mkdir -p /app/app/__pycache__ /app/app/api/__pycache__ /app/app/models/__pycache__

EXPOSE 8001

# Health check — FastAPI /health endpoint returns instantly
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=10s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
