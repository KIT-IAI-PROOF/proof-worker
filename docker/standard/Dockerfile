FROM python:3.12-slim AS python-build
ARG CI_JOB_TOKEN
ENV CI_JOB_TOKEN=${CI_JOB_TOKEN}
RUN apt-get update && apt-get install -y git && mkdir /app && cd /app && \
    git clone https://gitlab-ci-token:$CI_JOB_TOKEN@gitlab.kit.edu/kit/iai/webis/proof2/proof-sim-core.git && \
    cd proof-sim-core && pip install --no-cache-dir .

FROM iai-artifactory.iai.kit.edu/docker-webis/proof-2/proof-worker:latest
ENV PYTHONUNBUFFERED=1
COPY --from=python-build /usr/local/bin/python* /usr/local/bin/
COPY --from=python-build /usr/local/lib /usr/local/lib