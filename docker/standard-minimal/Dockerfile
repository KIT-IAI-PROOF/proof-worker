FROM python:3.12-alpine AS python-build-alpine
ARG CI_JOB_TOKEN
ENV CI_JOB_TOKEN=${CI_JOB_TOKEN}
RUN apt-get update && apt-get install -y git && mkdir /app && cd /app && \
    git clone https://gitlab-ci-token:$CI_JOB_TOKEN@gitlab.kit.edu/kit/iai/webis/proof2/proof-sim-core.git && \
    cd proof-sim-core && pip install --no-cache-dir .
#RUN git clone https://oauth2:$GITLAB_TOKEN@gitlab.kit.edu/kit/iai/webis/proof2/proof-sim-core.git

#FROM proof-worker:alpine-jre-only AS final
FROM iai-artifactory.iai.kit.edu/docker-webis/proof-2/proof-worker:latest
ENV PYTHONUNBUFFERED=1
COPY --from=python-build /usr/local/bin/python /usr/local/bin/python
COPY --from=python-build /usr/local/lib /usr/local/lib