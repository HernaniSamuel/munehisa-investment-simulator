FROM python:3.14-slim AS build
WORKDIR /app
COPY pyproject.toml ./
COPY data_service/ data_service/
RUN pip install --no-cache-dir --prefix=/install .

FROM python:3.14-slim
RUN groupadd --system data-service && \
    useradd --system --gid data-service --home /app --shell /usr/sbin/nologin data-service
WORKDIR /app
COPY --from=build /install /usr/local
COPY data_service/ data_service/
# The base image ships pip/setuptools/wheel; the app is started directly via
# `python -m data_service.main` and never needs them at runtime.
RUN pip uninstall -y pip setuptools wheel
ENV DATA_SERVICE_HOST=0.0.0.0
USER data-service
EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8001/health', timeout=3)"]
CMD ["python", "-m", "data_service.main"]
